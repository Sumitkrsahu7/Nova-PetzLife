package com.novapetzlife.auth;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.security.ClientIpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "User authentication, JWT sign-in, token refresh, and registration")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final long refreshExpirationMs;
    private final boolean cookieSecure;
    private final String cookieSameSite;

    public AuthController(
            AuthService authService,
            PasswordResetService passwordResetService,
            @Value("${app.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs,
            @Value("${app.jwt.cookie-secure:false}") boolean cookieSecure,
            @Value("${app.jwt.cookie-same-site:Lax}") String cookieSameSite) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.refreshExpirationMs = refreshExpirationMs;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and issue JWT + refresh token cookie")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = ClientIpUtil.getClientIp(httpRequest);
        AuthResult result = authService.login(request, ipAddress);

        ResponseCookie cookie = createRefreshTokenCookie(result.rawRefreshToken(), refreshExpirationMs / 1000);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok("Login successful", result.response()));
    }

    @PostMapping("/register")
    @Operation(summary = "Register new customer account")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = ClientIpUtil.getClientIp(httpRequest);
        AuthResult result = authService.register(request, ipAddress);

        ResponseCookie cookie = createRefreshTokenCookie(result.rawRefreshToken(), refreshExpirationMs / 1000);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok("Registration successful", result.response()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh expired access token using HttpOnly cookie")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = "refreshToken", required = false) String cookieRefreshToken,
            HttpServletRequest httpRequest) {

        if (!StringUtils.hasText(cookieRefreshToken)) {
            throw new BadCredentialsException("Refresh token cookie is required to renew session");
        }

        String ipAddress = ClientIpUtil.getClientIp(httpRequest);
        AuthResult result = authService.refresh(cookieRefreshToken.trim(), ipAddress);

        var responseBuilder = ResponseEntity.ok();
        // Only set rotated cookie if a new token was generated (initial rotation).
        // If recovered concurrently within grace period, the active cookie is already held by the client.
        if (result.rawRefreshToken() != null) {
            ResponseCookie cookie = createRefreshTokenCookie(result.rawRefreshToken(), refreshExpirationMs / 1000);
            responseBuilder.header(HttpHeaders.SET_COOKIE, cookie.toString());
        }

        return responseBuilder.body(ApiResponse.ok("Session refreshed successfully", result.response()));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile")
    public ResponseEntity<ApiResponse<AuthResponse>> getCurrentUser() {
        AuthResponse response = authService.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out user, invalidate refresh token, and clear security context")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = "refreshToken", required = false) String cookieRefreshToken,
            HttpServletRequest httpRequest) {

        String ipAddress = ClientIpUtil.getClientIp(httpRequest);
        if (StringUtils.hasText(cookieRefreshToken)) {
            authService.logout(cookieRefreshToken.trim(), ipAddress);
        } else {
            authService.logout(null, ipAddress);
        }

        ResponseCookie cleanCookie = createCleanRefreshTokenCookie();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cleanCookie.toString())
                .body(ApiResponse.ok("Logout successful", null));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Initiate password reset flow by sending reset link email")
    public ResponseEntity<ApiResponse<String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = ClientIpUtil.getClientIp(httpRequest);
        String message = passwordResetService.requestPasswordReset(request, ipAddress);
        return ResponseEntity.ok(ApiResponse.ok(message, message));
    }

    @GetMapping("/reset-password/validate")
    @Operation(summary = "Verify validity of a password reset token")
    public ResponseEntity<ApiResponse<Boolean>> validateResetToken(
            @RequestParam("token") String token) {
        boolean valid = passwordResetService.validateToken(token);
        return ResponseEntity.ok(ApiResponse.ok("Token is valid", valid));
    }

    @PostMapping("/reset-password/validate")
    @Operation(summary = "Verify validity of a password reset token via POST")
    public ResponseEntity<ApiResponse<Boolean>> validateResetTokenPost(
            @RequestBody(required = false) java.util.Map<String, String> body,
            @RequestParam(value = "token", required = false) String paramToken) {
        String token = (body != null && body.containsKey("token")) ? body.get("token") : paramToken;
        boolean valid = passwordResetService.validateToken(token);
        return ResponseEntity.ok(ApiResponse.ok("Token is valid", valid));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset account password using secure token")
    public ResponseEntity<ApiResponse<String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = ClientIpUtil.getClientIp(httpRequest);
        String message = passwordResetService.resetPassword(request, ipAddress);
        return ResponseEntity.ok(ApiResponse.ok(message, message));
    }

    private ResponseCookie createRefreshTokenCookie(String rawRefreshToken, long maxAgeSeconds) {
        return ResponseCookie.from("refreshToken", rawRefreshToken != null ? rawRefreshToken : "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/v1/auth")
                .maxAge(maxAgeSeconds)
                .build();
    }

    private ResponseCookie createCleanRefreshTokenCookie() {
        return ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
    }
}
