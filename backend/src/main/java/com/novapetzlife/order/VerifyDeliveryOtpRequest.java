package com.novapetzlife.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyDeliveryOtpRequest(
        @NotBlank(message = "Delivery OTP is required")
        @Pattern(regexp = "^[0-9]{6}$", message = "Delivery OTP must be a 6-digit numeric code")
        String otp
) {}
