package com.agri_system.application.product.service;

import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.application.product.dto.command.ProductUnitUpsertCommand;
import com.agri_system.application.product.dto.vo.ProductUnitVO;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.model.ProductUnit;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.product.repository.ProductUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** SKU 多单位校验、直接到基准单位换算及 legacy 主辅单位适配。 */
@Service
@RequiredArgsConstructor
public class ProductUnitService {
    private static final String LEGACY_UNCONFIRMED = "LEGACY_UNCONFIRMED";
    private static final String NEW_CHAIN_ENABLED = "NEW_CHAIN_ENABLED";
    private static final String NEW_CHAIN_NO_ENABLED_UNITS = "NEW_CHAIN_NO_ENABLED_UNITS";
    private static final String LEGACY = "LEGACY";

    private final ProductUnitRepository unitRepository;
    private final ProductRepository productRepository;

    public enum UnitOperation {
        PURCHASE, SALE, INVENTORY, TRANSFER
    }

    /** 返回启用单位；没有新模型时返回 legacy 的只读内存投影。 */
    public List<ProductUnit> getUnits(Long productId) {
        List<ProductUnit> enabled = unitRepository.findEnabledByProductId(productId);
        if (enabled != null && !enabled.isEmpty()) {
            return enabled;
        }
        List<ProductUnit> persisted = unitRepository.findByProductId(productId);
        if (persisted != null && !persisted.isEmpty()) {
            throw new IllegalStateException("无启用单位: 商品 " + productId);
        }
        return projectLegacyUnits(productId);
    }

    /** 管理端读取完整持久化集合，包含停用单位；无新记录时返回 legacy 投影。 */
    public List<ProductUnit> getManagementUnits(Long productId) {
        List<ProductUnit> persisted = unitRepository.findByProductId(productId);
        if (persisted != null && !persisted.isEmpty()) {
            return persisted;
        }
        return projectLegacyUnits(productId);
    }

    public List<ProductUnitVO> getUnitVOs(Long productId) {
        return getManagementUnits(productId).stream().map(this::toVO).collect(Collectors.toList());
    }

    public String getUnitModelStatus(Long productId) {
        List<ProductUnit> persisted = unitRepository.findByProductId(productId);
        if (persisted == null || persisted.isEmpty()) {
            return LEGACY;
        }
        boolean hasEnabled = persisted.stream().anyMatch(ProductUnit::isEnabled);
        return hasEnabled ? NEW_CHAIN_ENABLED : NEW_CHAIN_NO_ENABLED_UNITS;
    }

    public ProductUnit requireEnabledUnit(Long productId, String unitName, UnitOperation operation) {
        if (unitName == null || unitName.trim().isEmpty()) {
            throw new IllegalArgumentException("单位名称不能为空");
        }
        ProductUnit unit = getUnits(productId).stream()
                .filter(candidate -> unitName.trim().equals(candidate.getUnitName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("单位不存在或未启用: " + unitName));
        if (!unit.isEnabled()) {
            throw new IllegalArgumentException("单位未启用: " + unitName);
        }
        boolean allowed = switch (Objects.requireNonNull(operation, "operation")) {
            case PURCHASE -> Integer.valueOf(1).equals(unit.getAllowPurchase());
            case SALE -> Integer.valueOf(1).equals(unit.getAllowSale());
            case INVENTORY -> Integer.valueOf(1).equals(unit.getAllowInventory());
            case TRANSFER -> Integer.valueOf(1).equals(unit.getAllowTransfer());
        };
        if (!allowed) {
            throw new IllegalArgumentException("单位不允许" + operationLabel(operation) + ": " + unitName);
        }
        return unit;
    }

    public UnitConversionSnapshot toBaseQuantity(Long productId, String unitName, BigDecimal originalQuantity) {
        if (originalQuantity == null || originalQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("数量必须大于0");
        }
        ProductUnit unit = getUnits(productId).stream()
                .filter(candidate -> unitName != null && unitName.trim().equals(candidate.getUnitName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("单位不存在或未启用: " + unitName));
        if (!unit.isEnabled()) {
            throw new IllegalArgumentException("单位未启用: " + unitName);
        }
        BigDecimal coefficient = unit.getBaseQuantityPerUnit();
        if (coefficient == null || coefficient.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("单位基准数量系数必须大于0");
        }
        BigDecimal baseQuantity = originalQuantity.multiply(coefficient).setScale(8, RoundingMode.HALF_UP);
        return new UnitConversionSnapshot(unit.getUnitName(), originalQuantity, coefficient, baseQuantity);
    }

    /** 完整替换单位集合。所有校验完成后才执行停用、更新和插入。 */
    @Transactional(rollbackFor = Exception.class)
    public void replaceUnits(Long productId, List<ProductUnitUpsertCommand> commands) {
        productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("商品不存在: " + productId));
        if (commands == null || commands.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个单位");
        }

        List<ProductUnit> existing = unitRepository.findByProductId(productId);
        if (existing == null) {
            existing = List.of();
        }
        Map<Long, ProductUnit> existingById = existing.stream()
                .filter(unit -> unit.getId() != null)
                .collect(Collectors.toMap(ProductUnit::getId, unit -> unit, (a, b) -> a, LinkedHashMap::new));
        Map<String, ProductUnit> existingByName = existing.stream()
                .filter(unit -> unit.getUnitName() != null)
                .collect(Collectors.toMap(unit -> unit.getUnitName().trim().toLowerCase(Locale.ROOT), unit -> unit,
                        (a, b) -> a, LinkedHashMap::new));
        Set<Long> requestIds = new HashSet<>();
        Set<String> names = new HashSet<>();
        int enabledBaseCount = 0;
        for (ProductUnitUpsertCommand command : commands) {
            validateCommand(command);
            String name = command.getUnitName().trim();
            if (command.getId() != null) {
                if (!requestIds.add(command.getId())) {
                    throw new IllegalArgumentException("单位ID重复: " + command.getId());
                }
                ProductUnit existingUnit = existingById.get(command.getId());
                if (existingUnit == null) {
                    ProductUnit referenced = unitRepository.findById(command.getId()).orElse(null);
                    if (referenced != null && !productId.equals(referenced.getProductId())) {
                        throw new IllegalArgumentException("单位ID不属于当前商品: " + command.getId());
                    }
                    throw new IllegalArgumentException("单位ID不存在: " + command.getId());
                }
                if (!productId.equals(existingUnit.getProductId())) {
                    throw new IllegalArgumentException("单位ID不属于当前商品: " + command.getId());
                }
            } else {
                ProductUnit sameName = existingByName.get(name.toLowerCase(Locale.ROOT));
                if (sameName != null) {
                    // 允许客户端省略已停用行的 ID，以名称原位重启用，保持事实外键稳定。
                    if (sameName.isEnabled() && !requestIds.contains(sameName.getId())) {
                        throw new IllegalArgumentException("单位名称已存在: " + name);
                    }
                    command.setId(sameName.getId());
                    requestIds.add(sameName.getId());
                }
            }
            ProductUnit sameName = existingByName.get(name.toLowerCase(Locale.ROOT));
            if (sameName != null && !Objects.equals(sameName.getId(), command.getId())) {
                throw new IllegalArgumentException("单位名称已存在: " + name);
            }
            if (!names.add(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("单位名称重复: " + name);
            }
            boolean enabled = Integer.valueOf(1).equals(command.getStatus());
            if (Integer.valueOf(1).equals(command.getIsBaseUnit())) {
                if (command.getBaseQuantityPerUnit().compareTo(BigDecimal.ONE) != 0) {
                    throw new IllegalArgumentException("基准单位换算系数必须为1");
                }
            }
            if (enabled && Integer.valueOf(1).equals(command.getIsBaseUnit())) {
                enabledBaseCount++;
            }
        }
        if (enabledBaseCount != 1) {
            throw new IllegalArgumentException("必须且只能有一个启用的基准单位");
        }

        ProductUnit persistedBase = existing.stream()
                .filter(ProductUnit::isBase)
                .findFirst().orElse(null);
        if (persistedBase != null) {
            ProductUnitUpsertCommand baseCommand = commands.stream()
                    .filter(command -> Objects.equals(command.getId(), persistedBase.getId()))
                    .findFirst().orElse(null);
            if (baseCommand == null || !Integer.valueOf(1).equals(baseCommand.getStatus())
                    || !Integer.valueOf(1).equals(baseCommand.getIsBaseUnit())) {
                throw new IllegalArgumentException("已配置 SKU 的基准单位不可移除或停用");
            }
            if (!persistedBase.getUnitName().trim().equals(baseCommand.getUnitName().trim())) {
                throw new IllegalArgumentException("已配置 SKU 的基准单位名称不可变更");
            }
        } else if (!existing.isEmpty()) {
            throw new IllegalArgumentException("已配置 SKU 缺少基准单位，不能在线重定义");
        }

        Long staffId = currentStaffId();
        LocalDateTime now = LocalDateTime.now();
        Set<Long> retainedIds = commands.stream().map(ProductUnitUpsertCommand::getId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        // 完整集合未携带的历史行只停用，不删除，保护收货/移动/订单等事实外键。
        for (ProductUnit old : existing) {
            if (old.getId() != null && !retainedIds.contains(old.getId()) && old.isEnabled()) {
                old.setStatus(0);
                old.setUpdatedAt(now);
                old.setUpdatedBy(staffId);
                unitRepository.save(old);
            }
        }
        for (ProductUnitUpsertCommand command : commands) {
            ProductUnit unit = command.getId() == null ? new ProductUnit() : existingById.get(command.getId());
            if (unit == null) {
                throw new IllegalArgumentException("单位ID不存在: " + command.getId());
            }
            if (unit.getCreatedAt() == null) {
                unit.setCreatedAt(now);
            }
            if (unit.getCreatedBy() == null) {
                unit.setCreatedBy(staffId);
            }
            unit.setProductId(productId);
            unit.setUnitName(command.getUnitName().trim());
            unit.setBaseQuantityPerUnit(command.getBaseQuantityPerUnit());
            unit.setIsBaseUnit(command.getIsBaseUnit());
            unit.setAllowPurchase(flag(command.getAllowPurchase()));
            unit.setAllowSale(flag(command.getAllowSale()));
            unit.setAllowInventory(flag(command.getAllowInventory()));
            unit.setAllowTransfer(flag(command.getAllowTransfer()));
            unit.setBarcode(command.getBarcode());
            unit.setStatus(command.getStatus() == null ? 1 : command.getStatus());
            unit.setUpdatedAt(now);
            unit.setUpdatedBy(staffId);
            unitRepository.save(unit);
        }
    }

    private void validateCommand(ProductUnitUpsertCommand command) {
        if (command == null || command.getUnitName() == null || command.getUnitName().trim().isEmpty()) {
            throw new IllegalArgumentException("单位名称不能为空");
        }
        if (command.getBaseQuantityPerUnit() == null
                || command.getBaseQuantityPerUnit().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("基准数量系数必须大于0");
        }
        if (command.getIsBaseUnit() == null || (command.getIsBaseUnit() != 0 && command.getIsBaseUnit() != 1)) {
            throw new IllegalArgumentException("基准单位标记无效");
        }
        validateFlag(command.getAllowPurchase(), "采购");
        validateFlag(command.getAllowSale(), "销售");
        validateFlag(command.getAllowInventory(), "库存");
        validateFlag(command.getAllowTransfer(), "调拨");
        if (command.getStatus() != null && command.getStatus() != 0 && command.getStatus() != 1) {
            throw new IllegalArgumentException("单位状态无效");
        }
    }

    private void validateFlag(Integer value, String name) {
        if (value != null && value != 0 && value != 1) {
            throw new IllegalArgumentException(name + "操作标记无效");
        }
    }

    private int flag(Integer value) {
        return value == null ? 1 : value;
    }

    private List<ProductUnit> projectLegacyUnits(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("商品不存在: " + productId));
        List<ProductUnit> result = new ArrayList<>();
        if (product.getMainUnit() == null || product.getMainUnit().trim().isEmpty()) {
            return result;
        }
        result.add(legacyUnit(productId, product.getMainUnit(), BigDecimal.ONE, true));
        if (product.getAuxUnit() != null && !product.getAuxUnit().trim().isEmpty()
                && product.getMainToAuxRatio() != null
                && product.getMainToAuxRatio().compareTo(BigDecimal.ZERO) > 0
                && !product.getMainUnit().trim().equals(product.getAuxUnit().trim())) {
            // legacy 字段表示 1 主单位 = ratio 辅单位；基准单位是主单位，
            // 因此 1 个辅单位对应 1/ratio 个基准单位。
            BigDecimal auxCoefficient = BigDecimal.ONE.divide(
                    product.getMainToAuxRatio(), 8, RoundingMode.HALF_UP);
            result.add(legacyUnit(productId, product.getAuxUnit(), auxCoefficient, false));
        }
        return result;
    }

    private ProductUnit legacyUnit(Long productId, String name, BigDecimal coefficient, boolean base) {
        ProductUnit unit = new ProductUnit();
        unit.setProductId(productId);
        unit.setUnitName(name.trim());
        unit.setBaseQuantityPerUnit(coefficient);
        unit.setIsBaseUnit(base ? 1 : 0);
        unit.setAllowPurchase(1);
        unit.setAllowSale(1);
        unit.setAllowInventory(1);
        unit.setAllowTransfer(1);
        unit.setStatus(1);
        unit.setProjectionStatus(LEGACY_UNCONFIRMED);
        unit.setReadOnly(true);
        return unit;
    }

    private ProductUnitVO toVO(ProductUnit unit) {
        ProductUnitVO vo = new ProductUnitVO();
        vo.setId(unit.getId());
        vo.setProductId(unit.getProductId());
        vo.setUnitName(unit.getUnitName());
        vo.setBaseQuantityPerUnit(unit.getBaseQuantityPerUnit());
        vo.setIsBaseUnit(unit.getIsBaseUnit());
        vo.setAllowPurchase(unit.getAllowPurchase());
        vo.setAllowSale(unit.getAllowSale());
        vo.setAllowInventory(unit.getAllowInventory());
        vo.setAllowTransfer(unit.getAllowTransfer());
        vo.setBarcode(unit.getBarcode());
        vo.setStatus(unit.getStatus());
        vo.setReadOnly(unit.isReadOnly());
        vo.setProjectionStatus(unit.getProjectionStatus());
        return vo;
    }

    private String operationLabel(UnitOperation operation) {
        return switch (operation) {
            case PURCHASE -> "采购";
            case SALE -> "销售";
            case INVENTORY -> "库存";
            case TRANSFER -> "调拨";
        };
    }

    private Long currentStaffId() {
        if (!StpUtil.isLogin()) {
            return null;
        }
        try {
            String loginId = StpUtil.getLoginIdAsString();
            return loginId != null && loginId.startsWith("STAFF_")
                    ? Long.parseLong(loginId.substring("STAFF_".length())) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
