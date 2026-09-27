package com.bashorundino.nairawallet.service;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.bashorundino.nairawallet.dto.request.DepositRequest;
import com.bashorundino.nairawallet.dto.request.WithdrawRequest;
import com.bashorundino.nairawallet.dto.response.TransactionResponse;
import com.bashorundino.nairawallet.dto.request.TransferRequest;
import com.bashorundino.nairawallet.entity.Transaction;
import com.bashorundino.nairawallet.entity.Wallet;
import com.bashorundino.nairawallet.enums.LedgerEntryType;
import com.bashorundino.nairawallet.enums.TransactionStatus;
import com.bashorundino.nairawallet.enums.TransactionType;
import com.bashorundino.nairawallet.mapper.TransactionMapper;
import com.bashorundino.nairawallet.repository.TransactionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.IllegalTransactionStateException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionServiceImp implements TransactionService {

    private final WalletService walletService;
    private final LedgerEntryService ledgerEntryService;
    private final IdempotencyKeyService idempotencyKeyService;

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    @Transactional
    public TransactionResponse deposit(DepositRequest request, String idempotencyKey) {
        log.info("Deposit request received. Wallet = {}, Amount = {}",
                request.walletId(), request.amount()
        );
        idempotencyKeyService.validate(idempotencyKey);
        Wallet wallet = walletService.findById(request.walletId());

        BigDecimal balanceBefore = wallet.getBalance();
        wallet.credit(request.amount());
        BigDecimal balanceAfter = wallet.getBalance();

        Transaction transaction = createTransaction(
                wallet,
                TransactionType.DEPOSIT,
                request.amount(),
                "Wallet Deposit"

        );

        ledgerEntryService.createEntry(
                wallet,
                transaction,
                request.amount(),
                balanceBefore,
                balanceAfter,
                LedgerEntryType.CREDIT,
                "Wallet Deposit"

        );
        markSuccessful(transaction);
        idempotencyKeyService.save(idempotencyKey);
        log.info("Deposit successful. reference = {}", transaction.getTxReference());

        return transactionMapper.mapToResponse(transaction);


    }
    @Transactional
    public TransactionResponse withdrawal(WithdrawRequest request, String idempotencyKey) {
        idempotencyKeyService.validate(idempotencyKey);
        Wallet wallet = walletService.findById(request.walletId());

        BigDecimal balanceBefore = wallet.getBalance();
        wallet.debit(request.amount());
        BigDecimal balanceAfter = wallet.getBalance();

        Transaction transaction = createTransaction(
                wallet,
                TransactionType.WITHDRAWAL,
                request.amount(),
                "Wallet Withdrawal"
        );

        ledgerEntryService.createEntry(
                wallet,
                transaction,
                request.amount(),
                balanceBefore,
                balanceAfter,
                LedgerEntryType.DEBIT,
                "Wallet Withdrawal"
        );
        markSuccessful(transaction);
        idempotencyKeyService.save(idempotencyKey);
        log.info("Withdrawal successful. reference = {}", transaction.getTxReference());

        return transactionMapper.mapToResponse(transaction);
    }

    @Transactional
    public TransactionResponse transfer(TransferRequest request, String idempotencyKey) {
        idempotencyKeyService.validate(idempotencyKey);

        Wallet senderWallet = walletService.findById(request.senderWalletId());
        Wallet receiverWallet = walletService.findById(request.receiverWalletId());

        validateSelfTransfer(senderWallet, receiverWallet);

        BigDecimal senderBalanceBefore = senderWallet.getBalance();
        BigDecimal receiverBalanceBefore = receiverWallet.getBalance();

        senderWallet.debit(request.amount());
        receiverWallet.credit(request.amount());

        BigDecimal senderBalanceAfter = senderWallet.getBalance();
        BigDecimal receiverBalanceAfter = receiverWallet.getBalance();


        Transaction transaction = createTransaction(
                senderWallet,
                TransactionType.TRANSFER,
                request.amount(),
                "Wallet-Transfer"
        );

        ledgerEntryService.createEntry(
                senderWallet,
                transaction,
                request.amount(),
                senderBalanceBefore,
                senderBalanceAfter,
                LedgerEntryType.DEBIT,
                "Wallet Transfer"
        );
        ledgerEntryService.createEntry(
                receiverWallet,
                transaction,
                request.amount(),
                receiverBalanceBefore,
                receiverBalanceAfter,
                LedgerEntryType.CREDIT,
                "Wallet Transfer"
        );
        markSuccessful(transaction);
        idempotencyKeyService.save(idempotencyKey);
        log.info("Transfer successful. reference: {}", transaction.getTxReference());


        return transactionMapper.mapToResponse(transaction);
    }
@Transactional
    public Page<TransactionResponse> getTransactions(Long walletId, int page, int size) {
        walletService.findById(walletId);

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Transaction> transactions = transactionRepository.findByWalletIdOrderByCreatedAtDesc(walletId, pageable);

        return transactions.map(transactionMapper::mapToResponse);
    }

    private Transaction createTransaction(Wallet wallet, TransactionType type, BigDecimal amount, String narration) {
        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .txReference(referenceGenerator())
                .transactionType(type)
                .status(TransactionStatus.PENDING)
                .amount(amount)
                .narration(narration)
                .createdAt(Instant.now())
                .build();
        return transactionRepository.save(transaction);
    }

    private String referenceGenerator() {
        return "TXN-" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase();

    }

    private void markSuccessful(Transaction transaction) {
        transaction.markSuccessful();
        transactionRepository.save(transaction);

    }

    private void validateSelfTransfer(Wallet senderWallet, Wallet receiverWallet){

        if (senderWallet.getId().equals(receiverWallet.getId())){
            throw new IllegalTransactionStateException("Self-transfer is prohibited");
        }

    }

}
