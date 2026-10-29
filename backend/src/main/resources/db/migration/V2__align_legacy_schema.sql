-- =============================================================================
-- V2 存量库对齐：把"被旧脚本漏建/建错"的结构补齐，且必须可重复执行
--
-- 为什么需要这一版：Docker 初始化脚本 sql/procurement.sql 在 MySQL 8 上第 28 条语句
-- 就因 MariaDB 语法 ADD COLUMN IF NOT EXISTS 失败，导致后面的 21 条（补 version、
-- created_at、建 inventory_stock / sys_product 等）全部没执行。
-- 所以存量库缺列缺表，而全新库（V1）是完整的。
--
-- MySQL 8 不支持 ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS，
-- 因此这里用 information_schema 判断 + 动态 SQL 实现幂等：
--   全新库：V1 已建好 → 本文件的判断全部命中"已存在"，实际是空跑；
--   存量库：基线跳过 V1 → 本文件负责补齐。
-- =============================================================================

DROP PROCEDURE IF EXISTS sp_add_column;
DROP PROCEDURE IF EXISTS sp_exec_if_column;

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

-- 仅当"参照列"存在时才执行语句，用于历史数据回填（新库没有 create_time 这类旧列）
CREATE PROCEDURE sp_exec_if_column(IN t VARCHAR(64), IN c VARCHAR(64), IN stmt TEXT)
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = t AND column_name = c) THEN
        SET @s = stmt;
        PREPARE st2 FROM @s;
        EXECUTE st2;
        DEALLOCATE PREPARE st2;
    END IF;
END$$

DELIMITER ;

-- ---------- sys_user：历史脚本里的 display_name 因语法错误没加上 ----------
CALL sp_add_column('sys_user', 'display_name', 'VARCHAR(100) NULL');
CALL sp_exec_if_column('sys_user', 'display_name',
     'UPDATE sys_user SET display_name = nickname WHERE display_name IS NULL');

-- ---------- purchase_request：乐观锁与时间列 ----------
CALL sp_add_column('purchase_request', 'version', 'INT NOT NULL DEFAULT 0');
CALL sp_add_column('purchase_request', 'created_at', 'DATETIME NULL DEFAULT CURRENT_TIMESTAMP');
CALL sp_add_column('purchase_request', 'updated_at', 'DATETIME NULL DEFAULT CURRENT_TIMESTAMP');
CALL sp_exec_if_column('purchase_request', 'create_time',
     'UPDATE purchase_request SET created_at = create_time WHERE created_at IS NULL');
CALL sp_exec_if_column('purchase_request', 'update_time',
     'UPDATE purchase_request SET updated_at = update_time WHERE updated_at IS NULL');

-- ---------- purchase_request_item：商品名快照 ----------
CALL sp_add_column('purchase_request_item', 'product_name', 'VARCHAR(200) NOT NULL DEFAULT ''''');
UPDATE purchase_request_item i
    JOIN sys_product p ON p.id = i.product_id
SET i.product_name = p.name
WHERE i.product_name IS NULL OR i.product_name = '';

-- ---------- approval_record：action / created_at ----------
CALL sp_add_column('approval_record', 'action', 'VARCHAR(20) NULL');
CALL sp_add_column('approval_record', 'created_at', 'DATETIME NULL DEFAULT CURRENT_TIMESTAMP');
CALL sp_exec_if_column('approval_record', 'approval_time',
     'UPDATE approval_record SET created_at = approval_time WHERE created_at IS NULL');
-- 历史数据的 action 从 result 回填，保证新旧审批记录语义统一
CALL sp_exec_if_column('approval_record', 'result',
     'UPDATE approval_record SET action = result WHERE action IS NULL AND result IS NOT NULL');
UPDATE approval_record SET action = 'APPROVE' WHERE action IS NULL;

-- ---------- purchase_order：乐观锁与时间列；creator_id 放宽为可空 ----------
CALL sp_add_column('purchase_order', 'version', 'INT NOT NULL DEFAULT 0');
CALL sp_add_column('purchase_order', 'created_at', 'DATETIME NULL DEFAULT CURRENT_TIMESTAMP');
CALL sp_add_column('purchase_order', 'updated_at', 'DATETIME NULL DEFAULT CURRENT_TIMESTAMP');
CALL sp_exec_if_column('purchase_order', 'create_time',
     'UPDATE purchase_order SET created_at = create_time WHERE created_at IS NULL');
CALL sp_exec_if_column('purchase_order', 'update_time',
     'UPDATE purchase_order SET updated_at = update_time WHERE updated_at IS NULL');
-- 历史库 creator_id 是 NOT NULL 且无默认值，而实体从未写过该列 → 创建订单必失败。
-- 这里放宽为 NULL（保留列，等待后续由 OrderService 写入真实创建人）。
ALTER TABLE purchase_order MODIFY COLUMN creator_id BIGINT NULL COMMENT '预留：创建人';

-- ---------- purchase_order_item：商品名快照 ----------
CALL sp_add_column('purchase_order_item', 'product_name', 'VARCHAR(200) NOT NULL DEFAULT ''''');
UPDATE purchase_order_item i
    JOIN sys_product p ON p.id = i.product_id
SET i.product_name = p.name
WHERE i.product_name IS NULL OR i.product_name = '';

-- ---------- approval_record.result：历史列放宽为可空 ----------
-- 实体只写 action，result 保留用于存量数据可追溯；若保持 NOT NULL 会让审批写入失败。
ALTER TABLE approval_record MODIFY COLUMN result VARCHAR(24) NULL COMMENT '历史字段，新写入只用 action';

-- ---------- 历史脚本没建出来的表 ----------
CREATE TABLE IF NOT EXISTS sys_product (
    id BIGINT NOT NULL AUTO_INCREMENT, sku VARCHAR(64) NULL, name VARCHAR(200) NOT NULL,
    category VARCHAR(100) NULL, unit VARCHAR(20) NULL, reference_price DECIMAL(12, 2) NULL,
    stock INT NOT NULL DEFAULT 0, active TINYINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id), UNIQUE KEY uk_product_sku (sku)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '商品';

CREATE TABLE IF NOT EXISTS sys_supplier (
    id BIGINT NOT NULL AUTO_INCREMENT, code VARCHAR(64) NULL, name VARCHAR(200) NOT NULL,
    contact VARCHAR(100) NULL, phone VARCHAR(30) NULL, address VARCHAR(255) NULL,
    active TINYINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id), UNIQUE KEY uk_supplier_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '供应商';

CREATE TABLE IF NOT EXISTS inventory_stock (
    product_id BIGINT NOT NULL, quantity INT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '库存';

CREATE TABLE IF NOT EXISTS inventory_transaction (
    id BIGINT NOT NULL AUTO_INCREMENT, product_id BIGINT NOT NULL, type VARCHAR(10) NOT NULL,
    quantity INT NOT NULL, reference_no VARCHAR(64) NOT NULL, operator_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_inventory_tx_product (product_id), KEY idx_inventory_tx_reference (reference_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '库存流水';

CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT NOT NULL AUTO_INCREMENT, parent_id BIGINT NULL, name VARCHAR(64) NOT NULL,
    path VARCHAR(255) NULL, icon VARCHAR(128) NULL, permission_code VARCHAR(128) NULL,
    sort_order INT NOT NULL DEFAULT 0, status TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '菜单';

CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT NOT NULL AUTO_INCREMENT, config_key VARCHAR(128) NOT NULL, config_value TEXT NOT NULL,
    description VARCHAR(255) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_config_key (config_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '系统配置';

-- 回填完成后把时间列收紧为 NOT NULL（V1 的定义），保证两条路径结构一致
UPDATE purchase_request SET created_at = NOW() WHERE created_at IS NULL;
UPDATE purchase_request SET updated_at = NOW() WHERE updated_at IS NULL;
ALTER TABLE purchase_request MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE purchase_request MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

UPDATE purchase_order SET created_at = NOW() WHERE created_at IS NULL;
UPDATE purchase_order SET updated_at = NOW() WHERE updated_at IS NULL;
ALTER TABLE purchase_order MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE purchase_order MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

UPDATE approval_record SET created_at = NOW() WHERE created_at IS NULL;
ALTER TABLE approval_record MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- 清理临时存储过程
DROP PROCEDURE IF EXISTS sp_add_column;
DROP PROCEDURE IF EXISTS sp_exec_if_column;
