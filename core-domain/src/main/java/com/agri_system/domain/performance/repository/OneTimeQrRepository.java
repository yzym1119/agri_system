package com.agri_system.domain.performance.repository;

import com.agri_system.domain.performance.model.OneTimeQr;

import java.util.Optional;

public interface OneTimeQrRepository {

    OneTimeQr save(OneTimeQr qr);

    Optional<OneTimeQr> findByToken(String token);
}

