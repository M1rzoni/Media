package com.example.learning;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MediaCacheManager {
    private static MediaCacheManager instance;
    private final File cacheDir;
    private final Set<String> downloadingUrls;
    private final OkHttpClient httpClient;

    public static synchronized MediaCacheManager getInstance(Context context) {
        if (instance == null) {
            instance = new MediaCacheManager(context);
        }
        return instance;
    }

    private MediaCacheManager(Context context) {
        cacheDir = new File(context.getFilesDir(), "media_cache");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        downloadingUrls = new HashSet<>();
        httpClient = new OkHttpClient();
    }

    public File getMediaFile(String url) {
        String filename = generateFilename(url);
        return new File(cacheDir, filename);
    }

    public boolean isCached(String url) {
        return getMediaFile(url).exists();
    }

    public void downloadMediaAsync(String url, DownloadListener listener) {
        if (isCached(url)) {
            listener.onSuccess(getMediaFile(url));
            return;
        }

        synchronized (downloadingUrls) {
            if (downloadingUrls.contains(url)) {
                listener.onProgress();
                return;
            }
            downloadingUrls.add(url);
        }

        new Thread(() -> {
            try {
                File outputFile = getMediaFile(url);
                Request request = new Request.Builder().url(url).build();
                Response response = httpClient.newCall(request).execute();

                if (response.isSuccessful() && response.body() != null) {
                    InputStream input = response.body().byteStream();
                    FileOutputStream output = new FileOutputStream(outputFile);
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                    output.close();
                    input.close();

                    synchronized (downloadingUrls) {
                        downloadingUrls.remove(url);
                    }
                    listener.onSuccess(outputFile);
                } else {
                    synchronized (downloadingUrls) {
                        downloadingUrls.remove(url);
                    }
                    listener.onError(new Exception("Download failed: " + response.code()));
                }
            } catch (Exception e) {
                synchronized (downloadingUrls) {
                    downloadingUrls.remove(url);
                }
                listener.onError(e);
            }
        }).start();
    }

    public void clearCache() {
        File[] files = cacheDir.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
    }

    public long getCacheSize() {
        long size = 0;
        File[] files = cacheDir.listFiles();
        if (files != null) {
            for (File file : files) {
                size += file.length();
            }
        }
        return size;
    }

    private String generateFilename(String url) {
        return String.valueOf(url.hashCode()) + getFileExtension(url);
    }

    private String getFileExtension(String url) {
        if (url.contains(".mp4") || url.contains(".webm") || url.contains(".avi")) {
            return ".mp4";
        }
        return ".jpg";
    }

    public interface DownloadListener {
        void onSuccess(File file);
        void onError(Exception e);
        void onProgress();
    }
}