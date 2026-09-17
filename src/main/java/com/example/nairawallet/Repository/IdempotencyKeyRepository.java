package com.example.nairawallet.Repository;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.example.nairawallet.Entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByIdempotencyKey(String key);

    boolean existsByIdempotencyKeyAndExpireAtAfter(String key, LocalDateTime now);

    void deleteByExpireAtBefore(LocalDateTime dateTime);

}
