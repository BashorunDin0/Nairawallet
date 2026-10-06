package com.bashorundino.nairawallet.entity;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.bashorundino.nairawallet.enums.LedgerEntryType;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "ledger_entries", indexes = {@Index(name = "idx_ledger_transaction_id",
        columnList = "transaction_id"),
        @Index(name = "idx_ledger_wallet_id", columnList = "wallet_id"),
        @Index(name = "idx_ledger_created_at", columnList = "created_at")
}
)

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @DecimalMin(value = "0.01")
    @Digits(integer = 17, fraction = 2)
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Digits(integer = 17, fraction = 2)
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceBefore;

    @Digits(integer = 17, fraction = 2)
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerEntryType entryType;

    @NotBlank
    @Size(max = 255)
    @Column(length = 255, nullable = false)
    private String narration;

    @CreationTimestamp
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    public static LedgerEntry create(
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            LedgerEntryType entryType,
            String narration,
            Wallet wallet,
            Transaction transaction
    ){
        Objects.requireNonNull(amount, "ledger amount cannot be null");

        Objects.requireNonNull(balanceBefore, "balance before cannot be null");

        Objects.requireNonNull(balanceAfter, "balance after cannot be null");

        Objects.requireNonNull(entryType, "entry type  cannot be null");

        Objects.requireNonNull(wallet, "wallet cannot be null");

        Objects.requireNonNull(transaction, "transaction cannot be null");

        if (amount.compareTo(BigDecimal.ZERO) <= 0){
            throw new IllegalArgumentException(
                    "ledger amount must be greater than zero"
            );
        }

        if (balanceBefore.compareTo(BigDecimal.ZERO) < 0){
            throw new IllegalArgumentException(
                    "balance before cannot be negative"
            );
        }

        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0){
            throw new IllegalArgumentException(
                    "balance after cannot be negative"
            );
        }

        if (narration == null || narration.isBlank()){
            throw new IllegalArgumentException(
                    "ledger narration can not be blank"
            );
        }

        if (narration.length() > 255){
            throw new IllegalArgumentException(
                    "ledger narration cannot exceed 255 characters"
            );
        }

        validateBalanceTransition(
                amount,
                balanceBefore,
                balanceAfter,
                entryType
        );

        LedgerEntry entry = new LedgerEntry();

        entry.amount = amount;
        entry.balanceBefore = balanceBefore;
        entry.balanceAfter = balanceAfter;
        entry.entryType = entryType;
        entry.narration = narration;
        entry.wallet = wallet;
        entry.transaction = transaction;

        return entry;
    }

    private static void validateBalanceTransition(
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            LedgerEntryType entryType
    ){

        BigDecimal expectedBalance;

        if (entryType == LedgerEntryType.CREDIT){
            expectedBalance =balanceBefore.add(amount);
        } else {
            expectedBalance = balanceBefore.subtract(amount);
        }

        if (expectedBalance.compareTo(balanceAfter) != 0){
            throw new IllegalArgumentException(
                    "ledger balance transition is invalid "
            );
        }
    }

}
