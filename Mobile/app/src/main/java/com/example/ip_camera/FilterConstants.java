package com.example.ip_camera;

import android.graphics.ColorMatrix;

public class FilterConstants {
    public static final int NONE = 0;
    public static final int COLD = 1;
    public static final int WARM = 2;
    public static final int EFFECT = 3;

    public static ColorMatrix getColorMatrix(int filter) {
        switch (filter) {
            case COLD:
                return new ColorMatrix(new float[]{
                        0.8f, 0, 0, 0, 0,
                        0, 0.9f, 0, 0, 0,
                        0, 0, 1.1f, 0, 10,
                        0, 0, 0, 1, 0
                });
            case WARM:
                return new ColorMatrix(new float[]{
                        1.05f, 0, 0, 0, 25,
                        0, 1, 0, 0, 10,
                        0, 0, 0.8f, 0, 0,
                        0, 0, 0, 1, 0
                });
            case EFFECT: {
                ColorMatrix cm = new ColorMatrix();
                cm.setSaturation(1.6f);
                ColorMatrix contrast = new ColorMatrix(new float[]{
                        1.1f, 0, 0, 0, -35,
                        0, 1.1f, 0, 0, -35,
                        0, 0, 1.1f, 0, -35,
                        0, 0, 0, 1, 0
                });
                cm.postConcat(contrast);
                return cm;
            }
            default:
                return new ColorMatrix();
        }
    }
}
