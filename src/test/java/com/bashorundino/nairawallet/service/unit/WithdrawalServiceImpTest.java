package com.bashorundino.nairawallet.service.unit;

import com.bashorundino.nairawallet.dto.request.WithdrawRequest;
import com.bashorundino.nairawallet.dto.response.TransactionResponse;
import com.bashorundino.nairawallet.entity.Transaction;
import com.bashorundino.nairawallet.entity.User;
import com.bashorundino.nairawallet.entity.Wallet;
import com.bashorundino.nairawallet.enums.LedgerEntryType;
import com.bashorundino.nairawallet.enums.TransactionStatus;
import com.bashorundino.nairawallet.exception.InsufficientFundsException;
import com.bashorundino.nairawallet.mapper.TransactionMapper;
import com.bashorundino.nairawallet.repository.TransactionRepository;
import com.bashorundino.nairawallet.service.IdempotencyKeyService;
import com.bashorundino.nairawallet.service.LedgerEntryService;
import com.bashorundino.nairawallet.service.WalletService;
import com.bashorundino.nairawallet.service.transaction.TransactionLedgerValidator;
import com.bashorundino.nairawallet.service.transaction.TransactionReferenceGenerator;
import com.bashorundino.nairawallet.service.transaction.WithdrawalServiceImp;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.anyString;

@ExtendWith(MockitoExtension.class)
public class WithdrawalServiceImpTest {

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

    @Mock
    private TransactionLedgerValidator transactionLedgerValidator;

    @InjectMocks
    private WithdrawalServiceImp withdrawalService;

    @Test
    @DisplayName("Withdraw money successfully")
    public void shouldWithdrawSuccessfully() {
//        Arrange
        Long walletId = 1L;
        var request = new WithdrawRequest(walletId,
                new BigDecimal("300.00"));

        User user = createUser(
                "Idris Babatunde" + UUID.randomUUID(),
                "idris@gmail.com",
                "080"
        );

        var wallet = Wallet.createFor(user);
        wallet.credit(new BigDecimal("1000.00"));

        ReflectionTestUtils.setField(wallet, "id", walletId);

        var expectedResponse = mock(TransactionResponse.class);

        when(walletService.findByIdForCurrentUser(walletId)).
                thenReturn(wallet);
        when(referenceGenerator.generate())
                .thenReturn("TXN-TEST-001");

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(transactionMapper.mapToResponse(any(Transaction.class), anyLong()))
                .thenReturn(expectedResponse);

//        Act
        var response = withdrawalService.withdrawal(
                request, "Withdrawal-001");

//        Assert

        assertEquals(new BigDecimal("700.00"), wallet.getBalance());

        assertEquals(expectedResponse, response);

        verify(idempotencyKeyService).claim("Withdrawal-001");

        verify(walletService).findByIdForCurrentUser(walletId);

        ArgumentCaptor<Transaction> transactionCaptor =
                ArgumentCaptor.forClass(Transaction.class);

        verify(transactionRepository)
                .save(transactionCaptor.capture());

        List<Transaction> savedTransactions =
                transactionCaptor.getAllValues();
        Transaction finalTransaction =
                savedTransactions.get(savedTransactions.size() - 1);
        assertEquals(TransactionStatus.SUCCESS, finalTransaction.getStatus());

        verify(ledgerEntryService).createEntry(
                eq(wallet),
                eq(finalTransaction),
                eq(new BigDecimal("300.00")),
                eq(new BigDecimal("1000.00")),
                eq(new BigDecimal("700.00")),
                eq(LedgerEntryType.DEBIT),
                eq("Wallet Withdrawal")
        );
        verify(transactionMapper).mapToResponse(
                finalTransaction, walletId);
    }


    @Test
    @DisplayName("Should reject withdrawal when wallet does not belong to current user")
    void shouldRejectWithdrawalForWalletOwnedByAnotherUser() {

        when(walletService.findByIdForCurrentUser(1L))
                .thenThrow(new AccessDeniedException(
                        "You are not allowed to access this wallet"
                ));

        WithdrawRequest request = new WithdrawRequest(
                1L,
                new BigDecimal("500.00")
        );

        assertThrows(
                AccessDeniedException.class,
                () -> withdrawalService.withdrawal(
                        request,
                        "withdraw-auth-test"
                )
        );

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject withdrawal when funds are insufficient")
    void shouldRejectWithdrawalWhenFundsAreInsufficient() {
//    Arrange
        Long walletId = 1L;

        var request = new WithdrawRequest(
                walletId,
                new BigDecimal("1500.00")
        );

        User user = createUser(
                "Ibrahim Olawale" + UUID.randomUUID(),
                "ibrahim@gmail.com",
                "080"
        );

        var wallet = Wallet.createFor(user);
        wallet.credit(new BigDecimal("1000.00"));

        when(walletService.findByIdForCurrentUser(walletId))
                .thenReturn(wallet);

//    Act & Assert
        assertThrows(
                InsufficientFundsException.class,
                () -> withdrawalService.withdrawal(
                        request,
                        "Withdrawal-002"
                )
        );

//    Verify nothing was persisted
        verify(transactionRepository, never())
                .save(any(Transaction.class));

        verify(ledgerEntryService, never()).createEntry(
                any(Wallet.class),
                any(Transaction.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(LedgerEntryType.class),
                anyString()
        );

//    Verify wallet balance was not changed
        assertEquals(
                new BigDecimal("1000.00"),
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
