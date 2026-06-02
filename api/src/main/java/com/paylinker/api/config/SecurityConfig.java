package com.paylinker.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] ADMIN_PATHS = {
            "/api/users/**",
            "/api/campaigns/**",
            "/api/dashboard/**",
            "/api/notifications/**"
    };

    private static final String[] SWAGGER_PATHS = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**"
    };

    @Bean
    @Order(0)
    public SecurityFilterChain swaggerChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(SWAGGER_PATHS)
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(c -> c.disable())
                .build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain adminChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(ADMIN_PATHS)
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(c -> c.disable())
                .cors(Customizer.withDefaults())
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain recipientChain(HttpSecurity http,
                                              LinkSessionAuthFilter linkFilter) throws Exception {
        return http
                .securityMatcher("/api/secure-links/**", "/api/documents/me/**")
                .authorizeHttpRequests(a -> a
                        .requestMatchers(
                                "/api/secure-links/validate",
                                "/api/secure-links/resend-request").permitAll()
                        .requestMatchers("/api/documents/me/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(linkFilter, UsernamePasswordAuthenticationFilter.class)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(c -> c.disable())
                .cors(Customizer.withDefaults())
                .build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain actuatorChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/actuator/health/**")
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(c -> c.disable())
                .build();
    }

    @Bean
    @Order(4)
    public SecurityFilterChain unsubscribeChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/unsubscribe/**")
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(c -> c.disable())
                .build();
    }
}
