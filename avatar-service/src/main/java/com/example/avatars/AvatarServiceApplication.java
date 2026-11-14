package com.example.avatars;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@SpringBootApplication
public class AvatarServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AvatarServiceApplication.class, args);
    }

    /**
     * Tomcat refuses a file over MAX_BYTES before the controller runs. The whole
     * request may be a little larger than the file: the multipart wrapping
     * around it needs some room.
     */
    @Bean
    MultipartConfigElement multipartConfig(@Value("${avatars.max-bytes}") long maxBytes) {
        return new MultipartConfigElement("", maxBytes, maxBytes + 64 * 1024, 0);
    }

    /**
     * The news UI calls this service straight from the browser, like the backend:
     * /avatars/** for the pictures, and /health to say why pictures are off.
     * A servlet filter rather than Spring MVC's CORS mappings, so that errors
     * raised before a controller is chosen, such as Tomcat's 413, carry the
     * CORS headers too and the browser lets the UI read them.
     */
    @Bean
    CorsFilter corsFilter() {
        var cors = new CorsConfiguration();
        cors.addAllowedOriginPattern("*");
        cors.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        cors.addAllowedHeader("*");
        cors.addExposedHeader("Location");
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return new CorsFilter(source);
    }
}
