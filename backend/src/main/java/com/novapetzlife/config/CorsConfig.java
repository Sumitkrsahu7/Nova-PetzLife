package com.novapetzlife.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:4200,http://localhost:4201}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Sanitize origins to strip YAML sequence bracket artifacts if present
        List<String> origins = new ArrayList<>();
        if (allowedOrigins != null) {
            for (String origin : allowedOrigins) {
                if (origin != null) {
                    String clean = origin.replace("[", "").replace("]", "").trim();
                    if (!clean.isEmpty()) {
                        origins.add(clean);
                    }
                }
            }
        }
        if (origins.isEmpty()) {
            origins.add("http://localhost:4200");
            origins.add("http://localhost:4201");
        }

        // Set explicit origins and wildcard patterns for dev/staging flexibility
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:[*]",
                "http://127.0.0.1:[*]",
                "https://*.novapetzlife.com",
                "http://*.novapetzlife.com",
                "https://*.onrender.com",
                "http://*.onrender.com"
        ));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        // Allow all headers including custom X-Session-Token
        configuration.setAllowedHeaders(Arrays.asList(
                "*",
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers",
                "X-Session-Token"
        ));
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "X-Session-Token",
                "Content-Disposition"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
