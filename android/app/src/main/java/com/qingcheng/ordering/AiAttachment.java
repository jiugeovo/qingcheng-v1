package com.qingcheng.ordering;

final class AiAttachment {
    final String name;
    final String dataUrl;

    AiAttachment(String name, String dataUrl) {
        this.name = name == null || name.trim().isEmpty() ? "图片" : name;
        this.dataUrl = dataUrl;
    }
}
