package com.bashorundino.nairawallet.service;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.bashorundino.nairawallet.entity.IdempotencyKey;
import com.bashorundino.nairawallet.exception.DuplicateTransactionException;
import com.bashorundino.nairawallet.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyKeyServiceImp implements IdempotencyKeyService {

    private final IdempotencyKeyRepository repository;
    private static final int EXPIRY_HOUR = 24;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void claim(String key){
        if (key == null || key.isBlank()){
            throw new IllegalArgumentException("Idempotency-key header is required");
        }

        String normalizedKey = key.trim();
        Instant now = Instant.now();

        IdempotencyKey idempotencyKey = IdempotencyKey.builder()
                .idempotencyKey(normalizedKey)
                .createdAt(now)
                .expireAt(now.plus(Duration.ofHours(EXPIRY_HOUR)))
                .build();
        try {
            repository.saveAndFlush(idempotencyKey);
        } catch (DataIntegrityViolationException exception){
            log.warn("Duplicate idempotency detected: {}", normalizedKey);
            throw new DuplicateTransactionException(
                    "Duplicate request detected");
        }

    }

}
