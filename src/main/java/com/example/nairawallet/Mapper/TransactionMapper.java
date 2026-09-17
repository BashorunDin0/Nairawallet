package com.example.nairawallet.Mapper;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.example.nairawallet.Dto.Response.TransactionResponse;
import com.example.nairawallet.Entity.Transaction;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {
    public TransactionResponse mapToResponse(Transaction transaction){
        return new TransactionResponse(
                transaction.getTxReference(),
                transaction.getAmount(),
                transaction.getStatus(),
                transaction.getTransactionType(),
                transaction.getNarration(),
                transaction.getCreatedAt()
        );
    }
}