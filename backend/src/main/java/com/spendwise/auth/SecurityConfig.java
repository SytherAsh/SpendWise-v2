package com.spendwise.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Three of the four {@link SecurityFilterChain}s in the app (E1-S1-T7 + E1-S2-T1 + E11-S1-T1) —
 * the fourth, {@code com.spendwise.ingest.IngestSecurityConfig}'s {@code @Order(2)} chain for
 * {@code /api/v1/ingest/**} (E3-S1-T1), sits between {@code adminFilterChain} and {@code
 * defaultFilterChain}:
 *
 * <ol>
 *   <li>{@code adminLoginFilterChain} — matches only {@code /api/v1/admin/auth/login}, no auth
 *       filter at all (that's the whole point — this is where an admin token is obtained).
 *       Given {@code @Order(0)}, one step ahead of {@code adminFilterChain}'s broader {@code
 *       /api/v1/admin/**} matcher, so login requests never reach {@link AdminJwtAuthFilter} —
 *       which, unlike {@link UserJwtAuthFilter}, rejects outright when no Bearer header is
 *       present rather than deferring to {@code authorizeHttpRequests}, so a {@code permitAll}
 *       rule on the broader chain would never actually be reached for this path.
 *   <li>{@code adminFilterChain} — matches every other {@code /api/v1/admin/**} route, guarded
 *       by {@link AdminJwtAuthFilter} ({@code ADMIN_JWT_SECRET}). Given {@code @Order(1)}.
 *   <li>{@code defaultFilterChain} — everything else, guarded by {@link UserJwtAuthFilter}
 *       ({@code JWT_SECRET}). Permits the public {@code /auth/*} endpoints and {@code
 *       /health}; every other {@code /api/v1/**} route requires a valid user JWT. Given {@code
 *       @Order(3)} (last) so its catch-all {@code securityMatcher} never shadows the more
 *       specific chains ahead of it.
 * </ol>
 *
 * No two chains' filters delegate to each other's validation logic — see each filter's
 * class-level note.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(0)
    public SecurityFilterChain adminLoginFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/v1/admin/auth/login")
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http, @Value("${app.security.admin-jwt-secret}") String adminJwtSecret)
            throws Exception {
        http.securityMatcher("/api/v1/admin/**")
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .addFilterBefore(new AdminJwtAuthFilter(adminJwtSecret), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain defaultFilterChain(HttpSecurity http, UserJwtService userJwtService) throws Exception {
        http.cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
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
