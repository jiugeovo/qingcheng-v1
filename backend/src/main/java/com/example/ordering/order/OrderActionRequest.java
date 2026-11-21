package com.example.ordering.order;

import jakarta.validation.constraints.NotNull;

public record OrderActionRequest(@NotNull OrderAction action) { }
