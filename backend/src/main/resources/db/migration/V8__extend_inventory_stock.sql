-- =============================================================================
-- V8 库存补齐「能看能调」缺的两个字段
--
-- 背景：前端库存页要展示 商品名/分类/单位/最低库存/更新时间，并支持手工入库出库，
-- 但 inventory_stock 只有 (product_id, quantity, version)：
--   * 没有 min_quantity —— DashboardMapper 的注释里早就写着"等 P2 引入 min_quantity
--     后再升级为低于阈值的语义"，前端 Inventory.vue 也已经在读 row.minimumStock，
--     结果页面上的"库存预警"恒为 0（undefined 比较）；
--   * 没有 updated_at  —— 前端"更新时间"列永远空白，也无法回答"这条库存最后什么时候动的"。
--
-- 阈值给 10 是保守默认值：中小企业按"件"采购的常用耗材，个位数库存基本都要补货。
-- 需要按品类细化时再挪到 sys_config 或商品维度，现在不提前抽象。
--
-- 沿用 V2/V7 的幂等写法（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS）。
-- =============================================================================

DROP PROCEDURE IF EXISTS sp_add_column;

DELIMITER $$

CREATE PROCEDURE sp_add_column(IN t VARCHAR(64), IN c VARCHAR(64), IN ddl VARCHAR(400))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = t AND column_name = c) THEN
        SET @ddl = CONCAT('ALTER TABLE `', t, '` ADD COLUMN `', c, '` ', ddl);
        PREPARE st FROM @ddl;
        EXECUTE st;
        DEALLOCATE PREPARE st;
    END IF;
END$$

DELIMITER ;

CALL sp_add_column('inventory_stock', 'min_quantity',
    'INT NOT NULL DEFAULT 10 COMMENT ''安全库存阈值，低于它前端标红'' AFTER `version`');

-- ON UPDATE CURRENT_TIMESTAMP 让入库/出库/扣减任何一次变更都自动刷新，
-- 不需要业务代码记得去 set 时间（漏一次就会出现"数量变了时间没变"）。
CALL sp_add_column('inventory_stock', 'updated_at',
    'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''最后变动时间'' AFTER `min_quantity`');

DROP PROCEDURE IF EXISTS sp_add_column;
