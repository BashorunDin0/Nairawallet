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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;

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
    private final CurrentUserService currentUserService;

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    @Transactional
    public TransactionResponse deposit(DepositRequest request, String idempotencyKey) {
        log.info("Deposit request received. Wallet = {}, Amount = {}",
                request.walletId(), request.amount()
        );
        idempotencyKeyService.claim(idempotencyKey);
        Wallet wallet = walletService.findById(request.walletId());

        BigDecimal balanceBefore = wallet.getBalance();
        wallet.credit(request.amount());
        BigDecimal balanceAfter = wallet.getBalance();

        Transaction transaction = createTransaction(
                null,
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
        log.info("Deposit successful. reference = {}", transaction.getTxReference());

        return transactionMapper.mapToResponse(transaction, wallet.getId());


    }

    @Transactional
    public TransactionResponse withdrawal(WithdrawRequest request, String idempotencyKey) {
        idempotencyKeyService.claim(idempotencyKey);
        Wallet wallet = walletService.findById(request.walletId());

        BigDecimal balanceBefore = wallet.getBalance();
        wallet.debit(request.amount());
        BigDecimal balanceAfter = wallet.getBalance();

        Transaction transaction = createTransaction(
                wallet,
                null,
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
        log.info("Withdrawal successful. reference = {}", transaction.getTxReference());

        return transactionMapper.mapToResponse(transaction, wallet.getId());
    }

    @Transactional
    public TransactionResponse transfer(TransferRequest request, String idempotencyKey) {
        idempotencyKeyService.claim(idempotencyKey);

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
                receiverWallet,
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
        log.info("Transfer successful. reference: {}", transaction.getTxReference());


        return transactionMapper.mapToResponse(transaction, senderWallet.getId());
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(Long walletId, int page, int size) {

        Wallet wallet = walletService.findById(walletId);

        String currentUserEmail = currentUserService.getCurrentUserEmail();

        if (!wallet.getUser().getEmail().equals(currentUserEmail)){
            throw new AccessDeniedException(
                    "You are not allowed to access this wallet"
            );
        }

        Pageable pageable = PageRequest.of(page, size);

        Page<Transaction> transactions = transactionRepository.
                findBySourceWalletIdOrDestinationWalletIdOrderByCreatedAtDescIdDesc(
                        walletId,
                        walletId,
                        pageable);

        return transactions.map(transaction ->
                transactionMapper.mapToResponse(transaction, walletId));
    }

    private Transaction createTransaction(Wallet sourceWallet,
                                          Wallet destinationWallet,
                                          TransactionType type,
                                          BigDecimal amount,
                                          String narration) {
        Transaction transaction = Transaction.builder()
                .sourceWallet(sourceWallet)
                .destinationWallet(destinationWallet)
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
