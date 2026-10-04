package com.bashorundino.nairawallet.service.integration;

import com.bashorundino.nairawallet.exception.DuplicateTransactionException;
import com.bashorundino.nairawallet.repository.IdempotencyKeyRepository;
import com.bashorundino.nairawallet.service.IdempotencyKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

    @Test
    void shouldAllowOnlyOneConcurrentClaimForSameKey() throws Exception{

        String key = "CONCURRENT-" + UUID.randomUUID();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<String> task = () -> {
            startLatch.await();

            try {
                idempotencyKeyService.claim(key);
                return "Success";
            } catch (DuplicateTransactionException ex){
                return "Duplicate";
            }
        };

        Future<String> firstResult = executor.submit(task);
        Future<String> secondResult = executor.submit(task);

//        Start both threads at approximately the same time
        startLatch.countDown();

        String result1 = firstResult.get(5, TimeUnit.SECONDS);
        String result2 = secondResult.get(5, TimeUnit.SECONDS);

        assertTrue(result1.equals("Success") || result2.equals("Success"));
        assertTrue(result1.equals("Duplicate") || result2.equals("Duplicate"));
        assertTrue(repository.findByIdempotencyKey(key).isPresent());

        executor.shutdown();
    }
}
