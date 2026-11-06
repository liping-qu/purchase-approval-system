-- =============================================================================
-- V10 幂等键表：解决"重复点击 / 超时重试"造成的重复落库
--
-- 为什么需要它：
--   * 创建申请：没有天然唯一键（同一个用户可以合法地提交两张事由相同的单），
--     双击一次就是两张草稿，且无法从数据上判断哪张是重复的；
--   * 手工调库存：每次调用都会生成新的 ST 单号，uk_inventory_tx_ref 兜不住，
--     重试一次库存就多加/多减一次 —— 这是四个并发场景里唯一没有任何保护的写操作。
--
-- 为什么下单、入库不用这张表：
--   purchase_order.request_id 与 purchase_order_item 的 (order_id, product_id)
--   本身就是天然幂等键，再加一行幂等记录只是多一次写；它们靠唯一索引回放即可。
--
-- 设计要点：
--   1) 主键 (idem_key, user_id)：key 由客户端生成，作用域到人，防跨用户串号；
--   2) request_hash：同一个 key 被用于不同请求体时拒绝，挡住客户端 bug；
--   3) result_id：业务结果主键，重试时据此回放（创建申请回放已建的单据）；
--   4) 幂等记录与业务写在同一个本地事务里 —— 业务回滚时占位一起回滚，
--      不会出现"key 被占了但业务没做成"死锁用户的情况；
--   5) 24 小时过期 + 每日清理，表不会无限增长。
-- =============================================================================

CREATE TABLE IF NOT EXISTS sys_idempotency (
    idem_key     VARCHAR(64) NOT NULL COMMENT '客户端生成的幂等键（UUID）',
    user_id      BIGINT      NOT NULL COMMENT '用户作用域，同一 key 换人使用互不干扰',
    endpoint     VARCHAR(48) NOT NULL COMMENT '业务标识：CREATE_REQUEST / ADJUST_STOCK',
    request_hash CHAR(64)    NOT NULL COMMENT '请求内容 SHA-256，防同 key 不同内容',
    result_id    BIGINT      NULL COMMENT '业务结果主键，重试时据此回放',
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at   DATETIME    NOT NULL COMMENT '过期时间（创建后 24 小时）',
    PRIMARY KEY (idem_key, user_id),
    KEY idx_idem_expire (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '接口幂等键';

-- 清理说明（由应用侧定时任务或运维脚本执行，这里不建事件调度器）：
--   DELETE FROM sys_idempotency WHERE expires_at < NOW() LIMIT 1000;
