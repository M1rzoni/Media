package com.example.learning;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.content.Intent;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import android.view.ViewGroup;
import android.media.MediaPlayer;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import retrofit2.Call;

public class MainActivity extends AppCompatActivity {

    private VideoView videoView;
    private ImageView imageView;
    private TextView timerTextView;
    private List<MediaItem> mediaList;
    private int currentMediaIndex = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable nextRunnable = this::advanceToNext;

    private LottieAnimationView lottieAnimationView;
    private MediaCacheManager cacheManager;

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
    private GestureDetector gestureDetector;

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

        cacheManager = MediaCacheManager.getInstance(this);

        initializeGestureDetector();

        // Postavi touch listener na root layout
        FrameLayout rootLayout = findViewById(R.id.root);
        rootLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                return true;
            }
        });


        initializeMediaList(this);
        startSplashAnimation();

    }

    private void initializeGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                Log.d("DoubleClick", "Double tap detected");
                openPlayerActivity();
                return true;
            }
        });
    }

    private void openPlayerActivity() {
        // Pauziraj medije
        if (videoView.isPlaying()) {
            videoView.pause();
        }

        // Zaustavi timere
        handler.removeCallbacks(timerRunnable);
        handler.removeCallbacks(nextRunnable);

        // Pokreni PlayerActivity
        Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
        startActivity(intent);

        // Dodaj fade animaciju
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
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
                Toast.makeText(this, "Svi fajlovi su preuzeti", Toast.LENGTH_LONG).show();
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
                        startMediaPlayback(); // Uvijek pokreni playback
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

    private boolean checkAllFilesExist() {
        if (mediaList == null || mediaList.isEmpty()) return false;

        for (MediaItem item : mediaList) {
            if (!cacheManager.isCached(item.getUrl())) {
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

        for (int i = 0; i < mediaList.size(); i++) {
            MediaItem item = mediaList.get(i);

            if (cacheManager.isCached(item.getUrl())) {
                downloadedCount++;
                Log.d("Prefetch", "File already cached: " + item.getUrl());
                continue;
            }

            final int currentIndex = i;
            cacheManager.downloadMediaAsync(item.getUrl(), new MediaCacheManager.DownloadListener() {
                @Override
                public void onSuccess(File file) {
                    downloadedCount++;
                    String fileType = item.getType() == MediaType.VIDEO ? " Video" : "🖼 Slika";
                    Log.d("Prefetch", "Successfully downloaded: " + file.getName());

                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this,
                                " " + fileType + " preuzet (" + downloadedCount + "/" + totalMediaCount + ")",
                                Toast.LENGTH_SHORT).show();

                        if (downloadedCount == totalMediaCount && onComplete != null) {
                            onComplete.run();
                        }
                    });
                }

                @Override
                public void onError(Exception e) {
                    Log.e("Prefetch", "Error downloading: " + item.getUrl(), e);
                    downloadedCount++;

                    if (downloadedCount == totalMediaCount && onComplete != null) {
                        runOnUiThread(onComplete);
                    }
                }

                @Override
                public void onProgress() {
                    // Download in progress
                }
            });
        }

        // If all files were already cached
        if (downloadedCount == totalMediaCount && onComplete != null) {
            runOnUiThread(onComplete);
        }
    }

    private boolean areAllFilesDownloaded() {
        if (mediaList == null || mediaList.isEmpty()) return false;

        for (MediaItem item : mediaList) {
            if (!cacheManager.isCached(item.getUrl())) {
                return false;
            }
        }
        return true;
    }

    private void initializeMediaList(Context context) {
        mediaList = new ArrayList<>();

        loadMediaFromServer();

        try {
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
        } catch (Exception e) {
            Log.e("MediaList", "Error loading from properties", e);
        }

        if (mediaList.isEmpty()) {
            Log.e("MediaList", "No media items loaded, using defaults");
            mediaList.add(new MediaItem(
                    "https://picsum.photos/1920/1080",
                    MediaType.IMAGE,
                    5000
            ));
            mediaList.add(new MediaItem(
                    "https://picsum.photos/1920/1080?grayscale",
                    MediaType.IMAGE,
                    5000
            ));
        }
    }

    private void loadMediaFromServer() {
        MediaApiService apiService = RetrofitClient.getClient().create(MediaApiService.class);

        Call<List<MediaItemResponse>> call = apiService.getMediaItems();
        call.enqueue(new retrofit2.Callback<List<MediaItemResponse>>() {
            @Override
            public void onResponse(Call<List<MediaItemResponse>> call, retrofit2.Response<List<MediaItemResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<MediaItem> serverMediaList = new ArrayList<>();

                    for (MediaItemResponse itemResponse : response.body()) {
                        int duration = 5000;
                        if (itemResponse.getDurationInSeconds() != null) {
                            duration = itemResponse.getDurationInSeconds() * 1000;
                        } else if ("video".equalsIgnoreCase(itemResponse.getType())) {
                            duration = 15000;
                        }

                        MediaType mediaType = MediaType.fromString(itemResponse.getType());
                        serverMediaList.add(new MediaItem(itemResponse.getUrl(), mediaType, duration));
                    }

                    mediaList.clear();
                    mediaList.addAll(serverMediaList);

                    Log.d("API", "Loaded " + mediaList.size() + " items from server");
                } else {
                    Log.e("API", "Server error: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<MediaItemResponse>> call, Throwable t) {
                Log.e("API", "Network error: " + t.getMessage());
            }
        });
    }

    private void showCurrentMedia() {
        if (mediaList == null || mediaList.isEmpty()) {
            Log.e("MediaList", "Media list is null or empty");
            return;
        }
        if (currentMediaIndex >= mediaList.size()) currentMediaIndex = 0;

        MediaItem current = mediaList.get(currentMediaIndex);

        if (cacheManager.isCached(current.getUrl())) {
            File localFile = cacheManager.getMediaFile(current.getUrl());
            displayMedia(current, localFile);
        } else {
            String fileType = current.getType() == MediaType.VIDEO ? "Video" : "Slika";
            Toast.makeText(this, "Preuzimanje: " + fileType, Toast.LENGTH_SHORT).show();

            cacheManager.downloadMediaAsync(current.getUrl(), new MediaCacheManager.DownloadListener() {
                @Override
                public void onSuccess(File file) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Fajl je preuzet", Toast.LENGTH_SHORT).show();
                        displayMedia(current, file);
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> {
                        Log.e("Media", "Failed to download media: " + current.getUrl(), e);
                        handler.postDelayed(nextRunnable, 1000);
                    });
                }

                @Override
                public void onProgress() {
                    // Download in progress
                }
            });
        }
    }

    private void displayMedia(MediaItem mediaItem, File localFile) {
        handler.removeCallbacks(nextRunnable);
        handler.removeCallbacks(timerRunnable);

        startTime = System.currentTimeMillis();
        currentDuration = mediaItem.getDuration();

        if (mediaItem.getType() == MediaType.VIDEO) {
            showVideo(mediaItem, localFile);
        } else {
            showImage(mediaItem, localFile);
        }

        handler.post(timerRunnable);
    }

    @SuppressLint("DefaultLocale")
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

    private void showVideo(MediaItem mediaItem, File localFile) {
        imageView.setVisibility(View.GONE);
        videoView.setVisibility(View.VISIBLE);

        // Postavi VideoView da zauzme cijeli ekran
        ViewGroup.LayoutParams lp = videoView.getLayoutParams();
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        videoView.setLayoutParams(lp);

        try {
            Uri uri = Uri.fromFile(localFile);
            videoView.setVideoURI(uri);

            videoView.setOnPreparedListener(mp -> {
                // FORCE CENTER CROP SCALING
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    try {
                        mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                    } catch (Exception e) {
                        Log.e("Video", "Video scaling not supported", e);
                    }
                }

                // Manual scaling za starije verzije
                mp.setOnVideoSizeChangedListener((mp2, videoWidth, videoHeight) -> {
                    if (videoWidth <= 0 || videoHeight <= 0) return;

                    DisplayMetrics dm = getResources().getDisplayMetrics();
                    int screenWidth = dm.widthPixels;
                    int screenHeight = dm.heightPixels;

                    // Izračunaj scale factor za center crop
                    float widthRatio = (float) screenWidth / videoWidth;
                    float heightRatio = (float) screenHeight / videoHeight;
                    float scale = Math.max(widthRatio, heightRatio);

                    // Postavi nove dimenzije
                    int newWidth = Math.round(videoWidth * scale);
                    int newHeight = Math.round(videoHeight * scale);

                    ViewGroup.LayoutParams params = videoView.getLayoutParams();
                    params.width = newWidth;
                    params.height = newHeight;
                    videoView.setLayoutParams(params);

                    // Centriraj video
                    if (videoView.getParent() instanceof FrameLayout) {
                        FrameLayout.LayoutParams frameParams = (FrameLayout.LayoutParams) videoView.getLayoutParams();
                        frameParams.gravity = Gravity.CENTER;
                        videoView.setLayoutParams(frameParams);
                    }
                });

                mp.setLooping(false);
                videoView.start();

                int videoDuration = mp.getDuration();
                long scheduleDelay = Math.min(mediaItem.getDuration(), videoDuration > 0 ? videoDuration : mediaItem.getDuration());
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
    private void showImage(MediaItem mediaItem, File localFile) {
        videoView.setVisibility(View.GONE);
        imageView.setVisibility(View.VISIBLE);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

        try {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int targetW = dm.widthPixels;
            int targetH = dm.heightPixels;

            // Koristimo Glide bez disk cache jer vec imamo nas cache
            RequestOptions options = new RequestOptions()
                    .override(targetW, targetH)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.NONE) // Bez Glide cache
                    .skipMemoryCache(true); // Bez memory cache

            Glide.with(this)
                    .load(localFile)
                    .apply(options)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                    Target<Drawable> target, boolean isFirstResource) {
                            Log.e("Glide", "Image load failed");
                            handler.postDelayed(nextRunnable, 1000);
                            return true;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model,
                                                       Target<Drawable> target, DataSource dataSource,
                                                       boolean isFirstResource) {
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