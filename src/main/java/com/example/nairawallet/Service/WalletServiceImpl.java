package com.example.nairawallet.Service;

import com.example.nairawallet.Dto.Request.DepositRequest;
import com.example.nairawallet.Dto.Request.WithdrawRequest;
import com.example.nairawallet.Dto.Response.WalletResponse;
import com.example.nairawallet.Entity.Transaction;
import com.example.nairawallet.Entity.Wallet;
import com.example.nairawallet.Enum.TransactionStatus;
import com.example.nairawallet.Enum.TransactionType;
import com.example.nairawallet.Exception.WalletNotFoundException;
import com.example.nairawallet.Mapper.WalletMapper;
import com.example.nairawallet.Repository.TransactionRepository;
import com.example.nairawallet.Repository.WalletRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


import java.util.UUID;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final WalletMapper walletMapper;


    @Override
    public Wallet findById(Long walletId) {
        return walletRepository.findById(walletId).orElseThrow(() ->
                new WalletNotFoundException("wallet not found"));
    }

    @Override
    public WalletResponse getWallet(Long walletId) {

        Wallet wallet = findById(walletId);
        return walletMapper.mapToResponse(wallet);
    }



}
