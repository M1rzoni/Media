package com.example.learning;

import com.google.gson.annotations.SerializedName;

public class MediaItemResponse {
    @SerializedName("type")
    private String type;

    @SerializedName("url")
    private String url;

    @SerializedName("durationInSeconds")
    private Integer durationInSeconds;

    public String getType() { return type;}
    public void setType(String type) {this.type = type;}
    public String getUrl() { return url;}
    public void setUrl(String url) { this.url = url;}

    public Integer getDurationInSeconds() { return durationInSeconds; }
    public void setDurationInSeconds(Integer durationInSeconds) { this.durationInSeconds = durationInSeconds; }
}