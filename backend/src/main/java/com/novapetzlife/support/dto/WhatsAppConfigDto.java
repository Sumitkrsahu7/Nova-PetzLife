package com.novapetzlife.support.dto;

public record WhatsAppConfigDto(
        String phoneNumber,
        String displayPhoneNumber,
        String defaultMessage,
        String whatsappUrl
) {}
