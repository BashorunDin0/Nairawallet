package com.bashorundino.nairawallet.service;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.bashorundino.nairawallet.entity.IdempotencyKey;
import com.bashorundino.nairawallet.entity.Transaction;
import com.bashorundino.nairawallet.entity.User;
import com.bashorundino.nairawallet.enums.TransactionType;
import com.bashorundino.nairawallet.exception.DuplicateTransactionException;
import com.bashorundino.nairawallet.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyKeyServiceImp implements IdempotencyKeyService {

    private final IdempotencyKeyRepository repository;

    @Override
    @Transactional(readOnly = true)
    public IdempotencyKey findExisting(
            Long userId,
            TransactionType operationType,
            String key){

        if (key == null || key.isBlank()){
            throw new IllegalArgumentException(
                    "Idempotency-key header is required"
            );
        }

        return repository.findExistingWithTransaction(
                userId,
                operationType,
                key.trim()
        )
                .orElse(null);

    }

    @Override
    public void validateSameRequest(IdempotencyKey existing,
                                    String requestHash) {
        if (!existing.hasSameHash(requestHash)){
            throw new DuplicateTransactionException(
                    "Idempotency key was already used with different request details"
            );
        }

    }

    @Override
   public IdempotencyKey create(
            String key,
            User user,
            TransactionType operationType,
            String requestHash
    ){
        IdempotencyKey idempotencyKey = IdempotencyKey.create(
                key,
                user,
                operationType,
                requestHash
        );
        return repository.saveAndFlush(idempotencyKey);
    }

    @Override
    public void markCompleted(
            IdempotencyKey idempotencyKey,
            Transaction transaction) {
        idempotencyKey.markCompleted(transaction);
    }
}
