-- =============================================================================
-- V1 基线：企业采购与审批管理系统的完整结构（全新安装的权威定义）
--
-- 背景：改造前项目有两份互相矛盾的 DDL ——
--   * sql/procurement.sql（Docker 初始化）用了 MariaDB 语法 ADD COLUMN IF NOT EXISTS，
--     在 MySQL 8 上第 28 条语句即失败，导致 version / created_at 等列根本没建出来；
--   * backend/src/main/resources/schema.sql（应用启动执行）只有一部分表。
-- 谁先执行决定最终结构，于是"同一份代码在两套环境下行为不同"。
-- 本文件取代上述两份脚本，成为唯一结构来源。
--
-- 存量库处理：spring.flyway.baseline-on-migrate=true + baseline-version=1 会把
-- 已有表但没有 flyway_schema_history 的库标记为"已在 V1"，然后只执行 V2 起的增量，
-- 不会 DROP 任何表，也不会丢数据。
-- =============================================================================

CREATE TABLE IF NOT EXISTS sys_department (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(64)  NOT NULL COMMENT '部门名称',
    parent_id   BIGINT       NULL COMMENT '上级部门',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_department_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '部门';

CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(64)  NOT NULL COMMENT '登录名',
    password      VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希',
    nickname      VARCHAR(64)  NOT NULL COMMENT '姓名',
    display_name  VARCHAR(100) NULL COMMENT '历史字段，与 nickname 冗余，保留兼容',
    phone         VARCHAR(32)  NULL,
    email         VARCHAR(128) NULL,
    department_id BIGINT       NULL,
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    KEY idx_user_department (department_id),
    CONSTRAINT fk_user_department FOREIGN KEY (department_id) REFERENCES sys_department (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '用户';

CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    code        VARCHAR(64) NOT NULL COMMENT '角色编码，如 ADMIN',
    name        VARCHAR(64) NOT NULL,
    status      TINYINT     NOT NULL DEFAULT 1,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '角色';

CREATE TABLE IF NOT EXISTS sys_permission (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(128) NOT NULL COMMENT '权限编码，如 user:manage',
    name        VARCHAR(128) NOT NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '权限';

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_role_role (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '用户-角色';

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    KEY idx_role_permission_permission (permission_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '角色-权限';

CREATE TABLE IF NOT EXISTS sys_menu (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    parent_id       BIGINT       NULL,
    name            VARCHAR(64)  NOT NULL,
    path            VARCHAR(255) NULL,
    icon            VARCHAR(128) NULL,
    permission_code VARCHAR(128) NULL,
    sort_order      INT          NOT NULL DEFAULT 0,
    status          TINYINT      NOT NULL DEFAULT 1,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '菜单';

CREATE TABLE IF NOT EXISTS sys_config (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    config_key   VARCHAR(128) NOT NULL,
    config_value TEXT         NOT NULL,
    description  VARCHAR(255) NULL,
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '系统配置';

CREATE TABLE IF NOT EXISTS sys_operation_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    operator_id BIGINT       NULL COMMENT '操作人，取自 JWT 主体',
    module      VARCHAR(64)  NOT NULL,
    action      VARCHAR(128) NOT NULL,
    request_uri VARCHAR(255) NULL,
    ip          VARCHAR(64)  NULL,
    result      VARCHAR(16)  NULL COMMENT 'SUCCESS / FAIL',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_oplog_create_time (create_time),
    KEY idx_oplog_operator (operator_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '操作日志';

CREATE TABLE IF NOT EXISTS sys_product (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    sku             VARCHAR(64)   NULL,
    name            VARCHAR(200)  NOT NULL,
    category        VARCHAR(100)  NULL,
    unit            VARCHAR(20)   NULL,
    reference_price DECIMAL(12, 2) NULL COMMENT '参考单价',
    stock           INT           NOT NULL DEFAULT 0 COMMENT '历史字段，实时库存见 inventory_stock',
    active          TINYINT       NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_sku (sku)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '商品';

CREATE TABLE IF NOT EXISTS sys_supplier (
    id      BIGINT       NOT NULL AUTO_INCREMENT,
    code    VARCHAR(64)  NULL,
    name    VARCHAR(200) NOT NULL,
    contact VARCHAR(100) NULL,
    phone   VARCHAR(30)  NULL,
    address VARCHAR(255) NULL,
    active  TINYINT      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '供应商';

CREATE TABLE IF NOT EXISTS purchase_request (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    request_no    VARCHAR(32)    NOT NULL COMMENT '申请单号',
    applicant_id  BIGINT         NOT NULL COMMENT '申请人',
    department_id BIGINT         NULL COMMENT '提交时的部门快照',
    reason        VARCHAR(500)   NOT NULL COMMENT '采购事由',
    total_amount  DECIMAL(14, 2) NOT NULL DEFAULT 0,
    status        VARCHAR(24)    NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/APPROVED/REJECTED/PURCHASING/COMPLETED',
    version       INT            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_no (request_no),
    KEY idx_request_applicant (applicant_id),
    KEY idx_request_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '采购申请';

CREATE TABLE IF NOT EXISTS purchase_request_item (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    request_id   BIGINT         NOT NULL,
    product_id   BIGINT         NOT NULL,
    product_name VARCHAR(200)   NOT NULL COMMENT '下单时的商品名快照',
    quantity     INT            NOT NULL,
    unit_price   DECIMAL(14, 2) NOT NULL,
    amount       DECIMAL(14, 2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_request_item_request (request_id),
    CONSTRAINT fk_item_request FOREIGN KEY (request_id) REFERENCES purchase_request (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '采购申请明细';

CREATE TABLE IF NOT EXISTS approval_record (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    request_id  BIGINT       NOT NULL,
    approver_id BIGINT       NOT NULL,
    action      VARCHAR(20)  NOT NULL COMMENT 'APPROVE / REJECT',
    result      VARCHAR(24)  NULL COMMENT '历史字段，新写入只用 action，保留兼容存量数据',
    comment     VARCHAR(500) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_approval_request (request_id),
    KEY idx_approval_approver (approver_id),
    CONSTRAINT fk_approval_request FOREIGN KEY (request_id) REFERENCES purchase_request (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '审批记录';

CREATE TABLE IF NOT EXISTS purchase_order (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    order_no     VARCHAR(32)    NOT NULL COMMENT '订单号',
    request_id   BIGINT         NOT NULL COMMENT '来源采购申请',
    supplier_id  BIGINT         NOT NULL,
    total_amount DECIMAL(14, 2) NOT NULL DEFAULT 0,
    creator_id   BIGINT         NULL COMMENT '预留：创建人。历史库里是 NOT NULL，V2 会放宽为 NULL',
    status       VARCHAR(24)    NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/DELIVERED',
    version      INT            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_order_request (request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '采购订单';

CREATE TABLE IF NOT EXISTS purchase_order_item (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    order_id     BIGINT         NOT NULL,
    product_id   BIGINT         NOT NULL,
    product_name VARCHAR(200)   NOT NULL COMMENT '下单时的商品名快照',
    quantity     INT            NOT NULL,
    unit_price   DECIMAL(14, 2) NOT NULL,
    amount       DECIMAL(14, 2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_item_order (order_id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES purchase_order (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '采购订单明细';

CREATE TABLE IF NOT EXISTS inventory_stock (
    product_id BIGINT NOT NULL,
    quantity   INT    NOT NULL DEFAULT 0 COMMENT '当前库存',
    version    INT    NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '库存（按商品聚合）';

CREATE TABLE IF NOT EXISTS inventory_transaction (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    product_id   BIGINT      NOT NULL,
    type         VARCHAR(10) NOT NULL COMMENT 'IN 入库 / OUT 出库',
    quantity     INT         NOT NULL COMMENT '变动数量，正数',
    reference_no VARCHAR(64) NOT NULL COMMENT '来源单号，如采购订单号',
    operator_id  BIGINT      NULL,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_inventory_tx_product (product_id),
    KEY idx_inventory_tx_reference (reference_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '库存流水';
