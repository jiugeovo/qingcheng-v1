package com.example.ordering.piggy;

import com.example.ordering.common.ApiResponse;
import com.example.ordering.user.UserAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/piggy")
@PreAuthorize("hasAuthority('COUPLE_MEMBER')")
public class PiggyProgressController {
    public record Sync(@NotNull UUID deviceId, @NotNull @PositiveOrZero Long revision,
                       @NotNull @PositiveOrZero Long taps, @Positive Long legacyCount) { }
    public record Reset(@NotNull UUID deviceId, @NotNull @PositiveOrZero Long revision) { }

    private final PiggyProgressService progress;

    public PiggyProgressController(PiggyProgressService progress) {
        this.progress = progress;
    }

    @GetMapping
    public ApiResponse<PiggyProgressService.Progress> get(Authentication auth, @RequestParam UUID deviceId) {
        return ApiResponse.ok(progress.get((UserAccount) auth.getPrincipal(), deviceId));
    }

    @PostMapping("/sync")
    public ApiResponse<PiggyProgressService.Progress> sync(Authentication auth, @Valid @RequestBody Sync body) {
        return ApiResponse.ok(progress.sync((UserAccount) auth.getPrincipal(), body.deviceId(),
                body.revision(), body.taps(), body.legacyCount()));
    }

    @PostMapping("/reset")
    public ApiResponse<PiggyProgressService.Progress> reset(Authentication auth, @Valid @RequestBody Reset body) {
        return ApiResponse.ok(progress.reset((UserAccount) auth.getPrincipal(), body.deviceId(), body.revision()));
    }
}
