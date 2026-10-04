CREATE DATABASE IF NOT EXISTS procurement DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE procurement;

CREATE TABLE IF NOT EXISTS sys_department (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(64) NOT NULL UNIQUE,
  parent_id BIGINT NULL, status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, username VARCHAR(64) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL, nickname VARCHAR(64) NOT NULL,
  phone VARCHAR(32), email VARCHAR(128), department_id BIGINT,
  status TINYINT NOT NULL DEFAULT 1, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_dept FOREIGN KEY (department_id) REFERENCES sys_department(id) ON DELETE SET NULL
);
CREATE TABLE IF NOT EXISTS sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(64) NOT NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS sys_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, code VARCHAR(128) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS sys_user_role (user_id BIGINT NOT NULL, role_id BIGINT NOT NULL, PRIMARY KEY(user_id,role_id));
CREATE TABLE IF NOT EXISTS sys_role_permission (role_id BIGINT NOT NULL, permission_id BIGINT NOT NULL, PRIMARY KEY(role_id,permission_id));
CREATE TABLE IF NOT EXISTS supplier (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(128) NOT NULL, contact VARCHAR(64), phone VARCHAR(32),
  address VARCHAR(255), credit_level VARCHAR(16) DEFAULT 'A', status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(128) NOT NULL, category VARCHAR(64), specification VARCHAR(128),
  unit VARCHAR(16) NOT NULL DEFAULT '件', reference_price DECIMAL(12,2) NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS purchase_request (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, request_no VARCHAR(32) NOT NULL UNIQUE, applicant_id BIGINT NOT NULL,
  department_id BIGINT, total_amount DECIMAL(12,2) NOT NULL DEFAULT 0, reason VARCHAR(500) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'DRAFT', apply_time DATETIME,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS purchase_request_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, request_id BIGINT NOT NULL, product_id BIGINT NOT NULL,
  quantity DECIMAL(12,2) NOT NULL, unit_price DECIMAL(12,2) NOT NULL,
  amount DECIMAL(12,2) NOT NULL, CONSTRAINT fk_item_request FOREIGN KEY(request_id) REFERENCES purchase_request(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS approval_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, request_id BIGINT NOT NULL, approver_id BIGINT NOT NULL,
  result VARCHAR(24) NOT NULL, comment VARCHAR(500), approval_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_approval_request FOREIGN KEY(request_id) REFERENCES purchase_request(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS purchase_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, order_no VARCHAR(32) NOT NULL UNIQUE, request_id BIGINT NOT NULL,
  supplier_id BIGINT NOT NULL, total_amount DECIMAL(12,2) NOT NULL DEFAULT 0, creator_id BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'CREATED', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS purchase_order_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, order_id BIGINT NOT NULL, product_id BIGINT NOT NULL,
  quantity DECIMAL(12,2) NOT NULL, unit_price DECIMAL(12,2) NOT NULL, amount DECIMAL(12,2) NOT NULL,
  CONSTRAINT fk_order_item_order FOREIGN KEY(order_id) REFERENCES purchase_order(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS inventory (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, product_id BIGINT NOT NULL UNIQUE,
  current_quantity DECIMAL(12,2) NOT NULL DEFAULT 0, min_quantity DECIMAL(12,2) NOT NULL DEFAULT 0,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS inventory_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, product_id BIGINT NOT NULL, type VARCHAR(16) NOT NULL,
  quantity DECIMAL(12,2) NOT NULL, operator_id BIGINT NOT NULL, remark VARCHAR(255),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS sys_operation_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, operator_id BIGINT, module VARCHAR(64) NOT NULL,
  action VARCHAR(128) NOT NULL, request_uri VARCHAR(255), ip VARCHAR(64), result VARCHAR(16),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT IGNORE INTO sys_department(id,name) VALUES (1,'技术部'),(2,'财务部'),(3,'市场部'),(4,'行政部'),(5,'采购部');
INSERT IGNORE INTO sys_role(id,code,name) VALUES (1,'ADMIN','系统管理员'),(2,'EMPLOYEE','普通员工'),(3,'MANAGER','部门负责人'),(4,'PURCHASER','采购人员'),(5,'WAREHOUSE','仓库管理员');
INSERT IGNORE INTO sys_permission(id,code,name) VALUES
 (1,'user:manage','用户管理'),(2,'role:manage','角色管理'),(3,'purchase:create','创建采购申请'),(4,'purchase:view','查看采购申请'),(5,'purchase:approve','审批采购申请'),(6,'order:create','创建采购订单'),(7,'inventory:update','更新库存'),(8,'supplier:manage','供应商管理');
INSERT IGNORE INTO sys_user(id,username,password,nickname,department_id) VALUES (1,'admin','$2a$10$7Vv5m7f9o3sQfZ6B8a4W7OQj1xQ7d9l8O4o6s2i1A7h4R2m3t5u6K','系统管理员',5),(2,'employee','$2a$10$7Vv5m7f9o3sQfZ6B8a4W7OQj1xQ7d9l8O4o6s2i1A7h4R2m3t5u6K','普通员工',1),(3,'manager','$2a$10$7Vv5m7f9o3sQfZ6B8a4W7OQj1xQ7d9l8O4o6s2i1A7h4R2m3t5u6K','部门负责人',1),(4,'purchaser','$2a$10$7Vv5m7f9o3sQfZ6B8a4W7OQj1xQ7d9l8O4o6s2i1A7h4R2m3t5u6K','采购人员',5),(5,'warehouse','$2a$10$7Vv5m7f9o3sQfZ6B8a4W7OQj1xQ7d9l8O4o6s2i1A7h4R2m3t5u6K','仓库管理员',5);
INSERT IGNORE INTO sys_user_role(user_id,role_id) VALUES (1,1),(2,2),(3,3),(4,4),(5,5);
INSERT IGNORE INTO sys_role_permission(role_id,permission_id) VALUES (1,1),(1,2),(1,3),(1,4),(1,5),(1,6),(1,7),(1,8),(2,3),(2,4),(3,4),(3,5),(4,4),(4,6),(4,8),(5,7);
CREATE TABLE IF NOT EXISTS sys_menu (id BIGINT PRIMARY KEY AUTO_INCREMENT,parent_id BIGINT NULL,name VARCHAR(64) NOT NULL,path VARCHAR(255),icon VARCHAR(128),permission_code VARCHAR(128),sort_order INT DEFAULT 0,status TINYINT DEFAULT 1,create_time DATETIME DEFAULT CURRENT_TIMESTAMP,update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS sys_config (id BIGINT PRIMARY KEY AUTO_INCREMENT,config_key VARCHAR(128) UNIQUE NOT NULL,config_value TEXT NOT NULL,description VARCHAR(255),create_time DATETIME DEFAULT CURRENT_TIMESTAMP,update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP);
INSERT IGNORE INTO sys_config(config_key,config_value,description) VALUES ('system.name','企业采购与审批管理系统','系统名称');

-- Backend persistence compatibility columns and transactional inventory tables.
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS display_name VARCHAR(100) NULL;
UPDATE sys_user SET display_name = nickname WHERE display_name IS NULL;
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0;
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS created_at DATETIME NULL;
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS updated_at DATETIME NULL;
UPDATE purchase_request SET created_at = COALESCE(created_at, create_time), updated_at = COALESCE(updated_at, update_time);
ALTER TABLE purchase_request_item ADD COLUMN IF NOT EXISTS product_name VARCHAR(200) NULL;
UPDATE purchase_request_item i JOIN product p ON p.id=i.product_id SET i.product_name=p.name WHERE i.product_name IS NULL;
ALTER TABLE approval_record ADD COLUMN IF NOT EXISTS action VARCHAR(20) NULL;
ALTER TABLE approval_record ADD COLUMN IF NOT EXISTS created_at DATETIME NULL;
UPDATE approval_record SET action = COALESCE(action, result), created_at = COALESCE(created_at, approval_time);
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0;
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS created_at DATETIME NULL;
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS updated_at DATETIME NULL;
ALTER TABLE purchase_order_item ADD COLUMN IF NOT EXISTS product_name VARCHAR(200) NULL;
CREATE TABLE IF NOT EXISTS inventory_stock (
  product_id BIGINT PRIMARY KEY, quantity INT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS inventory_transaction (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, product_id BIGINT NOT NULL, type VARCHAR(10) NOT NULL,
  quantity INT NOT NULL, reference_no VARCHAR(64) NOT NULL, operator_id BIGINT, created_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS sys_product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, sku VARCHAR(64) UNIQUE, name VARCHAR(200) NOT NULL,
  category VARCHAR(100), unit VARCHAR(20), reference_price DECIMAL(12,2), stock INT DEFAULT 0, active TINYINT DEFAULT 1
);
CREATE TABLE IF NOT EXISTS sys_supplier (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, code VARCHAR(64) UNIQUE, name VARCHAR(200) NOT NULL,
  contact VARCHAR(100), phone VARCHAR(30), address VARCHAR(255), active TINYINT DEFAULT 1
);
INSERT IGNORE INTO sys_product(id,sku,name,category,unit,reference_price,stock) VALUES
 (1,'SKU-1001','办公笔记本电脑','办公设备','台',5999,20),(2,'SKU-1002','人体工学办公椅','办公家具','把',899,50);
INSERT IGNORE INTO sys_supplier(id,code,name,contact,phone,address) VALUES
 (1,'SUP-001','华夏办公设备有限公司','张三','13800000001','上海市浦东新区'),(2,'SUP-002','优品供应链有限公司','李四','13800000002','深圳市南山区');
