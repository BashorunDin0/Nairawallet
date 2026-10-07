package com.bashorundino.nairawallet.service.transaction;

import com.bashorundino.nairawallet.dto.request.DepositRequest;
import com.bashorundino.nairawallet.dto.response.TransactionResponse;

public interface DepositService {

    TransactionResponse deposit(
            DepositRequest request,
            String idempotencyKey
    );
}
