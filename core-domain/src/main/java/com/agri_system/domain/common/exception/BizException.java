package com.agri_system.domain.common.exception;

import com.agri_system.domain.common.error.IErrorCode;
import com.agri_system.domain.common.error.SystemErrorCode;

public class BizException extends RuntimeException {

    private final IErrorCode errorCode;

    public BizException(IErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BizException(IErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BizException(String message) {
        super(message);
        this.errorCode = SystemErrorCode.SYSTEM_ERROR;
    }

    public IErrorCode getErrorCode() {
        return errorCode;
    }
}






