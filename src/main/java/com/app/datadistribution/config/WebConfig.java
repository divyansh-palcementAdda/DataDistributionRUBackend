package com.app.datadistribution.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.file-storage.base-path:${APP_FILE_STORAGE_PATH:uploads}}")
    private String basePath;

    @Value("${app.file-storage.public-url:${APP_FILE_STORAGE_PUBLIC_URL:/uploads}}")
    private String publicUrl;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(basePath).toAbsolutePath().normalize();
        String uploadURI = uploadPath.toUri().toString();

        String handlerPattern = publicUrl.endsWith("/") ? publicUrl + "**" : publicUrl + "/**";

        registry.addResourceHandler(handlerPattern)
                .addResourceLocations(uploadURI.endsWith("/") ? uploadURI : uploadURI + "/");
    }
}
