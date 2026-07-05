package com.spendwise.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS policy for the browser clients (Next.js dashboard + admin portal on localhost:3000).
 *
 * <p>Exposed as a {@link CorsConfigurationSource} bean rather than a {@code WebMvcConfigurer}
 * mapping on purpose: every {@code /api/v1/**} request is claimed by one of the app's Spring
 * Security {@link org.springframework.security.web.SecurityFilterChain}s (see
 * {@link SecurityConfig} and {@code com.spendwise.ingest.IngestSecurityConfig}). A
 * {@code WebMvcConfigurer} CORS mapping only runs at the DispatcherServlet layer, which sits
 * <em>after</em> the security filter chain — so preflight {@code OPTIONS} requests to
 * {@code authenticated()} routes get rejected (401) before that mapping ever runs. Each chain
 * enables {@code http.cors(withDefaults())}, which picks up this bean and handles preflight at
 * the security layer instead. Same allowed origins as before; only the enforcement layer moved.
 */
@Configuration
public class CorsConfig {

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(List.of("http://localhost:3000", "http://127.0.0.1:3000"));
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		config.setAllowCredentials(true);
		config.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/v1/**", config);
		return source;
	}
}
