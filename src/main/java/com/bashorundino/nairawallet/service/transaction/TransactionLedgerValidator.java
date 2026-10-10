package com.bashorundino.nairawallet.service.transaction;


import com.bashorundino.nairawallet.entity.LedgerEntry;
import com.bashorundino.nairawallet.entity.Transaction;
import com.bashorundino.nairawallet.entity.Wallet;
import com.bashorundino.nairawallet.enums.LedgerEntryType;
import com.bashorundino.nairawallet.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TransactionLedgerValidator {

    private final LedgerEntryRepository ledgerEntryRepository;

    public void validate(Transaction transaction){

        Objects.requireNonNull(transaction, "transaction cannot be null");

        if (transaction.getId() == null){
            throw new IllegalStateException(
                    "transaction must be persisted before ledger validation"
            );
        }

        List<LedgerEntry> entries =
                ledgerEntryRepository.findByTransactionId(transaction.getId());

        switch (transaction.getTransactionType()){
            case DEPOSIT -> validateDeposit(transaction, entries);
            case WITHDRAWAL -> validateWithdrawal(transaction, entries);
            case TRANSFER -> validateTransfer(transaction, entries);
        }
    }

    private void validateDeposit(
            Transaction transaction,
            List<LedgerEntry> entries
    ){
        if (entries.size() != 1){
            throw new IllegalArgumentException(
                    "A deposit must have exactly one ledger entry"
            );
        }
        LedgerEntry entry = entries.get(0);

        validateEntry(
                entry,
                transaction,
                transaction.getDestinationWallet(),
                LedgerEntryType.CREDIT
        );
    }

    private void validateWithdrawal(
            Transaction transaction,
            List<LedgerEntry> entries
    ){
        if (entries.size() != 1){
            throw new IllegalArgumentException(
                    "A withdrawal must have exactly one ledger entry"
            );
        }

        LedgerEntry entry = entries.get(0);

        validateEntry(
                entry,
                transaction,
                transaction.getSourceWallet(),
                LedgerEntryType.DEBIT
        );
    }

    private void validateTransfer(
            Transaction transaction,
            List<LedgerEntry> entries
    ){
        if (entries.size() != 2){
            throw new IllegalArgumentException(
                    "A transfer must have exactly two ledger entries"
            );
        }
        LedgerEntry debit = entries.stream()
                .filter(e -> e.getEntryType() == LedgerEntryType.DEBIT)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transfer is missing its debit entry"
                ));
        LedgerEntry credit = entries.stream()
                .filter(e -> e.getEntryType() == LedgerEntryType.CREDIT)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transfer is missing its credit entry"
                ));

        validateEntry(
                debit,
                transaction,
                transaction.getSourceWallet(),
                LedgerEntryType.DEBIT
        );

        validateEntry(
                credit,
                transaction,
                transaction.getDestinationWallet(),
                LedgerEntryType.CREDIT
        );
    }
    private void validateEntry(
            LedgerEntry entry,
            Transaction transaction,
            Wallet expectedWallet,
            LedgerEntryType expectedType
    ){
        if(!entry.getTransaction().getId().equals(transaction.getId())){
            throw new IllegalStateException(
                    "ledger entry belongs to a different transaction"
            );
        }
        if (!entry.getWallet().getId().equals(expectedWallet.getId())){
            throw new IllegalStateException(
                    "ledger entry has a wrong wallet"
            );
        }
        if (entry.getEntryType() != expectedType){
            throw new IllegalStateException(
                    "ledger entry has a wrong type "
            );
        }
        if (entry.getAmount().compareTo(transaction.getAmount()) != 0){
            throw new IllegalStateException(
                    "ledger entry amount does not match transaction amount"
            );
        }
    }
}
