package com.bashorundino.nairawallet.service;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.bashorundino.nairawallet.dto.response.WalletResponse;
import com.bashorundino.nairawallet.entity.Wallet;

public interface WalletService {

   Wallet findById(Long walletId);

    WalletResponse getWallet(Long walletId);

}
