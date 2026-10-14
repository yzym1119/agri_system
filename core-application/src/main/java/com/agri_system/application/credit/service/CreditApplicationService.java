package com.agri_system.application.credit.service;

import com.agri_system.application.auth.util.CurrentUser;
import com.agri_system.application.credit.dto.*;
import com.agri_system.domain.credit.model.*;
import com.agri_system.domain.credit.repository.*;
import com.agri_system.domain.credit.service.CreditDomainService;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.after_sales.model.AfterSales;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.mybatisflex.core.paginate.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CreditApplicationService {
    private final CreditAccountRepository accountRepository;
    private final CreditRepaymentLogRepository repaymentLogRepository;
    private final CreditStatementRepository statementRepository;
    private final CreditDomainService creditDomainService;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final AfterSalesRepository afterSalesRepository;
    private final CreditAccessService accessService;

    public List<CreditAccountVO> listAccounts(Long customerId, Integer status) {
        Long storeId = accessService.resolve(CurrentUser.staffId(), null).storeId();
        return accountRepository.listAccounts(customerId,status,storeId,null,null).stream().map(this::toAccountVO).toList();
    }
    public Page<CreditAccountVO> listAccountsPage(Long customerId,Integer status,int pageNum,int pageSize) {
        return listAccountsPage(customerId,status,null,pageNum,pageSize,CurrentUser.staffId());
    }
    public Page<CreditAccountVO> listAccountsPage(Long customerId,Integer status,Long requestedStoreId,int pageNum,int pageSize,Long operatorId) {
        Long storeId = accessService.resolve(operatorId,requestedStoreId).storeId();
        if(pageNum<1 || pageSize<1 || pageSize>200) throw new IllegalArgumentException("分页参数无效");
        Page<CreditAccount> page=accountRepository.findPage(customerId,status,storeId,pageNum,pageSize);
        Page<CreditAccountVO> result=new Page<>(); result.setRecords(page.getRecords().stream().map(this::toAccountVO).toList());
        result.setTotalRow(page.getTotalRow());result.setPageNumber(page.getPageNumber());result.setPageSize(page.getPageSize());result.setTotalPage(page.getTotalPage());return result;
    }
    public List<CreditRepaymentVO> listRepayments(Long customerId) { return listRepayments(customerId,null,CurrentUser.staffId()); }
    public List<CreditRepaymentVO> listRepayments(Long customerId,Long requestedStoreId,Long operatorId) {
        Long storeId=accessService.resolve(operatorId,requestedStoreId).storeId();
        Map<String,List<CreditRepaymentLog>> batches=new LinkedHashMap<>();
        for(CreditRepaymentLog log:repaymentLogRepository.listByCustomer(customerId,storeId,null,null)) {
            String key=log.getCreditAccountId()!=null && log.getRepaymentNo()!=null && !log.getRepaymentNo().isBlank()
                    ? "BATCH:"+log.getRepaymentNo() : "LEGACY:"+log.getId();
            batches.computeIfAbsent(key,k->new ArrayList<>()).add(log);
        }
        return batches.values().stream().map(logs -> enrichBatch(CreditRepaymentService.batchVO(logs))).toList();
    }
    public CreditDebtVO statistics(Long customerId) { return statistics(customerId,null,CurrentUser.staffId()); }
    public CreditDebtVO statistics(Long customerId,Long requestedStoreId,Long operatorId) {
        CreditAccessService.Scope scope=accessService.resolve(operatorId,requestedStoreId);
        Customer customer=customerRepository.findById(customerId).orElseThrow(()->new IllegalArgumentException("客户不存在"));
        BigDecimal debt=zero(accountRepository.sumRemainingAmount(customerId,scope.storeId()));
        BigDecimal limit=zero(customer.getCreditLimit());
        BigDecimal globalDebt=zero(accountRepository.sumRemainingAmountAllStores(customerId));
        CreditDebtVO vo=new CreditDebtVO();vo.setStoreId(scope.storeId());vo.setTotalDebt(debt);vo.setCreditLimit(limit);
        vo.setRemainingCredit(limit.subtract(globalDebt).max(BigDecimal.ZERO));vo.setLatestDebtDate(accountRepository.findLatestDebtDate(customerId,scope.storeId()));return vo;
    }
    @Transactional(rollbackFor=Exception.class)
    public void updateCreditLimit(Long customerId,BigDecimal newLimit) { updateCreditLimit(customerId,newLimit,CurrentUser.staffId()); }
    @Transactional(rollbackFor=Exception.class)
    public void updateCreditLimit(Long customerId,BigDecimal newLimit,Long operatorId) {
        accessService.resolve(operatorId,null);
        if(newLimit==null || newLimit.signum()<0 || newLimit.scale()>2) throw new IllegalArgumentException("额度必须为精确到分的非负金额");
        Customer customer=customerRepository.findByIdWithLock(customerId).orElseThrow(()->new IllegalArgumentException("客户不存在"));
        customer.setCreditLimit(newLimit);customerRepository.save(customer);
    }
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public CreditStatement generateStatement(CreditStatementGenerateCommand command) { return generateStatement(command,CurrentUser.staffId()); }
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public CreditStatement generateStatement(CreditStatementGenerateCommand command,Long operatorId) {
        CreditAccessService.Scope scope=accessService.resolve(operatorId,command.getStoreId());
        if(command.getCreatedBy()!=null && !command.getCreatedBy().equals(operatorId)) throw new IllegalArgumentException("不能伪造对账操作员工");
        if(command.getStartDate()==null || command.getEndDate()==null || command.getStartDate().isAfter(command.getEndDate())) throw new IllegalArgumentException("对账日期无效");
        customerRepository.findByIdWithLock(command.getCustomerId()).orElseThrow(()->new IllegalArgumentException("客户不存在"));
        LocalDateTime start = command.getStartDate().atStartOfDay();
        LocalDateTime end = command.getEndDate().plusDays(1).atStartOfDay();
        List<CreditAccount> accounts = accountRepository.listAccounts(command.getCustomerId(), null, scope.storeId(), null, null);
        List<CreditRepaymentLog> repayments = repaymentLogRepository.listByCustomer(command.getCustomerId(), scope.storeId(), null, null);
        Map<Long, CreditAccount> byId = new HashMap<>();
        Map<Long, BigDecimal> sourcePaid = new HashMap<>();
        PeriodAmount credit = new PeriodAmount();
        PeriodAmount paid = new PeriodAmount();
        PeriodAmount reduction = new PeriodAmount();
        PeriodAmount cashRefund = new PeriodAmount();
        for (CreditAccount account : accounts) {
            if (!Objects.equals(account.getCustomerId(), command.getCustomerId()) || !Objects.equals(account.getStoreId(), scope.storeId())
                    || account.getId() == null || byId.putIfAbsent(account.getId(), account) != null) {
                throw new IllegalArgumentException("对账账户归属不正确或重复");
            }
            requireStatementMoney(account.getCreditAmount(), "原赊销额");
            requireStatementMoney(account.getPaidAmount(), "原累计实还");
            requireStatementMoney(account.getRemainingAmount(), "原剩余欠款");
            credit.add(account.getCreatedAt(), account.getCreditAmount(), start, end);
        }
        for (CreditRepaymentLog repayment : repayments) {
            CreditAccount account = byId.get(repayment.getCreditAccountId());
            if (account == null || !Objects.equals(repayment.getCustomerId(), command.getCustomerId())
                    || !Objects.equals(repayment.getStoreId(), scope.storeId())) {
                throw new IllegalArgumentException("历史还款缺少本店原单分配，不能生成准确对账单");
            }
            requireStatementMoney(repayment.getAmount(), "还款来源");
            requireStatementMoney(repayment.getRefundedAmount(), "来源累计已退");
            if (repayment.getRefundedAmount().compareTo(repayment.getAmount()) > 0) {
                throw new IllegalArgumentException("历史来源已退金额超出原收款，请先核对");
            }
            if (repayment.getCreatedAt() == null || repayment.getCreatedAt().isBefore(account.getCreatedAt())) {
                throw new IllegalArgumentException("历史还款时间无法归属原销售单，请先核对");
            }
            sourcePaid.merge(account.getId(), repayment.getAmount(), BigDecimal::add);
            paid.add(repayment.getCreatedAt(), repayment.getAmount(), start, end);
        }
        for (CreditAccount account : accounts) {
            BigDecimal actualPaid = sourcePaid.getOrDefault(account.getId(), BigDecimal.ZERO);
            if (actualPaid.compareTo(account.getPaidAmount()) != 0) {
                throw new IllegalArgumentException("历史实还缺少对应原单来源，不能生成准确对账单");
            }
            BigDecimal allReduction = BigDecimal.ZERO;
            for (AfterSales history : afterSalesRepository.findByOrderId(account.getOrderId())) {
                if (!Integer.valueOf(AfterSales.STATUS_FINISHED).equals(history.getStatus())) continue;
                requireStatementMoney(history.getRefundAmount(), "历史成交冲销");
                requireStatementMoney(history.getCreditReductionAmount(), "历史冲欠款");
                requireStatementMoney(history.getCashRefundAmount(), "历史退已收款");
                if (history.getCreditReductionAmount().add(history.getCashRefundAmount()).compareTo(history.getRefundAmount()) != 0) {
                    throw new IllegalArgumentException("历史退货资金拆分不守恒，请先核对");
                }
                allReduction = allReduction.add(history.getCreditReductionAmount());
                // 零金额物料没有资金跨期变化；有金额退款只能用真实执行时间，不能猜创建/更新时间。
                if (history.getRefundAmount().signum() == 0) continue;
                if (history.getRefundTime() == null || history.getRefundTime().isBefore(account.getCreatedAt())) {
                    throw new IllegalArgumentException("历史退货缺少有效资金执行时间，不能生成准确对账单");
                }
                reduction.add(history.getRefundTime(), history.getCreditReductionAmount(), start, end);
                cashRefund.add(history.getRefundTime(), history.getCashRefundAmount(), start, end);
            }
            if (account.getCreditAmount().subtract(actualPaid).subtract(allReduction).compareTo(account.getRemainingAmount()) != 0) {
                throw new IllegalArgumentException("当前原单应收、实还、冲欠款与余额不守恒，请先核对");
            }
        }
        BigDecimal opening = credit.before.subtract(paid.before).subtract(reduction.before);
        BigDecimal closing = credit.throughEnd().subtract(paid.throughEnd()).subtract(reduction.throughEnd());
        if (opening.signum() < 0 || closing.signum() < 0
                || opening.add(credit.inPeriod).subtract(paid.inPeriod).subtract(reduction.inPeriod).compareTo(closing) != 0) {
            throw new IllegalArgumentException("期间应收余额不守恒，请先核对原单资金时间");
        }
        CreditStatement statement = new CreditStatement();
        statement.setCustomerId(command.getCustomerId());
        statement.setStatementNo("CST" + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase(Locale.ROOT));
        statement.setStatementDate(LocalDate.now());
        statement.setStartDate(command.getStartDate());
        statement.setEndDate(command.getEndDate());
        statement.setTotalCreditAmount(credit.inPeriod);
        statement.setTotalPaidAmount(paid.inPeriod);
        statement.setTotalRemainingAmount(closing);
        statement.setStatus(1);
        statement.setCreatedAt(LocalDateTime.now());
        statement.setCreatedBy(operatorId);
        String remark = "门店=" + scope.storeId() + "；期初欠款=" + opening.setScale(2).toPlainString()
                + "；本期冲欠款=" + reduction.inPeriod.setScale(2).toPlainString()
                + "；本期退已收款=" + cashRefund.inPeriod.setScale(2).toPlainString()
                + "；期末截至=" + command.getEndDate() + "当日结束（不含次日）"
                + "；期初+本期挂账-本期实还-本期冲欠款=期末；" + Objects.toString(command.getRemark(), "");
        if (remark.length() > 500) throw new IllegalArgumentException("对账说明和备注合计不能超过500个字符");
        statement.setRemark(remark);
        return statementRepository.save(statement);
    }
    private static void requireStatementMoney(BigDecimal amount, String label) {
        if (amount == null || amount.signum() < 0 || amount.scale() > 2) {
            throw new IllegalArgumentException(label + "尚未核对或无效，不能生成准确对账单");
        }
    }
    /** 期间款项同时保存独立的期初和本期累计，用于核对选定期末，而非读取当前余额。 */
    private static final class PeriodAmount {
        private BigDecimal before = BigDecimal.ZERO;
        private BigDecimal inPeriod = BigDecimal.ZERO;
        void add(LocalDateTime time, BigDecimal amount, LocalDateTime start, LocalDateTime end) {
            if (time == null) throw new IllegalArgumentException("历史账款缺少实际发生时间，不能生成准确对账单");
            if (time.isBefore(start)) before = before.add(amount);
            else if (time.isBefore(end)) inPeriod = inPeriod.add(amount);
        }
        BigDecimal throughEnd() { return before.add(inPeriod); }
    }
    private CreditAccountVO toAccountVO(CreditAccount account) {
        CreditAccountVO vo=new CreditAccountVO();vo.setId(account.getId());vo.setCustomerId(account.getCustomerId());vo.setStoreId(account.getStoreId());
        customerRepository.findByIdIncludingDeleted(account.getCustomerId()).ifPresent(c->{vo.setCustomerName(c.getName());vo.setCustomerPhone(c.getPhone());});
        vo.setOrderId(account.getOrderId());orderRepository.findById(account.getOrderId()).ifPresent(o->vo.setOrderNo(o.getOrderNo()));
        vo.setCreditAmount(account.getCreditAmount());vo.setPaidAmount(account.getPaidAmount());vo.setRemainingAmount(account.getRemainingAmount());
        vo.setDueDate(account.getDueDate());vo.setStatus(account.getStatus());vo.setRemark(account.getRemark());vo.setCreatedAt(account.getCreatedAt());
        List<AfterSales> history=afterSalesRepository.findByOrderId(account.getOrderId()).stream().filter(a->Integer.valueOf(AfterSales.STATUS_FINISHED).equals(a.getStatus())).toList();
        vo.setCreditReductionAmount(history.stream().anyMatch(a->a.getCreditReductionAmount()==null) ? null : history.stream().map(AfterSales::getCreditReductionAmount).reduce(BigDecimal.ZERO,BigDecimal::add));
        List<CreditRepaymentLog> repayments=repaymentLogRepository.listByCustomer(account.getCustomerId(),account.getStoreId(),null,null).stream().filter(l->account.getId().equals(l.getCreditAccountId())).toList();
        BigDecimal allocatedPaid=repayments.stream().map(CreditRepaymentLog::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        vo.setRefundedRepaymentAmount(allocatedPaid.compareTo(zero(account.getPaidAmount()))!=0 || repayments.stream().anyMatch(l->l.getRefundedAmount()==null)
                ? null : repayments.stream().map(CreditRepaymentLog::getRefundedAmount).reduce(BigDecimal.ZERO,BigDecimal::add));
        return vo;
    }
    private CreditRepaymentVO enrichBatch(CreditRepaymentVO vo) {
        for(CreditRepaymentVO.Allocation allocation:vo.getAllocations()) {
            if(allocation.getCreditAccountId()==null) continue;
            accountRepository.findById(allocation.getCreditAccountId()).ifPresent(a->{allocation.setOrderId(a.getOrderId());orderRepository.findById(a.getOrderId()).ifPresent(o->allocation.setOrderNo(o.getOrderNo()));});
        }
        return vo;
    }
    private static BigDecimal zero(BigDecimal amount) { return amount==null ? BigDecimal.ZERO : amount; }
    @Transactional(rollbackFor=Exception.class)
    public void addDebt(Long customerId,Long orderId,BigDecimal amount) {
        Order order=orderRepository.findById(orderId).orElseThrow(()->new IllegalArgumentException("赊销原销售单不存在"));
        if(!Objects.equals(order.getCustomerId(),customerId)) throw new IllegalArgumentException("赊销客户与原销售单不一致");
        customerRepository.findByIdWithLock(customerId).orElseThrow(()->new IllegalArgumentException("客户不存在"));
        creditDomainService.addDebt(customerId,orderId,amount,order.getStoreId());
    }
}
