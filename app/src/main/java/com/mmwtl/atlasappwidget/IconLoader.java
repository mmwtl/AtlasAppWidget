package com.mmwtl.atlasappwidget;

import android.content.Context;
import android.graphics.ImageDecoder;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.LruCache;

import java.io.File;
import java.io.IOException;
import java.util.Map;

final class IconLoader {
    static final class Result {
        final Drawable drawable;
        final boolean custom;

        Result(Drawable drawable, boolean custom) {
            this.drawable = drawable;
            this.custom = custom;
        }
    }

    private static final int MAX_CACHE_KILOBYTES = 16 * 1024;

    private static final class CacheEntry {
        final Drawable.ConstantState state;
        final int kilobytes;

        CacheEntry(Drawable drawable) {
            state = drawable.getConstantState();
            long bytes;
            if (drawable instanceof BitmapDrawable) {
                bytes = ((BitmapDrawable) drawable).getBitmap().getAllocationByteCount();
            } else {
                long width = Math.max(1, drawable.getIntrinsicWidth());
                long height = Math.max(1, drawable.getIntrinsicHeight());
                bytes = width * height * 4L;
            }
            kilobytes = (int) Math.max(1,
                    Math.min(Integer.MAX_VALUE, (bytes + 1023L) / 1024L));
        }
    }

    private static final LruCache<String, CacheEntry> CACHE =
            new LruCache<String, CacheEntry>(MAX_CACHE_KILOBYTES) {
                @Override
                protected int sizeOf(String key, CacheEntry value) {
                    return value.kilobytes;
                }
            };

    private IconLoader() {
    }

    static Result load(Context context, Prefs prefs, AppEntry entry, int targetPixels) {
        String customUri = prefs.customIcon(entry.componentKey);
        if (customUri != null) {
            Drawable custom = cachedOrDecode(context,
                    "custom|" + entry.componentKey + "|" + customUri + "|" + targetPixels,
                    customUri, targetPixels);
            if (custom != null) {
                return new Result(custom, true);
            }
        }
        String systemKey = "system|" + entry.componentKey;
        Drawable cached = cached(systemKey, context);
        if (cached != null) {
            return new Result(cached, false);
        }
        try {
            Drawable system = context.getPackageManager().getActivityIcon(entry.componentName);
            cache(systemKey, system);
            return new Result(system, false);
        } catch (Exception error) {
            AppLog.warnRateLimited(
                    "system-icon-" + entry.componentKey,
                    "Cannot load system activity icon",
                    error
            );
            Drawable fallback = context.getPackageManager().getDefaultActivityIcon();
            cache(systemKey, fallback);
            return new Result(fallback, false);
        }
    }

    private static Drawable cachedOrDecode(Context context, String cacheKey, String uri, int targetPixels) {
        Drawable cached = cached(cacheKey, context);
        if (cached != null) {
            return cached;
        }
        try {
            File internal = CustomIconStore.resolve(context, uri);
            ImageDecoder.Source source = internal == null
                    ? ImageDecoder.createSource(context.getContentResolver(), Uri.parse(uri))
                    : ImageDecoder.createSource(internal);
            Bitmap bitmap = ImageDecoder.decodeBitmap(source, (decoder, info, sourceInfo) -> {
                // Icons are also rendered by TYPE_APPLICATION_OVERLAY windows and OEM
                // renderers may fall back to a software canvas. A software bitmap remains
                // valid on both hardware and software canvases.
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                int width = Math.max(1, info.getSize().getWidth());
                int height = Math.max(1, info.getSize().getHeight());
                float scale = Math.min(1f, (float) targetPixels / Math.max(width, height));
                decoder.setTargetSize(Math.max(1, Math.round(width * scale)),
                        Math.max(1, Math.round(height * scale)));
            });
            Drawable decoded = new BitmapDrawable(context.getResources(), bitmap);
            cache(cacheKey, decoded);
            return decoded;
        } catch (IOException | SecurityException | IllegalArgumentException error) {
            AppLog.warnRateLimited("icon-" + uri.hashCode(),
                    "Cannot decode custom icon", error);
            return null;
        }
    }

    static void clearComponent(String componentKey) {
        for (Map.Entry<String, CacheEntry> item : CACHE.snapshot().entrySet()) {
            if (item.getKey().contains("|" + componentKey + "|")) {
                CACHE.remove(item.getKey());
            }
        }
        CACHE.remove("system|" + componentKey);
    }

    private static Drawable cached(String key, Context context) {
        CacheEntry entry = CACHE.get(key);
        return entry == null || entry.state == null
                ? null
                : entry.state.newDrawable(context.getResources()).mutate();
    }

    private static void cache(String key, Drawable drawable) {
        if (drawable.getConstantState() != null) {
            CACHE.put(key, new CacheEntry(drawable));
        }
    }
}
