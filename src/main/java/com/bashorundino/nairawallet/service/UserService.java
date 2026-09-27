package com.bashorundino.nairawallet.service;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.bashorundino.nairawallet.dto.request.CreateUserRequest;
import com.bashorundino.nairawallet.dto.response.UserResponse;

public interface UserService {
    UserResponse createUser(CreateUserRequest request);
}
