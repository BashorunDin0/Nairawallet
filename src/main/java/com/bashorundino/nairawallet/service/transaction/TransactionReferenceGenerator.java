package com.bashorundino.nairawallet.service.transaction;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TransactionReferenceGenerator {
    public String generate() {
        return "TXN-" +
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 16)
                        .toUpperCase();
        }
    }

