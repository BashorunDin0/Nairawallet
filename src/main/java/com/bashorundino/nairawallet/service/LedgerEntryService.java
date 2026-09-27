package com.bashorundino.nairawallet.service;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.bashorundino.nairawallet.dto.response.LedgerEntryResponse;
import com.bashorundino.nairawallet.entity.Transaction;
import com.bashorundino.nairawallet.entity.Wallet;
import com.bashorundino.nairawallet.enums.LedgerEntryType;

import java.math.BigDecimal;
import java.util.List;

public interface LedgerEntryService {
    void createEntry(Wallet wallet, Transaction transaction,
                     BigDecimal amount, BigDecimal balanceBefore,
                     BigDecimal balanceAfter, LedgerEntryType type,
                     String narration);

    List<LedgerEntryResponse> getStatement(Long walletId);
}
