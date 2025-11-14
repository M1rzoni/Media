package com.example.learning;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private VideoView videoView;
    private ImageView imageView;
    private TextView timerTextView;
    private List<MediaItem> mediaList;
    private int currentMediaIndex = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable nextRunnable = this::advanceToNext;

    private LottieAnimationView lottieAnimationView;
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimer();
            handler.postDelayed(this, 1000);
        }
    };
    private long startTime;
    private long currentDuration;

    private int downloadedCount = 0;
    private int totalMediaCount = 0;
    private boolean allFilesAlreadyExist = false;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        videoView = findViewById(R.id.videoView);
        imageView = findViewById(R.id.imageView);
        timerTextView = findViewById(R.id.timerTextView);
        lottieAnimationView = findViewById(R.id.lottieAnimationView);

        initializeMediaList(this);
        startSplashAnimation();
    }

    private void startSplashAnimation() {
        lottieAnimationView.setVisibility(View.VISIBLE);
        videoView.setVisibility(View.GONE);
        imageView.setVisibility(View.GONE);
        timerTextView.setVisibility(View.GONE);

        lottieAnimationView.playAnimation();

        if (checkAllFilesExist()) {
            allFilesAlreadyExist = true;
            Toast.makeText(this, "Svi fajlovi su već preuzeti", Toast.LENGTH_LONG).show();
            Log.d("Prefetch", "All files already exist, skipping download");
        }


        prefetchAllMedia(() -> {

            Log.d("Prefetch", "All media files processed");
            if (!allFilesAlreadyExist) {
                Toast.makeText(this, "Svi fajlovi su  preuzeti", Toast.LENGTH_LONG).show();
            }
        });

        lottieAnimationView.addAnimatorListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {}

            @Override
            public void onAnimationEnd(Animator animation) {

                if (areAllFilesDownloaded()) {
                    startMediaPlayback();
                } else {
                    Toast.makeText(MainActivity.this, "Preuzimanje", Toast.LENGTH_SHORT).show();
                    handler.postDelayed(() -> {
                        if (areAllFilesDownloaded()) {
                            startMediaPlayback();
                        } else {
                            startMediaPlayback();
                        }
                    }, 2000);
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {}

            @Override
            public void onAnimationRepeat(Animator animation) {}
        });
    }

    private void startMediaPlayback() {
        lottieAnimationView.setVisibility(View.GONE);
        timerTextView.setVisibility(View.VISIBLE);
        showCurrentMedia();
    }

    private String getFileNameFromUrl(String url, int index, MediaType type) {
        String last = Uri.parse(url).getLastPathSegment();
        if (last == null || last.isEmpty()) {
            return "media_" + index + (type == MediaType.VIDEO ? ".mp4" : ".jpg");
        }
        return last;
    }

    private boolean checkAllFilesExist() {
        if (mediaList == null || mediaList.isEmpty()) return false;

        for (int i = 0; i < mediaList.size(); i++) {
            MediaItem item = mediaList.get(i);
            String fileName = getFileNameFromUrl(item.getUrl(), i, item.getType());
            File localFile = getMediaFile(fileName);
            if (!localFile.exists()) {
                return false;
            }
        }
        return true;
    }

    private void prefetchAllMedia(Runnable onComplete) {
        if (mediaList == null || mediaList.isEmpty()) {
            runOnUiThread(onComplete);
            return;
        }

        totalMediaCount = mediaList.size();
        downloadedCount = 0;

        new Thread(() -> {
            OkHttpClient client = new OkHttpClient();
            for (int i = 0; i < mediaList.size(); i++) {
                MediaItem item = mediaList.get(i);
                String fileName = getFileNameFromUrl(item.getUrl(), i, item.getType());
                File localFile = getMediaFile(fileName);

                if (!localFile.exists()) {
                    try {
                        Log.d("Prefetch", "Downloading: " + item.getUrl());
                        final int currentIndex = i;
                        Request request = new Request.Builder().url(item.getUrl()).build();
                        Response response = client.newCall(request).execute();
                        if (response.isSuccessful() && response.body() != null) {
                            InputStream input = response.body().byteStream();
                            FileOutputStream output = new FileOutputStream(localFile);
                            byte[] buffer = new byte[8192];
                            int read;
                            while ((read = input.read(buffer)) != -1) {
                                output.write(buffer, 0, read);
                            }
                            output.close();
                            input.close();
                            item.setLocalPath(localFile.getAbsolutePath());
                            Log.d("Prefetch", "Successfully downloaded: " + fileName);

                            runOnUiThread(() -> {
                                downloadedCount++;
                                String fileType = item.getType() == MediaType.VIDEO ? "🎥 Video" : "🖼️ Slika";
                                Toast.makeText(MainActivity.this,
                                        "✅ " + fileType + " preuzet: " + fileName +
                                                " (" + downloadedCount + "/" + totalMediaCount + ")",
                                        Toast.LENGTH_SHORT).show();
                            });
                        } else {
                            Log.e("Prefetch", "Failed to download: " + item.getUrl());
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivity.this,
                                        "Greška pri preuzimanju: " + fileName,
                                        Toast.LENGTH_SHORT).show();
                            });
                        }
                    } catch (Exception e) {
                        Log.e("Prefetch", "Error downloading: " + item.getUrl(), e);
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this,
                                    "Greška: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    item.setLocalPath(localFile.getAbsolutePath());
                    downloadedCount++;
                    Log.d("Prefetch", "File already exists: " + fileName);

                    runOnUiThread(() -> {
                        String fileType = item.getType() == MediaType.VIDEO ? "Video" : "Slika";
                        Toast.makeText(MainActivity.this,
                                "fijl" + fileType + " već postoji: " + fileName +
                                        " (" + downloadedCount + "/" + totalMediaCount + ")",
                                Toast.LENGTH_SHORT).show();
                    });
                }
            }

            runOnUiThread(() -> {
                Log.d("Prefetch", "Download completed: " + downloadedCount + "/" + totalMediaCount);
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        }).start();
    }

    private boolean areAllFilesDownloaded() {
        if (mediaList == null || mediaList.isEmpty()) return false;

        for (MediaItem item : mediaList) {
            if (item.getLocalPath() == null) {
                String fileName = getFileNameFromUrl(item.getUrl(), mediaList.indexOf(item), item.getType());
                File localFile = getMediaFile(fileName);
                if (!localFile.exists()) {
                    return false;
                }
            }
        }
        return true;
    }

    private File getMediaFile(String fileName) {
        File dir = new File(getFilesDir(), "media");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, fileName);
    }

    private void downloadFile(String url, File destination, Runnable onComplete) {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(url).build();
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    InputStream input = response.body().byteStream();
                    FileOutputStream output = new FileOutputStream(destination);
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = input.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                    }
                    output.close();
                    input.close();

                    runOnUiThread(onComplete);
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this,
                                "Greška pri preuzimanju fajla",
                                Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this,
                            "Greška: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void initializeMediaList(Context context) {
        mediaList = new ArrayList<>();

        Properties properties = PropertiesLoader.loadProperties(context, R.raw.media);

        int mediaCount = Integer.parseInt(properties.getProperty("media.count", "0"));

        for (int i = 1; i <= mediaCount; i++) {
            String url = properties.getProperty("media." + i + ".url");
            String typeStr = properties.getProperty("media." + i + ".type");
            String durationStr = properties.getProperty("media." + i + ".duration");

            if (url != null && typeStr != null && durationStr != null) {
                MediaType mediaType = MediaType.fromString(typeStr);
                int duration = Integer.parseInt(durationStr);

                mediaList.add(new MediaItem(url, mediaType, duration));
            }
        }

        if (mediaList.isEmpty()) {
            Log.e("MediaList", "No media items loaded from properties, using defaults");
            mediaList.add(new MediaItem(
                    "https://picsum.photos/200/300",
                    MediaType.IMAGE,
                    5000
            ));
        }
    }

    private void showCurrentMedia() {
        if (mediaList == null || mediaList.isEmpty()) {
            Log.e("MediaList", "Media list is null or empty");
            return;
        }
        if (currentMediaIndex >= mediaList.size()) currentMediaIndex = 0;

        MediaItem current = mediaList.get(currentMediaIndex);

        String fileName = getFileNameFromUrl(current.getUrl(), currentMediaIndex, current.getType());
        File localFile = getMediaFile(fileName);

        if (localFile.exists()) {
            current.setLocalPath(localFile.getAbsolutePath());
            Log.d("Media", "Using local file: " + localFile.getAbsolutePath());


            String fileType = current.getType() == MediaType.VIDEO ? " Video" : "Slika";
            Toast.makeText(this,
                    "Pocetak: " + fileType + " - " + fileName,
                    Toast.LENGTH_SHORT).show();

            displayMedia(current);
        } else {
            Log.w("Media", "File not found, downloading: " + fileName);
            String fileType = current.getType() == MediaType.VIDEO ? "Video" : "Slika";
            Toast.makeText(this,
                    "Preuzimanje: " + fileType + " - " + fileName,
                    Toast.LENGTH_SHORT).show();

            downloadFile(current.getUrl(), localFile, () -> {
                current.setLocalPath(localFile.getAbsolutePath());
                Toast.makeText(this,
                        " Fajl je preuzet: " + fileType + " - " + fileName,
                        Toast.LENGTH_SHORT).show();
                displayMedia(current);
            });
        }
    }

    private void displayMedia(MediaItem mediaItem) {
        handler.removeCallbacks(nextRunnable);
        handler.removeCallbacks(timerRunnable);

        startTime = System.currentTimeMillis();
        currentDuration = mediaItem.getDuration();

        if (mediaItem.getType() == MediaType.VIDEO) {
            showVideo(mediaItem);
        } else {
            showImage(mediaItem);
        }

        handler.post(timerRunnable);
    }

    private void updateTimer() {
        long elapsed = System.currentTimeMillis() - startTime;
        long remaining = currentDuration - elapsed;

        if (remaining < 0) remaining = 0;

        long seconds = remaining / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        String timeText = String.format("%02d:%02d", minutes, seconds);
        timerTextView.setText(timeText);
    }

    private void showVideo(MediaItem mediaItem) {
        imageView.setVisibility(View.GONE);
        videoView.setVisibility(View.VISIBLE);

        try {
            if (mediaItem.getLocalPath() == null) {
                Log.e("Video", "Local path is null for video");
                handler.postDelayed(nextRunnable, 1000);
                return;
            }

            File file = new File(mediaItem.getLocalPath());
            if (!file.exists()) {
                Log.e("Video", "Video file doesn't exist: " + mediaItem.getLocalPath());
                handler.postDelayed(nextRunnable, 1000);
                return;
            }

            Uri uri = Uri.fromFile(file);
            videoView.setVideoURI(uri);

            videoView.setOnPreparedListener(mp -> {
                mp.setLooping(false);
                videoView.start();

                int videoDuration = mp.getDuration();
                long scheduleDelay = mediaItem.getDuration();
                if (videoDuration > 0) {
                    scheduleDelay = Math.min(scheduleDelay, videoDuration);
                }
                handler.postDelayed(nextRunnable, scheduleDelay);
            });

            videoView.setOnCompletionListener(mp -> {
                handler.removeCallbacks(nextRunnable);
                handler.post(nextRunnable);
            });

            videoView.setOnErrorListener((mp, what, extra) -> {
                Log.e("Video", "Video error: " + what + ", " + extra);
                handler.removeCallbacks(nextRunnable);
                handler.postDelayed(nextRunnable, 1000);
                return true;
            });

        } catch (Exception e) {
            e.printStackTrace();
            handler.postDelayed(nextRunnable, 1000);
        }
    }

    private void showImage(MediaItem mediaItem) {
        videoView.setVisibility(View.GONE);
        imageView.setVisibility(View.VISIBLE);

        try {
            if (mediaItem.getLocalPath() == null) {
                Log.e("Image", "Local path is null for image");
                handler.postDelayed(nextRunnable, 1000);
                return;
            }

            File file = new File(mediaItem.getLocalPath());
            if (!file.exists()) {
                Log.e("Image", "Image file doesn't exist: " + mediaItem.getLocalPath());
                handler.postDelayed(nextRunnable, 1000);
                return;
            }

            DisplayMetrics dm = getResources().getDisplayMetrics();
            int targetW = dm.widthPixels;
            int targetH = dm.heightPixels;

            RequestOptions options = new RequestOptions()
                    .override(targetW, targetH)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC);

            Glide.with(this)
                    .load(file)
                    .thumbnail(0.1f)
                    .apply(options)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Log.e("Glide", "Image load failed");
                            handler.postDelayed(nextRunnable, 1000);
                            return true;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            handler.postDelayed(nextRunnable, mediaItem.getDuration());
                            return false;
                        }
                    })
                    .into(imageView);

        } catch (Exception e) {
            e.printStackTrace();
            handler.postDelayed(nextRunnable, 1000);
        }
    }

    private void advanceToNext() {
        currentMediaIndex++;
        if (currentMediaIndex >= mediaList.size()) currentMediaIndex = 0;
        showCurrentMedia();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(null);
        if (videoView != null && videoView.isPlaying()) videoView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mediaList != null && !mediaList.isEmpty()) {
            showCurrentMedia();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (videoView != null) videoView.stopPlayback();
    }
}