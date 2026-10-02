package com.qr_ordering_system.app.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Serves uploaded files (e.g. /uploads/menu-images/abc.jpg) from the local
 * upload directory so the frontend can display them via <img src="...">.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String uploadDir;
    private final String uploadPublicPath;

    public WebConfig(
            @Value("${app.upload.dir:uploads/menu-images}") String uploadDir,
            @Value("${app.upload.public-path:/uploads/menu-images}") String uploadPublicPath) {
        this.uploadDir = uploadDir;
        this.uploadPublicPath = uploadPublicPath;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler(uploadPublicPath + "/**")
                .addResourceLocations(location);
    }
}
