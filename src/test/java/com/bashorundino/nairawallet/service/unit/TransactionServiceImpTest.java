package com.bashorundino.nairawallet.service.unit;

import com.bashorundino.nairawallet.dto.request.DepositRequest;
import com.bashorundino.nairawallet.dto.request.TransferRequest;
import com.bashorundino.nairawallet.dto.request.WithdrawRequest;
import com.bashorundino.nairawallet.dto.response.TransactionResponse;
import com.bashorundino.nairawallet.entity.Transaction;
import com.bashorundino.nairawallet.entity.User;
import com.bashorundino.nairawallet.entity.Wallet;
import com.bashorundino.nairawallet.enums.LedgerEntryType;
import com.bashorundino.nairawallet.enums.TransactionStatus;
import com.bashorundino.nairawallet.exception.DuplicateTransactionException;
import com.bashorundino.nairawallet.exception.InactiveWalletException;
import com.bashorundino.nairawallet.exception.InsufficientFundsException;
import com.bashorundino.nairawallet.exception.WalletNotFoundException;
import com.bashorundino.nairawallet.mapper.TransactionMapper;
import com.bashorundino.nairawallet.repository.TransactionRepository;
import com.bashorundino.nairawallet.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
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
    private IdempotencyKeyService idempotencyKeyService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private CurrentUserService currentUserService;

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

        when(walletService.findById(walletId)).
                thenReturn(wallet);

//        when(transactionRepository.save(any(Transaction.class))).
//                thenReturn(savedTransaction);
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(transactionMapper.mapToResponse(any(Transaction.class),anyLong())).
                thenReturn(expectedResponse);

//        Act
        var response = transactionService.deposit(request, "Deposit-001");

//        Assert
        assertEquals(new BigDecimal("1500.00"), wallet.getBalance());

        assertEquals(expectedResponse, response);

        verify(idempotencyKeyService).claim("Deposit-001");

        verify(walletService).findById(walletId);

        ArgumentCaptor<Transaction> transactionCaptor =
                ArgumentCaptor.forClass(Transaction.class);

        verify(transactionRepository, times(2))
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
    @DisplayName("Should reject deposit into inactive wallet")
    void shouldRejectDepositIntoInactiveWallet() {
//      Arrange

        Long walletId = 1L;

        var request = new DepositRequest(
                walletId, new BigDecimal("200.00"));

        User user = createUser(
                "Ibrahim Olawale" + UUID.randomUUID(),
                "Ib@gmail.com",
                "0801"
                );

        Wallet wallet = Wallet.createFor(user);
        wallet.credit(new BigDecimal("1000.00"));

//        Make wallet inactive
        wallet.deactivate();

        when(walletService.findById(walletId)).
                thenReturn(wallet);

//        Act & Assert
        assertThrows(InactiveWalletException.class,
                () -> transactionService.deposit(request, "Deposit-002"));

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
    @DisplayName("Withdraw money successfully")
    public void shouldWithdrawSuccessfully() {
//        Arrange
        Long walletId = 1L;
        var request = new WithdrawRequest(walletId,
                new BigDecimal("300.00"));

        User user = createUser(
                "Idris Babatunde" + UUID.randomUUID(),
                "idris@gmail.com",
                "070"
                );

        var wallet = Wallet.createFor(user);
        wallet.credit(new BigDecimal("1000.00"));

        ReflectionTestUtils.setField(wallet, "id", walletId);

        var expectedResponse = mock(TransactionResponse.class);

        when(walletService.findById(walletId)).
                thenReturn(wallet);

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(transactionMapper.mapToResponse(any(Transaction.class), anyLong()))
                .thenReturn(expectedResponse);

//        Act
        var response = transactionService.withdrawal(
                request, "Withdrawal-001");

//        Assert

        assertEquals(new BigDecimal("700.00"), wallet.getBalance());

        assertEquals(expectedResponse, response);

        verify(idempotencyKeyService).claim("Withdrawal-001");

        verify(walletService).findById(walletId);

        ArgumentCaptor<Transaction> transactionCaptor =
                ArgumentCaptor.forClass(Transaction.class);

        verify(transactionRepository, times(2))
                .save(transactionCaptor.capture());

        List<Transaction> savedTransactions =
                transactionCaptor.getAllValues();
        Transaction finalTransaction =
                savedTransactions.get(savedTransactions.size() - 1);
        assertEquals( TransactionStatus.SUCCESS, finalTransaction.getStatus());

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
                "070"
                );

        var wallet = Wallet.createFor(user);
        wallet.credit(new BigDecimal("1000.00"));

        when(walletService.findById(walletId))
                .thenReturn(wallet);

//    Act & Assert
        assertThrows(
                InsufficientFundsException.class,
                () -> transactionService.withdrawal(
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
                "0801"
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

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(transactionMapper.mapToResponse(any(Transaction.class), anyLong()))
                .thenReturn(expectedResponse);

//    Act
        var response = transactionService.transfer(
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

        verify(transactionRepository, times(2))
                .save(transactionCaptor.capture());
        List<Transaction> savedTransactions =
                transactionCaptor.getAllValues();
        Transaction finalTransaction =
                savedTransactions.get(savedTransactions.size() - 1); assertEquals( TransactionStatus.SUCCESS, finalTransaction.getStatus() );

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
                () -> transactionService.transfer(
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
                () -> transactionService.transfer(
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
                () -> transactionService.transfer(
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
                "080"
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
                () -> transactionService.transfer(
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
    @DisplayName("Should retrieve transactions for wallet")
    void shouldGetTransactionsForWallet() {

        // Arrange
        Long walletId = 2L;

        User sender = createUser(
                "Sender User" + UUID.randomUUID(),
                "history-sender",
                "0801"
                );

        User receiver = createUser(
                "Receiver User" + UUID.randomUUID(),
                "history-receiver",
                "0801"
                );

        Wallet senderWallet = Wallet.createFor(sender);
        Wallet receiverWallet = Wallet.createFor(receiver);

        ReflectionTestUtils.setField(
                senderWallet, "id", 1L);

        ReflectionTestUtils.setField(
                receiverWallet, "id", walletId);

        Transaction transaction = Transaction.createTransfer(
                        "TXN-HISTORY-001",
                        new BigDecimal("300.00"),
                        "Wallet Transfer",
                        senderWallet,
                receiverWallet
                );

        Page<Transaction> transactionPage =
                new PageImpl<>(List.of(transaction));

        TransactionResponse expectedResponse = mock(TransactionResponse.class);

        when(walletService.findById(walletId))
                .thenReturn(receiverWallet);
        when(currentUserService.getCurrentUserEmail())
                .thenReturn("history-receiver@gmail.com");

        when(transactionRepository
                .findBySourceWalletIdOrDestinationWalletIdOrderByCreatedAtDescIdDesc(
                        eq(walletId),
                        eq(walletId),
                        any(Pageable.class)
                ))
                .thenReturn(transactionPage);

        when(transactionMapper.mapToResponse(
                transaction,
                walletId
        )).thenReturn(expectedResponse);

        // Act
        org.springframework.data.domain.Page<TransactionResponse> response =
                transactionService.getTransactions(
                        walletId,
                        0,
                        10
                );

        // Assert
        assertEquals(1, response.getTotalElements());
        assertEquals(expectedResponse, response.getContent().get(0));

        verify(walletService).findById(walletId);

        verify(transactionRepository)
                .findBySourceWalletIdOrDestinationWalletIdOrderByCreatedAtDescIdDesc(
                        eq(walletId),
                        eq(walletId),
                        any(Pageable.class)
                );

        verify(transactionMapper)
                .mapToResponse(
                        transaction,
                        walletId
                );
    }

    @Test
    @DisplayName("Should return empty transaction history when wallet has no transactions")
    void shouldReturnEmptyTransactionHistory() {

        // Arrange
        Long walletId = 2L;

        Wallet wallet = mock(Wallet.class);
        User user = createUser(
                "History User",
                "history-user",
                "080"
                );

        when(wallet.getUser())
                .thenReturn(user);

        when(currentUserService.getCurrentUserEmail())
                .thenReturn("history-user@gmail.com");

        Page<Transaction> emptyPage = Page.empty();

        when(walletService.findById(walletId))
                .thenReturn(wallet);

        when(transactionRepository
                .findBySourceWalletIdOrDestinationWalletIdOrderByCreatedAtDescIdDesc(
                        eq(walletId),
                        eq(walletId),
                        any(Pageable.class)
                ))
                .thenReturn(emptyPage);

        // Act
        Page<TransactionResponse> response =
                transactionService.getTransactions(
                        walletId,
                        0,
                        10
                );

        // Assert
        assertNotNull(response);
        assertTrue(response.isEmpty());
        assertEquals(0, response.getTotalElements());

        verify(walletService).findById(walletId);

        verify(transactionRepository)
                .findBySourceWalletIdOrDestinationWalletIdOrderByCreatedAtDescIdDesc(
                        eq(walletId),
                        eq(walletId),
                        any(Pageable.class)
                );

        verifyNoInteractions(transactionMapper);
    }

    @Test
    @DisplayName("Should reject transaction history request for non-existent wallet")
    void shouldRejectTransactionHistoryForNonExistentWallet() {

        // Arrange
        Long walletId = 912L;

        when(walletService.findById(walletId))
                .thenThrow(new WalletNotFoundException(
                        "Wallet not found: " + walletId
                ));

        // Act & Assert
        assertThrows(
                WalletNotFoundException.class,
                () -> transactionService.getTransactions(
                        walletId,
                        0,
                        10
                )
        );

        verify(walletService).findById(walletId);

        verifyNoInteractions(transactionRepository);
        verifyNoInteractions(transactionMapper);
    }

    @Test
    @DisplayName("Should apply pagination when retrieving transaction history")
    void shouldApplyPaginationWhenRetrievingTransactions() {

        // Arrange
        Long walletId = 2L;

        Wallet wallet = mock(Wallet.class);

        User user = createUser(
                "pager nation",
                "pagination-user",
                "080"
                );

        when(wallet.getUser())
                .thenReturn(user);

        when(currentUserService.getCurrentUserEmail())
                .thenReturn("pagination-user@gmail.com");

        Page<Transaction> transactionPage =
                new PageImpl<>(List.of());

        when(walletService.findById(walletId))
                .thenReturn(wallet);

        when(transactionRepository
                .findBySourceWalletIdOrDestinationWalletIdOrderByCreatedAtDescIdDesc(
                        eq(walletId),
                        eq(walletId),
                        any(Pageable.class)
                ))
                .thenReturn(transactionPage);

        // Act
        transactionService.getTransactions(
                walletId,
                1,
                5
        );

        // Assert
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(transactionRepository)
                .findBySourceWalletIdOrDestinationWalletIdOrderByCreatedAtDescIdDesc(
                        eq(walletId),
                        eq(walletId),
                        pageableCaptor.capture()
                );

        Pageable pageable = pageableCaptor.getValue();

        assertEquals(1, pageable.getPageNumber());
        assertEquals(5, pageable.getPageSize());
    }

    @Test
    @DisplayName("Should reject duplicate idempotency key")
    void shouldRejectDuplicateIdempotencyKey() {

        // Arrange
        Long walletId = 1L;
        String idempotencyKey = "IDEMP-001";

        User user = createUser(
                "Idempotency User" + UUID.randomUUID(),
                "idempotency",
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
                () -> transactionService.deposit(
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

        when(walletService.findById(walletId))
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

        // Act
        transactionService.deposit(request, idempotencyKey);

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

    @Test
    @DisplayName("Should reject transaction history access for another user's wallet")
    void shouldRejectAccessToAnotherUsersWallet() {

        // Arrange
        Long walletId = 2L;

        User walletOwner = createUser(
                "Wallet Owner" + UUID.randomUUID(),
                "owner@gmail.com",
                "080"
                );

        Wallet wallet = Wallet.createFor(walletOwner);

        ReflectionTestUtils.setField(wallet, "id", walletId);

        when(walletService.findById(walletId))
                .thenReturn(wallet);

        when(currentUserService.getCurrentUserEmail())
                .thenReturn("attacker@gmail.com");

        // Act & Assert
        assertThrows(
                AccessDeniedException.class,
                () -> transactionService.getTransactions(
                        walletId,
                        0,
                        10
                )
        );

        // Verify repository was never reached
        verify(transactionRepository, never())
                .findBySourceWalletIdOrDestinationWalletIdOrderByCreatedAtDescIdDesc(
                        anyLong(),
                        anyLong(),
                        any(Pageable.class)
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
