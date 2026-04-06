package com.agri_system.infrastructure.performance.repository;

import com.agri_system.domain.performance.model.OneTimeQr;
import com.agri_system.domain.performance.repository.OneTimeQrRepository;
import com.agri_system.infrastructure.performance.mapper.OneTimeQrMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OneTimeQrRepositoryImpl implements OneTimeQrRepository {

    private final OneTimeQrMapper mapper;

    @Override
    public OneTimeQr save(OneTimeQr qr) {
        if (qr.getId() == null) {
            mapper.insert(qr);
        } else {
            mapper.update(qr);
        }
        return qr;
    }

    @Override
    public Optional<OneTimeQr> findByToken(String token) {
        return Optional.ofNullable(mapper.selectOneByQuery(
                QueryWrapper.create().where("qr_token = ?", token)));
    }
}

