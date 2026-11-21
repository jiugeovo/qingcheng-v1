package com.example.ordering.capability;

import com.example.ordering.upload.ImageUploadService;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

@Component
public final class ImageUploadExecutor implements CapabilityExecutor {
    private final ImageUploadService service;

    public ImageUploadExecutor(ImageUploadService service) {
        this.service = service;
    }

    @Override public String capabilityId() { return ImageUploadCapability.ID; }

    @Override public Map<String, Object> execute(Map<String, Object> input, CapabilityExecutionContext context) {
        try {
            return Map.of("upload", service.uploadImage((MultipartFile) input.get("file")));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
