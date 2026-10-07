package com.bashorundino.nairawallet.service.unit;


import com.bashorundino.nairawallet.dto.request.DepositRequest;
import com.bashorundino.nairawallet.dto.response.TransactionResponse;
import com.bashorundino.nairawallet.entity.Transaction;
import com.bashorundino.nairawallet.entity.User;
import com.bashorundino.nairawallet.entity.Wallet;
import com.bashorundino.nairawallet.enums.LedgerEntryType;
import com.bashorundino.nairawallet.enums.TransactionStatus;
import com.bashorundino.nairawallet.exception.DuplicateTransactionException;
import com.bashorundino.nairawallet.exception.InactiveWalletException;
import com.bashorundino.nairawallet.mapper.TransactionMapper;
import com.bashorundino.nairawallet.repository.TransactionRepository;
import com.bashorundino.nairawallet.service.IdempotencyKeyService;
import com.bashorundino.nairawallet.service.LedgerEntryService;
import com.bashorundino.nairawallet.service.WalletService;
import com.bashorundino.nairawallet.service.transaction.DepositServiceImp;
import com.bashorundino.nairawallet.service.transaction.TransactionReferenceGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class DepositServiceImpTest {

    @Mock
    private WalletService walletService;

    @Mock
    private LedgerEntryService ledgerEntryService;

    @Mock
    private IdempotencyKeyService idempotencyKeyService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private TransactionReferenceGenerator referenceGenerator;

    @InjectMocks
    private DepositServiceImp depositService;

    @Test
    @DisplayName("Deposit Money Successfully")
    void shouldDepositSuccessfully() {
//        Arrange
        Long walletId = 1L;

        var request = new DepositRequest(
                walletId, new BigDecimal("500.00")
        );

        User user = createUser(
                "Ibrahim Olawale" + UUID.randomUUID(),
                "ib@gmail.com",
                "070"
        );

        var wallet = Wallet.createFor(user);
        wallet.credit(new BigDecimal("1000.00"));

        ReflectionTestUtils.setField(wallet, "id", walletId);

        var savedTransaction = Transaction.createDeposit(
                "TXN-ABC-001",
                new BigDecimal("500.00"),
                "Wallet Deposit",
                wallet
        );

        var expectedResponse = mock(TransactionResponse.class);

        when(walletService.findByIdForCurrentUser(walletId)).
                thenReturn(wallet);
        when(referenceGenerator.generate())
                .thenReturn("TXN-TEST-001");

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(transactionMapper.mapToResponse(any(Transaction.class), anyLong())).
                thenReturn(expectedResponse);


//        Act
        var response = depositService.deposit(request, "Deposit-001");

//        Assert
        assertEquals(new BigDecimal("1500.00"), wallet.getBalance());

        assertEquals(expectedResponse, response);

        verify(idempotencyKeyService).claim("Deposit-001");

        verify(walletService).findByIdForCurrentUser(walletId);

        ArgumentCaptor<Transaction> transactionCaptor =
                ArgumentCaptor.forClass(Transaction.class);

        verify(transactionRepository)
                .save(transactionCaptor.capture());

        List<Transaction> savedTransactions = transactionCaptor.getAllValues();
        Transaction finalTransaction = savedTransactions
                .get(savedTransactions.size() - 1);

        assertEquals(TransactionStatus.SUCCESS,
                finalTransaction.getStatus());

        verify(ledgerEntryService).createEntry(
                eq(wallet),
                eq(finalTransaction),
                eq(new BigDecimal("500.00")),
                eq(new BigDecimal("1000.00")),
                eq(new BigDecimal("1500.00")),
                eq(LedgerEntryType.CREDIT),
                eq("Wallet Deposit")
        );

        verify(transactionMapper).mapToResponse(
                finalTransaction, walletId);

    }

    @Test
    @DisplayName("Should reject deposit when wallet does not belong to current user")
    void shouldRejectDepositForWalletOwnedByAnotherUser() {

        when(walletService.findByIdForCurrentUser(1L))
                .thenThrow(new AccessDeniedException(
                        "You are not allowed to access this wallet"
                ));

        DepositRequest request = new DepositRequest(
                1L,
                new BigDecimal("500.00")
        );

        assertThrows(
                AccessDeniedException.class,
                () -> depositService.deposit(
                        request,
                        "deposit-auth-test"
                )
        );

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject deposit into inactive wallet")
    void shouldRejectDepositIntoInactiveWallet() {
//      Arrange

        Long walletId = 1L;

        var request = new DepositRequest(
                walletId, new BigDecimal("200.00"));

        User user = createUser(
                "Ibrahim Olawale" + UUID.randomUUID(),
                "Ib@gmail.com",
                "080"
        );

        Wallet wallet = Wallet.createFor(user);
        wallet.credit(new BigDecimal("1000.00"));

//        Make wallet inactive
        wallet.deactivate();

        when(walletService.findByIdForCurrentUser(walletId)).
                thenReturn(wallet);

//        Act & Assert
        assertThrows(InactiveWalletException.class,
                () -> depositService.deposit(request, "Deposit-002"));

//        Verify nothing was persisted
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(ledgerEntryService, never()).createEntry(
                any(Wallet.class),
                any(Transaction.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(LedgerEntryType.class),
                anyString()
        );
    }

    @Test
    @DisplayName("Should reject duplicate idempotency key")
    void shouldRejectDuplicateIdempotencyKey() {

        // Arrange
        Long walletId = 1L;
        String idempotencyKey = "IDEMP-001";

        User user = User.create(
                "Idempotency User" + UUID.randomUUID(),
                "idempotency",
                "12345678",
                "080"
        );

        Wallet wallet = Wallet.createFor(user);
        ReflectionTestUtils.setField(wallet, "id", walletId);

        doThrow(new DuplicateTransactionException("Duplicate idempotency key"))
                .when(idempotencyKeyService)
                .claim(idempotencyKey);

        DepositRequest request = new DepositRequest(
                walletId,
                new BigDecimal("500.00")
        );

        // Act & Assert
        assertThrows(
                DuplicateTransactionException.class,
                () -> depositService.deposit(
                        request,
                        idempotencyKey
                )
        );

        // Verify the wallet was never modified
        assertEquals(
                BigDecimal.ZERO,
                wallet.getBalance()
        );

        verify(idempotencyKeyService)
                .claim(idempotencyKey);

        verifyNoInteractions(transactionRepository);
        verifyNoInteractions(ledgerEntryService);
        verifyNoInteractions(transactionMapper);

        verify(walletService, never()).findById(walletId);
    }

    @Test
    @DisplayName("Should save idempotency key after successful deposit")
    void shouldSaveIdempotencyKeyAfterSuccessfulDeposit() {

        // Arrange
        Long walletId = 1L;
        String idempotencyKey = "IDEMP-SUCCESS-001";

        User user = createUser(
                "Idempotency Success User" + UUID.randomUUID(),
                "idempotency-success-",
                "080"
        );

        Wallet wallet = Wallet.createFor(user);
        ReflectionTestUtils.setField(wallet, "id", walletId);

        when(walletService.findByIdForCurrentUser(walletId))
                .thenReturn(wallet);

        DepositRequest request = new DepositRequest(
                walletId,
                new BigDecimal("500.00")
        );

        Transaction transaction = Transaction.createDeposit(
                "TXN-IDEMP-001",
                new BigDecimal("500.00"),
                "Wallet Deposit",
                wallet
        );

        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(transaction);
        when(referenceGenerator.generate())
                .thenReturn("TXN-TEST-001");

        // Act
        depositService.deposit(request, idempotencyKey);

        // Assert
        verify(idempotencyKeyService)
                .claim(idempotencyKey);

        verify(transactionRepository, atLeastOnce())
                .save(any(Transaction.class));

        assertEquals(
                new BigDecimal("500.00"),
                wallet.getBalance()
        );
    }

    private User createUser(
            String fullName,
            String emailPrefix,
            String phonePrefix
    ) {
        return User.create(
                fullName,
                emailPrefix + "@gmail.com",
                "password123",
                phonePrefix + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8)
        );
    }
}

