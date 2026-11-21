package com.example.ordering.push;

import jakarta.validation.constraints.NotBlank;

public record PushDeviceRequest(
        String provider,
        @NotBlank String registrationId,
        String platform
) {
}
