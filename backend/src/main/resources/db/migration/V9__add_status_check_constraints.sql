-- =============================================================================
-- V9 状态类字段的 CHECK 约束：让数据库也认识"合法取值"
--
-- 改造前的处境：状态是一串裸字符串，合法范围只写在 Java 枚举 Enums.TRANSITIONS 里。
-- 应用层确实拦得住（会抛"非法状态流转"），但那是**唯一的**一道门：
--   · 任何绕过 service 的写入（运维手工 SQL、别的服务、以后加的数据导入工具）
--     都能把 status 写成 'APROVED' 或 'DONE'；
--   · 拼错的状态不会被任何东西发现，只会让那条单据从所有列表里"消失"
--     （每个查询都按状态过滤），排障时极难定位。
--
-- 这一版把"合法取值"写进表结构，与应用层形成两道独立防线：
--   应用层负责"这次流转合不合规"（需要上下文，数据库做不了）；
--   数据库负责"这个值是不是这个词表里的"（不需要上下文，一条 CHECK 就够）。
--
-- 关于幂等：MySQL 8 没有 ADD CONSTRAINT IF NOT EXISTS，沿用 V2/V6 的做法——
-- 查 information_schema.table_constraints 后动态执行 ALTER，重复执行即空跑。
--
-- 关于存量脏数据：先在 sp_assert_allowed 里预检，发现非法值就 SIGNAL 中止并报出
-- "哪张表哪个字段有几行不合法"。这样做的理由是——ALTER 自己也会失败，但报的是
-- "Check constraint 'x' is violated"，既不知道是哪一行，也不知道合法值是什么；
-- 预检把这句话翻译成人能直接行动的提示。绝不会为了让迁移通过而静默改写业务状态。
-- =============================================================================

-- 历史审批动作的语义对齐。
-- 存量库的 approval_record.action 在 V2 里是从旧的 result 列回填的，
-- 而旧脚本写的是过去式（APPROVED/REJECTED）或 PASS 这类词；新代码只写 APPROVE/REJECT。
-- 这里把已知的等价说法映射成新词表，注意只映射语义明确的三种，
-- 其余任何陌生取值都留给下面的预检中止——猜错比报错更危险。
UPDATE approval_record SET action = 'APPROVE' WHERE UPPER(TRIM(action)) IN ('APPROVED', 'PASS', 'AGREE');
UPDATE approval_record SET action = 'REJECT'  WHERE UPPER(TRIM(action)) IN ('REJECTED', 'DENY', 'REFUSE');

DROP PROCEDURE IF EXISTS sp_assert_allowed;
DROP PROCEDURE IF EXISTS sp_add_check;

DELIMITER $$

-- 预检：ok 是"合法"的判定表达式（与随后 CHECK 里的表达式是同一个字符串）
CREATE PROCEDURE sp_assert_allowed(IN t VARCHAR(64), IN c VARCHAR(64), IN ok VARCHAR(512), IN label VARCHAR(255))
BEGIN
    SET @cnt_sql = CONCAT('SELECT COUNT(*) INTO @bad FROM `', t, '` WHERE `', c,
                          '` IS NULL OR NOT (', ok, ')');
    PREPARE chk FROM @cnt_sql;
    EXECUTE chk;
    DEALLOCATE PREPARE chk;
    IF @bad > 0 THEN
        SET @msg = CONCAT('V9 中止：', t, '.', c, ' 有 ', @bad,
                          ' 行非法值（合法取值：', label, '）。请先修正这些行再启动应用。');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = @msg;
    END IF;
END$$

CREATE PROCEDURE sp_add_check(IN t VARCHAR(64), IN name VARCHAR(64), IN expr VARCHAR(512))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE constraint_schema = DATABASE()
                     AND table_name = t
                     AND constraint_name = name
                     AND constraint_type = 'CHECK') THEN
        SET @ddl = CONCAT('ALTER TABLE `', t, '` ADD CONSTRAINT `', name, '` CHECK (', expr, ')');
        PREPARE st FROM @ddl;
        EXECUTE st;
        DEALLOCATE PREPARE st;
    END IF;
END$$

DELIMITER ;

-- ---------- 单据状态：词表与 Enums.ApplicationStatus / OrderStatus 逐字对应 ----------
-- 谓词只在这里定义一次，预检和约束共用同一个变量，
-- 避免"检查的是一套、约束的是另一套"这种最容易出现的漂移。
SET @p_request_status = 'status IN (''DRAFT'',''PENDING'',''APPROVED'',''REJECTED'',''PURCHASING'',''COMPLETED'',''CANCELLED'')';
CALL sp_assert_allowed('purchase_request', 'status', @p_request_status,
     'DRAFT/PENDING/APPROVED/REJECTED/PURCHASING/COMPLETED/CANCELLED');
CALL sp_add_check('purchase_request', 'ck_purchase_request_status', @p_request_status);

SET @p_order_status = 'status IN (''CREATED'',''DELIVERED'')';
CALL sp_assert_allowed('purchase_order', 'status', @p_order_status, 'CREATED/DELIVERED');
CALL sp_add_check('purchase_order', 'ck_purchase_order_status', @p_order_status);

-- ---------- 审批动作 / 库存流水方向 ----------
SET @p_approval_action = 'action IN (''APPROVE'',''REJECT'')';
CALL sp_assert_allowed('approval_record', 'action', @p_approval_action, 'APPROVE/REJECT');
CALL sp_add_check('approval_record', 'ck_approval_record_action', @p_approval_action);

SET @p_tx_type = '`type` IN (''IN'',''OUT'')';
CALL sp_assert_allowed('inventory_transaction', 'type', @p_tx_type, 'IN/OUT');
CALL sp_add_check('inventory_transaction', 'ck_inventory_tx_type', @p_tx_type);

-- ---------- 数值不变量：这几条对应的正是升级 3/10 在应用层修过的坑 ----------
-- 流水数量恒为正（出库靠 type=OUT 表达方向，不靠负数）；
-- 库存不为负（出库用 WHERE quantity >= n 的原子扣减保证）；
-- 明细数量至少 1（DTO 上有 @Min(1)，这里补数据库侧兜底）。
SET @p_tx_qty = 'quantity >= 1';
CALL sp_assert_allowed('inventory_transaction', 'quantity', @p_tx_qty, '正整数');
CALL sp_add_check('inventory_transaction', 'ck_inventory_tx_quantity', @p_tx_qty);

SET @p_stock_qty = 'quantity >= 0';
CALL sp_assert_allowed('inventory_stock', 'quantity', @p_stock_qty, '非负整数');
CALL sp_add_check('inventory_stock', 'ck_inventory_stock_quantity', @p_stock_qty);

SET @p_req_item_qty = 'quantity >= 1';
CALL sp_assert_allowed('purchase_request_item', 'quantity', @p_req_item_qty, '正整数');
CALL sp_add_check('purchase_request_item', 'ck_request_item_quantity', @p_req_item_qty);

SET @p_order_item_qty = 'quantity >= 1';
CALL sp_assert_allowed('purchase_order_item', 'quantity', @p_order_item_qty, '正整数');
CALL sp_add_check('purchase_order_item', 'ck_order_item_quantity', @p_order_item_qty);

-- ---------- 启用位：只允许 0/1，避免 2 这种"既非启用也非停用"的第三种状态 ----------
SET @p_user_status = 'status IN (0,1)';
CALL sp_assert_allowed('sys_user', 'status', @p_user_status, '0 或 1');
CALL sp_add_check('sys_user', 'ck_sys_user_status', @p_user_status);

SET @p_product_active = 'active IN (0,1)';
CALL sp_assert_allowed('sys_product', 'active', @p_product_active, '0 或 1');
CALL sp_add_check('sys_product', 'ck_sys_product_active', @p_product_active);

SET @p_supplier_active = 'active IN (0,1)';
CALL sp_assert_allowed('sys_supplier', 'active', @p_supplier_active, '0 或 1');
CALL sp_add_check('sys_supplier', 'ck_sys_supplier_active', @p_supplier_active);

DROP PROCEDURE IF EXISTS sp_assert_allowed;
DROP PROCEDURE IF EXISTS sp_add_check;

-- 迁移因"存量非法值"中止时的排查语句（把表名/字段名替换进去即可）：
--   SELECT status, COUNT(*) FROM purchase_request
--     WHERE status NOT IN ('DRAFT','PENDING','APPROVED','REJECTED','PURCHASING','COMPLETED','CANCELLED')
--        OR status IS NULL GROUP BY status;
