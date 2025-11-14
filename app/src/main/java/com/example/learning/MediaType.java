package com.example.learning;

public enum MediaType {
    VIDEO,
    IMAGE;

    public static MediaType fromString(String type) {
        try {
            return MediaType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return IMAGE;
        }
    }
}