package com.example.learning;

public class MediaItem {
    private String url;
    private MediaType type;
    private long duration;

    private String localPath;

    public MediaItem(String url, MediaType type, long duration) {
        this.url = url;
        this.type = type;
        this.duration = duration;
    }


    public void setLocalPath(String path) { this.localPath = path; }
    public String getLocalPath() { return localPath; }

    public String getUrl() { return url; }
    public MediaType getType() { return type; }
    public long getDuration() { return duration; }

}