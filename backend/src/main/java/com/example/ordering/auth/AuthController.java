package com.example.ordering.auth;

import com.example.ordering.common.ApiResponse;
import com.example.ordering.user.UserAccount;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    public AuthController(AuthService authService, TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    @PostMapping("/register")
    public ApiResponse<UserProfileResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok("注册成功", authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok("登录成功", authService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        tokenService.revoke(resolveBearerToken(authorization));
        return ApiResponse.ok("退出登录成功", null);
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me(Authentication authentication) {
        UserAccount user = (UserAccount) authentication.getPrincipal();
        return ApiResponse.ok(authService.profile(user));
    }

    @PostMapping("/nickname")
    public ApiResponse<UserProfileResponse> updateNickname(
            Authentication authentication,
            @Valid @RequestBody UpdateNicknameRequest request
    ) {
        UserAccount user = (UserAccount) authentication.getPrincipal();
        return ApiResponse.ok("昵称修改成功，已扣除 10 青程币", authService.updateNickname(user, request));
    }

    @PostMapping("/password")
    public ApiResponse<Void> updatePassword(
            Authentication authentication,
            @Valid @RequestBody UpdatePasswordRequest request
    ) {
        UserAccount user = (UserAccount) authentication.getPrincipal();
        authService.updatePassword(user, request);
        return ApiResponse.ok("密码修改成功，请重新登录", null);
    }

    private String resolveBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring(7);
    }
}
