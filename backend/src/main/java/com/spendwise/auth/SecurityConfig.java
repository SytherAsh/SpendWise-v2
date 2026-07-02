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
 * Two of the three {@link SecurityFilterChain}s in the app (E1-S1-T7 + E1-S2-T1) — the third,
 * {@code com.spendwise.ingest.IngestSecurityConfig}'s {@code @Order(2)} chain for {@code
 * /api/v1/ingest/**} (E3-S1-T1), sits between these two:
 *
 * <ol>
 *   <li>{@code adminFilterChain} — matches only {@code /api/v1/admin/**}, guarded by {@link
 *       AdminJwtAuthFilter} ({@code ADMIN_JWT_SECRET}). Given {@code @Order(1)}, so it always
 *       evaluates before every other chain for admin paths.
 *   <li>{@code defaultFilterChain} — everything else, guarded by {@link UserJwtAuthFilter}
 *       ({@code JWT_SECRET}). Permits the public {@code /auth/*} endpoints and {@code
 *       /health}; every other {@code /api/v1/**} route requires a valid user JWT. Given {@code
 *       @Order(3)} (last) so its catch-all {@code securityMatcher} never shadows the ingest
 *       chain's more specific one.
 * </ol>
 *
 * No two chains' filters delegate to each other's validation logic — see each filter's
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
    @Order(3)
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
