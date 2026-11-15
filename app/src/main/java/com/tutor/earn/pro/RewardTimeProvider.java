package com.tutor.earn.pro;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;

import androidx.annotation.NonNull;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Provides monotonic uptime, boot count, and authoritative HTTPS server UTC timestamps
 * so 24-hour reward cooldown enforcement cannot be bypassed via device clock changes.
 */
public interface RewardTimeProvider {

    interface ServerTimeCallback {
        void onResult(long serverUtcTimeMs);
    }

    long elapsedRealtimeMs();

    long wallClockMs();

    int getBootCount(@NonNull Context context);

    void fetchAuthoritativeServerTimeMs(@NonNull ServerTimeCallback callback);

    /**
     * Production implementation querying an HTTPS server's authoritative HTTP Date header.
     */
    final class NetworkTimeProvider implements RewardTimeProvider {

        private static final String[] TIME_ENDPOINTS = new String[]{
                "https://clients3.google.com/generate_204",
                "https://www.google.com"
        };
        private static final int TIMEOUT_MS = 5000;

        private final ExecutorService executor = Executors.newSingleThreadExecutor();

        @Override
        public long elapsedRealtimeMs() {
            return SystemClock.elapsedRealtime();
        }

        @Override
        public long wallClockMs() {
            return System.currentTimeMillis();
        }

        @Override
        public int getBootCount(@NonNull Context context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    return Settings.Global.getInt(
                            context.getContentResolver(),
                            Settings.Global.BOOT_COUNT,
                            0
                    );
                } catch (Throwable ignored) {
                    return 0;
                }
            }
            return 0;
        }

        @Override
        public void fetchAuthoritativeServerTimeMs(@NonNull ServerTimeCallback callback) {
            executor.execute(() -> {
                long resolved = -1L;
                for (String endpoint : TIME_ENDPOINTS) {
                    resolved = queryHttpDateHeader(endpoint);
                    if (resolved > 0L) {
                        break;
                    }
                }
                callback.onResult(resolved);
            });
        }

        private long queryHttpDateHeader(@NonNull String endpoint) {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(endpoint);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("HEAD");
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setUseCaches(false);
                connection.connect();
                long serverDate = connection.getDate();
                return serverDate > 0L ? serverDate : -1L;
            } catch (Throwable ignored) {
                return -1L;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }
}
