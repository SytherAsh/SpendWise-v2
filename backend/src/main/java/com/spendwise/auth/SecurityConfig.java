package com.spendwise.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Two fully independent {@link SecurityFilterChain}s (E1-S1-T7 + E1-S2-T1):
 *
 * <ol>
 *   <li>{@code adminFilterChain} — matches only {@code /api/v1/admin/**}, guarded by {@link
 *       AdminJwtAuthFilter} ({@code ADMIN_JWT_SECRET}). Given {@code @Order(1)}, so it always
 *       evaluates before the default chain for admin paths.
 *   <li>{@code defaultFilterChain} — everything else, guarded by {@link UserJwtAuthFilter}
 *       ({@code JWT_SECRET}). Permits the public {@code /auth/*} endpoints and {@code
 *       /health}; every other {@code /api/v1/**} route requires a valid user JWT.
 * </ol>
 *
 * Neither chain's filter delegates to the other's validation logic — see each filter's
 * class-level note.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http, @Value("${app.security.admin-jwt-secret}") String adminJwtSecret)
            throws Exception {
        http.securityMatcher("/api/v1/admin/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .addFilterBefore(new AdminJwtAuthFilter(adminJwtSecret), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultFilterChain(HttpSecurity http, UserJwtService userJwtService) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/v1/auth/otp/send",
                                "/api/v1/auth/otp/verify",
                                "/api/v1/auth/google",
                                "/api/v1/auth/token/refresh",
                                "/api/v1/health")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(new UserJwtAuthFilter(userJwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
