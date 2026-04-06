package com.agri_system.infrastructure.credit.repository;

import com.agri_system.domain.credit.model.CreditStatement;
import com.agri_system.domain.credit.repository.CreditStatementRepository;
import com.agri_system.infrastructure.credit.mapper.CreditStatementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CreditStatementRepositoryImpl implements CreditStatementRepository {

    private final CreditStatementMapper mapper;

    @Override
    public CreditStatement save(CreditStatement statement) {
        if (statement.getId() == null) {
            mapper.insert(statement);
        } else {
            mapper.update(statement);
        }
        return statement;
    }
}






