package com.agri_system.application.customer.service;

import com.agri_system.application.customer.dto.CustomerCreateCommand;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.wallet.model.CustomerWallet;
import com.agri_system.domain.wallet.repository.CustomerWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** 后台与已核验新客共用原建档/三零钱包事务，不发送交易或会员事件。 */
@Service
@RequiredArgsConstructor
public class CustomerRegistrationService {
    private final CustomerRepository customerRepository;
    private final CustomerWalletRepository walletRepository;
    @Transactional(rollbackFor=Exception.class)
    public Customer create(CustomerCreateCommand command) {
        var mainland = com.agri_system.domain.customer.service.CustomerPhoneNumbers.mainland(command.getPhone());
        String phone = mainland.orElse(command.getPhone());
        // 新建统一保存；旧号码只读匹配，包含已停用/删除档案，不能据此重建钱包。
        if (mainland.isPresent()) {
            if (!customerRepository.findIdsByPhoneIncludingDeleted(phone).isEmpty())
                throw new IllegalArgumentException("手机号已有客户档案，请先查询并核对，不能重复建档");
        } else {
            customerRepository.findByPhone(phone).ifPresent(c -> { throw new IllegalArgumentException("手机号已存在"); });
        }

        // 创建客户
        Customer customer = new Customer();
        customer.setCode("CUST_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        customer.setName(command.getName());
        customer.setPhone(phone);
        customer.setIdCard(command.getIdCard());
        customer.setType(1); // 普通客户
        customer.setStatus(1); // 正常
        customer.setAddress(command.getAddress());
        customer.setBindStaffId(command.getBindStaffId());
        customer.setRemark(command.getRemark());
        customer.setVersion(0); // 初始化乐观锁版本号
        customer.setCreatedAt(LocalDateTime.now());
        customer.setUpdatedAt(LocalDateTime.now());
        customer.setCreatedBy(command.getCreatedBy());
        customer = customerRepository.save(customer);

        // 初始化三钱包
        for (int walletType = 1; walletType <= 3; walletType++) {
            CustomerWallet wallet = new CustomerWallet();
            wallet.setCustomerId(customer.getId());
            wallet.setWalletType(walletType);
            wallet.setBalance(BigDecimal.ZERO);
            wallet.setFrozenBalance(BigDecimal.ZERO);
            wallet.setVersion(0);
            wallet.setCreatedAt(LocalDateTime.now());
            wallet.setUpdatedAt(LocalDateTime.now());
            walletRepository.save(wallet);
        }

        return customer;
    }
}
