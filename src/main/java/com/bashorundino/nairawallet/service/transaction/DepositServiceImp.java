package com.bashorundino.nairawallet.service.transaction;

import com.bashorundino.nairawallet.dto.request.DepositRequest;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Slf4j
@RequiredArgsConstructor
public class DepositServiceImp implements DepositService {

    private final WalletService walletService;
    private final LedgerEntryService ledgerEntryService;
    private final IdempotencyKeyService idempotencyKeyService;

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final TransactionReferenceGenerator referenceGenerator;


    @Override
    @Transactional
    public TransactionResponse deposit(DepositRequest request, String idempotencyKey) {

        log.info("Deposit request received. Wallet = {}, Amount = {}",
                request.walletId(), request.amount()
        );

        idempotencyKeyService.claim(idempotencyKey);
        Wallet wallet =
                walletService.findByIdForCurrentUser(request.walletId());

        BigDecimal balanceBefore = wallet.getBalance();
        wallet.credit(request.amount());
        BigDecimal balanceAfter = wallet.getBalance();

        Transaction transaction = Transaction.createDeposit(
                referenceGenerator.generate(),
                request.amount(),
                "Wallet Deposit",
                wallet
        );

        transactionRepository.save(transaction);

        ledgerEntryService.createEntry(
                wallet,
                transaction,
                request.amount(),
                balanceBefore,
                balanceAfter,
                LedgerEntryType.CREDIT,
                "Wallet Deposit"

        );

        transaction.markSuccessful();

        log.info("Deposit successful. reference = {}",
                transaction.getTxReference());

        return transactionMapper.mapToResponse(
                transaction, wallet.getId());


    }
}
