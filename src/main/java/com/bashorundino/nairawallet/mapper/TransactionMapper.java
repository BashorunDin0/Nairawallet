package com.bashorundino.nairawallet.mapper;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.bashorundino.nairawallet.dto.response.TransactionResponse;
import com.bashorundino.nairawallet.entity.Transaction;
import com.bashorundino.nairawallet.enums.TransactionDirection;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {
    public TransactionResponse mapToResponse(
            Transaction transaction,
            Long walletId){

        TransactionDirection direction = determineDirection(transaction, walletId);

        return new TransactionResponse(
                transaction.getTxReference(),
                transaction.getAmount(),
                transaction.getStatus(),
                transaction.getTransactionType(),
                transaction.getNarration(),
                transaction.getCreatedAt(),
                direction
        );
    }

    private TransactionDirection determineDirection(
            Transaction transaction,
            Long walletId) {
        if (transaction.getDestinationWallet() != null &&
                transaction.getDestinationWallet().getId().equals(walletId)){
            return TransactionDirection.INCOMING;
        }
        if (transaction.getSourceWallet() != null &&
                transaction.getSourceWallet().getId().equals(walletId)){
            return TransactionDirection.OUTGOING;
        }
        throw new IllegalArgumentException("Wallet is not associated with transaction");
    }
}