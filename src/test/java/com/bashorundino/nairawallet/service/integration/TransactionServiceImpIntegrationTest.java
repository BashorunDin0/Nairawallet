package com.bashorundino.nairawallet.service.integration;


import com.bashorundino.nairawallet.dto.request.DepositRequest;
import com.bashorundino.nairawallet.dto.request.TransferRequest;
import com.bashorundino.nairawallet.dto.request.WithdrawRequest;
import com.bashorundino.nairawallet.dto.response.TransactionResponse;
import com.bashorundino.nairawallet.entity.LedgerEntry;
import com.bashorundino.nairawallet.entity.Transaction;
import com.bashorundino.nairawallet.entity.User;
import com.bashorundino.nairawallet.entity.Wallet;
import com.bashorundino.nairawallet.enums.LedgerEntryType;
import com.bashorundino.nairawallet.enums.TransactionStatus;
import com.bashorundino.nairawallet.enums.TransactionType;
import com.bashorundino.nairawallet.exception.InactiveWalletException;
import com.bashorundino.nairawallet.repository.LedgerEntryRepository;
import com.bashorundino.nairawallet.repository.TransactionRepository;
import com.bashorundino.nairawallet.repository.UserRepository;
import com.bashorundino.nairawallet.repository.WalletRepository;
import com.bashorundino.nairawallet.service.transaction.TransactionLedgerValidator;
import com.bashorundino.nairawallet.service.transaction.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
public class TransactionServiceImpIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @MockitoSpyBean
    private TransactionLedgerValidator transactionLedgerValidator;

    @Test
    void shouldRollbackTransferWhenReceiverIsInactive() {

//        Arrange
        User sender = createUser(
                "Integration-" + UUID.randomUUID(),
                "integration-sender-",
                "080"
        );

        Wallet senderWallet = Wallet.createFor(sender);
        senderWallet.credit(new BigDecimal("1000.00"));

        User receiver = createUser(
                "Integration-" + UUID.randomUUID(),
                "integration-receiver-",
                "070"
        );

        Wallet receiverWallet = Wallet.createFor(receiver);
        receiverWallet.credit(new BigDecimal("500.00"));
        receiverWallet.deactivate();

        userRepository.save(sender);
        userRepository.save(receiver);

        Long senderWalletId = senderWallet.getId();
        Long receiverWalletId = receiverWallet.getId();

        TransferRequest request = new TransferRequest(
                senderWalletId,
                receiverWalletId,
                new BigDecimal("300.00"),
                "Transfer"
        );
//        Authenticate as the sender
        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        sender.getEmail(),
                        null,
                        Collections.emptyList()
                );

        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        try {
            //  Act & Assert
            assertThrows(InactiveWalletException.class,
                    () -> transactionService.transfer(request,
                            "Integration-transfer-001-" + UUID.randomUUID()));

//        Reload wallets from database
            Wallet senderAfter = walletRepository.
                    findById(senderWalletId).orElseThrow();

            Wallet receiverAfter = walletRepository.
                    findById(receiverWalletId).orElseThrow();

//  Verify and rollback

            assertEquals(new BigDecimal("1000.00"),
                    senderAfter.getBalance());

            assertEquals(new BigDecimal("500.00"),
                    receiverAfter.getBalance());
        } finally {
            SecurityContextHolder.clearContext();
        }


    }

    @Test
    @DisplayName("Should reject stale wallet update using optimistic locking")
    void shouldRejectStaleWalletUpdate() {
//        Arrange
        User user = createUser(
                "Optimistic Lock",
                "optimisticlock-",
                "080"
        );

        Wallet wallet = Wallet.createFor(user);
        wallet.credit(new BigDecimal("1000.00"));

        User savedUser = userRepository.saveAndFlush(user);
        Long walletId = savedUser.getWallet().getId();

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);

//        Load the same wallet into two different persistence contexts.

        Wallet walletSnapshot1 = transactionTemplate.execute(status ->
                walletRepository.findById(walletId).orElseThrow()
        );

        Wallet walletSnapshot2 = transactionTemplate.execute(status ->
                walletRepository.findById(walletId).orElseThrow()
        );

//        Both snapshots should initially have the same version.
        assertEquals(walletSnapshot1.getVersion(), walletSnapshot2.getVersion());

//        First update succeeds
        transactionTemplate.executeWithoutResult(transactionStatus -> {
            walletSnapshot1.credit(new BigDecimal("100.00"));
            walletRepository.saveAndFlush(walletSnapshot1);
        });

        Wallet currentWallet = transactionTemplate.execute(status ->
                walletRepository.findById(walletId).get()
        );

//        Second update is base on stale version

        assertThrows(
                OptimisticLockingFailureException.class,
                () -> transactionTemplate.executeWithoutResult(transactionStatus -> {
                    walletSnapshot2.credit(new BigDecimal("100.00"));
                    walletRepository.saveAndFlush(walletSnapshot2);
                })
        );

    }

    @Test
    @DisplayName("Should handle concurrent transfers between the same two wallets")
    void shouldHandleConcurrentOppositeDirectionTransfers() throws Exception {

        // Arrange
        User userA = createUser(
                "Concurrent User A" + UUID.randomUUID(),
                "concurrent-a-",
                "080"
        );

        User userB = createUser(
                "Concurrent User B" + UUID.randomUUID(),
                "concurrent-b-",
                "070"
        );

        Wallet walletA = Wallet.createFor(userA);
        Wallet walletB = Wallet.createFor(userB);

        walletA.credit(new BigDecimal("1000.00"));
        walletB.credit(new BigDecimal("1000.00"));

        userRepository.saveAndFlush(userA);
        userRepository.saveAndFlush(userB);

        Long walletAId = walletA.getId();
        Long walletBId = walletB.getId();

        TransferRequest transferAtoB = new TransferRequest(
                walletAId,
                walletBId,
                new BigDecimal("100.00"),
                "Concurrent A to B"
        );

        TransferRequest transferBtoA = new TransferRequest(
                walletBId,
                walletAId,
                new BigDecimal("100.00"),
                "Concurrent B to A"
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {

            Future<?> transfer1 = executor.submit(() -> {

                SecurityContext context =
                        SecurityContextHolder.createEmptyContext();

                Authentication authentication =
                        new UsernamePasswordAuthenticationToken(
                                userA.getEmail(),
                                null,
                                Collections.emptyList()
                        );

                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);

                try {
                    startLatch.await();

                    transactionService.transfer(
                            transferAtoB,
                            "concurrent-a-" + UUID.randomUUID()
                    );

                } catch (Exception e) {
                    throw new RuntimeException(e);

                } finally {
                    SecurityContextHolder.clearContext();
                }
            });

            Future<?> transfer2 = executor.submit(() -> {

                SecurityContext context =
                        SecurityContextHolder.createEmptyContext();

                Authentication authentication =
                        new UsernamePasswordAuthenticationToken(
                                userB.getEmail(),
                                null,
                                Collections.emptyList()
                        );

                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);

                try {
                    startLatch.await();

                    transactionService.transfer(
                            transferBtoA,
                            "concurrent-b-" + UUID.randomUUID()
                    );

                } catch (Exception e) {
                    throw new RuntimeException(e);

                } finally {
                    SecurityContextHolder.clearContext();
                }
            });

            // Start both transfers at approximately the same time
            startLatch.countDown();

            // If locking is broken, this may hang or deadlock.
            transfer1.get(10, TimeUnit.SECONDS);
            transfer2.get(10, TimeUnit.SECONDS);

        } finally {
            executor.shutdownNow();
        }

        // Reload wallets from the database
        Wallet walletAAfter = walletRepository
                .findById(walletAId)
                .orElseThrow();

        Wallet walletBAfter = walletRepository
                .findById(walletBId)
                .orElseThrow();

        // Both transfers were for the same amount in opposite directions,
        // so the final balances should remain unchanged.
        assertEquals(
                new BigDecimal("1000.00"),
                walletAAfter.getBalance()
        );

        assertEquals(
                new BigDecimal("1000.00"),
                walletBAfter.getBalance()
        );
    }


    @Test
    @DisplayName("Should create debit and credit ledger entries for successful transfer")
    void shouldCreateLedgerEntriesForSuccessfulTransfer() {

        // Arrange
        User sender = createUser(
                "Ledger Sender",
                "ledger-sender-",
                "080"
        );

        User receiver = createUser(
                "Ledger Receiver",
                "ledger-receiver-",
                "070"
        );

        Wallet senderWallet = Wallet.createFor(sender);
        Wallet receiverWallet = Wallet.createFor(receiver);

        senderWallet.credit(new BigDecimal("1000.00"));
        receiverWallet.credit(new BigDecimal("500.00"));

        userRepository.saveAndFlush(sender);
        userRepository.saveAndFlush(receiver);

        Long senderWalletId = senderWallet.getId();
        Long receiverWalletId = receiverWallet.getId();

        TransferRequest request = new TransferRequest(
                senderWalletId,
                receiverWalletId,
                new BigDecimal("300.00"),
                "Ledger Transfer"
        );

        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        sender.getEmail(),
                        null,
                        Collections.emptyList()
                );

        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        try {
            // Act
            TransactionResponse response =
                    transactionService.transfer(
                            request,
                            "ledger-transfer-" + UUID.randomUUID()
                    );

            // Retrieve the persisted transaction using its public reference
            Transaction transaction =
                    transactionRepository
                            .findByTxReference(response.reference())
                            .orElseThrow();

            List<LedgerEntry> entries =
                    ledgerEntryRepository
                            .findByTransactionId(transaction.getId());

            // Assert
            assertEquals(2, entries.size());

            assertTrue(entries.stream()
                    .anyMatch(entry ->
                            entry.getEntryType() == LedgerEntryType.DEBIT
                                    && entry.getWallet().getId()
                                    .equals(senderWalletId)
                                    && entry.getAmount()
                                    .compareTo(new BigDecimal("300.00")) == 0
                    ));

            assertTrue(entries.stream()
                    .anyMatch(entry ->
                            entry.getEntryType() == LedgerEntryType.CREDIT
                                    && entry.getWallet().getId()
                                    .equals(receiverWalletId)
                                    && entry.getAmount()
                                    .compareTo(new BigDecimal("300.00")) == 0
                    ));

        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("Should create credit ledger entry for successful deposit")
    void shouldCreateCreditLedgerEntryForSuccessfulDeposit() {

        User user = createUser(
                "Deposit Ledger User",
                "deposit-ledger-",
                "080"
        );

        Wallet wallet = Wallet.createFor(user);

        userRepository.saveAndFlush(user);

        Long walletId = wallet.getId();

        DepositRequest request = new DepositRequest(
                walletId,
                new BigDecimal("500.00")
        );

        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(),
                        null,
                        Collections.emptyList()
                );

        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        try {
            TransactionResponse response =
                    transactionService.deposit(
                            request,
                            "deposit-ledger-" + UUID.randomUUID()
                    );

            Transaction transaction =
                    transactionRepository
                            .findByTxReference(response.reference())
                            .orElseThrow();

            List<LedgerEntry> entries =
                    ledgerEntryRepository
                            .findByTransactionId(transaction.getId());

            assertEquals(1, entries.size());

            LedgerEntry entry = entries.get(0);

            assertEquals(LedgerEntryType.CREDIT, entry.getEntryType());
            assertEquals(walletId, entry.getWallet().getId());

            assertEquals(
                    new BigDecimal("500.00"),
                    entry.getAmount()
            );

            assertEquals(
                    new BigDecimal("0.00"),
                    entry.getBalanceBefore()
            );

            assertEquals(
                    new BigDecimal("500.00"),
                    entry.getBalanceAfter()
            );

            assertEquals(
                    TransactionStatus.SUCCESS,
                    transaction.getStatus()
            );

        } finally {
            SecurityContextHolder.clearContext();
        }
    }


    @Test
    @DisplayName("Should create debit ledger entry for successful withdrawal")
    void shouldCreateDebitLedgerEntryForSuccessfulWithdrawal() {

        User user = createUser(
                "Withdrawal Ledger User",
                "withdrawal-ledger-",
                "070"
        );

        Wallet wallet = Wallet.createFor(user);

        wallet.credit(new BigDecimal("1000.00"));

        userRepository.saveAndFlush(user);

        Long walletId = wallet.getId();

        WithdrawRequest request = new WithdrawRequest(
                walletId,
                new BigDecimal("300.00")
        );

        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(),
                        null,
                        Collections.emptyList()
                );

        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        try {
            TransactionResponse response =
                    transactionService.withdrawal(
                            request,
                            "withdrawal-ledger-" + UUID.randomUUID()
                    );

            Transaction transaction =
                    transactionRepository
                            .findByTxReference(response.reference())
                            .orElseThrow();

            List<LedgerEntry> entries =
                    ledgerEntryRepository
                            .findByTransactionId(transaction.getId());

            assertEquals(1, entries.size());

            LedgerEntry entry = entries.get(0);

            assertEquals(LedgerEntryType.DEBIT, entry.getEntryType());
            assertEquals(walletId, entry.getWallet().getId());

            assertEquals(
                    new BigDecimal("300.00"),
                    entry.getAmount()
            );

            assertEquals(
                    new BigDecimal("1000.00"),
                    entry.getBalanceBefore()
            );

            assertEquals(
                    new BigDecimal("700.00"),
                    entry.getBalanceAfter()
            );

            assertEquals(
                    TransactionStatus.SUCCESS,
                    transaction.getStatus()
            );

        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void shouldProcessOnlyOneConcurrentDepositForSameIdempotencyKey()
            throws Exception {

        // Arrange
        User user = createUser(
                "Concurrent Deposit User",
                "concurrent-deposit-",
                "080"
        );

        Wallet wallet = Wallet.createFor(user);

        userRepository.save(user);

        Long walletId = wallet.getId();

        String idempotencyKey = "CONCURRENT-DEPOSIT-" + UUID.randomUUID();

        DepositRequest request = new DepositRequest(
                walletId,
                new BigDecimal("500.00")
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<TransactionResponse> task = () -> {
            SecurityContext context =
                    SecurityContextHolder.createEmptyContext();

            context.setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            user.getEmail(),
                            null,
                            Collections.emptyList()
                    )
            );

            SecurityContextHolder.setContext(context);

            try {
                startLatch.await();
                return transactionService.deposit(request, idempotencyKey);
            } finally {
                SecurityContextHolder.clearContext();
            }
        };

        try {
            // Act
            Future<TransactionResponse> firstResult = executor.submit(task);
            Future<TransactionResponse> secondResult = executor.submit(task);

            startLatch.countDown();

            TransactionResponse response1 =
                    firstResult.get(10, TimeUnit.SECONDS);

            TransactionResponse response2 =
                    secondResult.get(10, TimeUnit.SECONDS);


            // Assert: exactly one request succeeds
            assertEquals(response1.reference(), response2.reference());

            // Assert: financial operation executed only once
            Wallet updatedWallet = walletRepository.findById(walletId)
                    .orElseThrow();

            assertEquals(
                    new BigDecimal("500.00"),
                    updatedWallet.getBalance()
            );

            // Assert: exactly one transaction was created
            long transactionCount =
                    transactionRepository
                            .findBySourceWalletIdOrDestinationWalletIdOrderByCreatedAtDescIdDesc(
                                    walletId,
                                    walletId,
                                    Pageable.unpaged()
                            )
                            .getTotalElements();

            assertEquals(1, transactionCount);

            // Assert: exactly one ledger entry was created
            assertEquals(
                    1,
                    ledgerEntryRepository
                            .findByWalletIdOrderByCreatedAtDesc(walletId)
                            .size()
            );

        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("Should prevent concurrent withdrawals from spending the same funds")
    void shouldPreventConcurrentWithdrawalsFromSameWallet() throws Exception {

        // Arrange: create a wallet with only 1000.00
        User user = createUser(
                "Concurrent Withdrawal User" + UUID.randomUUID(),
                "concurrent-withdrawal-",
                "080"
        );

        Wallet wallet = Wallet.createFor(user);
        wallet.credit(new BigDecimal("1000.00"));

        userRepository.saveAndFlush(user);

        Long walletId = wallet.getId();
        String userEmail = user.getEmail();

        WithdrawRequest withdrawal1 = new WithdrawRequest(
                walletId,
                new BigDecimal("800.00")
        );

        WithdrawRequest withdrawal2 = new WithdrawRequest(
                walletId,
                new BigDecimal("800.00")
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        try {
            Callable<Void> withdrawalTask = () -> {

                SecurityContext context =
                        SecurityContextHolder.createEmptyContext();

                Authentication authentication =
                        new UsernamePasswordAuthenticationToken(
                                userEmail,
                                null,
                                Collections.emptyList()
                        );

                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);

                try {
                    startLatch.await();

                    // Each request uses a different idempotency key.
                    // We are testing balance concurrency, not duplicate-key handling.
                    String key = "concurrent-withdrawal-" + UUID.randomUUID();

                    // Adapt this call if your TransactionService.withdraw(...)
                    // signature differs.
                    transactionService.withdrawal(
                            new WithdrawRequest(
                                    walletId,
                                    new BigDecimal("800.00")
                            ),
                            key
                    );

                    successes.incrementAndGet();

                } catch (Exception exception) {
                    failures.incrementAndGet();

                } finally {
                    SecurityContextHolder.clearContext();
                }

                return null;
            };

            Future<Void> future1 = executor.submit(withdrawalTask);
            Future<Void> future2 = executor.submit(withdrawalTask);

            startLatch.countDown();

            future1.get(10, TimeUnit.SECONDS);
            future2.get(10, TimeUnit.SECONDS);

        } finally {
            executor.shutdownNow();
        }

        // Assert: only one withdrawal may succeed.
        assertEquals(1, successes.get());
        assertEquals(1, failures.get());

        // Reload the wallet from the database.
        Wallet walletAfter = walletRepository.findById(walletId)
                .orElseThrow();

        assertEquals(
                new BigDecimal("200.00"),
                walletAfter.getBalance()
        );

// Exactly one withdrawal transaction should have been persisted.
        List<Transaction> withdrawals =
                transactionRepository.findAll()
                        .stream()
                        .filter(tx -> tx.getTransactionType()
                                == TransactionType.WITHDRAWAL)
                        .filter(tx -> tx.getSourceWallet() != null
                                && tx.getSourceWallet().getId().equals(walletId))
                        .filter(tx -> tx.getStatus() == TransactionStatus.SUCCESS)
                        .toList();

        assertEquals(1, withdrawals.size());

// Exactly one debit ledger entry should exist for this wallet
// and the successful withdrawal.
        Transaction successfulWithdrawal = withdrawals.get(0);

        List<LedgerEntry> ledgerEntries =
                ledgerEntryRepository.findByTransactionId(
                        successfulWithdrawal.getId()
                );

        assertEquals(1, ledgerEntries.size());

        LedgerEntry ledgerEntry = ledgerEntries.get(0);

        assertEquals(LedgerEntryType.DEBIT, ledgerEntry.getEntryType());
        assertEquals(new BigDecimal("800.00"), ledgerEntry.getAmount());
        assertEquals(new BigDecimal("1000.00"), ledgerEntry.getBalanceBefore());
        assertEquals(new BigDecimal("200.00"), ledgerEntry.getBalanceAfter());
    }

    @Test
    @DisplayName("Should rollback deposit when ledger validation fails")
    void shouldRollbackDepositWhenLedgerValidationFails() {

        User user = createUser(
                "Rollback-" + UUID.randomUUID(),
                "rollback-deposit-",
                "080"
        );

        Wallet wallet = Wallet.createFor(user);
        wallet.credit(new BigDecimal("1000.00"));

        userRepository.saveAndFlush(user);

        Long walletId = wallet.getId();

        long transactionsBefore = transactionRepository.count();
        long ledgerEntriesBefore = ledgerEntryRepository.count();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(),
                        null,
                        Collections.emptyList()
                )
        );
        SecurityContextHolder.setContext(context);

        try {
            doThrow(new IllegalStateException("Simulated ledger validation failure"))
                    .when(transactionLedgerValidator)
                    .validate(any(Transaction.class));

            DepositRequest request = new DepositRequest(
                    walletId,
                    new BigDecimal("500.00")
            );

            assertThrows(
                    IllegalStateException.class,
                    () -> transactionService.deposit(
                            request,
                            "rollback-deposit-" + UUID.randomUUID()
                    )
            );

            Wallet walletAfter = walletRepository.findById(walletId).orElseThrow();

            assertEquals(
                    new BigDecimal("1000.00"),
                    walletAfter.getBalance()
            );

            assertEquals(transactionsBefore, transactionRepository.count());
            assertEquals(ledgerEntriesBefore, ledgerEntryRepository.count());

        } finally {
            SecurityContextHolder.clearContext();
        }
    }



    @Test
    @DisplayName("Should rollback failed deposit and allow retry with the same idempotency key")
    void shouldAllowRetryWithSameKeyAfterDepositRollback() {
        User user = createUser(
                "Retry-" + UUID.randomUUID(),
                "retry-deposit-",
                "080"
        );

        Wallet wallet = Wallet.createFor(user);
        wallet.credit(new BigDecimal("1000.00"));
        userRepository.saveAndFlush(user);

        Long walletId = wallet.getId();

        String idempotencyKey = "retry-deposit-" + UUID.randomUUID();

        DepositRequest request = new DepositRequest(
                walletId,
                new BigDecimal("500.00")
        );

        long transactionsBefore = transactionRepository.count();
        long ledgerEntriesBefore = ledgerEntryRepository.count();

        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(),
                        null,
                        Collections.emptyList()
                )
        );

        SecurityContextHolder.setContext(context);

        try {
            // First validation call fails; the next uses the real validator.
            doThrow(new IllegalStateException(
                    "Simulated ledger validation failure"))
                    .doCallRealMethod()
                    .when(transactionLedgerValidator)
                    .validate(any(Transaction.class));

            // First attempt must fail and roll back.
            assertThrows(IllegalStateException.class,
                    () -> transactionService.deposit(
                            request, idempotencyKey));

            Wallet walletAfterFailure =
                    walletRepository.findById(walletId).orElseThrow();

            assertEquals(
                    new BigDecimal("1000.00"),
                    walletAfterFailure.getBalance()
            );

            assertEquals(
                    transactionsBefore,
                    transactionRepository.count()
            );

            assertEquals(
                    ledgerEntriesBefore,
                    ledgerEntryRepository.count()
            );

            // Retry the identical request using the identical key.
            transactionService.deposit(request, idempotencyKey);

            Wallet walletAfterRetry =
                    walletRepository.findById(walletId).orElseThrow();

            assertEquals(
                    new BigDecimal("1500.00"),
                    walletAfterRetry.getBalance()
            );

            assertEquals(
                    transactionsBefore + 1,
                    transactionRepository.count()
            );

            assertEquals(
                    ledgerEntriesBefore + 1,
                    ledgerEntryRepository.count()
            );
        } finally {
            SecurityContextHolder.clearContext();
        }
    }



    @Test
    @DisplayName("Should return the original result when a successful deposit is retried with the same key")
    void shouldReturnOriginalDepositWhenSameRequestIsRetried() {
        User user = createUser(
                "Lost-response-" + UUID.randomUUID(),
                "lost-response-",
                "080"
        );

        Wallet wallet = Wallet.createFor(user);
        wallet.credit(new BigDecimal("1000.00"));
        userRepository.saveAndFlush(user);

        Long walletId = wallet.getId();
        String idempotencyKey = "lost-response-" + UUID.randomUUID();

        DepositRequest request = new DepositRequest(
                walletId,
                new BigDecimal("500.00")
        );

        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(),
                        null,
                        Collections.emptyList()
                )
        );

        SecurityContextHolder.setContext(context);

        try {
            long transactionsBefore = transactionRepository.count();
            long ledgerEntriesBefore = ledgerEntryRepository.count();

            // First request succeeds.
            TransactionResponse firstResponse =
                    transactionService.deposit(request, idempotencyKey);

            // Simulate a client retry after not receiving the response.
            TransactionResponse retryResponse =
                    transactionService.deposit(request, idempotencyKey);

            Wallet walletAfter =
                    walletRepository.findById(walletId).orElseThrow();

            // The deposit must only be applied once.
            assertEquals(
                    new BigDecimal("1500.00"),
                    walletAfter.getBalance()
            );

            assertEquals(
                    transactionsBefore + 1,
                    transactionRepository.count()
            );

            assertEquals(
                    ledgerEntriesBefore + 1,
                    ledgerEntryRepository.count()
            );

            // Both calls should identify the same original result.
            assertEquals(firstResponse, retryResponse);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }


    //  Helper create a user method
    private User createUser(
            String fullName,
            String emailPrefix,
            String phonePrefix
    ) {
        return User.create(
                fullName,
                emailPrefix + UUID.randomUUID() + "@gmail.com",
                "password123",
                phonePrefix + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8)
        );
    }
}
