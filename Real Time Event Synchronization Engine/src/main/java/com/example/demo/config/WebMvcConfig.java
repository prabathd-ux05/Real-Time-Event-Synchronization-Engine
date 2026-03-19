package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    /**
     * Serves uploaded files at /uploads/** as static resources.
     * e.g. GET /uploads/uuid_photo.jpg → reads from ./uploads/ on disk.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
            .addResourceHandler("/" + uploadDir + "/**")
            .addResourceLocations("file:" + uploadDir + "/");
    }
}
