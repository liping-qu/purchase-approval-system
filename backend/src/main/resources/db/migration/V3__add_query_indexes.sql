-- =============================================================================
-- V3 查询索引：只加"有真实查询依据"的索引，避免为了看起来专业而堆索引
--
-- 依据（对应 Mapper 中的实际 SQL）：
--   1) PurchaseBusinessService.pending()
--        status = 'PENDING' AND department_id = ?
--      status 选择性极低（值域 6），单列索引会回表大量行 → 复合索引 (status, department_id)
--   2) PurchaseRequestMapper.byApplicant()
--        applicant_id = ? ORDER BY created_at DESC
--      → (applicant_id, created_at) 让排序也走索引，避免 filesort
--   3) PurchaseBusinessService.orderList()
--        ORDER BY created_at DESC（无过滤）
--      → (created_at)
--   4) InventoryTransactionMapper.recent()
--        ORDER BY created_at DESC
--      → (created_at)
--
-- 注意：当前演示库数据量很小（< 10 行），EXPLAIN 里可能仍显示全表扫描。
-- 索引收益必须用造数脚本（10 万行）才能量化，那一步在 P6 阶段连同 EXPLAIN 对比一起做。
-- =============================================================================

DROP PROCEDURE IF EXISTS sp_add_index;

DELIMITER $$

CREATE PROCEDURE sp_add_index(IN t VARCHAR(64), IN idx VARCHAR(64), IN cols VARCHAR(255))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = t AND index_name = idx) THEN
        SET @ddl = CONCAT('CREATE INDEX `', idx, '` ON `', t, '` (', cols, ')');
        PREPARE st FROM @ddl;
        EXECUTE st;
        DEALLOCATE PREPARE st;
    END IF;
END$$

DELIMITER ;

CALL sp_add_index('purchase_request', 'idx_request_status_dept', 'status, department_id');
CALL sp_add_index('purchase_request', 'idx_request_applicant_created', 'applicant_id, created_at');
CALL sp_add_index('purchase_order', 'idx_order_created', 'created_at');
CALL sp_add_index('inventory_transaction', 'idx_inventory_tx_created', 'created_at');

DROP PROCEDURE IF EXISTS sp_add_index;
