package com.bashorundino.nairawallet.service.transaction;


import com.bashorundino.nairawallet.dto.request.TransferRequest;
import com.bashorundino.nairawallet.dto.response.TransactionResponse;
import com.bashorundino.nairawallet.entity.IdempotencyKey;
import com.bashorundino.nairawallet.entity.Transaction;
import com.bashorundino.nairawallet.entity.User;
import com.bashorundino.nairawallet.entity.Wallet;
import com.bashorundino.nairawallet.enums.LedgerEntryType;
import com.bashorundino.nairawallet.enums.TransactionType;
import com.bashorundino.nairawallet.exception.IdempotencyClaimConflictException;
import com.bashorundino.nairawallet.mapper.TransactionMapper;
import com.bashorundino.nairawallet.repository.TransactionRepository;
import com.bashorundino.nairawallet.repository.UserRepository;
import com.bashorundino.nairawallet.service.IdempotencyKeyService;
import com.bashorundino.nairawallet.service.LedgerEntryService;
import com.bashorundino.nairawallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferTransactionProcessor {

        private final WalletService walletService;
        private final LedgerEntryService ledgerEntryService;
        private final IdempotencyKeyService idempotencyKeyService;
        private final TransactionRepository transactionRepository;
        private final TransactionMapper transactionMapper;
        private final TransactionReferenceGenerator referenceGenerator;
        private final TransactionLedgerValidator transactionLedgerValidator;
        private final UserRepository userRepository;

        @Transactional
        public TransactionResponse process(
                TransferRequest request,
                String idempotencyKey,
                Long userId,
                String requestHash) {

            User user = userRepository.getReferenceById(userId);


            IdempotencyKey claimedKey;

            try {
                claimedKey = idempotencyKeyService.create(
                        idempotencyKey,
                        user,
                        TransactionType.TRANSFER,
                        requestHash
                );
            } catch (DataIntegrityViolationException exception) {
                throw new IdempotencyClaimConflictException(
                        "Idempotency key has already been claimed",
                        exception
                );
            }

            Long senderId = request.senderWalletId();
            Long receiverId = request.receiverWalletId();

            if (senderId.equals(receiverId)) {
                throw new IllegalTransactionStateException(
                        "Sender and receiver wallets must be different"
                );
            }

            // Lock both wallets in ascending ID order to reduce deadlock risk.
            Long firstWalletId = Math.min(senderId, receiverId);
            Long secondWalletId = Math.max(senderId, receiverId);

            Wallet firstWallet = walletService.findByIdForUpdate(firstWalletId);
            Wallet secondWallet = walletService.findByIdForUpdate(secondWalletId);

            Wallet senderWallet = senderId.equals(firstWallet.getId())
                    ? firstWallet : secondWallet;

            Wallet receiverWallet = receiverId.equals(firstWallet.getId())
                    ? firstWallet : secondWallet;

            walletService.verifyOwnership(senderWallet);

            BigDecimal senderBalanceBefore = senderWallet.getBalance();
            BigDecimal receiverBalanceBefore = receiverWallet.getBalance();

            senderWallet.debit(request.amount());
            receiverWallet.credit(request.amount());

            BigDecimal senderBalanceAfter = senderWallet.getBalance();
            BigDecimal receiverBalanceAfter = receiverWallet.getBalance();

            Transaction transaction = Transaction.createTransfer(
                    referenceGenerator.generate(),
                    request.amount(),
                    "Wallet Transfer",
                    senderWallet,
                    receiverWallet
            );

            transactionRepository.save(transaction);

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

            transactionLedgerValidator.validate(transaction);
            transaction.markSuccessful();

            idempotencyKeyService.markCompleted(claimedKey, transaction);

            log.info(
                    "Transfer successful. reference = {}",
                    transaction.getTxReference()
            );

            return transactionMapper.mapToResponse(
                    transaction,
                    senderWallet.getId()
            );
        }
    }