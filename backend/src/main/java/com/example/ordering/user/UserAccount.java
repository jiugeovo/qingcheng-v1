package com.example.ordering.user;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class UserAccount {

    private Long id;

    private String username;

    private String passwordHash;

    private String nickname;

    private String phone;

    private UserRole role = UserRole.USER;

    private boolean enabled = true;

    private BigDecimal qingchengCoins = BigDecimal.ZERO;

    private LocalDate lastCheckInDate;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime lastLoginAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public BigDecimal getQingchengCoins() {
        if (qingchengCoins == null) {
            return BigDecimal.ZERO;
        }
        return qingchengCoins;
    }

    public void setQingchengCoins(BigDecimal qingchengCoins) {
        this.qingchengCoins = qingchengCoins == null ? BigDecimal.ZERO : qingchengCoins;
    }

    public LocalDate getLastCheckInDate() {
        return lastCheckInDate;
    }

    public void setLastCheckInDate(LocalDate lastCheckInDate) {
        this.lastCheckInDate = lastCheckInDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

}
