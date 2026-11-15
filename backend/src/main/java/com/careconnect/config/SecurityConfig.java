package com.careconnect.config;
import jakarta.servlet.http.HttpServletResponse;

import com.careconnect.security.JwtAuthFilter;
import com.careconnect.security.JwtAuthenticationEntryPoint;
import com.careconnect.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final JwtAuthenticationEntryPoint entryPoint;

    @Value("${app.cors.allowed-origin}")
    private String allowedOrigin;

    public SecurityConfig(JwtService jwtService, JwtAuthenticationEntryPoint entryPoint) {
        this.jwtService = jwtService;
        this.entryPoint = entryPoint;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)   // stateless token API, no cookies -> CSRF not applicable
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
    .authenticationEntryPoint(entryPoint)
    .accessDeniedHandler((request, response, exception) -> {
        System.out.println("ACCESS DENIED HANDLER CALLED: " + exception.getMessage());

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(
            "{\"status\":403,\"message\":\"Access denied\"}"
        );
    })
)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                // role gates (services re-check ownership too: defence in depth)
                .requestMatchers(HttpMethod.POST, "/api/family/invite", "/api/family/approve").hasRole("PARENT")
                .requestMatchers(HttpMethod.POST, "/api/family/connect").hasRole("CHILD")
                // medications: anyone logged in may read (the service checks ownership / approved link),
                // only parents may change
                .requestMatchers(HttpMethod.POST, "/api/medications/**").hasRole("PARENT")
                .requestMatchers(HttpMethod.PUT, "/api/medications/**").hasRole("PARENT")
                .requestMatchers(HttpMethod.DELETE, "/api/medications/**").hasRole("PARENT")
                // dose tracking: reads for any logged-in user (service checks ownership / approved link),
                // confirming and undoing doses is PARENT only
                .requestMatchers(HttpMethod.POST, "/api/medication-logs/**").hasRole("PARENT")
                .requestMatchers(HttpMethod.DELETE, "/api/medication-logs/**").hasRole("PARENT")
                // blister strips: reading is open to any logged-in user (service checks), taking/undoing is PARENT only
                .requestMatchers(HttpMethod.POST, "/api/tablets/**").hasRole("PARENT")
                // daily check-ins: reading is open to any logged-in user (the service checks ownership, approval and
                // the share-check-ins consent); every way of changing one is PARENT only
                .requestMatchers(HttpMethod.POST, "/api/checkins/**").hasRole("PARENT")
                .requestMatchers(HttpMethod.PUT, "/api/checkins/**").hasRole("PARENT")
                .requestMatchers(HttpMethod.DELETE, "/api/checkins/**").hasRole("PARENT")
                .anyRequest().authenticated())
            .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
