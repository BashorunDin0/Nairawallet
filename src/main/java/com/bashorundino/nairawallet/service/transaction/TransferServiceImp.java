package com.bashorundino.nairawallet.service.transaction;

import com.bashorundino.nairawallet.dto.request.TransferRequest;
import com.bashorundino.nairawallet.dto.response.TransactionResponse;
import com.bashorundino.nairawallet.entity.Transaction;
import com.bashorundino.nairawallet.entity.Wallet;
import com.bashorundino.nairawallet.enums.LedgerEntryType;
import com.bashorundino.nairawallet.mapper.TransactionMapper;
import com.bashorundino.nairawallet.repository.TransactionRepository;
import com.bashorundino.nairawallet.service.IdempotencyKeyService;
import com.bashorundino.nairawallet.service.LedgerEntryService;
import com.bashorundino.nairawallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransferServiceImp implements TransferService {

    private final WalletService walletService;
    private final LedgerEntryService ledgerEntryService;
    private final IdempotencyKeyService idempotencyKeyService;

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final TransactionReferenceGenerator referenceGenerator;
    private final TransactionLedgerValidator transactionLedgerValidator;

    @Override
    @Transactional
    public TransactionResponse transfer(TransferRequest request, String idempotencyKey) {

        idempotencyKeyService.claim(idempotencyKey);

        Long senderId = request.senderWalletId();
        Long receiverId = request.receiverWalletId();

//      prevent self transfer before acquiring database lock
        if (senderId.equals(receiverId)) {
            throw new IllegalTransactionStateException(
                    "sender and receiver wallets must be different"
            );
        }


        /*
         * Always acquire wallet locks in ascending ID order
         *
         * Example:
         *   Transfer 1 -> 2  => lock 1, then 2
         *   Transfer 2 -> 1  => lock 1, then 2
         *
         * This prevents circular waiting and reduces deadlock risk.
         */

        Long firstWalletId = Math.min(senderId, receiverId);
        Long secondWalletId = Math.max(senderId, receiverId);

        Wallet firstWallet =
                walletService.findByIdForUpdate(firstWalletId);
        Wallet secondWallet =
                walletService.findByIdForUpdate(secondWalletId);

        /*
         * Restore the business rows after locking.
         *
         * The first wallet is not necessarily the sender.
         */

        Wallet senderWallet =
                senderId.equals(firstWallet.getId())
                        ? firstWallet : secondWallet;

        Wallet receiverWallet =
                receiverId.equals(firstWallet.getId())
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

//        create debit ledger entry
        ledgerEntryService.createEntry(
                senderWallet,
                transaction,
                request.amount(),
                senderBalanceBefore,
                senderBalanceAfter,
                LedgerEntryType.DEBIT,
                "Wallet Transfer"
        );

//        create credit ledger entry
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
        log.info("Transfer successful. reference: {}", transaction.getTxReference());


        return transactionMapper.mapToResponse(transaction, senderWallet.getId());
    }
}
