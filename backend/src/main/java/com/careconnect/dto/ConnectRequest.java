package com.careconnect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConnectRequest(
        @NotBlank(message = "Invitation code is required")
        @Size(max = 12, message = "Invitation code is too long")
        String code) { }
