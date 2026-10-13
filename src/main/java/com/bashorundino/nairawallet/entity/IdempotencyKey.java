package com.bashorundino.nairawallet.entity;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.bashorundino.nairawallet.enums.IdempotencyStatus;
import com.bashorundino.nairawallet.enums.TransactionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "idempotency_keys", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_idempotency_user_operation_key",
                columnNames = {
                        "user_id",
                        "operation_type",
                        "idempotency_key"
                }
        )
},
        indexes = {
        @Index(name = "idx_idempotency_expire_at", columnList = "expire_at")
}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKey {

    private static final Duration DEFAULT_VALIDITY = Duration.ofHours(24);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 30)
    private TransactionType operationType;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdempotencyStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expire_at", nullable = false)
    private Instant expireAt;

    public static IdempotencyKey create(
            String idempotencyKey,
            User user,
            TransactionType operationType,
            String requestHash
    ) {
        Objects.requireNonNull(idempotencyKey, "idempotency key cannot be null");
        Objects.requireNonNull(user, "user cannot be null");
        Objects.requireNonNull(operationType, "operation type cannot be null");
        Objects.requireNonNull(requestHash, "request hash cannot be null");

        String normalizedKey = validateKey(idempotencyKey);

        if (!requestHash.matches("[a-fA-F0-9]{64}")){
            throw new IllegalArgumentException(
                    "Request hash must be a 64-character SHA-256 hex string"
            );
        }

        Instant now = Instant.now();

        IdempotencyKey key = new IdempotencyKey();
        key.idempotencyKey = normalizedKey;
        key.user = user;
        key.operationType = operationType;
        key.requestHash = requestHash.toLowerCase(Locale.ROOT);
        key.status = IdempotencyStatus.PROCESSING;
        key.createdAt = now;
        key.expireAt = now.plus(DEFAULT_VALIDITY);

        return key;
    }

    public void markCompleted(Transaction transaction){
        Objects.requireNonNull(transaction, "transaction cannot be null");
        if (status != IdempotencyStatus.PROCESSING){
            throw new IllegalStateException(
                    "only a processing idempotency key can be completed"
            );
        }
        this.transaction = transaction;
        this.status = IdempotencyStatus.COMPLETED;
    }

    public boolean hasSameHash(String requestHash){
        return this.requestHash.equalsIgnoreCase(requestHash);
    }

    public boolean isExpired() {
        return !Instant.now().isBefore(expireAt);
    }

    private static String validateKey(String key){
        Objects.requireNonNull(key, "key cannot be null");

        String normalizedKey = key.trim();

        if (normalizedKey.isBlank()) {
            throw new IllegalArgumentException(
                    "idempotency key cannot be blank"
            );
        }

        if (normalizedKey.length() > 255) {
            throw new IllegalArgumentException(
                    "idempotency key cannot exceed 255 characters"
            );
        }
        return normalizedKey;

    }


}
