package com.agri_system.application.credit.service;

import com.agri_system.application.credit.dto.CreditRepayCommand;
import com.agri_system.application.credit.dto.CreditRepaymentVO;
import com.agri_system.domain.credit.model.CreditAccount;
import com.agri_system.domain.credit.model.CreditRepaymentLog;
import com.agri_system.domain.credit.repository.CreditAccountRepository;
import com.agri_system.domain.credit.repository.CreditRepaymentLogRepository;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.after_sales.model.AfterSales;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** 一次真实收款按FIFO分配到原欠款单；批次请求防重与各来源可退款金额一起保存。 */
@Service
@RequiredArgsConstructor
public class CreditRepaymentService {
    private final CreditAccountRepository creditAccountRepository;
    private final CreditRepaymentLogRepository repaymentLogRepository;
    private final CustomerRepository customerRepository;
    private final AfterSalesRepository afterSalesRepository;
    private final CreditAccessService accessService;
    private final OrderRepository orderRepository;

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public CreditRepaymentVO repay(CreditRepayCommand command, Long operatorId) {
        validate(command, operatorId);
        CreditAccessService.Scope scope = accessService.resolve(operatorId, command.getStoreId());
        Customer customer = customerRepository.findByIdWithLock(command.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("客户不存在"));
        if (!Integer.valueOf(1).equals(customer.getStatus())) throw new IllegalArgumentException("客户已停用");
        List<CreditRepaymentLog> heads = repaymentLogRepository.findByRequestKey(scope.storeId(), command.getRequestKey());
        if (!heads.isEmpty()) {
            if (heads.size() != 1 || !Objects.equals(heads.get(0).getCustomerId(), customer.getId())
                    || !Objects.equals(heads.get(0).getStoreId(), scope.storeId())) {
                throw new IllegalArgumentException("请求标识已用于另一笔还款，不能更改原收款参数");
            }
            List<CreditRepaymentLog> old = repaymentLogRepository.findByRepaymentNoForUpdate(scope.storeId(), heads.get(0).getRepaymentNo());
            if (old.isEmpty() || old.stream().anyMatch(log -> !Objects.equals(log.getStoreId(), scope.storeId())
                    || !Objects.equals(log.getRepaymentNo(), heads.get(0).getRepaymentNo()))) {
                throw new IllegalArgumentException("原还款批次来源不完整，请先核对台账");
            }
            verifyRetry(command, old);
            return enrichBatch(batchVO(old));
        }
        List<CreditAccount> accounts = creditAccountRepository.findOutstandingForUpdate(customer.getId(), scope.storeId());
        BigDecimal outstanding = accounts.stream().map(CreditAccount::getRemainingAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (outstanding.signum() <= 0) throw new IllegalArgumentException("该客户在本门店无欠款");
        if (command.getAmount().compareTo(outstanding) > 0) throw new IllegalArgumentException("还款金额超过本门店剩余欠款");
        List<Allocation> planned = new ArrayList<>();
        BigDecimal remaining = command.getAmount();
        for (CreditAccount account : accounts) {
            if (remaining.signum() <= 0) break;
            verifyAccount(account, customer.getId(), scope.storeId());
            BigDecimal amount = account.getRemainingAmount().min(remaining);
            planned.add(new Allocation(account, amount)); remaining = remaining.subtract(amount);
        }
        if (remaining.signum() != 0) throw new IllegalArgumentException("还款金额未全部核销，请检查台账");
        String no = "CRP" + UUID.randomUUID().toString().replace("-", "").substring(0,20).toUpperCase(Locale.ROOT);
        LocalDateTime now = LocalDateTime.now();
        List<CreditRepaymentLog> logs = new ArrayList<>();
        for (int index=0; index<planned.size(); index++) {
            Allocation allocation = planned.get(index);
            CreditAccount account = allocation.account();
            CreditRepaymentLog log = new CreditRepaymentLog();
            // 只有首条来源保存请求身份；其余来源用还款号归属于同一真实收款批次。
            log.setRepaymentNo(no); log.setRequestKey(index==0 ? command.getRequestKey() : null); log.setCreditAccountId(account.getId());
            log.setCustomerId(customer.getId()); log.setStoreId(scope.storeId()); log.setAmount(allocation.amount());
            log.setRefundedAmount(BigDecimal.ZERO); log.setPaymentMethod(command.getPaymentMethod());
            log.setTransactionId(command.getTransactionId()); log.setRemark(command.getRemark()); log.setCreatedBy(operatorId); log.setCreatedAt(now);
            logs.add(log);
        }
        // 先由唯一约束争用批次身份。失败必须退出并回滚，不能先改任何账户余额。
        try {
            repaymentLogRepository.save(logs.get(0));
        } catch (DuplicateKeyException conflict) {
            throw new IllegalArgumentException("请求标识已用于或正在处理另一笔还款，请核对原收款后重试", conflict);
        }
        for (int index=0; index<planned.size(); index++) {
            Allocation allocation=planned.get(index);
            CreditAccount account=allocation.account();
            account.applyRepayment(allocation.amount()); account.setUpdatedAt(now);
            creditAccountRepository.save(account);
            if (index>0) repaymentLogRepository.save(logs.get(index));
        }
        return enrichBatch(batchVO(logs));
    }

    private void validate(CreditRepayCommand command, Long operatorId) {
        if (command == null || command.getCustomerId() == null) throw new IllegalArgumentException("客户ID不能为空");
        BigDecimal amount = command.getAmount();
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || amount.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new IllegalArgumentException("还款金额必须为精确到分的正金额");
        }
        if (command.getPaymentMethod() == null || command.getPaymentMethod() < 1 || command.getPaymentMethod() > 4) throw new IllegalArgumentException("还款仅支持现金、微信、支付宝或银行转账登记");
        if (command.getRequestKey() == null || command.getRequestKey().isBlank() || command.getRequestKey().length() > 64
                || !command.getRequestKey().matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("还款请求标识无效");
        if (operatorId == null || command.getCreatedBy() != null && !command.getCreatedBy().equals(operatorId)) throw new IllegalArgumentException("不能伪造还款操作员工");
    }
    private void verifyRetry(CreditRepayCommand command, List<CreditRepaymentLog> old) {
        BigDecimal sum = old.stream().map(CreditRepaymentLog::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean changed = sum.compareTo(command.getAmount()) != 0 || old.stream().anyMatch(log ->
                !Objects.equals(log.getCustomerId(), command.getCustomerId()) || !Objects.equals(log.getPaymentMethod(), command.getPaymentMethod())
                || !Objects.equals(log.getTransactionId(), command.getTransactionId()));
        if (changed) throw new IllegalArgumentException("请求标识已用于另一笔还款，不能更改原收款参数");
    }
    private void verifyAccount(CreditAccount account, Long customerId, Long storeId) {
        if (!Objects.equals(account.getCustomerId(), customerId) || !Objects.equals(account.getStoreId(), storeId)) throw new IllegalArgumentException("还款账户归属不正确");
        BigDecimal reduced = BigDecimal.ZERO;
        for (AfterSales history : afterSalesRepository.findByOrderId(account.getOrderId())) {
            if (!Integer.valueOf(AfterSales.STATUS_FINISHED).equals(history.getStatus())) continue;
            if (history.getCreditReductionAmount() == null || history.getCashRefundAmount() == null || history.getRefundAmount() == null
                    || history.getCreditReductionAmount().signum() < 0 || history.getCashRefundAmount().signum() < 0
                    || history.getCreditReductionAmount().add(history.getCashRefundAmount()).compareTo(history.getRefundAmount()) != 0) {
                throw new IllegalArgumentException("原销售单存在未核对的历史退货资金，请先核对再收还款");
            }
            reduced = reduced.add(history.getCreditReductionAmount());
        }
        if (account.getCreditAmount() == null || account.getPaidAmount() == null || account.getRemainingAmount() == null
                || account.getPaidAmount().signum() < 0 || account.getRemainingAmount().signum() <= 0
                || account.getCreditAmount().subtract(account.getPaidAmount()).subtract(reduced).compareTo(account.getRemainingAmount()) != 0) {
            throw new IllegalArgumentException("原单应收、实还、冲欠款与余额不守恒，请先核对台账");
        }
        BigDecimal allocatedPaid = BigDecimal.ZERO;
        for (CreditRepaymentLog source : repaymentLogRepository.findByCreditAccountIdForUpdate(account.getId())) {
            if (!Objects.equals(source.getCreditAccountId(), account.getId()) || !Objects.equals(source.getCustomerId(), customerId)
                    || !Objects.equals(source.getStoreId(), storeId) || source.getAmount() == null || source.getAmount().signum() <= 0
                    || source.getRefundedAmount() == null || source.getRefundedAmount().signum() < 0
                    || source.getRefundedAmount().compareTo(source.getAmount()) > 0) {
                throw new IllegalArgumentException("原单历史还款来源尚未核对，请先核对再收还款");
            }
            allocatedPaid = allocatedPaid.add(source.getAmount());
        }
        if (allocatedPaid.compareTo(account.getPaidAmount()) != 0) {
            throw new IllegalArgumentException("原单累计实还缺少对应分配来源，请先核对再收还款");
        }
    }
    public static CreditRepaymentVO batchVO(List<CreditRepaymentLog> logs) {
        if (logs == null || logs.isEmpty()) throw new IllegalArgumentException("还款批次不能为空");
        logs = logs.stream().sorted(Comparator.comparing(CreditRepaymentLog::getId, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        CreditRepaymentLog first = logs.get(0);
        CreditRepaymentVO vo = new CreditRepaymentVO(); vo.setId(first.getId()); vo.setRepaymentNo(first.getRepaymentNo());
        vo.setRequestKey(first.getRequestKey()); vo.setCustomerId(first.getCustomerId()); vo.setStoreId(first.getStoreId());
        vo.setPaymentMethod(first.getPaymentMethod()); vo.setTransactionId(first.getTransactionId()); vo.setRemark(first.getRemark()); vo.setCreatedAt(first.getCreatedAt());
        vo.setAmount(logs.stream().map(CreditRepaymentLog::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        vo.setRefundedAmount(logs.stream().anyMatch(l -> l.getRefundedAmount() == null) ? null : logs.stream().map(CreditRepaymentLog::getRefundedAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        List<CreditRepaymentVO.Allocation> allocations = new ArrayList<>();
        for (CreditRepaymentLog log : logs) {
            CreditRepaymentVO.Allocation line = new CreditRepaymentVO.Allocation(); line.setId(log.getId()); line.setCreditAccountId(log.getCreditAccountId());
            line.setAmount(log.getAmount()); line.setRefundedAmount(log.getRefundedAmount()); allocations.add(line);
        }
        vo.setAllocations(allocations); return vo;
    }
    private CreditRepaymentVO enrichBatch(CreditRepaymentVO vo) {
        for (CreditRepaymentVO.Allocation line : vo.getAllocations()) {
            if (line.getCreditAccountId() == null) continue;
            creditAccountRepository.findById(line.getCreditAccountId()).ifPresent(account -> {
                line.setOrderId(account.getOrderId());
                orderRepository.findById(account.getOrderId()).ifPresent(order -> line.setOrderNo(order.getOrderNo()));
            });
        }
        return vo;
    }
    private record Allocation(CreditAccount account, BigDecimal amount) {}
}
