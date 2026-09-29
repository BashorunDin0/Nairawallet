package com.bashorundino.nairawallet.dto.response;

import com.bashorundino.nairawallet.enums.TransactionDirection;
import com.bashorundino.nairawallet.enums.TransactionStatus;
import com.bashorundino.nairawallet.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

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
         Instant createdAt,
         TransactionDirection direction
) {
}
