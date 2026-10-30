# 数据库文档与初始化入口

更新：2026-10-30。当前结构以[Flyway迁移](../core-infrastructure/src/main/resources/db/migration/)和目标库实际迁移记录为准。

## 新库与已有库

- 新建开发库：按[根目录启动说明](../README.md)启动后端，由Flyway从空库依次迁移；确认全部成功后，才在首次本地开发初始化时执行 scripts/dev-bootstrap.sql。
- 已有库：先备份并核对迁移历史，再随对应版本升级；不得重新初始化或清空。数据重建必须有用户明确授权。
- schema.sql、早期V1—V10表清单及ER图是历史设计快照，不是当前完整建库脚本，不能导入快照后再假装已完成现行迁移。不要修改已执行迁移以修正文档。
- 本轮3092隔离验收库为89份成功迁移、最高V133；其他预览库不因此自动升级，具体版本逐库核对。

## 当前业务结构

| 业务 | 当前事实来源 | 约束 |
|---|---|---|
| 商品与单位 | product、product_unit | 单位直接换算到持久化基准单位；整件粒度按后端策略 |
| 商品/套餐价格 | 商品档案、套餐档案及权威报价服务 | 旧product_price只供历史查询，不恢复多维可写定价 |
| 库存 | inventory_receipt_batch、inventory_batch_balance、inventory_movement | 原批次、货位余额和追加移动为唯一库存账；inventory仅为汇总投影 |
| 交易与履约 | order、order_item、order_payment、order_delivery及原批次分配 | 退货沿原分配；提货/签收不重复出库或收款 |
| POS结果恢复 | pos_sale_submission | 同UUID结果与钱货同事务，UNKNOWN不证明未成交 |
| 赊销与退款 | 原应收、明确还款分配和售后事实 | 冲欠与实还区分，来源未知不能猜零 |

详细契约见[AGENTS.md](../AGENTS.md)、[当前状态](../docs/WORKING_STATE.md)及[统一库存运行说明](../docs/runbooks/retail-fact-chain-rollout.md)。

## 历史设计资料

- [DATABASE_DESIGN.md](DATABASE_DESIGN.md)：历史结构说明；开头标明当前替代依据，支付/订单状态已按源码修正，其他旧结构不能直接作为现行DDL。
- [er-diagram.md](er-diagram.md)、er-diagram.puml：历史关系示意，不能以其中表数或枚举代替实际迁移。

新增结构前说明现有表不能解决的实际问题和最简方案，再设计约束、迁移、验证及回退。迁移编号使用实施时最新可用版本，不照早期文档预占V11或V134。
