package com.bashorundino.nairawallet.exception;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

public class InactiveWalletException extends RuntimeException {
    public InactiveWalletException(String message){
        super(message);
    }
}
