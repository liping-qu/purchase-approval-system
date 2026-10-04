CREATE TABLE IF NOT EXISTS sys_menu (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, parent_id BIGINT NULL, name VARCHAR(64) NOT NULL,
 path VARCHAR(255), icon VARCHAR(128), permission_code VARCHAR(128), sort_order INT DEFAULT 0,
 status TINYINT NOT NULL DEFAULT 1, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS sys_config (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, config_key VARCHAR(128) NOT NULL UNIQUE,
 config_value TEXT NOT NULL, description VARCHAR(255),
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
INSERT IGNORE INTO sys_menu(id,parent_id,name,path,icon,permission_code,sort_order) VALUES
 (1,NULL,'系统管理','/admin','Setting','user:manage',1),(2,1,'用户管理','/admin/users','User','user:manage',1),
 (3,1,'角色管理','/admin/roles','UserFilled','role:manage',2),(4,1,'菜单管理','/admin/menus','Menu','role:manage',3);
INSERT IGNORE INTO sys_config(config_key,config_value,description) VALUES ('system.name','企业采购与审批管理系统','系统名称');
