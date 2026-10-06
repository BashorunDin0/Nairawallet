package com.bashorundino.nairawallet.service.integration;


import com.bashorundino.nairawallet.dto.request.TransferRequest;
import com.bashorundino.nairawallet.entity.User;
import com.bashorundino.nairawallet.entity.Wallet;
import com.bashorundino.nairawallet.exception.InactiveWalletException;
import com.bashorundino.nairawallet.repository.UserRepository;
import com.bashorundino.nairawallet.repository.WalletRepository;
import com.bashorundino.nairawallet.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
                try {
                    startLatch.await();

                    transactionService.transfer(
                            transferAtoB,
                            "concurrent-" + UUID.randomUUID()
                    );

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            Future<?> transfer2 = executor.submit(() -> {
                try {
                    startLatch.await();

                    transactionService.transfer(
                            transferBtoA,
                            "concurrent-" + UUID.randomUUID()
                    );

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            // Start both transfers at approximately the same time
            startLatch.countDown();

            // If the locking strategy is broken, this can hang.
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

        // Both transfers were for the same amount in opposite directions.
        assertEquals(
                new BigDecimal("1000.00"),
                walletAAfter.getBalance()
        );

        assertEquals(
                new BigDecimal("1000.00"),
                walletBAfter.getBalance()
        );
    }



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
