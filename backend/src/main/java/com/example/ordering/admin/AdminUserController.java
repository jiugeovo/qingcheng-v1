package com.example.ordering.admin;

import com.example.ordering.admin.audit.AdminAuditService;
import com.example.ordering.auth.ReservedUsernamePolicy;
import com.example.ordering.auth.TokenService;
import com.example.ordering.auth.UserProfileResponse;
import com.example.ordering.common.ApiResponse;
import com.example.ordering.push.PushNotificationService;
import com.example.ordering.user.UserAccount;
import com.example.ordering.user.UserRepository;
import com.example.ordering.user.UserRole;
import com.example.ordering.wallet.WalletLedgerService;
import com.example.ordering.wallet.WalletTransactionType;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserRepository userRepository;
    private final WalletLedgerService walletLedgerService;
    private final AdminAuditService adminAuditService;
    private final TokenService tokenService;
    private final PushNotificationService pushNotificationService;
    private final ReservedUsernamePolicy reservedUsernamePolicy;

    public AdminUserController(
            UserRepository userRepository,
            WalletLedgerService walletLedgerService,
            AdminAuditService adminAuditService,
            TokenService tokenService,
            PushNotificationService pushNotificationService,
            ReservedUsernamePolicy reservedUsernamePolicy
    ) {
        this.userRepository = userRepository;
        this.walletLedgerService = walletLedgerService;
        this.adminAuditService = adminAuditService;
        this.tokenService = tokenService;
        this.pushNotificationService = pushNotificationService;
        this.reservedUsernamePolicy = reservedUsernamePolicy;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<List<UserProfileResponse>> listUsers() {
        List<UserProfileResponse> users = userRepository.findAll().stream()
                .map(UserProfileResponse::from)
                .toList();
        return ApiResponse.ok(users);
    }

    @PutMapping("/{id}/role")
    @Transactional
    public ApiResponse<UserProfileResponse> updateRole(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRoleRequest request
    ) {
        UserAccount currentUser = (UserAccount) authentication.getPrincipal();
        if (currentUser.getId().equals(id)) {
            throw new IllegalArgumentException("不能修改自己的权限");
        }
        UserAccount user = userRepository.findLockedById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        UserRole beforeRole = user.getRole();
        if ((beforeRole == UserRole.SUPER_ADMIN || request.role() == UserRole.SUPER_ADMIN)
                && !isInitialSuperAdmin(currentUser)) {
            throw new IllegalArgumentException("只有初始超管可以授予或撤销超级管理员");
        }
        user.setRole(request.role());
        userRepository.updateRole(user.getId(), user.getRole());
        adminAuditService.record(
                currentUser,
                "USER_ROLE_UPDATE",
                "USER",
                user.getId(),
                beforeRole.name(),
                user.getRole().name(),
                null,
                "用户角色变更，username=" + user.getUsername()
        );
        return ApiResponse.ok("权限已更新", UserProfileResponse.from(user));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Void> deleteUser(Authentication authentication, @PathVariable Long id) {
        UserAccount currentUser = (UserAccount) authentication.getPrincipal();
        if (currentUser.getId().equals(id)) {
            throw new IllegalArgumentException("不能禁用当前登录账号");
        }
        UserAccount user = userRepository.findLockedById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (isInitialSuperAdmin(user)) {
            throw new IllegalArgumentException("不能禁用初始超级管理员");
        }
        if (user.getRole() == UserRole.SUPER_ADMIN && !isInitialSuperAdmin(currentUser)) {
            throw new IllegalArgumentException("只有初始超管可以禁用超级管理员");
        }
        boolean beforeEnabled = user.isEnabled();
        UserRole beforeRole = user.getRole();
        user.setEnabled(false);
        userRepository.updateEnabled(user.getId(), false);
        tokenService.revokeAllForUser(user.getId());
        pushNotificationService.disableAllDevicesForUser(user.getId());
        adminAuditService.record(
                currentUser,
                "USER_DISABLE",
                "USER",
                user.getId(),
                "enabled=" + beforeEnabled + ",role=" + beforeRole.name(),
                "enabled=" + user.isEnabled() + ",role=" + user.getRole().name(),
                null,
                "禁用用户，username=" + user.getUsername()
        );
        return ApiResponse.ok("用户已禁用", null);
    }

    @PutMapping("/{id}/coins")
    @Transactional
    public ApiResponse<UserProfileResponse> updateCoins(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserCoinsRequest request
    ) {
        UserAccount user = userRepository.findLockedById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        UserAccount currentUser = (UserAccount) authentication.getPrincipal();
        var before = user.getQingchengCoins();
        user.setQingchengCoins(request.qingchengCoins());
        userRepository.updateQingchengCoins(user.getId(), user.getQingchengCoins());
        var delta = user.getQingchengCoins().subtract(before);
        UserProfileResponse response = UserProfileResponse.from(user);
        if (delta.signum() != 0) {
            walletLedgerService.record(
                    user,
                    WalletTransactionType.ADMIN_ADJUSTMENT,
                    delta,
                    "管理员调整",
                    "由 " + currentUser.getUsername() + " 调整青程币",
                    "admin:" + currentUser.getId()
            );
        }
        adminAuditService.record(
                currentUser,
                "USER_COINS_UPDATE",
                "USER",
                user.getId(),
                before.toPlainString(),
                user.getQingchengCoins().toPlainString(),
                delta,
                "手动调整青程币，username=" + user.getUsername()
        );
        return ApiResponse.ok("青程币已更新", response);
    }

    private boolean isInitialSuperAdmin(UserAccount user) {
        return user != null
                && reservedUsernamePolicy.isReserved(user.getUsername());
    }
}
