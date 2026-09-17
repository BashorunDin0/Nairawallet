package com.example.nairawallet.Service;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import java.time.LocalDateTime;

public interface IdempotencyKeyService {

    void validate(String key);

    void save(String key);

}
