package com.example.nairawallet.Dto.Request;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferRequest(
        @NotNull
        Long senderWalletId,
        @NotNull
        Long receiverWalletId,
        @Positive
        BigDecimal amount,

        String narration
) {
}
