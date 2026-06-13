package com.golinkgone.glgbackend.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.allowed-origins}")
    private List<String> allowedOrigins;

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
                        .anyRequest()
                        .authenticated())
                // 4. Enable JWT verification
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }
}
