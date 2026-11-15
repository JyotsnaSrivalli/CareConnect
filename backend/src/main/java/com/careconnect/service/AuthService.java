package com.careconnect.service;

import com.careconnect.dto.AuthResponse;
import com.careconnect.dto.LoginRequest;
import com.careconnect.dto.RegisterRequest;
import com.careconnect.dto.UserResponse;
import com.careconnect.entity.User;
import com.careconnect.exception.ApiException;
import com.careconnect.repository.UserRepository;
import com.careconnect.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Email is already registered");
        }
        // BCrypt adds a random salt and is deliberately slow, so stolen hashes are hard to crack
        String hash = passwordEncoder.encode(request.password());
        User user = userRepository.save(new User(request.name().trim(), email, hash, request.role()));
        return new AuthResponse(jwtService.generateToken(user.getId(), user.getEmail(), user.getRole()),
                UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // Same message for "unknown email" and "wrong password" so attackers can't discover which emails exist
        User user = userRepository.findByEmail(normalize(request.email()))
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        return new AuthResponse(jwtService.generateToken(user.getId(), user.getEmail(), user.getRole()),
                UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
