package com.example.nairawallet.Service;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.example.nairawallet.Dto.Response.LedgerEntryResponse;
import com.example.nairawallet.Entity.Transaction;
import com.example.nairawallet.Entity.Wallet;
import com.example.nairawallet.Enum.LedgerEntryType;

import java.math.BigDecimal;
import java.util.List;

public interface LedgerEntryService {
    void createEntry(Wallet wallet, Transaction transaction,
                     BigDecimal amount, BigDecimal balanceBefore,
                     BigDecimal balanceAfter, LedgerEntryType type,
                     String narration);

    List<LedgerEntryResponse> getStatement(Long walletId);
}
