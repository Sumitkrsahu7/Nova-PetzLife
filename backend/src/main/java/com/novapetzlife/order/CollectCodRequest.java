package com.novapetzlife.order;

import java.math.BigDecimal;

public record CollectCodRequest(BigDecimal amount, String notes) {}
