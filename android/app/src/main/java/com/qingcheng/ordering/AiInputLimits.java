package com.qingcheng.ordering;

import java.util.List;

/** Mirrors the app-facing server limits; validation never mutates the current draft. */
final class AiInputLimits {
    static final int MAX_TEXT_CHARACTERS = 12000;
    static final int MAX_IMAGES = 4;
    static final int MAX_IMAGE_REFERENCE_CHARACTERS = 16 * 1024 * 1024;

    private AiInputLimits() { }

    static String error(String prompt, List<AiAttachment> attachments) {
        if (prompt.length() > MAX_TEXT_CHARACTERS) return "单次消息最多 12000 个字符，请缩短后发送";
        if (attachments.size() > MAX_IMAGES) return "单次最多发送 4 张图片，请移除多余图片";
        long total = 0;
        for (AiAttachment attachment : attachments) {
            if (attachment.dataUrl == null || attachment.dataUrl.isEmpty()) return "图片读取失败，请重新添加";
            total += attachment.dataUrl.length();
            if (total > MAX_IMAGE_REFERENCE_CHARACTERS) return "图片总大小超出限制，请减少或压缩图片";
        }
        return null;
    }
}
