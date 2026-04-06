package com.agri_system.domain.common.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 编码生成服务
 * 根据业务类型自动生成唯一编码
 * 
 * @author AI Generated
 */
public class CodeGenerationService {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    
    /**
     * 编码类型枚举
     */
    public enum CodeType {
        /** 门店编码：STORE + 6位序号（如：STORE000001） */
        STORE("STORE", 6),
        /** 员工编码：EMP + 6位序号（如：EMP000001） */
        STAFF("EMP", 6),
        /** 商品编码：PROD + 5位序号（如：PROD00001） */
        PRODUCT("PROD", 5),
        /** 客户编码：CUST + 8位序号（如：CUST00000001） */
        CUSTOMER("CUST", 8),
        /** 供应商编码：SUPP + 6位序号（如：SUPP000001） */
        SUPPLIER("SUPP", 6),
        /** 角色编码：ROLE + 4位序号（如：ROLE0001） */
        ROLE("ROLE", 4),
        /** 分类编码：CAT + 3位序号（如：CAT002） */
        CATEGORY("CAT", 3);
        
        private final String prefix;
        private final int sequenceLength;
        
        CodeType(String prefix, int sequenceLength) {
            this.prefix = prefix;
            this.sequenceLength = sequenceLength;
        }
        
        public String getPrefix() {
            return prefix;
        }
        
        public int getSequenceLength() {
            return sequenceLength;
        }
    }
    
    /**
     * 生成下一个编码
     * 
     * @param codeType 编码类型
     * @param lastCode 最后一个编码（用于获取序号）
     * @return 新的编码
     */
    public static String generateNextCode(CodeType codeType, String lastCode) {
        String prefix = codeType.getPrefix();
        int sequenceLength = codeType.getSequenceLength();
        
        if (lastCode == null || !lastCode.startsWith(prefix)) {
            // 如果没有最后一个编码或格式不匹配，从1开始
            return prefix + String.format("%0" + sequenceLength + "d", 1);
        }
        
        // 提取序号部分
        String sequencePart = lastCode.substring(prefix.length());
        try {
            int sequence = Integer.parseInt(sequencePart);
            sequence++;
            return prefix + String.format("%0" + sequenceLength + "d", sequence);
        } catch (NumberFormatException e) {
            // 如果解析失败，从1开始
            return prefix + String.format("%0" + sequenceLength + "d", 1);
        }
    }
    
    /**
     * 生成带日期的编码（用于需要按日期重置序号的场景）
     * 格式：前缀 + 年月日 + 4位序号（如：PKG202401010001）
     * 
     * @param prefix 编码前缀
     * @param lastCode 最后一个编码
     * @return 新的编码
     */
    public static String generateDateBasedCode(String prefix, String lastCode) {
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        String fullPrefix = prefix + datePart;
        
        if (lastCode == null || !lastCode.startsWith(fullPrefix)) {
            // 如果日期不匹配，从1开始
            return fullPrefix + "0001";
        }
        
        // 提取序号部分
        String sequencePart = lastCode.substring(fullPrefix.length());
        try {
            int sequence = Integer.parseInt(sequencePart);
            sequence++;
            return fullPrefix + String.format("%04d", sequence);
        } catch (NumberFormatException e) {
            return fullPrefix + "0001";
        }
    }
}





