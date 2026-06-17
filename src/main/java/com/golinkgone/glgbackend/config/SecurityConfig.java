package com.golinkgone.glgbackend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.allowed-origins}")
    private List<String> allowedOrigins;

    @Value("${ADMIN_EMAIL:}")
    private String adminEmail;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http.headers(headers -> headers.contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        // HSTS: enforce HTTPS for one year for both domains and all subdomains.
                        .httpStrictTransportSecurity(hsts ->
                                hsts.includeSubDomains(true).preload(true).maxAgeInSeconds(31_536_000))
                        .referrerPolicy(rp -> rp.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration config = new CorsConfiguration();
                    config.setAllowedOrigins(allowedOrigins);
                    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                    config.setAllowedHeaders(List.of("*"));
                    // Cache preflight for an hour so the dashboard doesn't preflight every call.
                    config.setMaxAge(3600L);
                    return config;
                }))
                // 2. Disable CSRF (safe to do for stateless REST APIs using JWTs)
                .csrf(AbstractHttpConfigurer::disable)
                // 3. Define endpoint access rules
                .authorizeHttpRequests(authz -> authz.requestMatchers("/health", "/info", "/actuator/health")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/{shortKey:[A-Za-z0-9]{6}}")
                        .permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/{shortKey:[A-Za-z0-9]{6}}")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/create")
                        .permitAll()
                        .requestMatchers("/actuator/**")
                        .access(this::isAdmin)
                        .anyRequest()
                        .authenticated())
                // 4. Enable JWT verification
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }

    private AuthorizationDecision isAdmin(
            Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        Authentication auth = authentication.get();
        boolean allowed = !adminEmail.isBlank()
                && auth instanceof JwtAuthenticationToken jwt
                && adminEmail.equalsIgnoreCase(jwt.getToken().getClaimAsString("email"));
        return new AuthorizationDecision(allowed);
    }

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        RestTemplate rest = new RestTemplate();
        rest.setRequestFactory(factory);

        Cache cache = new CaffeineCache(
                "jwkSet", Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(30)).maximumSize(4).build());

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .restOperations(rest)
                .cache(cache)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri));
        return decoder;
    }
}
