package com.example.learning;

import android.content.Context;
import java.io.InputStream;
import java.util.Properties;

public class PropertiesLoader {

    public static Properties loadProperties(Context context, int resourceId) {
        Properties properties = new Properties();
        try (InputStream input = context.getResources().openRawResource(resourceId)) {
            properties.load(input);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return properties;
    }
}