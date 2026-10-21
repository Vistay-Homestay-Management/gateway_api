package com.vistay.gateway.config;

import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.web.server.ServerHttpSecurity;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    ReactiveJwtDecoder jwtDecoder(@Value("${vistay.security.jwt-secret}") String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("VISTAY_JWT_SECRET must contain at least 32 characters");
        }
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    SecurityWebFilterChain securityWebFilterChain(
        ServerHttpSecurity http,
        ReactiveStringRedisTemplate redis
    ) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .pathMatchers(
                    "/api/v1/auth/register/customer",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/internal/service-info",
                    "/actuator/health/**",
                    "/actuator/info"
                ).permitAll()
                .anyExchange().access((authentication, context) ->
                    authentication
                        .filter(auth -> auth.isAuthenticated())
                        .cast(JwtAuthenticationToken.class)
                        .flatMap(token -> {
                            var jwt = token.getToken();
                            var sessionId = jwt.getClaimAsString("session_id");
                            var jtiKey = "auth:revoked:jti:" + jwt.getId();
                            var sessionKey = "auth:revoked:session:" + sessionId;
                            return redis.hasKey(jtiKey)
                                .zipWith(redis.hasKey(sessionKey))
                                .<AuthorizationResult>map(result ->
                                    new AuthorizationDecision(!result.getT1() && !result.getT2()));
                        })
                        .defaultIfEmpty(new AuthorizationDecision(false))
                ))
            .oauth2ResourceServer(resource -> resource.jwt(jwt -> {}))
            .build();
    }

    @Bean
    CorsWebFilter corsWebFilter(@Value("${vistay.cors.allowed-origins}") List<String> origins) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id", "Last-Event-ID"));
        config.setExposedHeaders(List.of("X-Request-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
