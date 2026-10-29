-- =============================================================================
-- V5 用户令牌版本：让 JWT 具备"改密/强制下线即失效"的能力
--
-- 背景：JWT 是无状态凭证，签发后在过期前一直有效。改密码、管理员禁用账号、
-- 用户主动登出，都无法让已发出的 token 立即作废——这是 JWT 方案最常被追问的短板。
--
-- 方案：给用户加一个单调递增的 token_version。
--   * 登录时把当前值写进 claim（tv），并在响应里返回；
--   * 鉴权过滤器每次用 claim 与库中值比对，不一致直接视为未登录；
--   * 改密 / 强制下线时 token_version = token_version + 1，此前签发的所有 token 同时失效。
--
-- 代价：每个请求多一次按主键的点查（走 PRIMARY，成本可控）。
-- 后续 P7 会把"用户 -> tokenVersion"放进 Redis，把这步变成内存读，并给出压测对比。
-- =============================================================================

DROP PROCEDURE IF EXISTS sp_add_column;

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

DELIMITER ;

CALL sp_add_column('sys_user', 'token_version',
     'INT NOT NULL DEFAULT 0 COMMENT ''令牌版本，改密/强制下线时 +1 使旧 token 失效''');

DROP PROCEDURE IF EXISTS sp_add_column;
