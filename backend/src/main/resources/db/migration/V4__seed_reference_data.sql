-- =============================================================================
-- V4 基础数据（演示/开发环境）
--
-- 全部使用 INSERT IGNORE，因此：
--   * 全新库：插入一套完整可用的演示数据；
--   * 存量库：已存在的行保持不变，只补缺失的行 —— 不会覆盖你已有的业务数据。
--
-- 演示账号密码统一为 admin123（BCrypt，cost=10）。
-- 这三个账号只用于本地演示；生产部署必须删掉本文件或改成 ENV 注入的初始管理员
-- （对应审计报告 D-12：原 DemoDataInitializer 每次启动都把密码重置为 admin123，
--  已在本阶段删除，改为"只在初始化时写一次"）。
-- =============================================================================

INSERT IGNORE INTO sys_department (id, name) VALUES
    (1, '技术部'), (2, '财务部'), (3, '市场部'), (4, '行政部'), (5, '采购部');

INSERT IGNORE INTO sys_role (id, code, name) VALUES
    (1, 'ADMIN', '系统管理员'),
    (2, 'EMPLOYEE', '普通员工'),
    (3, 'MANAGER', '部门负责人'),
    (4, 'PURCHASER', '采购人员'),
    (5, 'WAREHOUSE', '仓库管理员');

INSERT IGNORE INTO sys_permission (id, code, name) VALUES
    (1, 'user:manage', '用户管理'),
    (2, 'role:manage', '角色管理'),
    (3, 'purchase:create', '创建采购申请'),
    (4, 'purchase:view', '查看采购申请'),
    (5, 'purchase:approve', '审批采购申请'),
    (6, 'order:create', '创建采购订单'),
    (7, 'inventory:update', '更新库存'),
    (8, 'supplier:manage', '供应商管理');

-- 密码：admin123
INSERT IGNORE INTO sys_user (id, username, password, nickname, department_id, status) VALUES
    (1, 'admin',     '$2a$10$4bwXZSkzRCnlx.V3/vWzbO8NM8rqTEGzxrWDWqVS/5x6SNGybvhrK', '系统管理员', 5, 1),
    (2, 'employee',  '$2a$10$4bwXZSkzRCnlx.V3/vWzbO8NM8rqTEGzxrWDWqVS/5x6SNGybvhrK', '普通员工',   1, 1),
    (3, 'manager',   '$2a$10$4bwXZSkzRCnlx.V3/vWzbO8NM8rqTEGzxrWDWqVS/5x6SNGybvhrK', '部门负责人', 1, 1),
    (4, 'purchaser', '$2a$10$4bwXZSkzRCnlx.V3/vWzbO8NM8rqTEGzxrWDWqVS/5x6SNGybvhrK', '采购人员',   5, 1),
    (5, 'warehouse', '$2a$10$4bwXZSkzRCnlx.V3/vWzbO8NM8rqTEGzxrWDWqVS/5x6SNGybvhrK', '仓库管理员', 5, 1);

UPDATE sys_user SET display_name = nickname WHERE display_name IS NULL;

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 1), (2, 2), (3, 3), (4, 4), (5, 5);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
    (1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6), (1, 7), (1, 8),
    (2, 3), (2, 4),
    (3, 4), (3, 5),
    (4, 4), (4, 6), (4, 8),
    (5, 7);

INSERT IGNORE INTO sys_menu (id, parent_id, name, path, icon, permission_code, sort_order) VALUES
    (1, NULL, '系统管理', '/admin',        'Setting',    'user:manage', 1),
    (2, 1,    '用户管理', '/admin/users',  'User',       'user:manage', 1),
    (3, 1,    '角色管理', '/admin/roles',  'UserFilled', 'role:manage', 2),
    (4, 1,    '菜单管理', '/admin/menus',  'Menu',       'role:manage', 3);

INSERT IGNORE INTO sys_config (config_key, config_value, description) VALUES
    ('system.name', '企业采购与审批管理系统', '系统名称');

INSERT IGNORE INTO sys_product (id, sku, name, category, unit, reference_price, stock, active) VALUES
    (1, 'SKU-1001', '办公笔记本电脑', '办公设备', '台', 5999.00, 20, 1),
    (2, 'SKU-1002', '人体工学办公椅', '办公家具', '把', 899.00, 50, 1);

INSERT IGNORE INTO sys_supplier (id, code, name, contact, phone, address, active) VALUES
    (1, 'SUP-001', '华夏办公设备有限公司', '张三', '13800000001', '上海市浦东新区', 1),
    (2, 'SUP-002', '优品供应链有限公司',   '李四', '13800000002', '深圳市南山区',   1);

-- ---------------------------------------------------------------------------
-- 修复：存量库的无效占位哈希
-- 旧 sql/procurement.sql 写进 sys_user 的口令是一串从未参与过真实散列的占位值，
-- 改造前靠启动时的 DemoDataInitializer 重置掩盖了这个问题；该初始化器已删除，
-- 因此必须在迁移里把"仍然是占位值"的账号改成已知口令的真实 BCrypt（admin123）。
-- 条件里带上旧占位值，确保不会覆盖用户后来改过的口令。
-- ---------------------------------------------------------------------------
UPDATE sys_user SET password = '$2a$10$4bwXZSkzRCnlx.V3/vWzbO8NM8rqTEGzxrWDWqVS/5x6SNGybvhrK'
WHERE password = '$2a$10$7Vv5m7f9o3sQfZ6B8a4W7OQj1xQ7d9l8O4o6s2i1A7h4R2m3t5u6K';
