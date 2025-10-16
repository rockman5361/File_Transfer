package org.ft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for Gson JSON serialization/deserialization.
 */
@Configuration
public class GsonConfig {

    /**
     * Creates a Gson bean with pretty printing enabled.
     * Used for JSON serialization of file tracking data.
     *
     * @return configured Gson instance
     */
    @Bean
    public Gson gson() {
        return new GsonBuilder()
                .setPrettyPrinting()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                .create();
    }
}
