package com.bashorundino.nairawallet.service;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.bashorundino.nairawallet.dto.request.CreateUserRequest;
import com.bashorundino.nairawallet.dto.response.UserResponse;
import com.bashorundino.nairawallet.entity.User;
import com.bashorundino.nairawallet.entity.Wallet;
import com.bashorundino.nairawallet.exception.UserAlreadyExistsException;
import com.bashorundino.nairawallet.mapper.UserMapper;
import com.bashorundino.nairawallet.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImp implements UserService{

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional
    @Override
    public UserResponse createUser(CreateUserRequest request) {

        String email = request.email().trim().toLowerCase();

        String phoneNumber = request.phoneNumber().trim();

        if (userRepository.existsByEmail(email)){
            throw new UserAlreadyExistsException("User with email already exists");
        }

        if (userRepository.existsByPhoneNumber(phoneNumber)){
            throw new UserAlreadyExistsException("Phone number already exists");
        }

        User user = User.builder()
                .fullName(request.fullName())
                .email(email)
                .phoneNumber(phoneNumber)
                .build();

        Wallet wallet = Wallet.createFor(user);
        user.assignWallet(wallet);

        User savedUser = userRepository.save(user);

        return userMapper.mapToResponse(savedUser);

    }
}
