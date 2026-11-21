package com.example.ordering.friend;


import java.time.LocalDateTime;

public class FriendshipEntity {

    private Long id;

    private Long userLowId;

    private Long userHighId;

    private Long requesterId;

    private Long addresseeId;

    private FriendshipStatus status = FriendshipStatus.PENDING;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    private LocalDateTime requesterClearedMessagesAt;

    private LocalDateTime addresseeClearedMessagesAt;

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

    public Long getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(Long requesterId) {
        this.requesterId = requesterId;
    }

    public Long getAddresseeId() {
        return addresseeId;
    }

    public void setAddresseeId(Long addresseeId) {
        this.addresseeId = addresseeId;
    }

    public FriendshipStatus getStatus() {
        return status;
    }

    public void setStatus(FriendshipStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getRequesterClearedMessagesAt() {
        return requesterClearedMessagesAt;
    }

    public void setRequesterClearedMessagesAt(LocalDateTime requesterClearedMessagesAt) {
        this.requesterClearedMessagesAt = requesterClearedMessagesAt;
    }

    public LocalDateTime getAddresseeClearedMessagesAt() {
        return addresseeClearedMessagesAt;
    }

    public void setAddresseeClearedMessagesAt(LocalDateTime addresseeClearedMessagesAt) {
        this.addresseeClearedMessagesAt = addresseeClearedMessagesAt;
    }
}
