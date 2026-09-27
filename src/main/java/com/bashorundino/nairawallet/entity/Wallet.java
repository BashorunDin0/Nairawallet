package com.bashorundino.nairawallet.entity;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.bashorundino.nairawallet.enums.Currency;
import com.bashorundino.nairawallet.enums.WalletStatus;
import com.bashorundino.nairawallet.exception.InactiveWalletException;
import com.bashorundino.nairawallet.exception.InsufficientFundsException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;


@Entity
@Table(name = "wallets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletStatus status = WalletStatus.ACTIVE;

    @Version
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency = Currency.NGN;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    public static Wallet createFor(User user){
        Wallet wallet = new Wallet();
        wallet.user = user;
        user.assignWallet(wallet);
        return wallet;
    }

    public void credit(BigDecimal amount){
        validateAmount(amount);
        ensureActive();
        this.balance = this.balance.add(amount);
    }

    public void debit(BigDecimal amount){
        validateAmount(amount);
        ensureActive();
        if (this.balance.compareTo(amount) < 0){
            throw new  InsufficientFundsException(
                    "Insufficient funds for wallet " + this.id);
        }
        this.balance = this.balance.subtract(amount);

    }

    public void deactivate(){
        this.status = WalletStatus.INACTIVE;
    }

    public  boolean canDebit(BigDecimal amount){
        validateAmount(amount);
        ensureActive();
        return this.balance.compareTo(amount) >= 0;
    }

    private void validateAmount(BigDecimal amount){
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0){
            throw new IllegalArgumentException(
                    "Amount must be greater zero"
            );
        }
    }

    private void ensureActive(){
        if (this.status != WalletStatus.ACTIVE){
            throw new InactiveWalletException(
                    "Wallet is not active"
            );
        }
    }

}
