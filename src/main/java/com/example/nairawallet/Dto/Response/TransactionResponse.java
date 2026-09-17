package com.example.nairawallet.Dto.Response;

import com.example.nairawallet.Enum.TransactionStatus;
import com.example.nairawallet.Enum.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

public record TransactionResponse(
         String reference,
         BigDecimal amount,
         TransactionStatus status,
         TransactionType type,
         String narration,
         Instant createdAt
) {
}
