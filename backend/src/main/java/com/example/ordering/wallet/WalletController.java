package com.example.ordering.wallet;

import com.example.ordering.common.ApiResponse;
import com.example.ordering.capability.CapabilityExecutionContext;
import com.example.ordering.capability.CapabilityRegistry;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;
    private final CapabilityRegistry capabilities;

    public WalletController(WalletService walletService, CapabilityRegistry capabilities) {
        this.walletService = walletService;
        this.capabilities = capabilities;
    }

    @GetMapping
    public ApiResponse<WalletResponse> getWallet(Authentication authentication) {
        CapabilityExecutionContext context = CapabilityExecutionContext.from(authentication);
        return ApiResponse.ok(walletService.getWallet(context.toUserAccount()));
    }

    @PostMapping("/check-in")
    public ApiResponse<WalletResponse> checkIn(Authentication authentication) {
        Map<String, Object> result = capabilities.execute("wallet.check-in", Map.of(),
                CapabilityExecutionContext.from(authentication));
        return ApiResponse.ok("签到成功", WalletResponse.class.cast(result.get("wallet")));
    }
}
