package com.example.nairawallet.Service;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.example.nairawallet.Dto.Request.CreateUserRequest;
import com.example.nairawallet.Dto.Response.UserResponse;
import com.example.nairawallet.Entity.User;
import com.example.nairawallet.Entity.Wallet;
import com.example.nairawallet.Enum.Currency;
import com.example.nairawallet.Enum.WalletStatus;
import com.example.nairawallet.Exception.UserAlreadyExistsException;
import com.example.nairawallet.Mapper.UserMapper;
import com.example.nairawallet.Repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class UserServiceImp implements UserService{

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional
    @Override
    public UserResponse createUser(CreateUserRequest request) {

        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(request.email())){
            throw new UserAlreadyExistsException("User with email already exists");
        }

        if (userRepository.existsByPhoneNumber(request.phoneNumber())){
            throw new UserAlreadyExistsException("Phone number already exists");
        }

        User user = User.builder()
                .fullName(request.fullName())
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .build();

        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(BigDecimal.ZERO)
                .currency(Currency.NGN)
                .status(WalletStatus.ACTIVE)
                .build();

        user.setWallet(wallet);
        User savedUser = userRepository.save(user);

        return userMapper.mapToResponse(savedUser);

    }
}
