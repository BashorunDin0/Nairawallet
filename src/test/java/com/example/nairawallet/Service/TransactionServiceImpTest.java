package com.example.nairawallet.Service;

import com.example.nairawallet.Dto.Request.DepositRequest;
import com.example.nairawallet.Dto.Response.TransactionResponse;
import com.example.nairawallet.Entity.Transaction;
import com.example.nairawallet.Entity.Wallet;
import com.example.nairawallet.Enum.*;
import com.example.nairawallet.Mapper.TransactionMapper;
import com.example.nairawallet.Repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Transaction Service Implementation Test")
public class TransactionServiceImpTest {
    @Mock
    private WalletService walletService;

    @Mock
    private LedgerEntryService ledgerEntryService;

    @Mock
    private IdempotencyKeyServiceImp idempotencyKeyService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @InjectMocks
    private TransactionServiceImp transactionService;

    @Test
    @DisplayName("Deposit Money Successfully")
    void shouldDepositSuccessfully() {
//        Arrange
        Long walletId = 1L;

        var request = new DepositRequest(
                walletId, new BigDecimal("500.00")
        );

        var wallet = Wallet.createFor(null);
                wallet.credit(new BigDecimal("1000.00"));

        var savedTransaction = Transaction.builder()
                .wallet(wallet)
                .txReference("TXN-ABC-001")
                .amount(new BigDecimal("500.00"))
                .transactionType(TransactionType.DEPOSIT)
                .status(TransactionStatus.PENDING)
                .narration("Wallet Deposit")
                .build();

        var expectedResponse = mock(TransactionResponse.class);

        when(walletService.findById(walletId))
                .thenReturn(wallet);

        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(savedTransaction);

        when(transactionMapper.mapToResponse(any(Transaction.class))).
                thenReturn(expectedResponse);

//        Act
        var response = transactionService.deposit(request, "Deposit-001");

//        Assert
        assertEquals(new BigDecimal("1500.00"), wallet.getBalance());

        assertEquals(expectedResponse, response);

        verify(idempotencyKeyService).validate("Deposit-001");

        verify(walletService).findById(walletId);

        verify(transactionRepository, times(2)).save(any(Transaction.class));

        verify(ledgerEntryService).createEntry(
                eq(wallet),
                eq(savedTransaction),
                eq(new BigDecimal("500.00")),
                eq(new BigDecimal("1000.00")),
                eq(new BigDecimal("1500.00")),
                eq(LedgerEntryType.CREDIT),
                eq("Wallet Deposit")
        );

        verify(idempotencyKeyService).save("Deposit-001");

        verify(transactionMapper).mapToResponse(savedTransaction);

    }
}
