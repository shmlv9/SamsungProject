package com.example.ip_camera;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class OnboardingActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "onboarding";
    private static final String KEY_DONE = "done";

    private int currentPage = 0;

    private final int[] images = {R.drawable.onb1, R.drawable.onb2, R.drawable.onb3};
    private final int[] titles = {R.string.onb_title_1, R.string.onb_title_2, R.string.onb_title_3};
    private final int[] descs = {R.string.onb_desc_1, R.string.onb_desc_2, R.string.onb_desc_3};
    private final int[] dots = {R.id.dot0, R.id.dot1, R.id.dot2};

    private ImageView onboardingImage;
    private TextView onboardingTitle;
    private TextView onboardingDesc;
    private Button btnContinue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        onboardingImage = findViewById(R.id.onboardingImage);
        onboardingTitle = findViewById(R.id.onboardingTitle);
        onboardingDesc = findViewById(R.id.onboardingDesc);
        btnContinue = findViewById(R.id.btnContinue);

        updatePage();

        btnContinue.setOnClickListener(v -> {
            if (currentPage < 2) {
                currentPage++;
                updatePage();
            } else {
                setOnboardingDone();
                startActivity(new Intent(OnboardingActivity.this, MainActivity.class));
                finish();
            }
        });
    }

    private void updatePage() {
        onboardingImage.setImageResource(images[currentPage]);
        onboardingTitle.setText(titles[currentPage]);
        onboardingDesc.setText(descs[currentPage]);

        for (int i = 0; i < dots.length; i++) {
            View dot = findViewById(dots[i]);
            dot.setBackgroundResource(i == currentPage
                    ? R.drawable.dot_active : R.drawable.dot_inactive);
        }

        if (currentPage == 2) {
            btnContinue.setText(R.string.onb_start);
        } else {
            btnContinue.setText(R.string.onb_continue);
        }
    }

    private void setOnboardingDone() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DONE, true)
                .apply();
    }

    static boolean isDone(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_DONE, false);
    }
}
