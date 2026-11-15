package com.careconnect.service;

import com.careconnect.dto.AuthResponse;
import com.careconnect.dto.LoginRequest;
import com.careconnect.dto.RegisterRequest;
import com.careconnect.entity.Role;
import com.careconnect.entity.User;
import com.careconnect.exception.ApiException;
import com.careconnect.repository.UserRepository;
import com.careconnect.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);   // low cost = fast tests
    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtService jwt = new JwtService("unit-test-secret-unit-test-secret-1234567890", 60_000);
        authService = new AuthService(userRepository, encoder, jwt);
    }

    @Test
    void registerStoresBcryptHashAndNormalisedEmailAndReturnsToken() {
        when(userRepository.existsByEmail("asha@test.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 1L);
            return u;
        });

        AuthResponse response = authService.register(
                new RegisterRequest("  Asha  ", " ASHA@Test.com ", "Secret123!", Role.PARENT));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertNotEquals("Secret123!", saved.getValue().getPasswordHash());
        assertTrue(encoder.matches("Secret123!", saved.getValue().getPasswordHash()));
        assertEquals("asha@test.com", saved.getValue().getEmail());
        assertEquals("Asha", saved.getValue().getName());
        assertFalse(response.token().isBlank());
        assertEquals(Role.PARENT, response.user().role());
    }

    @Test
    void registerDuplicateEmailIsConflict() {
        when(userRepository.existsByEmail("asha@test.com")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () ->
                authService.register(new RegisterRequest("Asha", "asha@test.com", "Secret123!", Role.PARENT)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void loginWithCorrectPasswordReturnsToken() {
        User user = existingUser("Secret123!");
        when(userRepository.findByEmail("asha@test.com")).thenReturn(Optional.of(user));

        AuthResponse response = authService.login(new LoginRequest("Asha@test.com", "Secret123!"));

        assertFalse(response.token().isBlank());
        assertEquals("asha@test.com", response.user().email());
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() {
        when(userRepository.findByEmail("asha@test.com")).thenReturn(Optional.of(existingUser("Secret123!")));

        ApiException ex = assertThrows(ApiException.class,
                () -> authService.login(new LoginRequest("asha@test.com", "WrongPassword")));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void loginWithUnknownEmailGivesSameUnauthorizedMessage() {
        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> authService.login(new LoginRequest("nobody@test.com", "whatever123")));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("Invalid email or password", ex.getMessage());
    }

    private User existingUser(String rawPassword) {
        User u = new User("Asha", "asha@test.com", encoder.encode(rawPassword), Role.PARENT);
        ReflectionTestUtils.setField(u, "id", 1L);
        return u;
    }
}
