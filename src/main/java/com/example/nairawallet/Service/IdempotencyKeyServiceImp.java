package com.example.nairawallet.Service;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.example.nairawallet.Entity.IdempotencyKey;
import com.example.nairawallet.Exception.DuplicateTransactionException;
import com.example.nairawallet.Repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyKeyServiceImp implements IdempotencyKeyService {

    private final IdempotencyKeyRepository repository;
    private static final int EXPIRY_HOUR = 24;


    @Override
    public void validate(String key) {
        log.debug("Validating idempotency-key: {}", key);
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-key header is required");
        }

        String normalizedKey = key.trim();
        Instant now = Instant.now();


        boolean exists = repository
                .existsByIdempotencyKeyAndExpireAtAfter(
                        normalizedKey, now);

        if (exists) {
            log.warn("Duplicate key detected : {}", normalizedKey);
            throw new DuplicateTransactionException("Duplicate request detected");
        }
        log.debug("Idempotency key validated successfully : {}", normalizedKey);
    }

    @Override
    public void save(String key) {
        log.debug("Saving idempotency key: {}", key);
        String normalizedKey = key.trim();
        Instant now = Instant.now();

        IdempotencyKey idempotencyKey = IdempotencyKey.builder()
                .idempotencyKey(normalizedKey)
                .createdAt(now)
                .expireAt(now.plus(Duration.ofHours(EXPIRY_HOUR)))
                .build();
        repository.save(idempotencyKey);
    }
}
