package com.example.ip_camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "settings";
    private static final String KEY_BG_EXISTS = "background_exists";

    private ImageView bgPreview;
    private TextView bgStatus;
    private MaterialButton btnChoose;
    private MaterialButton btnRemove;

    private final ActivityResultContracts.PickVisualMedia pickContract =
            new ActivityResultContracts.PickVisualMedia();

    private final ActivityResultContracts.PickVisualMedia.VisualMediaType imageType =
            ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE;

    private final androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest> pickLauncher =
            registerForActivityResult(pickContract, uri -> {
                if (uri != null) {
                    saveBackgroundImage(uri);
                    updatePreview();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        bgPreview = findViewById(R.id.bgPreview);
        bgStatus = findViewById(R.id.bgStatus);
        btnChoose = findViewById(R.id.btnChooseBg);
        btnRemove = findViewById(R.id.btnRemoveBg);

        updatePreview();

        btnChoose.setOnClickListener(v ->
                pickLauncher.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(imageType)
                        .build()));

        btnRemove.setOnClickListener(v -> {
            deleteBackgroundImage();
            updatePreview();
        });

        findViewById(R.id.btnApply).setOnClickListener(v -> finish());
    }

    private void updatePreview() {
        File bgFile = new File(getFilesDir(), "background.jpg");
        if (bgFile.exists()) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 4;
            Bitmap preview = BitmapFactory.decodeFile(bgFile.getAbsolutePath(), opts);
            if (preview != null) {
                bgPreview.setImageBitmap(preview);
            }
            bgStatus.setText(R.string.settings_bg_title);
            btnRemove.setEnabled(true);
        } else {
            bgPreview.setImageResource(R.drawable.icon);
            bgStatus.setText(R.string.settings_bg_empty);
            btnRemove.setEnabled(false);
        }
    }

    private void saveBackgroundImage(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 2;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeStream(is, null, opts);
            if (bitmap == null) return;

            File bgFile = new File(getFilesDir(), "background.jpg");
            try (FileOutputStream fos = new FileOutputStream(bgFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            }
            bitmap.recycle();

            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_BG_EXISTS, true)
                    .apply();
        } catch (Exception e) {
            bgStatus.setText(R.string.settings_bg_error);
        }
    }

    private void deleteBackgroundImage() {
        File bgFile = new File(getFilesDir(), "background.jpg");
        bgFile.delete();
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_BG_EXISTS, false)
                .apply();
    }

    static boolean hasBackgroundImage(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_BG_EXISTS, false);
    }

    static Bitmap loadBackgroundBitmap(Context context) {
        File bgFile = new File(context.getFilesDir(), "background.jpg");
        if (bgFile.exists()) {
            return BitmapFactory.decodeFile(bgFile.getAbsolutePath());
        }
        return null;
    }
}
