package com.bashorundino.nairawallet.service.transaction;

import com.bashorundino.nairawallet.dto.request.WithdrawRequest;
import com.bashorundino.nairawallet.dto.response.TransactionResponse;

public interface WithdrawalService {

    TransactionResponse withdrawal(WithdrawRequest request, String idempotencyKey);
}
