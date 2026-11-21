package com.example.ordering.upload;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;
import java.util.Objects;

/** Owns image validation and storage independently of the HTTP and capability transports. */
@Service
public class ImageUploadService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    private final UploadProperties uploadProperties;
    private final Clock clock;

    public ImageUploadService(UploadProperties uploadProperties) {
        this(uploadProperties, Clock.system(ZoneId.of("Asia/Shanghai")));
    }

    @Autowired
    public ImageUploadService(UploadProperties uploadProperties, Clock clock) {
        this.uploadProperties = uploadProperties;
        this.clock = Objects.requireNonNull(clock, "clock").withZone(ZoneId.of("Asia/Shanghai"));
    }

    public UploadResponse uploadImage(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("图片不能超过 5MB");
        }
        byte[] bytes = file.getBytes();
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("图片不能超过 5MB");
        }
        ImageFileType imageType = ImageFileType.detect(bytes)
                .orElseThrow(() -> new IllegalArgumentException("仅支持 jpg、png、gif、webp 图片"));

        String folder = LocalDate.now(clock).toString();
        Path baseDir = Paths.get(uploadProperties.getDir()).toAbsolutePath().normalize();
        Path uploadDir = baseDir.resolve(folder).normalize();
        Files.createDirectories(uploadDir);

        String filename = UUID.randomUUID().toString().replace("-", "") + "." + imageType.extension();
        Path target = uploadDir.resolve(filename).normalize();
        if (!target.startsWith(baseDir)) {
            throw new IllegalArgumentException("上传目录无效");
        }

        Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

        String relativePath = "uploads/" + folder + "/" + filename;
        return new UploadResponse(relativePath);
    }
}
