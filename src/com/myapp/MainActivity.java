package com.myapp;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private static final int PICK_IMAGE_REQUEST = 100;
    private static final int PERMISSION_REQUEST_CODE = 200;
    private TextView statusText;
    private Button btn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 100, 50, 50);

        // Titre de l'application
        TextView title = new TextView(this);
        title.setText("GORAD PHOTO");
        title.setTextSize(28);
        title.setTextColor(Color.parseColor("#00FF41"));
        title.setGravity(android.view.Gravity.CENTER);
        title.setPadding(0, 0, 0, 50);

        // Bouton principal
        btn = new Button(this);
        btn.setText("📸 Choisir une photo et l'envoyer");
        btn.setTextSize(18);
        btn.setEnabled(false);

        // Texte de statut (affiche les messages de debug)
        statusText = new TextView(this);
        statusText.setText("⏳ Vérification des permissions...");
        statusText.setTextSize(16);
        statusText.setPadding(0, 50, 0, 0);
        statusText.setGravity(android.view.Gravity.CENTER);

        btn.setOnClickListener(v -> ouvrirSelecteur());

        layout.addView(title);
        layout.addView(btn);
        layout.addView(statusText);
        setContentView(layout);

        // Demande de permission à l'ouverture
        demanderPermission();
    }

    private void demanderPermission() {
        if (Build.VERSION.SDK_INT <= 30) {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 
                    PERMISSION_REQUEST_CODE
                );
            } else {
                autoriserUtilisation();
            }
        } else {
            autoriserUtilisation();
        }
    }

    private void autoriserUtilisation() {
        statusText.setText("✅ Prêt. Sélectionnez une photo.");
        statusText.setTextColor(Color.parseColor("#00AA00"));
        btn.setEnabled(true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                autoriserUtilisation();
            } else {
                statusText.setText("❌ Permission refusée. Impossible de lire les photos.");
                statusText.setTextColor(Color.RED);
                btn.setEnabled(false);
            }
        }
    }

    private void ouvrirSelecteur() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            
            statusText.setText("⏳ Envoi en cours...");
            statusText.setTextColor(Color.parseColor("#FFA500"));
            btn.setEnabled(false);
            
            new Thread(() -> {
                ResultatEnvoi resultat = envoyerImage(imageUri);
                runOnUiThread(() -> {
                    if (resultat.succes) {
                        statusText.setText("✅ PHOTO ENVOYÉE !\n\n" + resultat.message);
                        statusText.setTextColor(Color.parseColor("#00AA00"));
                        Toast.makeText(MainActivity.this, "Succès !", Toast.LENGTH_LONG).show();
                    } else {
                        statusText.setText("❌ ERREUR D'ENVOI\n\n" + resultat.message);
                        statusText.setTextColor(Color.RED);
                        Toast.makeText(MainActivity.this, "Échec", Toast.LENGTH_LONG).show();
                    }
                    btn.setEnabled(true);
                });
            }).start();
        }
    }

    // Classe pour stocker le résultat de l'envoi
    private static class ResultatEnvoi {
        boolean succes;
        String message;
        ResultatEnvoi(boolean succes, String message) {
            this.succes = succes;
            this.message = message;
        }
    }

    private ResultatEnvoi envoyerImage(Uri imageUri) {
        String urlString = "https://cybergorad.alwaysdata.net/upload.php"; // ⚠️ ADAPTEZ L'URL
        String boundary = "*****";
        String lineEnd = "\r\n";
        String twoHyphens = "--";

        try {
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

            InputStream is = getContentResolver().openInputStream(imageUri);
            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalBytes = 0;
            while ((bytesRead = is.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            is.close();
            dos.writeBytes(lineEnd);
            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
            dos.flush();
            dos.close();

            int responseCode = conn.getResponseCode();
            
            // Lecture de la réponse du serveur
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
            debug += "Taille : " + (totalBytes / 1024) + " Ko\n";
            debug += "Serveur : " + responseMessage;

            return new ResultatEnvoi(responseCode == 200, debug);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResultatEnvoi(false, "Exception : " + e.getClass().getSimpleName() + "\n" + e.getMessage());
        }
    }
}
