package com.agri_system.infrastructure.common;

import com.agri_system.infrastructure.common.error.SystemErrorCode;
import com.agri_system.infrastructure.common.exception.BizException;
import com.agri_system.infrastructure.common.response.R;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExceptionTest {

    @Test
    void testBizExceptionResponse() {
        BizException exception = new BizException(SystemErrorCode.DATA_NOT_FOUND);
        R<Void> response = R.fail(exception.getErrorCode().getCode(), exception.getErrorCode().getMessage());
        assertEquals(SystemErrorCode.DATA_NOT_FOUND.getCode(), response.getCode());
        assertEquals(SystemErrorCode.DATA_NOT_FOUND.getMessage(), response.getMessage());
        assertNotNull(response.getTraceId());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void testValidationMessageFormat() {
        String message = "手机号不能为空; 密码长度不足";
        R<Void> response = R.fail(SystemErrorCode.INVALID_ARGUMENT.getCode(), message);
        assertEquals(SystemErrorCode.INVALID_ARGUMENT.getCode(), response.getCode());
        assertEquals(message, response.getMessage());
        assertNotNull(response.getTraceId());
    }
}






