package com.example.nairawallet.Service;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.example.nairawallet.Dto.Request.CreateUserRequest;
import com.example.nairawallet.Dto.Response.UserResponse;

public interface UserService {
    UserResponse createUser(CreateUserRequest request);
}
