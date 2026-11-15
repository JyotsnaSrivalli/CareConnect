package com.careconnect.controller;

import com.careconnect.dto.AuthResponse;
import com.careconnect.dto.LoginRequest;
import com.careconnect.dto.RegisterRequest;
import com.careconnect.dto.UserResponse;
import com.careconnect.security.AuthenticatedUser;
import com.careconnect.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Returns the logged-in user (the frontend calls this on page load to validate the stored token). */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser caller) {
        return authService.currentUser(caller.id());
    }
}
