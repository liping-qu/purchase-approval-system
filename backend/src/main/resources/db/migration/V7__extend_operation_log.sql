-- =============================================================================
-- V7 操作日志补字段：让日志从「有记录」变成「能追责」
--
-- 改造前的 sys_operation_log 只有 operator_id（而且代码里恒为 null）、
-- module/action/request_uri/ip/result/create_time。缺少三样东西：
--   1. operator_name —— 只有 id 时查日志要再 join 一次用户表，且用户改名后无法回溯；
--   2. biz_id        —— 不知道这条日志操作的是哪一笔申请/订单，等于没法追；
--   3. cost_ms       —— 没有耗时就无法用它排查慢操作。
--
-- 沿用 V2 的幂等写法（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS），
-- 保证存量库、全新库、半对齐脏库都能安全执行。
-- =============================================================================

DROP PROCEDURE IF EXISTS sp_add_column;
DROP PROCEDURE IF EXISTS sp_add_index;

DELIMITER $$

CREATE PROCEDURE sp_add_column(IN t VARCHAR(64), IN c VARCHAR(64), IN ddl VARCHAR(255))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = t AND column_name = c) THEN
        SET @ddl = CONCAT('ALTER TABLE `', t, '` ADD COLUMN `', c, '` ', ddl);
        PREPARE st FROM @ddl;
        EXECUTE st;
        DEALLOCATE PREPARE st;
    END IF;
END$$

-- 索引也要幂等：存量库可能已有人手工建过同名索引
CREATE PROCEDURE sp_add_index(IN t VARCHAR(64), IN i VARCHAR(64), IN cols VARCHAR(255))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = t AND index_name = i) THEN
        SET @ddl = CONCAT('CREATE INDEX `', i, '` ON `', t, '` (', cols, ')');
        PREPARE st FROM @ddl;
        EXECUTE st;
        DEALLOCATE PREPARE st;
    END IF;
END$$

DELIMITER ;

CALL sp_add_column('sys_operation_log', 'operator_name', 'VARCHAR(64) NULL COMMENT ''操作人用户名（冗余存储，便于直接检索）'' AFTER `operator_id`');
CALL sp_add_column('sys_operation_log', 'biz_id', 'BIGINT NULL COMMENT ''业务对象 id（申请/订单）'' AFTER `action`');
CALL sp_add_column('sys_operation_log', 'cost_ms', 'INT NULL COMMENT ''方法耗时（毫秒）'' AFTER `result`');

-- 后台最常见的查法是「某人某段时间做了什么」，operator_id 为空时也能靠时间兜住
CALL sp_add_index('sys_operation_log', 'idx_oplog_operator_time', '`operator_id`, `create_time`');

DROP PROCEDURE IF EXISTS sp_add_column;
DROP PROCEDURE IF EXISTS sp_add_index;
