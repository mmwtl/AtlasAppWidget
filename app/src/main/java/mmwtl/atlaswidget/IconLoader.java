package mmwtl.atlaswidget;

import android.content.Context;
import android.graphics.ImageDecoder;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.LruCache;

import java.io.IOException;

final class IconLoader {
    static final class Result {
        final Drawable drawable;
        final boolean custom;

        Result(Drawable drawable, boolean custom) {
            this.drawable = drawable;
            this.custom = custom;
        }
    }

    private static final LruCache<String, Drawable.ConstantState> CACHE = new LruCache<>(96);

    private IconLoader() {
    }

    static Result load(Context context, Prefs prefs, AppEntry entry, int targetPixels) {
        String customUri = prefs.customIcon(entry.componentKey);
        if (customUri != null) {
            Drawable custom = cachedOrDecode(context, entry.componentKey + "|" + customUri + "|" + targetPixels,
                    customUri, targetPixels);
            if (custom != null) {
                return new Result(custom, true);
            }
        }
        try {
            return new Result(context.getPackageManager().getActivityIcon(entry.componentName), false);
        } catch (Exception ignored) {
            return new Result(context.getPackageManager().getDefaultActivityIcon(), false);
        }
    }

    private static Drawable cachedOrDecode(Context context, String cacheKey, String uri, int targetPixels) {
        Drawable.ConstantState state = CACHE.get(cacheKey);
        if (state != null) {
            return state.newDrawable(context.getResources()).mutate();
        }
        try {
            ImageDecoder.Source source = ImageDecoder.createSource(context.getContentResolver(), Uri.parse(uri));
            Drawable decoded = ImageDecoder.decodeDrawable(source, (decoder, info, sourceInfo) -> {
                int width = Math.max(1, info.getSize().getWidth());
                int height = Math.max(1, info.getSize().getHeight());
                float scale = Math.min(1f, (float) targetPixels / Math.max(width, height));
                decoder.setTargetSize(Math.max(1, Math.round(width * scale)),
                        Math.max(1, Math.round(height * scale)));
            });
            if (decoded.getConstantState() != null) {
                CACHE.put(cacheKey, decoded.getConstantState());
            }
            return decoded;
        } catch (IOException | SecurityException | IllegalArgumentException ignored) {
            return null;
        }
    }
}
