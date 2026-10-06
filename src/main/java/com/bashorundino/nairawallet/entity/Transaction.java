package com.bashorundino.nairawallet.entity;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.bashorundino.nairawallet.enums.TransactionStatus;
import com.bashorundino.nairawallet.enums.TransactionType;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "wallet_transactions", indexes = {
        @Index(name = "idx_tx_source_wallet", columnList = "source_wallet_id"),
        @Index(name = "idx_destination_wallet", columnList = "destination_wallet_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(name = "tx_reference", nullable = false, unique = true, length = 100)
    private String txReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType transactionType;

    @DecimalMin(value = "0.01")
    @Digits(integer = 17, fraction = 2)
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(length = 255)
    private String narration;

    @CreationTimestamp
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_wallet_id")
    private Wallet sourceWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_wallet_id")
    private Wallet destinationWallet;

    @OneToMany(mappedBy = "transaction", fetch = FetchType.LAZY)
    private List<LedgerEntry> ledgerEntries = new ArrayList<>();

    public static Transaction createDeposit(
            String txReference,
            BigDecimal amount,
            String narration,
            Wallet destinationWallet
    ){
        Transaction transaction = new Transaction();

        transaction.txReference = validateReference(txReference);
        transaction.transactionType = TransactionType.DEPOSIT;
        transaction.amount = validateAmount(amount);
        transaction.narration = narration;

        transaction.destinationWallet =
                Objects.requireNonNull(destinationWallet,
                        "destination wallet cannot be null");

        transaction.status = TransactionStatus.PENDING;

        return  transaction;


    }

    public static Transaction createWithdrawal(
            String txReference,
            BigDecimal amount,
            String narration,
            Wallet sourceWallet
    ){
        Transaction transaction = new Transaction();

        transaction.txReference = validateReference(txReference);
        transaction.transactionType = TransactionType.WITHDRAWAL;
        transaction.amount = validateAmount(amount);
        transaction.narration = narration;

        transaction.sourceWallet =
                Objects.requireNonNull(sourceWallet,
                        "source wallet cannot be null");

        transaction.status = TransactionStatus.PENDING;

        return  transaction;


    }

    public static Transaction createTransfer(
            String txReference,
            BigDecimal amount,
            String narration,
            Wallet sourceWallet,
            Wallet destinationWallet
    ){
        Transaction transaction = new Transaction();

        transaction.txReference = validateReference(txReference);
        transaction.transactionType = TransactionType.TRANSFER;
        transaction.amount = validateAmount(amount);
        transaction.narration = narration;

        transaction.sourceWallet =
                Objects.requireNonNull(sourceWallet,
                        "source wallet cannot be null");

        transaction.destinationWallet =
                Objects.requireNonNull(destinationWallet,
                        "destination wallet cannot be null");

        if (sourceWallet.equals(destinationWallet)){
            throw new IllegalArgumentException(
                    "source and destination wallets must be the same"
            );
        }

        transaction.status = TransactionStatus.PENDING;

        return  transaction;


    }


    public void markSuccessful(){
        ensureStatus(TransactionStatus.PENDING);
        this.status = TransactionStatus.SUCCESS;
    }

    public void markFailed(){
        ensureStatus(TransactionStatus.PENDING);
        this.status = TransactionStatus.FAILED;
    }

    public void markedReversed(){
        ensureStatus(TransactionStatus.SUCCESS);
        this.status = TransactionStatus.REVERSED;
    }

    public void ensureStatus(TransactionStatus expectedStatus){
        if (status != expectedStatus){
            throw  new IllegalStateException(
                    "Transaction must be "
                            + expectedStatus
                            + "but was "
                            + this.status);
        }
    }

//    Validations

    private static String validateReference(String txReference){
        Objects.requireNonNull(txReference,
                "Transaction reference cannot be null");

        if (txReference.isBlank()){
            throw new IllegalArgumentException(
                    "Transaction reference cannot be blank"
            );
        }

        if (txReference.length() > 100){
            throw new IllegalArgumentException(
                    "Transaction reference cannot exceed 100 characters"
            );
        }
        return  txReference;
    }

    private static BigDecimal validateAmount(BigDecimal amount){
        if (amount.compareTo(BigDecimal.ZERO) < 0){
            throw new IllegalArgumentException(
                    "Transaction amount must be greater than zero"
            );
        }

        if (amount.scale() > 2){
            throw new IllegalArgumentException(
                    "Transaction amount cannot have more than 2 decimal places"
            );
        }
        return amount;
    }

}
