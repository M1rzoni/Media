package com.example.learning;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;

public interface MediaApiService {
    @GET("/")
    Call<List<MediaItemResponse>> getMediaItems();
}
