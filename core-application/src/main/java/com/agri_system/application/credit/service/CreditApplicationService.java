package com.agri_system.application.credit.service;

import com.agri_system.application.credit.dto.CreditAccountVO;
import com.agri_system.application.credit.dto.CreditDebtVO;
import com.agri_system.application.credit.dto.CreditRepaymentVO;
import com.agri_system.application.credit.dto.CreditStatementGenerateCommand;
import com.agri_system.domain.credit.model.CreditAccount;
import com.agri_system.domain.credit.model.CreditRepaymentLog;
import com.agri_system.domain.credit.model.CreditStatement;
import com.agri_system.domain.credit.repository.CreditAccountRepository;
import com.agri_system.domain.credit.repository.CreditRepaymentLogRepository;
import com.agri_system.domain.credit.repository.CreditStatementRepository;
import com.agri_system.domain.credit.service.CreditDomainService;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.order.model.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CreditApplicationService {

    private final CreditAccountRepository accountRepository;
    private final CreditRepaymentLogRepository repaymentLogRepository;
    private final CreditStatementRepository statementRepository;
    private final CreditDomainService creditDomainService;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    public List<CreditAccountVO> listAccounts(Long customerId, Integer status) {
        return accountRepository.listAccounts(customerId, status).stream()
                .map(this::toAccountVO)
                .collect(Collectors.toList());
    }

    public List<CreditRepaymentVO> listRepayments(Long customerId) {
        return repaymentLogRepository.listByCustomer(customerId).stream()
                .map(this::toRepaymentVO)
                .collect(Collectors.toList());
    }

    public com.mybatisflex.core.paginate.Page<CreditAccountVO> listAccountsPage(Long customerId, Integer status, int pageNum, int pageSize) {
        com.mybatisflex.core.paginate.Page<CreditAccount> page = accountRepository.findPage(customerId, status, pageNum, pageSize);
        com.mybatisflex.core.paginate.Page<CreditAccountVO> result = new com.mybatisflex.core.paginate.Page<>();
        List<CreditAccountVO> voList = page.getRecords().stream().map(this::toAccountVO).collect(Collectors.toList());
        result.setRecords(voList);
        result.setTotalRow(page.getTotalRow());
        result.setPageNumber(page.getPageNumber());
        result.setPageSize(page.getPageSize());
        result.setTotalPage(page.getTotalPage());
        return result;
    }

    public CreditDebtVO statistics(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("客户不存在"));
        BigDecimal totalDebt = creditDomainService.calculateTotalDebt(customerId);
        BigDecimal creditLimit = customer.getCreditLimit() == null ? BigDecimal.ZERO : customer.getCreditLimit();
        BigDecimal remaining = creditLimit.subtract(totalDebt);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            remaining = BigDecimal.ZERO;
        }

        CreditDebtVO vo = new CreditDebtVO();
        vo.setTotalDebt(totalDebt);
        vo.setCreditLimit(creditLimit);
        vo.setRemainingCredit(remaining);
        vo.setLatestDebtDate(accountRepository.findLatestDebtDate(customerId));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateCreditLimit(Long customerId, BigDecimal newLimit) {
        if (newLimit == null || newLimit.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("额度必须大于等于0");
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("客户不存在"));
        customer.setCreditLimit(newLimit);
        customerRepository.save(customer);
    }

    @Transactional(rollbackFor = Exception.class)
    public CreditStatement generateStatement(CreditStatementGenerateCommand command) {
        if (command.getStartDate().isAfter(command.getEndDate())) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        List<CreditAccount> accounts = accountRepository.listAccounts(
                command.getCustomerId(), null, command.getStartDate(), command.getEndDate());
        List<CreditRepaymentLog> repayments = repaymentLogRepository.listByCustomer(
                command.getCustomerId(), command.getStartDate(), command.getEndDate());

        BigDecimal totalCredit = accounts.stream()
                .map(CreditAccount::getCreditAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = repayments.stream()
                .map(CreditRepaymentLog::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRemaining = creditDomainService.calculateTotalDebt(command.getCustomerId());

        CreditStatement statement = new CreditStatement();
        statement.setCustomerId(command.getCustomerId());
        statement.setStatementNo(generateStatementNo());
        statement.setStatementDate(LocalDate.now());
        statement.setStartDate(command.getStartDate());
        statement.setEndDate(command.getEndDate());
        statement.setTotalCreditAmount(totalCredit);
        statement.setTotalPaidAmount(totalPaid);
        statement.setTotalRemainingAmount(totalRemaining);
        statement.setStatus(1);
        statement.setCreatedAt(LocalDateTime.now());
        statement.setCreatedBy(command.getCreatedBy());
        statement.setRemark(command.getRemark());
        return statementRepository.save(statement);
    }

    private CreditAccountVO toAccountVO(CreditAccount account) {
        CreditAccountVO vo = new CreditAccountVO();
        vo.setId(account.getId());
        vo.setOrderId(account.getOrderId());
        // 尝试关联查询订单号，若找不到则不设置
        if (account.getOrderId() != null) {
            try {
                orderRepository.findById(account.getOrderId()).ifPresent((Order order) -> {
                    vo.setOrderNo(order.getOrderNo());
                });
            } catch (Exception ex) {
                // 忽略关联查询异常，避免影响主要流程
            }
        }
        vo.setCreditAmount(account.getCreditAmount());
        vo.setPaidAmount(account.getPaidAmount());
        vo.setRemainingAmount(account.getRemainingAmount());
        vo.setDueDate(account.getDueDate());
        vo.setStatus(account.getStatus());
        vo.setRemark(account.getRemark());
        vo.setCreatedAt(account.getCreatedAt());
        return vo;
    }

    private CreditRepaymentVO toRepaymentVO(CreditRepaymentLog log) {
        CreditRepaymentVO vo = new CreditRepaymentVO();
        vo.setId(log.getId());
        vo.setRepaymentNo(log.getRepaymentNo());
        vo.setAmount(log.getAmount());
        vo.setPaymentMethod(log.getPaymentMethod());
        vo.setTransactionId(log.getTransactionId());
        vo.setRemark(log.getRemark());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }

    private String generateStatementNo() {
        return "CST" + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase();
    }

    @Transactional(rollbackFor = Exception.class)
    public void addDebt(Long customerId, Long orderId, BigDecimal amount) {
        creditDomainService.addDebt(customerId, orderId, amount);
    }
}






