package com.bashorundino.nairawallet.dto.request;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransferRequest(
        @NotNull(message = "senderWalletId is required")
        @Positive(message = "senderWalletId must be positive")
        Long senderWalletId,

        @NotNull(message = "receiverWalletId is required")
        @Positive(message = "receiverWalletId must be positive")
        Long receiverWalletId,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be greater than zero")
        @Digits(integer = 17, fraction = 2, message = "amount must have at most 2 decimal places")
        BigDecimal amount,

        @Size(max = 255, message = "narration cannot exceed 255 characters")
        String narration
) {
}
