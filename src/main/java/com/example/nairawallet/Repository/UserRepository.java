package com.example.nairawallet.Repository;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */


import com.example.nairawallet.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    Optional<User> findByPhoneNumber(String phoneNumber);
}
