package com.careconnect.security;

import com.careconnect.entity.Role;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234567890";

    @Test
    void tokenRoundTripKeepsIdEmailAndRole() {
        JwtService jwt = new JwtService(SECRET, 60_000);
        String token = jwt.generateToken(7L, "a@b.com", Role.PARENT);

        AuthenticatedUser user = jwt.parse(token);

        assertEquals(7L, user.id());
        assertEquals("a@b.com", user.email());
        assertEquals(Role.PARENT, user.role());
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService jwt = new JwtService(SECRET, 60_000);
        String token = jwt.generateToken(1L, "a@b.com", Role.CHILD);
        int i = token.indexOf('.') + 5;                          // a character inside the payload
        char flipped = token.charAt(i) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, i) + flipped + token.substring(i + 1);

        assertThrows(JwtException.class, () -> jwt.parse(tampered));
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        String token = new JwtService("another-secret-another-secret-1234567890", 60_000)
                .generateToken(1L, "a@b.com", Role.CHILD);

        assertThrows(JwtException.class, () -> new JwtService(SECRET, 60_000).parse(token));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwt = new JwtService(SECRET, -1_000);   // already expired when created
        String token = jwt.generateToken(1L, "a@b.com", Role.PARENT);

        assertThrows(JwtException.class, () -> jwt.parse(token));
    }

    @Test
    void shortSecretFailsFast() {
        assertThrows(IllegalStateException.class, () -> new JwtService("too-short", 60_000));
    }
}
