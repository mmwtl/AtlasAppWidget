package com.mmwtl.atlasappwidget;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class CustomIconStore {
    static final String INTERNAL_PREFIX = "internal:";
    private static final int MAX_IMPORTED_BITMAP_DIMENSION = 192;
    private static final int BUFFER_SIZE = 16 * 1024;
    private static final long MAX_ICON_BYTES = 25L * 1024L * 1024L;

    private CustomIconStore() {
    }

    static String importIcon(Context context, Uri source, String componentKey) throws IOException {
        File directory = iconDirectory(context);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create custom icon directory");
        }
        String fileName = digest(componentKey) + ".img";
        File destination = new File(directory, fileName);
        File temporary = new File(directory, fileName + ".tmp");
        long total = 0;
        try (InputStream input = context.getContentResolver().openInputStream(source);
             FileOutputStream output = new FileOutputStream(temporary, false)) {
            if (input == null) {
                throw new IOException("Document provider returned no data");
            }
            byte[] buffer = new byte[BUFFER_SIZE];
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_ICON_BYTES) {
                    throw new IOException("Selected image is larger than 25 MB");
                }
                output.write(buffer, 0, count);
            }
            output.getFD().sync();
        } catch (IOException error) {
            temporary.delete();
            throw error;
        }
        if (total == 0) {
            temporary.delete();
            throw new IOException("Selected image is empty");
        }
        try {
            Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException error) {
            temporary.delete();
            throw error;
        }
        return INTERNAL_PREFIX + fileName;
    }

    static String importIcon(Context context, Bitmap bitmap, String componentKey)
            throws IOException {
        if (bitmap == null || bitmap.isRecycled()) {
            throw new IOException("Shortcut icon bitmap is unavailable");
        }
        File temporary = prepareTemporary(context, componentKey);
        long bytes;
        Bitmap encoded = bitmap;
        boolean ownsEncoded = false;
        int largest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (largest > MAX_IMPORTED_BITMAP_DIMENSION) {
            float scale = (float) MAX_IMPORTED_BITMAP_DIMENSION / largest;
            encoded = Bitmap.createScaledBitmap(bitmap,
                    Math.max(1, Math.round(bitmap.getWidth() * scale)),
                    Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
            ownsEncoded = encoded != bitmap;
        }
        try (FileOutputStream output = new FileOutputStream(temporary, false)) {
            if (!encoded.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IOException("Cannot encode shortcut icon");
            }
            output.getFD().sync();
            bytes = temporary.length();
        } catch (IOException error) {
            temporary.delete();
            throw error;
        } finally {
            if (ownsEncoded) encoded.recycle();
        }
        return finishImport(context, componentKey, temporary, bytes);
    }

    static String importIcon(Context context, Drawable drawable, String componentKey)
            throws IOException {
        if (drawable == null) {
            throw new IOException("Shortcut icon drawable is unavailable");
        }
        if (drawable instanceof BitmapDrawable bitmapDrawable) {
            return importIcon(context, bitmapDrawable.getBitmap(), componentKey);
        }
        int intrinsicWidth = drawable.getIntrinsicWidth();
        int intrinsicHeight = drawable.getIntrinsicHeight();
        int width = intrinsicWidth > 0
                ? Math.min(MAX_IMPORTED_BITMAP_DIMENSION, intrinsicWidth)
                : MAX_IMPORTED_BITMAP_DIMENSION;
        int height = intrinsicHeight > 0
                ? Math.min(MAX_IMPORTED_BITMAP_DIMENSION, intrinsicHeight)
                : MAX_IMPORTED_BITMAP_DIMENSION;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            drawable.setBounds(0, 0, width, height);
            drawable.draw(new Canvas(bitmap));
            return importIcon(context, bitmap, componentKey);
        } finally {
            bitmap.recycle();
        }
    }

    static String importIcon(Context context, byte[] bytes, String componentKey)
            throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_ICON_BYTES) {
            throw new IOException("Shortcut icon data is invalid");
        }
        File temporary = prepareTemporary(context, componentKey);
        try (FileOutputStream output = new FileOutputStream(temporary, false)) {
            output.write(bytes);
            output.getFD().sync();
        } catch (IOException error) {
            temporary.delete();
            throw error;
        }
        return finishImport(context, componentKey, temporary, bytes.length);
    }

    static String importIcon(Context context, Intent.ShortcutIconResource resource,
            String componentKey)
            throws IOException {
        if (resource == null || resource.packageName == null || resource.resourceName == null) {
            throw new IOException("Shortcut icon resource is invalid");
        }
        try {
            PackageManager packageManager = context.getPackageManager();
            android.content.res.Resources resources =
                    packageManager.getResourcesForApplication(resource.packageName);
            int resourceId = resources.getIdentifier(
                    resource.resourceName, null, resource.packageName);
            if (resourceId == 0) {
                int separator = resource.resourceName.indexOf(':');
                String unqualified = separator >= 0
                        ? resource.resourceName.substring(separator + 1)
                        : resource.resourceName;
                if (unqualified.startsWith("@")) unqualified = unqualified.substring(1);
                resourceId = resources.getIdentifier(unqualified, null, resource.packageName);
            }
            if (resourceId == 0) throw new IOException("Shortcut icon resource not found");
            Drawable drawable = resources.getDrawable(resourceId, context.getTheme());
            return importIcon(context, drawable, componentKey);
        } catch (PackageManager.NameNotFoundException | RuntimeException error) {
            throw new IOException("Cannot load shortcut icon resource", error);
        }
    }

    private static File prepareTemporary(Context context, String componentKey) throws IOException {
        File directory = iconDirectory(context);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create custom icon directory");
        }
        return new File(directory, digest(componentKey) + ".img.tmp");
    }

    private static String finishImport(Context context, String componentKey, File temporary,
            long bytes) throws IOException {
        if (bytes <= 0) {
            temporary.delete();
            throw new IOException("Shortcut icon is empty");
        }
        if (bytes > MAX_ICON_BYTES) {
            temporary.delete();
            throw new IOException("Shortcut icon is larger than 25 MB");
        }
        File destination = new File(iconDirectory(context), digest(componentKey) + ".img");
        try {
            Files.move(temporary.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            temporary.delete();
            throw error;
        }
        return INTERNAL_PREFIX + destination.getName();
    }

    static File resolve(Context context, String storedValue) {
        if (storedValue == null || !storedValue.startsWith(INTERNAL_PREFIX)) {
            return null;
        }
        String fileName = storedValue.substring(INTERNAL_PREFIX.length());
        if (fileName.isEmpty() || fileName.contains("/") || fileName.contains("\\")) {
            return null;
        }
        return new File(iconDirectory(context), fileName);
    }

    static void delete(Context context, String storedValue) {
        File file = resolve(context, storedValue);
        if (file != null && file.isFile()) {
            file.delete();
        }
    }

    private static File iconDirectory(Context context) {
        return new File(context.getFilesDir(), "custom_icons");
    }

    private static String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
