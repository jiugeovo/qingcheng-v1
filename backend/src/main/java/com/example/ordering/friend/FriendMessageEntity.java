package com.example.ordering.friend;


import java.time.LocalDateTime;

public class FriendMessageEntity {

    private Long id;

    private Long userLowId;

    private Long userHighId;

    private Long senderId;

    private Long receiverId;

    private String content;

    private FriendMessageType messageType = FriendMessageType.TEXT;

    private String mediaUrl;

    private Long replyToMessageId;

    private Long replyToSenderId;

    private FriendMessageType replyToType;

    private String replyToPreview;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime readAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserLowId() {
        return userLowId;
    }

    public void setUserLowId(Long userLowId) {
        this.userLowId = userLowId;
    }

    public Long getUserHighId() {
        return userHighId;
    }

    public void setUserHighId(Long userHighId) {
        this.userHighId = userHighId;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(Long receiverId) {
        this.receiverId = receiverId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public FriendMessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(FriendMessageType messageType) {
        this.messageType = messageType;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public Long getReplyToMessageId() {
        return replyToMessageId;
    }

    public void setReplyToMessageId(Long replyToMessageId) {
        this.replyToMessageId = replyToMessageId;
    }

    public Long getReplyToSenderId() {
        return replyToSenderId;
    }

    public void setReplyToSenderId(Long replyToSenderId) {
        this.replyToSenderId = replyToSenderId;
    }

    public FriendMessageType getReplyToType() {
        return replyToType;
    }

    public void setReplyToType(FriendMessageType replyToType) {
        this.replyToType = replyToType;
    }

    public String getReplyToPreview() {
        return replyToPreview;
    }

    public void setReplyToPreview(String replyToPreview) {
        this.replyToPreview = replyToPreview;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }
}
