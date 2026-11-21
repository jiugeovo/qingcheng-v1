package com.example.ordering.rules;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoveRuleRequest(
        @NotBlank
        @Size(max = 500)
        String content,

        Integer sortOrder
) {
}
