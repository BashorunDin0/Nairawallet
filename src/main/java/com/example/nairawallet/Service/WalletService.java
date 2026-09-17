package com.example.nairawallet.Service;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.example.nairawallet.Dto.Request.DepositRequest;
import com.example.nairawallet.Dto.Request.WithdrawRequest;
import com.example.nairawallet.Dto.Response.WalletResponse;
import com.example.nairawallet.Entity.Wallet;

public interface WalletService {

   Wallet findById(Long walletId);

    WalletResponse getWallet(Long walletId);

}
