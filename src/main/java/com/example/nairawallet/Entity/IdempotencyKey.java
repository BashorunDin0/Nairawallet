package com.example.nairawallet.Entity;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;
    @CreationTimestamp
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime expireAt;
}
