package com.bashorundino.nairawallet.entity;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "idempotency_keys", indexes = {
        @Index(name = "idx_idempotency_expire_at", columnList = "expire_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKey {

    private static final Duration DEFAULT_VALIDITY = Duration.ofHours(24);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    private Instant createdAt;

    @Column(nullable = false)
    private Instant expireAt;

    public static IdempotencyKey create(String idempotencyKey){
        Objects.requireNonNull(idempotencyKey,
                "idempotency key cannot be null"
        );

        String normalizedKey = idempotencyKey.trim();

        if (normalizedKey.isBlank()){
            throw new IllegalArgumentException(
                    "idempotency key cannot be blank"
            );
        }

        if (normalizedKey.length() > 255){
            throw new IllegalArgumentException(
                    "idempotency key cannot exceed 255 characters"
            );
        }

        Instant now = Instant.now();

        IdempotencyKey key = new IdempotencyKey();
        key.idempotencyKey = normalizedKey;
        key.createdAt = now;
        key.expireAt = now.plus(DEFAULT_VALIDITY);

        return key;
    }

    public boolean isExpired(){
        return Instant.now().isAfter(expireAt);
    }
}
