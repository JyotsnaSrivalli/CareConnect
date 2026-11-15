package com.careconnect.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Runs on every request. If a valid "Authorization: Bearer <token>" header is present,
 * it marks the request as authenticated. If not, it does nothing and Spring Security
 * decides (401/403) based on the rules in SecurityConfig.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        System.out.println("REQUEST: " + request.getMethod() + " " + request.getRequestURI());
        System.out.println("AUTH HEADER PRESENT: " + (header != null));
        if (header != null && header.startsWith("Bearer ")) {
            try {
                AuthenticatedUser user = jwtService.parse(header.substring(7));
                System.out.println("JWT VALID: " + user.email() + " ROLE: " + user.role());
                var authentication = new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                System.out.println("AUTHENTICATION SET: " + SecurityContextHolder.getContext().getAuthentication());
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext(); // bad/expired token => stay unauthenticated
                System.out.println("JWT INVALID: " + e.getMessage());
            }
        }
        System.out.println("BEFORE CHAIN: " + SecurityContextHolder.getContext().getAuthentication());
        chain.doFilter(request, response);
    }
}
