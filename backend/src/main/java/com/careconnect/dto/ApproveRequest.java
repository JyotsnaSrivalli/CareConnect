package com.careconnect.dto;

import jakarta.validation.constraints.NotNull;

/** approve=true approves a pending request; approve=false rejects it (or revokes an approved link). */
public record ApproveRequest(
        @NotNull(message = "connectionId is required") Long connectionId,
        @NotNull(message = "approve (true/false) is required") Boolean approve,
        Boolean shareCheckins) { }
