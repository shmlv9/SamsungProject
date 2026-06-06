package com.example.ip_camera;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Class<?> target = OnboardingActivity.isDone(this)
                    ? MainActivity.class : OnboardingActivity.class;
            startActivity(new Intent(SplashActivity.this, target));
            finish();
        }, 1500);
    }
}
