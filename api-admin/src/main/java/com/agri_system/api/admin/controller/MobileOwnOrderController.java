package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.auth.util.CurrentUser;
import com.agri_system.application.order.dto.vo.MobileOwnOrderVO;
import com.agri_system.application.order.service.MobileOwnOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.agri_system.infrastructure.common.response.R;
import com.agri_system.domain.common.error.SystemErrorCode;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/mobile/orders")
@SaCheckPermission("order:own:query")
@RequiredArgsConstructor
public class MobileOwnOrderController {
    private final MobileOwnOrderService orders;
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> invalidFilter(MethodArgumentTypeMismatchException error) {
        return R.fail(SystemErrorCode.INVALID_ARGUMENT.getCode(),"查询日期、状态或订单编号格式无效");
    }
    @ModelAttribute
    public void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping
    public MobileOwnOrderVO.Orders list(@RequestParam(required=false) Long after,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) Integer state) {
        return orders.list(CurrentUser.staffId(),after,from,to,state);
    }
    @GetMapping("/{orderId}")
    public MobileOwnOrderVO.Detail detail(@PathVariable Long orderId){return orders.detail(CurrentUser.staffId(),orderId);}
}
