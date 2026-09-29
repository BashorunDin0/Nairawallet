package com.bashorundino.nairawallet.service;


import com.bashorundino.nairawallet.dto.request.TransferRequest;
import com.bashorundino.nairawallet.entity.User;
import com.bashorundino.nairawallet.entity.Wallet;
import com.bashorundino.nairawallet.exception.InactiveWalletException;
import com.bashorundino.nairawallet.repository.UserRepository;
import com.bashorundino.nairawallet.repository.WalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
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
        User sender = User.builder()
                .fullName("Integration-" + UUID.randomUUID())
                .email("integration-sender-" + UUID.randomUUID() + "@gmail.com")
                .phoneNumber("080" + UUID.randomUUID().toString().replace("-",
                        "").substring(0, 8))
                .build();

        Wallet senderWallet = Wallet.createFor(sender);
        senderWallet.credit(new BigDecimal("1000.00"));

        User receiver = User.builder()
                .fullName("receiver-" + UUID.randomUUID())
                .email("integration-receiver-" + UUID.randomUUID() + "@gmail.com")
                .phoneNumber("070" + UUID.randomUUID().toString().
                        replace("-", "").substring(0, 8))
                .build();

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
        User user = User.builder()
                .fullName("Optimistic Lock")
                .email("optimisticlock" + UUID.randomUUID() + "@gmail.com")
                .phoneNumber("080" + String.format("%08d", new Random().nextInt(100_000_000)))
                .build();

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
}
