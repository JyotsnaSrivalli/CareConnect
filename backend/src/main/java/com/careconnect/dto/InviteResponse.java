package com.careconnect.dto;

import java.time.LocalDateTime;

public record InviteResponse(String code, LocalDateTime expiresAt) { }
