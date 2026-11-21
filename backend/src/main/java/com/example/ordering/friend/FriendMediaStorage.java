package com.example.ordering.friend;

import com.example.ordering.upload.ImageFileType;
import com.example.ordering.upload.UploadProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class FriendMediaStorage {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    private final UploadProperties uploadProperties;

    public FriendMediaStorage(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
    }

    StoredImage store(MultipartFile file, Long userId, String category) throws IOException {
        if (file == null || file.isEmpty()) {
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

        String safeCategory = "stickers".equals(category) ? "stickers" : "images";
        Path baseDir = Paths.get(uploadProperties.getDir()).toAbsolutePath().normalize();
        Path uploadDir = baseDir
                .resolve("friends")
                .resolve(String.valueOf(userId))
                .resolve(safeCategory)
                .resolve(LocalDate.now().toString())
                .normalize();
        if (!uploadDir.startsWith(baseDir)) {
            throw new IllegalArgumentException("上传目录无效");
        }
        Files.createDirectories(uploadDir);

        String filename = UUID.randomUUID().toString().replace("-", "") + "." + imageType.extension();
        Path target = uploadDir.resolve(filename).normalize();
        if (!target.startsWith(uploadDir)) {
            throw new IllegalArgumentException("上传目录无效");
        }
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

        String relativePath = baseDir.relativize(target).toString().replace('\\', '/');
        return new StoredImage("uploads/" + relativePath, target);
    }

    void deleteQuietly(StoredImage image) {
        if (image == null) {
            return;
        }
        try {
            Files.deleteIfExists(image.path());
        } catch (IOException ignored) {
        }
    }

    FriendMediaContent read(String mediaUrl) throws IOException {
        String normalized = mediaUrl == null ? "" : mediaUrl.trim().replace('\\', '/');
        if (!normalized.startsWith("uploads/friends/") || normalized.contains("..")) {
            throw new IllegalArgumentException("图片不存在");
        }
        Path baseDir = Paths.get(uploadProperties.getDir()).toAbsolutePath().normalize();
        Path friendDir = baseDir.resolve("friends").normalize();
        Path target = baseDir.resolve(normalized.substring("uploads/".length())).normalize();
        if (!target.startsWith(friendDir) || !Files.isRegularFile(target)) {
            throw new IllegalArgumentException("图片不存在");
        }
        if (Files.size(target) > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("图片不存在");
        }
        byte[] bytes = Files.readAllBytes(target);
        ImageFileType imageType = ImageFileType.detect(bytes)
                .orElseThrow(() -> new IllegalArgumentException("图片不存在"));
        return new FriendMediaContent(bytes, imageType.mimeType());
    }

    record StoredImage(String url, Path path) {
    }
}
