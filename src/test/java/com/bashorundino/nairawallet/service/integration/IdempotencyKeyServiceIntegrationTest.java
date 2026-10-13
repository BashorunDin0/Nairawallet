package com.bashorundino.nairawallet.service.integration;

import com.bashorundino.nairawallet.entity.User;
import com.bashorundino.nairawallet.enums.TransactionType;
import com.bashorundino.nairawallet.repository.IdempotencyKeyRepository;
import com.bashorundino.nairawallet.repository.UserRepository;
import com.bashorundino.nairawallet.service.IdempotencyKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class IdempotencyKeyServiceIntegrationTest {

    @Autowired
    private IdempotencyKeyService idempotencyKeyService;

    @Autowired
    private IdempotencyKeyRepository repository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldAllowOnlyOneConcurrentClaimForSameKey() throws Exception {

        String suffix = UUID.randomUUID().toString();

        User user = userRepository.saveAndFlush(
                User.create(
                        "Idempotency Test User",
                        "idempotency-" + suffix + "@example.com",
                        "test-password",
                        "080" + suffix.replace("-", "").substring(0, 8)
                )
        );

        String key = "CONCURRENT-" + suffix;
        String requestHash = "a".repeat(64);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            Callable<String> task = () -> {
                startLatch.await();

                try {
                    idempotencyKeyService.create(
                            key,
                            user,
                            TransactionType.DEPOSIT,
                            requestHash
                    );

                    return "Success";

                } catch (DataIntegrityViolationException exception) {
                    return "Duplicate";
                }
            };

            Future<String> firstResult = executor.submit(task);
            Future<String> secondResult = executor.submit(task);

            startLatch.countDown();

            String result1 = firstResult.get(10, TimeUnit.SECONDS);
            String result2 = secondResult.get(10, TimeUnit.SECONDS);

            assertTrue(
                    (result1.equals("Success") && result2.equals("Duplicate"))
                            || (result1.equals("Duplicate") && result2.equals("Success"))
            );

            assertTrue(
                    repository
                            .findByUser_IdAndOperationTypeAndIdempotencyKey(
                                    user.getId(),
                                    TransactionType.DEPOSIT,
                                    key
                            )
                            .isPresent()
            );

        } finally {
            executor.shutdownNow();
        }
    }
}
