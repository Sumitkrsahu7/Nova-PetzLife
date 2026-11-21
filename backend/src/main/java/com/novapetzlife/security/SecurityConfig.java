package com.novapetzlife.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final AuthCookieCsrfFilter authCookieCsrfFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    public SecurityConfig(
            CorsConfigurationSource corsConfigurationSource,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RateLimitingFilter rateLimitingFilter,
            AuthCookieCsrfFilter authCookieCsrfFilter,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            JwtAccessDeniedHandler jwtAccessDeniedHandler) {
        this.corsConfigurationSource = corsConfigurationSource;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.authCookieCsrfFilter = authCookieCsrfFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(contentType -> {})
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Preflight CORS OPTIONS requests must always be permitted unconditionally
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Public Health, Swagger, Docs & Actuator
                        .requestMatchers(
                                "/api/health/**",
                                "/api/v1/health/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/**"
                        ).permitAll()
                        // Public Auth Endpoints
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Public Catalog Browsing (GET only)
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/brands/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                        // Public Shopping Cart, Delivery Geolocation & Store Settings
                        .requestMatchers("/api/v1/cart/**").permitAll()
                        .requestMatchers("/api/v1/delivery/**").permitAll()
                        .requestMatchers("/api/v1/settings/**").permitAll()
                        // Public Order Tracking, Invoices & Returns (Checkout requires authentication)
                        .requestMatchers("/api/v1/orders/*/track").permitAll()
                        .requestMatchers("/api/v1/orders/*/invoice").permitAll()
                        .requestMatchers("/api/v1/orders/*/return-eligibility").permitAll()
                        .requestMatchers("/api/v1/orders/*/cancel").permitAll()
                        .requestMatchers("/api/v1/returns/**").permitAll()
                        // Public Promotions & Customer Pet Profiles
                        .requestMatchers("/api/v1/promotions/coupons/**").permitAll()
                        .requestMatchers("/api/v1/customer/pets/**").permitAll()
                        // Public Customer Support, FAQs & Notifications
                        .requestMatchers("/api/v1/support/**").permitAll()
                        .requestMatchers("/api/v1/customer/notifications/**").permitAll()
                        // Public Customer Product Reviews & Ratings
                        .requestMatchers("/api/v1/reviews/**").permitAll()
                        // Public Google Business Profile & Synced Reviews
                        .requestMatchers("/api/v1/google-business/**").permitAll()
                        // Payment Processing: Webhooks are public with HMAC signature check; all client payment endpoints require authentication
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhook").permitAll()
                        .requestMatchers("/api/v1/payments/**").authenticated()
                        // Admin Protected Endpoints
                        .requestMatchers("/api/v1/admin/**").hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_STAFF_ADMIN")
                        // Customer Protected Endpoints
                        .requestMatchers("/api/v1/customer/**").authenticated()
                        .requestMatchers("/api/v1/orders/**").authenticated()
                        // Any other request requires authentication
                        .anyRequest().authenticated()
                )
                // Add rate limiting filter
                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
                // Add CSRF origin filter for cookie-authenticated auth endpoints
                .addFilterBefore(authCookieCsrfFilter, UsernamePasswordAuthenticationFilter.class)
                // Add JWT filter before UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
