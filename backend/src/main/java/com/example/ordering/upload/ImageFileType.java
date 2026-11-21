package com.example.ordering.upload;

import java.util.Locale;
import java.util.Optional;

public enum ImageFileType {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    GIF("gif", "image/gif"),
    WEBP("webp", "image/webp");

    private final String extension;
    private final String mimeType;

    ImageFileType(String extension, String mimeType) {
        this.extension = extension;
        this.mimeType = mimeType;
    }

    public String extension() {
        return extension;
    }

    public String mimeType() {
        return mimeType;
    }

    public boolean matchesDataMime(String mime) {
        String normalized = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        if (this == JPEG && "image/jpg".equals(normalized)) {
            return true;
        }
        return mimeType.equals(normalized);
    }

    public static Optional<ImageFileType> fromDataMime(String mime) {
        String normalized = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        for (ImageFileType type : values()) {
            if (type.matchesDataMime(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    public static Optional<ImageFileType> detect(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return Optional.empty();
        }
        if ((bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return Optional.of(JPEG);
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47
                && bytes[4] == 0x0d
                && bytes[5] == 0x0a
                && bytes[6] == 0x1a
                && bytes[7] == 0x0a) {
            return Optional.of(PNG);
        }
        if (bytes.length >= 6
                && bytes[0] == 0x47
                && bytes[1] == 0x49
                && bytes[2] == 0x46
                && bytes[3] == 0x38
                && (bytes[4] == 0x37 || bytes[4] == 0x39)
                && bytes[5] == 0x61) {
            return Optional.of(GIF);
        }
        if (bytes.length >= 12
                && bytes[0] == 0x52
                && bytes[1] == 0x49
                && bytes[2] == 0x46
                && bytes[3] == 0x46
                && bytes[8] == 0x57
                && bytes[9] == 0x45
                && bytes[10] == 0x42
                && bytes[11] == 0x50) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }
}
