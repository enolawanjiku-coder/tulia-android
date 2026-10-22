package com.tulia.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class ImageCompressor {

    public static final class Result {
        @NonNull
        public final byte[] bytes;
        public final int width;
        public final int height;

        Result(@NonNull byte[] bytes, int width, int height) {
            this.bytes = bytes;
            this.width = width;
            this.height = height;
        }
    }

    private ImageCompressor() {
    }

    @NonNull
    public static Result compress(@NonNull Context context,
                                  @NonNull Uri uri,
                                  int maxDimension,
                                  int quality) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = open(context, uri)) {
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Could not read this image.");
        }

        int sampleSize = 1;
        while (bounds.outWidth / sampleSize > maxDimension * 2
                || bounds.outHeight / sampleSize > maxDimension * 2) {
            sampleSize *= 2;
        }

        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = sampleSize;
        Bitmap bitmap;
        try (InputStream input = open(context, uri)) {
            bitmap = BitmapFactory.decodeStream(input, null, decode);
        }
        if (bitmap == null) {
            throw new IOException("Could not decode this image.");
        }

        bitmap = applyRotation(context, uri, bitmap);
        bitmap = scaleDown(bitmap, maxDimension);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
            throw new IOException("Could not compress this image.");
        }
        Result result = new Result(output.toByteArray(), bitmap.getWidth(), bitmap.getHeight());
        bitmap.recycle();
        return result;
    }

    private static InputStream open(@NonNull Context context, @NonNull Uri uri)
            throws IOException {
        InputStream input = context.getContentResolver().openInputStream(uri);
        if (input == null) {
            throw new IOException("Could not open this image.");
        }
        return input;
    }

    @NonNull
    private static Bitmap applyRotation(@NonNull Context context,
                                        @NonNull Uri uri,
                                        @NonNull Bitmap bitmap) {
        int rotation = 0;
        try (InputStream input = open(context, uri)) {
            ExifInterface exif = new ExifInterface(input);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                rotation = 90;
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                rotation = 180;
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                rotation = 270;
            }
        } catch (IOException ignored) {
            return bitmap;
        }

        if (rotation == 0) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        Bitmap rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) {
            bitmap.recycle();
        }
        return rotated;
    }

    @NonNull
    private static Bitmap scaleDown(@NonNull Bitmap bitmap, int maxDimension) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int largest = Math.max(width, height);
        if (largest <= maxDimension) {
            return bitmap;
        }
        float scale = maxDimension / (float) largest;
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                Math.round(width * scale), Math.round(height * scale), true);
        if (scaled != bitmap) {
            bitmap.recycle();
        }
        return scaled;
    }
}
