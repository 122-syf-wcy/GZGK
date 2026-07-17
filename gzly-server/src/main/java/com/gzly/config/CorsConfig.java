package com.gzly.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

@Configuration
public class CorsConfig {

    @Value("${gzly.cors.allowed-origins:https://gzly.dongsiwei.com,http://39.97.232.141}")
    private String allowedOrigins;

    @Value("${gzly.cors.allow-localhost:false}")
    private boolean allowLocalhost;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .forEach(config::addAllowedOrigin);
        if (allowLocalhost) {
            config.addAllowedOriginPattern("http://localhost:*");
            config.addAllowedOriginPattern("http://127.0.0.1:*");
        }
        config.addAllowedHeader("*");
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("PATCH");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("OPTIONS");
        config.addExposedHeader("Authorization");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
