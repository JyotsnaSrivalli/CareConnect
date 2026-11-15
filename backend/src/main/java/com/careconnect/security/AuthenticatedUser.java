package com.careconnect.security;

import com.careconnect.entity.Role;

/** What we know about the caller after the JWT is verified. Available in controllers via @AuthenticationPrincipal. */
public record AuthenticatedUser(Long id, String email, Role role) { }
