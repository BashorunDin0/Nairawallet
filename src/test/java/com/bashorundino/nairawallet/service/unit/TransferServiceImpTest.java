package com.bashorundino.nairawallet.service.unit;


import com.bashorundino.nairawallet.dto.request.TransferRequest;
import com.bashorundino.nairawallet.dto.response.TransactionResponse;
import com.bashorundino.nairawallet.entity.Transaction;
import com.bashorundino.nairawallet.entity.User;
import com.bashorundino.nairawallet.entity.Wallet;
import com.bashorundino.nairawallet.enums.LedgerEntryType;
import com.bashorundino.nairawallet.enums.TransactionStatus;
import com.bashorundino.nairawallet.exception.InactiveWalletException;
import com.bashorundino.nairawallet.exception.InsufficientFundsException;
import com.bashorundino.nairawallet.mapper.TransactionMapper;
import com.bashorundino.nairawallet.repository.TransactionRepository;
import com.bashorundino.nairawallet.service.IdempotencyKeyService;
import com.bashorundino.nairawallet.service.LedgerEntryService;
import com.bashorundino.nairawallet.service.WalletService;
import com.bashorundino.nairawallet.service.transaction.TransactionReferenceGenerator;
import com.bashorundino.nairawallet.service.transaction.TransferServiceImp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.anyString;

@ExtendWith(MockitoExtension.class)
public class TransferServiceImpTest {

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
    private TransferServiceImp transferService;

    @Test
    @DisplayName("Transfer Money Successfully")
    void shouldTransferSuccessfully() {
//    Arrange
        Long senderWalletId = 1L;
        Long receiverWalletId = 2L;

        var request = new TransferRequest(
                senderWalletId,
                receiverWalletId,
                new BigDecimal("300.00"),
                "Transfer money"
        );

        User sender = createUser(
                "Ibrahim Olawale" + UUID.randomUUID(),
                "ibrahim@gmail.com",
                "0801"
        );

        var receiver = createUser(
                "Idris Tunde" + UUID.randomUUID(),
                "idris@gmail.com",
                "0802"
        );

        var senderWallet = Wallet.createFor(sender);
        senderWallet.credit(new BigDecimal("1000.00"));

        var receiverWallet = Wallet.createFor(receiver);
        receiverWallet.credit(new BigDecimal("500.00"));

//  Manually creating the database auto-generated Ids.
        ReflectionTestUtils.setField(senderWallet, "id", senderWalletId);
        ReflectionTestUtils.setField(receiverWallet, "id", receiverWalletId);

        var expectedResponse = mock(TransactionResponse.class);

        when(walletService.findByIdForUpdate(1L))
                .thenReturn(senderWallet);

        when(walletService.findByIdForUpdate(2L))
                .thenReturn(receiverWallet);
        when(referenceGenerator.generate())
                .thenReturn("TXN-TEST-001");

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(transactionMapper.mapToResponse(any(Transaction.class), anyLong()))
                .thenReturn(expectedResponse);

//    Act
        var response = transferService.transfer(
                request,
                "Transfer-001"
        );

//    Assert

        assertEquals(
                new BigDecimal("700.00"),
                senderWallet.getBalance()
        );

        assertEquals(
                new BigDecimal("800.00"),
                receiverWallet.getBalance()
        );

        assertEquals(expectedResponse, response);

//    Verify idempotency
        verify(idempotencyKeyService)
                .claim("Transfer-001");

//    Verify both wallets were retrieved
        verify(walletService)
                .findByIdForUpdate(1L);

        verify(walletService)
                .findByIdForUpdate(2L);

        ArgumentCaptor<Transaction> transactionCaptor =
                ArgumentCaptor.forClass(Transaction.class);

        verify(transactionRepository)
                .save(transactionCaptor.capture());
        List<Transaction> savedTransactions =
                transactionCaptor.getAllValues();
        Transaction finalTransaction =
                savedTransactions.get(savedTransactions.size() - 1);
        assertEquals(TransactionStatus.SUCCESS, finalTransaction.getStatus());

//    Verify sender ledger entry
        verify(ledgerEntryService).createEntry(
                eq(senderWallet),
                eq(finalTransaction),
                eq(new BigDecimal("300.00")),
                eq(new BigDecimal("1000.00")),
                eq(new BigDecimal("700.00")),
                eq(LedgerEntryType.DEBIT),
                eq("Wallet Transfer")
        );

//    Verify receiver ledger entry
        verify(ledgerEntryService).createEntry(
                eq(receiverWallet),
                eq(finalTransaction),
                eq(new BigDecimal("300.00")),
                eq(new BigDecimal("500.00")),
                eq(new BigDecimal("800.00")),
                eq(LedgerEntryType.CREDIT),
                eq("Wallet Transfer")
        );

//    Verify response mapping
        verify(transactionMapper)
                .mapToResponse(finalTransaction, senderWalletId);
    }

    @Test
    @DisplayName("Should reject self transfer")
    void shouldRejectSelfTransfer() {
//    Arrange
        Long walletId = 1L;

        var request = new TransferRequest(
                walletId,
                walletId,
                new BigDecimal("300.00"),
                "Transfer"
        );

        User user = createUser(
                "Ibrahim Olawale" + UUID.randomUUID(),
                "ibrahim@gmail.com",
                "0801"
        );

        var wallet = Wallet.createFor(user);
        wallet.credit(new BigDecimal("1000.00"));

        ReflectionTestUtils.setField(
                wallet,
                "id",
                walletId
        );

//    Act & Assert
        assertThrows(
                IllegalTransactionStateException.class,
                () -> transferService.transfer(
                        request,
                        "Transfer-002"
                )
        );

//    Verify wallet balance did not change
        assertEquals(
                new BigDecimal("1000.00"),
                wallet.getBalance()
        );

//    Verify no transaction was created
        verify(transactionRepository, never())
                .save(any(Transaction.class));

//    Verify no ledger entry was created
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
    @DisplayName("Should reject transfer from inactive sender wallet")
    void shouldRejectTransferFromInactiveSenderWallet() {
//    Arrange
        Long senderWalletId = 1L;
        Long receiverWalletId = 2L;

        var request = new TransferRequest(
                senderWalletId,
                receiverWalletId,
                new BigDecimal("300.00"),
                "Transfer"
        );

        User sender = createUser(
                "Ibrahim Olawale" + UUID.randomUUID(),
                "ib@gmail.com",
                "08012345686"
        );

        User receiver = createUser(
                "Idris Tunde" + UUID.randomUUID(),
                "idris@gmail.com",
                "0801"
        );

        var senderWallet = Wallet.createFor(sender);
        senderWallet.credit(new BigDecimal("1000.00"));
        senderWallet.deactivate();

        var receiverWallet = Wallet.createFor(receiver);
        receiverWallet.credit(new BigDecimal("500.00"));

        ReflectionTestUtils.setField(
                senderWallet,
                "id",
                senderWalletId
        );

        ReflectionTestUtils.setField(
                receiverWallet,
                "id",
                receiverWalletId
        );

        when(walletService.findByIdForUpdate(senderWalletId))
                .thenReturn(senderWallet);

        when(walletService.findByIdForUpdate(receiverWalletId))
                .thenReturn(receiverWallet);

//    Act & Assert
        assertThrows(
                InactiveWalletException.class,
                () -> transferService.transfer(
                        request,
                        "Transfer-004"
                )
        );

//    Verify balances did not change
        assertEquals(
                new BigDecimal("1000.00"),
                senderWallet.getBalance()
        );

        assertEquals(
                new BigDecimal("500.00"),
                receiverWallet.getBalance()
        );

//    Verify no transaction was created
        verify(transactionRepository, never())
                .save(any(Transaction.class));

//    Verify no ledger entry was created
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
    @DisplayName("Should reject transfer to inactive receiver wallet")
    void shouldRejectTransferToInactiveReceiverWallet() {

        // Arrange
        Long senderWalletId = 1L;
        Long receiverWalletId = 2L;

        var request = new TransferRequest(
                senderWalletId,
                receiverWalletId,
                new BigDecimal("300.00"),
                "Transfer"
        );

        User sender = createUser(
                "Active Sender" + UUID.randomUUID(),
                "active-sender-2@gmail.com",
                "0801"
        );

        User receiver = createUser(
                "Inactive Receiver" + UUID.randomUUID(),
                "inactive-receiver-2@gmail.com",
                "0801"
        );

        var senderWallet = Wallet.createFor(sender);
        senderWallet.credit(new BigDecimal("1000.00"));

        var receiverWallet = Wallet.createFor(receiver);
        receiverWallet.credit(new BigDecimal("500.00"));
        receiverWallet.deactivate();

        ReflectionTestUtils.setField(senderWallet, "id", senderWalletId);
        ReflectionTestUtils.setField(receiverWallet, "id", receiverWalletId);

        when(walletService.findByIdForUpdate(senderWalletId))
                .thenReturn(senderWallet);

        when(walletService.findByIdForUpdate(receiverWalletId))
                .thenReturn(receiverWallet);

        // Act & Assert
        assertThrows(
                InactiveWalletException.class,
                () -> transferService.transfer(
                        request,
                        "Transfer-005"
                )
        );

        // Verify no transaction was created
        verify(transactionRepository, never())
                .save(any(Transaction.class));

        // Verify no ledger entry was created
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
    @DisplayName("Should reject transfer when sender wallet does not belong to current user")
    void shouldRejectTransferFromWalletOwnedByAnotherUser() {

        Wallet senderWallet = mock(Wallet.class);
        Wallet receiverWallet = mock(Wallet.class);

        // The service uses wallet IDs to determine
        // which locked wallet is the sender.
        when(senderWallet.getId()).thenReturn(1L);

        when(walletService.findByIdForUpdate(1L))
                .thenReturn(senderWallet);

        when(walletService.findByIdForUpdate(2L))
                .thenReturn(receiverWallet);

        doThrow(new AccessDeniedException(
                "You are not allowed to access this wallet"
        ))
                .when(walletService)
                .verifyOwnership(senderWallet);

        TransferRequest request = new TransferRequest(
                1L,
                2L,
                new BigDecimal("100.00"),
                "Unauthorized transfer"
        );

        assertThrows(
                AccessDeniedException.class,
                () -> transferService.transfer(
                        request,
                        "transfer-auth-test"
                )
        );

        verify(walletService).findByIdForUpdate(1L);
        verify(walletService).findByIdForUpdate(2L);
        verify(walletService).verifyOwnership(senderWallet);

        verify(senderWallet, never()).debit(any());
        verify(receiverWallet, never()).credit(any());

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject transfer when sender has insufficient funds")
    void shouldRejectTransferWhenSenderHasInsufficientFunds() {
//    Arrange
        Long senderWalletId = 1L;
        Long receiverWalletId = 2L;

        var request = new TransferRequest(
                senderWalletId,
                receiverWalletId,
                new BigDecimal("1500.00"),
                "Transfer"
        );

        User senderUser = createUser(
                "Ibrahim Sender" + UUID.randomUUID(),
                "sender-insufficient@gmail.com" + UUID.randomUUID(),
                "0801"
        );

        User receiverUser = createUser(
                "Receiver User" + UUID.randomUUID(),
                "receiver-insufficient@gmail.com",
                "0801"
        );

        var senderWallet = Wallet.createFor(senderUser);
        senderWallet.credit(new BigDecimal("1000.00"));

        var receiverWallet = Wallet.createFor(receiverUser);
        receiverWallet.credit(new BigDecimal("500.00"));

        ReflectionTestUtils.setField(
                senderWallet,
                "id",
                senderWalletId
        );

        ReflectionTestUtils.setField(
                receiverWallet,
                "id",
                receiverWalletId
        );

        when(walletService.findByIdForUpdate(senderWalletId))
                .thenReturn(senderWallet);

        when(walletService.findByIdForUpdate(receiverWalletId))
                .thenReturn(receiverWallet);

//    Act & Assert
        assertThrows(
                InsufficientFundsException.class,
                () -> transferService.transfer(
                        request,
                        "Transfer-003"
                )
        );

//    Verify sender balance did not change
        assertEquals(
                new BigDecimal("1000.00"),
                senderWallet.getBalance()
        );

//    Verify receiver balance did not change
        assertEquals(
                new BigDecimal("500.00"),
                receiverWallet.getBalance()
        );

//    Verify no transaction was created
        verify(transactionRepository, never())
                .save(any(Transaction.class));

//    Verify no ledger entry was created
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
