package com.agri_system.infrastructure.auth.repository;

import com.agri_system.domain.auth.model.CustomerIdentity;
import com.agri_system.domain.auth.repository.CustomerIdentityRepository;
import com.agri_system.infrastructure.auth.mapper.CustomerIdentityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 客户身份仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class CustomerIdentityRepositoryImpl implements CustomerIdentityRepository {
    
    private final CustomerIdentityMapper mapper;
    
    @Override
    public CustomerIdentity save(CustomerIdentity identity) {
        if (identity.getId() == null) {
            mapper.insert(identity);
        } else {
            mapper.update(identity);
        }
        return identity;
    }
    
    @Override
    public Optional<CustomerIdentity> findByTypeAndValue(String identityType, String identityValue) {
        CustomerIdentity identity = mapper.selectOneByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .where("identity_type = ?", identityType)
                        .and("identity_value = ?", identityValue)
        );
        return Optional.ofNullable(identity);
    }
    
    @Override
    public Optional<CustomerIdentity> findByCustomerIdAndType(Long customerId, String identityType) {
        CustomerIdentity identity = mapper.selectOneByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .where("customer_id = ?", customerId)
                        .and("identity_type = ?", identityType)
        );
        return Optional.ofNullable(identity);
    }
}






