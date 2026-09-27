package com.agri_system.infrastructure.inventory.mapper;

import com.agri_system.domain.inventory.model.InventoryDecisionLog;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库存风险人工决策留痕 Mapper。
 *
 * <p>表为追加写（V120 触发器禁止更新/删除），因此只暴露 BaseMapper 的写入能力。</p>
 */
@Mapper
public interface InventoryDecisionLogMapper extends BaseMapper<InventoryDecisionLog> {
}
