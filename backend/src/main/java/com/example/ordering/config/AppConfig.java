package com.example.ordering.config;

import com.example.ordering.ai.AiProperties;
import com.example.ordering.birthday.BirthdayGreetingProperties;
import com.example.ordering.push.PushProperties;
import com.example.ordering.upload.UploadProperties;
import com.example.ordering.weather.WeatherProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@EnableConfigurationProperties({
        UploadProperties.class,
        AiProperties.class,
        BirthdayGreetingProperties.class,
        PushProperties.class,
        WeatherProperties.class
})
public class AppConfig implements WebMvcConfigurer {

    private final UploadProperties uploadProperties;

    public AppConfig(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDir = Paths.get(uploadProperties.getDir()).toAbsolutePath().normalize();
        String location = uploadDir.toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/");
    }
}
