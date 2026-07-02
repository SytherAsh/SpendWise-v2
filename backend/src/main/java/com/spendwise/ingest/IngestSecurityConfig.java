package com.spendwise.ingest;

import com.spendwise.auth.UserJwtAuthFilter;
import com.spendwise.auth.UserJwtService;
import com.spendwise.user.DeviceApiKeyService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Third {@link SecurityFilterChain} (E3-S1-T1), alongside {@code com.spendwise.auth.SecurityConfig}'s
 * admin ({@code @Order(1)}) and default ({@code @Order(3)}) chains — matches only {@code
 * /api/v1/ingest/**} so it evaluates before the default chain would otherwise claim the path.
 * Composes the existing {@link UserJwtAuthFilter} (E1-S1-T7) with the new {@link
 * DeviceApiKeyAuthFilter} (E3-S1-T1): both must pass for a request to reach {@link
 * IngestController}. Reuses {@code UserJwtAuthFilter} rather than duplicating JWT validation —
 * only the device-key half is new.
 */
@Configuration
public class IngestSecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain ingestFilterChain(
            HttpSecurity http, UserJwtService userJwtService, DeviceApiKeyService deviceApiKeyService) throws Exception {
        http.securityMatcher("/api/v1/ingest/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .addFilterBefore(new UserJwtAuthFilter(userJwtService), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new DeviceApiKeyAuthFilter(deviceApiKeyService), UserJwtAuthFilter.class);
        return http.build();
    }
}
