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

    public SecurityConfig(
            CorsConfigurationSource corsConfigurationSource,
            JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.corsConfigurationSource = corsConfigurationSource;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
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
                        // Public Shopping Cart & Delivery Geolocation
                        .requestMatchers("/api/v1/cart/**").permitAll()
                        .requestMatchers("/api/v1/delivery/**").permitAll()
                        // Public Checkout, Order Tracking, Invoices & Returns
                        .requestMatchers("/api/v1/orders/checkout").permitAll()
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
                        // Public Payment Processing & Razorpay Webhooks
                        .requestMatchers("/api/v1/payments/**").permitAll()
                        // Admin Protected Endpoints
                        .requestMatchers("/api/v1/admin/**").hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_STAFF_ADMIN")
                        // Any other request requires authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
