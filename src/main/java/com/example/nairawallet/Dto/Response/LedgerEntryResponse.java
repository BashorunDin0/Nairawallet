package com.example.nairawallet.Dto.Response;

import com.example.nairawallet.Enum.LedgerEntryType;

import java.math.BigDecimal;
import java.time.Instant;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */
public record LedgerEntryResponse(
        BigDecimal amount,
        LedgerEntryType entryType,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        String narration,
        Instant createdAt
) {
}
