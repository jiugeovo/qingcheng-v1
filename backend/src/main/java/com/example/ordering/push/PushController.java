package com.example.ordering.push;

import com.example.ordering.common.ApiResponse;
import com.example.ordering.user.UserAccount;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushNotificationService pushNotificationService;

    public PushController(PushNotificationService pushNotificationService) {
        this.pushNotificationService = pushNotificationService;
    }

    @PostMapping("/devices")
    public ApiResponse<PushStatusResponse> registerDevice(
            Authentication authentication,
            @Valid @RequestBody PushDeviceRequest request
    ) {
        UserAccount user = (UserAccount) authentication.getPrincipal();
        pushNotificationService.registerDevice(user, request);
        return ApiResponse.ok("推送设备已绑定", pushNotificationService.status());
    }

    @PostMapping("/devices/unregister")
    public ApiResponse<Void> unregisterDevice(
            Authentication authentication,
            @Valid @RequestBody PushDeviceRequest request
    ) {
        UserAccount user = (UserAccount) authentication.getPrincipal();
        pushNotificationService.unregisterDevice(user, request);
        return ApiResponse.ok("推送设备已解绑", null);
    }

    @GetMapping("/status")
    public ApiResponse<PushStatusResponse> status() {
        return ApiResponse.ok(pushNotificationService.status());
    }
}
