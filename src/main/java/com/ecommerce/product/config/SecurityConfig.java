package com.ecommerce.product.config;

import com.ecommerce.product.dto.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
import java.io.IOException;
import java.time.Instant;

@Configuration
public class SecurityConfig {
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
		var entryPoint = (org.springframework.security.web.AuthenticationEntryPoint) (request, response,
				error) -> write(mapper, request, response, 401, "UNAUTHORIZED", "A valid bearer token is required");
		var denied = (org.springframework.security.web.access.AccessDeniedHandler) (request, response,
				error) -> write(mapper, request, response, 403, "FORBIDDEN", "Insufficient permissions");
		return http.csrf(c -> c.disable())
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(a -> a
						.requestMatchers("/actuator/health", "/actuator/health/**", "/swagger-ui/**",
								"/swagger-ui.html", "/v3/api-docs/**")
						.permitAll().requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/products").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PUT, "/api/products/*").hasRole("ADMIN")
						.requestMatchers(HttpMethod.DELETE, "/api/products/*").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PATCH, "/internal/api/inventory/*/adjust").hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/internal/api/inventory/*").hasAnyRole("ADMIN", "SERVICE")
						.requestMatchers(HttpMethod.POST, "/internal/api/inventory/reserve",
								"/internal/api/inventory/release", "/internal/api/inventory/confirm")
						.hasRole("SERVICE").requestMatchers("/actuator/info").hasRole("ADMIN").anyRequest().denyAll())
				.exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied))
				.oauth2ResourceServer(o -> o.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied)
						.jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter())))
				.build();
	}

	@Bean
	JwtDecoder jwtDecoder(@Value("${app.security.jwk-set-uri}") String jwks,
			@Value("${app.security.issuer}") String issuer, @Value("${app.security.audience}") String audience) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
		OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(audience)
				? OAuth2TokenValidatorResult.success()
				: OAuth2TokenValidatorResult
						.failure(new OAuth2Error("invalid_token", "Required audience missing", null));
		decoder.setJwtValidator(
				new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audienceValidator));
		return decoder;
	}

	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
		roles.setAuthoritiesClaimName("roles");
		roles.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(roles);
		return converter;
	}

	private void write(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response, int status,
			String code, String message) throws IOException {
		response.setStatus(status);
		response.setContentType("application/json");
		if (status == 401)
			response.setHeader("WWW-Authenticate", "Bearer");
		mapper.writeValue(response.getOutputStream(), new ErrorResponse(Instant.now(), status, code, message,
				request.getRequestURI(), MDC.get("correlationId"), null, null, null));
	}
}
