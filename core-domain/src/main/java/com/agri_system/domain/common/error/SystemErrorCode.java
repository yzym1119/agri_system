package com.agri_system.domain.common.error;

/**
 * 系统通用错误码：10000-19999
 * 业务模块约定：
 *  - 商品模块：20000-20999
 *  - 库存模块：21000-21999
 *  - 订单模块：30000-30999
 *  - 赊销模块：40000-40999
 *  其他模块以此类推，保持区间不重叠
 */
public enum SystemErrorCode implements IErrorCode {

    SUCCESS(0, "success"),
    INVALID_ARGUMENT(10001, "参数错误"),
    SYSTEM_ERROR(10002, "系统繁忙，请稍后再试"),
    UNAUTHORIZED(10003, "未登录或登录已失效"),
    FORBIDDEN(10004, "无权限访问"),
    DUPLICATE_REQUEST(10005, "重复请求，请勿重复提交"),
    DATA_NOT_FOUND(10006, "数据不存在"),
    DATA_CONFLICT(10007, "数据冲突或状态异常");

    private final int code;
    private final String message;

    SystemErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}






