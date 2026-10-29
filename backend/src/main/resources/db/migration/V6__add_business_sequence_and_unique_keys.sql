-- =============================================================================
-- V6 单号序列 + 两条幂等兜底唯一约束
--
-- 1) sys_sequence：按"业务类型 + 日期"维护单调递增的流水号。
--    改造前单号是 "PR" + yyyyMMddHHmmssSSS，同毫秒并发必然撞 uk_request_no 直接 500。
--    这里用 MySQL 的 LAST_INSERT_ID(expr) 技巧实现原子自增：
--      INSERT ... VALUES (name, CURDATE(), LAST_INSERT_ID(1))
--        ON DUPLICATE KEY UPDATE next_val = LAST_INSERT_ID(next_val + 1);
--      SELECT LAST_INSERT_ID();
--    无论是新建当天第一号还是已有号段自增，都能在同一条语句里取到"本次分配值"。
--
-- 2) uk_order_request：一个采购申请最多生成一张订单。
--    状态机（APPROVED -> PURCHASING 的 CAS）已经能挡住重复下单，这条唯一键是第二道防线：
--    万一将来有人绕过 service 直接写库，数据库层也不会出现一申请多单。
--
-- 3) uk_inventory_tx_ref：同一来源单据 + 同一商品 + 同一方向只允许一条流水。
--    同样是"状态机之外"的兜底，保证库存流水不会被重复记账。
--
-- 注意：2) 3) 若存量数据已存在重复，ALTER 会失败并中止启动——这是有意的，
-- 因为带着重复数据继续跑比启动失败更危险。清理办法见文件末尾注释。
-- =============================================================================

CREATE TABLE IF NOT EXISTS sys_sequence (
    name     VARCHAR(64) NOT NULL COMMENT '业务类型，如 REQUEST / ORDER',
    seq_date DATE        NOT NULL COMMENT '日期分片',
    next_val BIGINT      NOT NULL COMMENT '当前已分配到的值',
    PRIMARY KEY (name, seq_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '业务单号序列';

DROP PROCEDURE IF EXISTS sp_add_unique;

DELIMITER $$

CREATE PROCEDURE sp_add_unique(IN t VARCHAR(64), IN idx VARCHAR(64), IN cols VARCHAR(255))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = t AND index_name = idx) THEN
        SET @ddl = CONCAT('ALTER TABLE `', t, '` ADD UNIQUE KEY `', idx, '` (', cols, ')');
        PREPARE st FROM @ddl;
        EXECUTE st;
        DEALLOCATE PREPARE st;
    END IF;
END$$

DELIMITER ;

CALL sp_add_unique('purchase_order', 'uk_order_request', 'request_id');
CALL sp_add_unique('inventory_transaction', 'uk_inventory_tx_ref', 'reference_no, product_id, type');

DROP PROCEDURE IF EXISTS sp_add_unique;

-- 若上面两条 ALTER 因存量重复数据失败，先执行下面的排查，再重新启动应用：
--   SELECT request_id, COUNT(*) c FROM purchase_order GROUP BY request_id HAVING c > 1;
--   SELECT reference_no, product_id, type, COUNT(*) c FROM inventory_transaction
--     GROUP BY reference_no, product_id, type HAVING c > 1;
