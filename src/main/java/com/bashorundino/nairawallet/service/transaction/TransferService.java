package com.bashorundino.nairawallet.service.transaction;

import com.bashorundino.nairawallet.dto.request.TransferRequest;
import com.bashorundino.nairawallet.dto.response.TransactionResponse;

public interface TransferService {

    TransactionResponse transfer(TransferRequest request, String idempotencyKey);

}
