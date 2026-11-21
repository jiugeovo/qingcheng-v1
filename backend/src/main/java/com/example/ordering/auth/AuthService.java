package com.example.ordering.auth;

import com.example.ordering.couple.CoupleAccess;
import com.example.ordering.user.UserAccount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.example.ordering.user.UserRepository;
import com.example.ordering.user.UserRole;
import com.example.ordering.push.PushNotificationService;
import com.example.ordering.wallet.WalletLedgerService;
import com.example.ordering.wallet.WalletTransactionType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Service
public class AuthService {

    public static final BigDecimal NICKNAME_CHANGE_COST = new BigDecimal("10.00");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final PushNotificationService pushNotificationService;
    private final WalletLedgerService walletLedgerService;
    private final ReservedUsernamePolicy reservedUsernamePolicy;
    private final Clock clock;
    private CoupleAccess couple = CoupleAccess.legacy();

    @Autowired(required = false)
    void configureCouple(CoupleAccess couple) {
        this.couple = couple;
    }

    public UserProfileResponse profile(UserAccount user) {
        return UserProfileResponse.from(user, couple.profile(user));
    }

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            PushNotificationService pushNotificationService,
            WalletLedgerService walletLedgerService,
            ReservedUsernamePolicy reservedUsernamePolicy
    ) {
        this(userRepository, passwordEncoder, tokenService, pushNotificationService, walletLedgerService,
                reservedUsernamePolicy, Clock.system(ZoneId.of("Asia/Shanghai")));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            PushNotificationService pushNotificationService,
            WalletLedgerService walletLedgerService,
            ReservedUsernamePolicy reservedUsernamePolicy,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.pushNotificationService = pushNotificationService;
        this.walletLedgerService = walletLedgerService;
        this.reservedUsernamePolicy = reservedUsernamePolicy;
        this.clock = Objects.requireNonNull(clock, "clock").withZone(ZoneId.of("Asia/Shanghai"));
    }

    @Transactional
    public UserProfileResponse register(RegisterRequest request) {
        if (couple.enabled()) throw new ResponseStatusException(HttpStatus.GONE, "已关闭注册");
        String username = request.username().trim();
        if (reservedUsernamePolicy.isReserved(username)) {
            throw new IllegalArgumentException("用户名不可用");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (request.phone() != null && !request.phone().isBlank() && userRepository.existsByPhone(request.phone().trim())) {
            throw new IllegalArgumentException("手机号已被注册");
        }

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname().trim());
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        user.setRole(UserRole.USER);
        return UserProfileResponse.from(userRepository.save(user));
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        UserAccount user = userRepository.findByUsername(request.username().trim())
                .orElseThrow(this::invalidLogin);
        if (couple.enabled() && !couple.canLogIn(user)) throw invalidLogin();
        if (!user.isEnabled()) {
            throw new IllegalArgumentException("账号已被禁用");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidLogin();
        }

        UserAccount current = userRepository.findLockedById(user.getId())
                .orElseThrow(this::invalidLogin);
        if (couple.enabled() && !couple.canLogIn(current)) throw invalidLogin();
        if (!current.isEnabled()) {
            throw new IllegalArgumentException("账号已被禁用");
        }
        if (!Objects.equals(user.getPasswordHash(), current.getPasswordHash())) {
            throw new IllegalArgumentException("登录信息已更新，请重新登录");
        }
        current.setLastLoginAt(LocalDateTime.now(clock));
        userRepository.updateLastLoginAt(current.getId(), current.getLastLoginAt());
        tokenService.revokeAllForUser(current.getId());
        pushNotificationService.disableAllDevicesForUser(current.getId());
        String token = tokenService.createToken(current);
        return new LoginResponse(token, profile(current));
    }

    private RuntimeException invalidLogin() {
        String message = "用户名或密码错误";
        return couple.enabled() ? new ResponseStatusException(HttpStatus.UNAUTHORIZED, message)
                : new IllegalArgumentException(message);
    }

    @Transactional
    public UserProfileResponse updateNickname(UserAccount user, UpdateNicknameRequest request) {
        String nickname = request.nickname() == null ? "" : request.nickname().trim();
        if (nickname.length() < 2 || nickname.length() > 20) {
            throw new IllegalArgumentException("昵称长度需要在 2 到 20 个字之间");
        }
        UserAccount current = userRepository.findLockedById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (nickname.equals(current.getNickname())) {
            return profile(current);
        }
        if (current.getQingchengCoins().compareTo(NICKNAME_CHANGE_COST) < 0) {
            throw new IllegalArgumentException("青程币不足，修改昵称需要 10 青程币");
        }
        current.setNickname(nickname);
        current.setQingchengCoins(current.getQingchengCoins().subtract(NICKNAME_CHANGE_COST));
        UserAccount saved = userRepository.save(current);
        walletLedgerService.record(
                saved,
                WalletTransactionType.NICKNAME_CHANGE,
                NICKNAME_CHANGE_COST.negate(),
                "修改昵称",
                "修改为「" + nickname + "」",
                "nickname:" + saved.getId()
        );
        return profile(saved);
    }

    @Transactional
    public void updatePassword(UserAccount user, UpdatePasswordRequest request) {
        UserAccount current = userRepository.findLockedById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (!passwordEncoder.matches(request.oldPassword(), current.getPasswordHash())) {
            throw new IllegalArgumentException("原密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), current.getPasswordHash())) {
            throw new IllegalArgumentException("新密码不能和原密码相同");
        }
        current.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(current);
        tokenService.revokeAllForUser(current.getId());
    }
}
