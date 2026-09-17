package com.example.nairawallet.Service;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.example.nairawallet.Dto.Response.LedgerEntryResponse;
import com.example.nairawallet.Entity.LedgerEntry;
import com.example.nairawallet.Entity.Transaction;
import com.example.nairawallet.Entity.Wallet;
import com.example.nairawallet.Enum.LedgerEntryType;
import com.example.nairawallet.Mapper.LedgerMapper;
import com.example.nairawallet.Repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LedgerEntryServiceImp implements LedgerEntryService {

    private final LedgerEntryRepository ledgerRepository;
    private final LedgerMapper ledgerMapper;


    @Override
    public void createEntry(Wallet wallet, Transaction transaction,
                            BigDecimal amount, BigDecimal balanceBefore,
                            BigDecimal balanceAfter, LedgerEntryType type,
                            String narration) {
        LedgerEntry ledger =  LedgerEntry.builder()
                .wallet(wallet)
                .transaction(transaction)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .entryType(type)
                .narration(narration)
                .build();
        ledgerRepository.save(ledger);

    }

    public List<LedgerEntryResponse> getStatement(Long walletId){
        List<LedgerEntry> entries =
                ledgerRepository.findByWalletIdOrderByCreatedAtDesc(walletId);
        return entries.stream()
                .map(ledgerMapper::mapToResponse)
                .toList();


    }
}
