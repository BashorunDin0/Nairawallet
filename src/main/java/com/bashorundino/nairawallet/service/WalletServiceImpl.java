package com.bashorundino.nairawallet.service;

import com.bashorundino.nairawallet.dto.response.WalletResponse;
import com.bashorundino.nairawallet.entity.Wallet;
import com.bashorundino.nairawallet.exception.WalletNotFoundException;
import com.bashorundino.nairawallet.mapper.WalletMapper;
import com.bashorundino.nairawallet.repository.WalletRepository;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import java.util.Optional;

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
                new WalletNotFoundException("wallet not found" + walletId));
    }

    @Override
    public WalletResponse getWallet(Long walletId) {

        Wallet wallet = findById(walletId);
        return walletMapper.mapToResponse(wallet);
    }

    public Wallet findByIdForUpdate(Long walletId){
        return walletRepository.findByIdForUpdate(walletId).orElseThrow(() ->
                new WalletNotFoundException("wallet not found " + walletId)
                );
    }
}
