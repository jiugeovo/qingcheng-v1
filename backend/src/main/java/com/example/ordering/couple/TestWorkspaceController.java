package com.example.ordering.couple;

import com.example.ordering.auth.LoginResponse;
import com.example.ordering.common.ApiResponse;
import com.example.ordering.user.UserAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/test")
@ConditionalOnProperty(name = "app.couple.mode", havingValue = "TEST")
public class TestWorkspaceController {
    public record Selection(@NotNull CoupleAccess.Member member) { }

    public record Balance(@NotNull CoupleAccess.Member member,
                          @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal balance) { }

    private final TestWorkspace workspace;

    public TestWorkspaceController(TestWorkspace workspace) {
        this.workspace = workspace;
    }

    @PostMapping("/actor")
    public ApiResponse<LoginResponse> select(Authentication authentication, @Valid @RequestBody Selection selection) {
        return ApiResponse.ok(workspace.selectActor((UserAccount) authentication.getPrincipal(), selection.member()));
    }

    @PostMapping("/reset")
    public ApiResponse<Void> reset(Authentication authentication) {
        workspace.resetOrdersAndWallets((UserAccount) authentication.getPrincipal());
        return ApiResponse.ok("测试订单和钱包已重置", null);
    }

    @PostMapping("/wallet")
    public ApiResponse<Void> balance(Authentication authentication, @Valid @RequestBody Balance request) {
        workspace.setBalance((UserAccount) authentication.getPrincipal(), request.member(), request.balance());
        return ApiResponse.ok("测试余额已更新", null);
    }
}
