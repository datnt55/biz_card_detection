package com.mymeishi.liveedgedetection.interfaces;

import android.graphics.Bitmap;

/**
 * Interface between activity and surface view
 */

public interface IScanner {
    void onPictureClicked(Bitmap bitmap);

    void onPreviewCropped(Bitmap bitmap);
}
