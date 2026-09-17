package com.example.nairawallet.Repository;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.example.nairawallet.Dto.Response.LedgerEntryResponse;
import com.example.nairawallet.Dto.Response.TransactionResponse;
import com.example.nairawallet.Entity.LedgerEntry;
import com.example.nairawallet.Entity.Transaction;
import com.example.nairawallet.Entity.Wallet;
import com.example.nairawallet.Enum.LedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findByWalletIdOrderByCreatedAtDesc(Long walletId);
}
