package com.bashorundino.nairawallet.service.integration;


import com.bashorundino.nairawallet.dto.request.DepositRequest;
import com.bashorundino.nairawallet.entity.User;
import com.bashorundino.nairawallet.entity.Wallet;
import com.bashorundino.nairawallet.repository.TransactionRepository;
import com.bashorundino.nairawallet.repository.UserRepository;
import com.bashorundino.nairawallet.repository.WalletRepository;
import com.bashorundino.nairawallet.service.LedgerEntryService;
import com.bashorundino.nairawallet.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
public class TransactionAtomicityIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @MockitoBean
    private LedgerEntryService ledgerEntryService;

    void shouldRollbackEntireDepositWhenLedgerCreationFails(){

        User user = User.create(
                "Atomic User",
                "atomicity@test.com",
                "123456789",
                "080"
        );

        Wallet wallet = Wallet.createFor(user);
        userRepository.saveAndFlush(user);

        Long walletId = wallet.getId();

        DepositRequest request = new DepositRequest(
                walletId, new BigDecimal("500.00")
        );

        doThrow(new RuntimeException("Simulated ledger failure"))
                .when(ledgerEntryService)
                .createEntry(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()
                );

        String idempotencyKey =
                "atomicity-" + UUID.randomUUID();

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
            assertThrows(
                    RuntimeException.class,
                    () -> transactionService.deposit(
                            request,
                            idempotencyKey
                    )
            );

            Wallet walletAfter =
                    walletRepository
                            .findById(walletId)
                            .orElseThrow();

            assertEquals(
                    new BigDecimal("0.00"),
                    walletAfter.getBalance()
            );

            assertEquals(
                    0,
                    transactionRepository.count()
            );

        } finally {
            SecurityContextHolder.clearContext();
        }
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



