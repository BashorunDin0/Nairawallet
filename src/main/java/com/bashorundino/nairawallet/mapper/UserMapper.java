package com.bashorundino.nairawallet.mapper;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.bashorundino.nairawallet.dto.response.UserResponse;
import com.bashorundino.nairawallet.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserResponse mapToResponse(User user){
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getWallet().getId()
        );
    }
}
