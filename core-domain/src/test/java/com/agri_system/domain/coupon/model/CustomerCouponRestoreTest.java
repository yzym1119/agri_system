package com.agri_system.domain.coupon.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 优惠券退款回退规则 —— CustomerCoupon.restore() 的状态/过期判定。
 * 业务规则：仅全额退款才退回券，且已过期则不退回。
 */
@DisplayName("优惠券退款回退规则 restore()")
class CustomerCouponRestoreTest {

    private CustomerCoupon usedCoupon(LocalDateTime endTime) {
        CustomerCoupon c = new CustomerCoupon();
        c.setStatus(2); // 已使用
        c.setUsedOrderId(10L);
        c.setUsedTime(LocalDateTime.now().minusDays(1));
        c.setEndTime(endTime);
        return c;
    }

    @Test
    @DisplayName("已使用且未过期：恢复为未使用，清空使用痕迹")
    void usedAndNotExpiredRestores() {
        CustomerCoupon c = usedCoupon(LocalDateTime.now().plusDays(7));
        assertTrue(c.restore());
        assertEquals(1, c.getStatus());
        assertNull(c.getUsedOrderId());
        assertNull(c.getUsedTime());
    }

    @Test
    @DisplayName("已使用但已过期：不恢复（状态不变），返回 false")
    void usedButExpiredNotRestored() {
        CustomerCoupon c = usedCoupon(LocalDateTime.now().minusDays(1));
        assertFalse(c.restore());
        assertEquals(2, c.getStatus());
        assertEquals(10L, c.getUsedOrderId());
    }

    @Test
    @DisplayName("已使用且 endTime 为空（无过期限制）：视为永不过期，可恢复")
    void usedNullEndTimeRestores() {
        CustomerCoupon c = usedCoupon(null);
        assertTrue(c.restore());
        assertEquals(1, c.getStatus());
    }

    @Test
    @DisplayName("非已使用状态（未使用/已作废）：不恢复，返回 false")
    void nonUsedStatusNotRestored() {
        CustomerCoupon c = usedCoupon(LocalDateTime.now().plusDays(7));
        c.setStatus(1); // 未使用
        assertFalse(c.restore());
        c.setStatus(4); // 已作废
        assertFalse(c.restore());
    }

    @Test
    @DisplayName("use() 核销后 restore() 可逆恢复：与核销对称")
    void useThenRestoreRoundTrip() {
        CustomerCoupon c = new CustomerCoupon();
        c.setStatus(1);
        c.setAmount(new BigDecimal("10"));
        c.setType(1); // 满减券
        BigDecimal deduct = c.use(99L, new BigDecimal("100"));
        assertEquals(new BigDecimal("10"), deduct);
        assertEquals(2, c.getStatus());
        assertTrue(c.restore());
        assertEquals(1, c.getStatus());
        assertNull(c.getUsedOrderId());
    }
}
