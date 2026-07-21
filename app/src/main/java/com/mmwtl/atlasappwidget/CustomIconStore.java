package com.mmwtl.atlasappwidget;

import android.content.Context;
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
