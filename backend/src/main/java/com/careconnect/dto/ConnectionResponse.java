package com.careconnect.dto;

import com.careconnect.entity.ConnectionStatus;
import com.careconnect.entity.FamilyConnection;

import java.time.LocalDateTime;

public record ConnectionResponse(
        Long id,
        ConnectionStatus status,
        boolean shareCheckins,
        LocalDateTime createdAt,
        LocalDateTime approvedAt,
        UserResponse parent,
        UserResponse child) {

    public static ConnectionResponse from(FamilyConnection c) {
        return new ConnectionResponse(c.getId(), c.getStatus(), c.isShareCheckins(),
                c.getCreatedAt(), c.getApprovedAt(),
                UserResponse.from(c.getParent()), UserResponse.from(c.getChild()));
    }
}
