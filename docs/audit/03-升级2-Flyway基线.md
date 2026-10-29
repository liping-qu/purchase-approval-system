# 升级 2：Flyway 基线，把两份互相矛盾的 DDL 收敛成唯一结构来源

> 分支：`refactor-hardening`
> 状态：**代码完成；全新库安装、存量库升级、脏库幂等三种路径均已实测通过**

---

## 1. 这一步解决什么问题

审计报告 D-00：项目里有两份都"会建表"的脚本，而且**谁先跑决定最终结构**。

| 脚本 | 谁执行 | 内容 | 在 MySQL 8 上的结果 |
|---|---|---|---|
| `sql/procurement.sql` | Docker 容器 `/docker-entrypoint-initdb.d` | 建表 + 种子数据 + 21 条 `ALTER ... ADD COLUMN IF NOT EXISTS` | **第 28 条语句语法错误**，后续 21 条全部未执行 |
| `backend/src/main/resources/schema.sql` | 应用启动 `spring.sql.init` | 只有一部分表，无 `version` 列 | 与上面互相覆盖 |
| `config/DemoDataInitializer.java` | 应用启动 | 重置演示账号口令 | 掩盖了种子口令无效的问题 |

复现证据（在 MySQL 8.4.11 上执行旧脚本）：

```
### ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS display_name VARCHAR(100) NULL
ERR You have an error in your SQL syntax ... near 'IF NOT EXISTS display_name VARCHAR(100) NULL'
### ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0
ERR You have an error in your SQL syntax ... near 'IF NOT EXISTS version INT NOT NULL DEFAULT 0'
...（此后 19 条 ALTER 全部报同样的错）
```

`ADD COLUMN IF NOT EXISTS` 是 **MariaDB 语法**，MySQL 8 不支持。直接后果：

- `purchase_request` / `purchase_order` 没有 `version` 列 → 乐观锁 SQL `WHERE status=? AND version=?` 执行时报 `Unknown column 'version'` → **提交/审批/入库全链路 500**；
- `approval_record` 没有 `action` 列、`result` 却是 `NOT NULL` 且实体从不写它 → **审批写库直接失败**；
- `purchase_order.creator_id` 是 `NOT NULL` 无默认值，而实体里根本没有这个字段 → **创建订单必定失败**；
- 报错发生在容器初始化阶段，`docker compose up` 之后系统"看起来起来了"，实际是个残废库。

也就是说：**这个项目在干净机器上按 README 跑一遍，核心流程是走不通的**。这不是"代码风格问题"，是可用性问题。

## 2. 方案与取舍

**选择：引入 Flyway，把结构定义收敛到 `db/migration` 下的版本化脚本，其余两份 DDL 与初始化器全部下线。**

不选另外两种方案的理由：

1. **"把 `sql/procurement.sql` 改成 MySQL 8 语法"** —— 治标。脚本仍然是"只能跑一次"的一次性脚本，没有版本记录，改一次结构就要人工判断目标库是哪一代，问题会在下一次改表时原样复发。
2. **"引入 Liquibase"** —— 更重（XML/YAML changelog、学习成本高），本项目用纯 SQL 迁移足够，且 SQL 迁移对面试讲解更直观。

**存量库不能丢数据**，因此启用 `baseline-on-migrate`：

- 空库 → 从 V1 开始全量执行；
- 已有表但没有 `flyway_schema_history` 的库 → 把当前状态记为 `V1` 基线，**跳过 V1**，只执行 V2 起的增量。

这条策略的代价是 V1 与 V2 的职责必须切干净：**V1 只描述"全新安装应该长什么样"，V2 负责"把任何历史态修成 V1 的样子"**。V2 因此必须是幂等的。

### 幂等怎么写（关键取舍）

MySQL 8 既没有 `ADD COLUMN IF NOT EXISTS`，也没有 `CREATE INDEX IF NOT EXISTS`。三种可选写法：

| 写法 | 评价 |
|---|---|
| `information_schema` 判断 + 存储过程 + 动态 SQL | **采用**。语义最直白，一次编写多处复用 |
| `SET @s = IF(EXISTS(...), 'SELECT 1', 'ALTER ...')` + `PREPARE` | 可行，但每条 DDL 要写 4 行样板，40 条 DDL 就是 160 行噪音 |
| 应用层用 JDBC 查元数据再执行 | 把结构变更逻辑从 SQL 挪进 Java，迁移脚本不再自解释，排除 |

存储过程版的核心：

```sql
CREATE PROCEDURE sp_add_column(IN t VARCHAR(64), IN c VARCHAR(64), IN ddl VARCHAR(255))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = t AND column_name = c) THEN
        SET @ddl = CONCAT('ALTER TABLE `', t, '` ADD COLUMN `', c, '` ', ddl);
        PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;
    END IF;
END$$
```

另配一个 `sp_exec_if_column(t, c, stmt)`：只有"参照列存在"时才执行回填语句。因为全新库没有 `create_time`、`approval_time` 这些历史列，直接 `UPDATE ... SET created_at = create_time` 会在新库上报 `Unknown column`。用"列存在性"做开关，同一份脚本才能在两种库上都跑通。

> 关于 `DELIMITER $$`：它是 mysql 客户端指令而非服务端语法，但在 Flyway 的 MySQL 解析器里可用。本次三种路径都实测通过，未出现解析错误。

## 3. 改动清单

### 3.1 新增（4 个迁移脚本 + 1 份归档）

| 文件 | 作用 |
|---|---|
| `db/migration/V1__baseline_schema.sql` | 19 张表的权威结构：utf8mb4、InnoDB、外键、唯一键、`version` 乐观锁列、`created_at/updated_at` |
| `db/migration/V2__align_legacy_schema.sql` | 存量库对齐：补列、补表、历史数据回填、把 `creator_id`/`result`/`created_at` 收紧或放宽到与 V1 一致 |
| `db/migration/V3__add_query_indexes.sql` | 4 个复合索引（见 §3.3） |
| `db/migration/V4__seed_reference_data.sql` | 部门/角色/权限/菜单/配置/演示账号/商品/供应商；末尾修复存量库的无效口令哈希 |
| `docs/audit/legacy/procurement.sql.retired` | 旧 Docker 初始化脚本，归档为审计证据 |

### 3.2 修改

| 文件 | 改动 |
|---|---|
| `backend/pom.xml` | 新增 `flyway-core`、`flyway-mysql`（MySQL 方言支持单独成包，缺了会报 "Unsupported Database: MySQL"） |
| `application.yml` | 开启 Flyway；`locations`、`baseline-on-migrate: true`、`baseline-version: 1`、`fail-on-missing-locations: true`；`spring.sql.init.mode: never` |
| `docker-compose.yml` | 移除 `./sql/procurement.sql` 的 initdb 挂载，容器只建空库，表结构交给应用 |
| `README.md` | 新增"数据库版本管理（Flyway）"一节，说明三条执行路径与 checksum 约束 |

### 3.3 为什么是这 4 个索引

`V3` 只加"有明确查询对应"的索引，不为凑数而加：

| 索引 | 对应查询 |
|---|---|
| `idx_request_status_dept (status, department_id)` | 审批待办 `WHERE status='PENDING' AND department_id=?` |
| `idx_request_applicant_created (applicant_id, created_at)` | "我的申请"按时间倒序分页 |
| `idx_order_created (created_at)` | 订单列表默认按时间倒序 |
| `idx_inventory_tx_created (created_at)` | 库存流水时间区间筛选 |

**没加** `purchase_request(reason)` 之类的全文/模糊索引：`LIKE '%x%'` 用不上 B+Tree 前缀，加了也是白占空间，这类需求应该走全文索引或搜索引擎，属于过度设计。

### 3.4 删除

| 文件 | 原因 |
|---|---|
| `backend/src/main/resources/schema.sql` | 与 V1 重复且不完整，`spring.sql.init` 已关闭 |
| `config/DemoDataInitializer.java` | 启动时改数据的反模式；种子数据归 V4 |
| `sql/procurement.sql` | MariaDB 语法，MySQL 8 上必然失败，留着就会被再次误用 |

## 4. 实测

环境：MySQL 8.4.11（`127.0.0.1:3307`），JDK 17，`java -jar` 启动，只改 `MYSQL_PORT/MYSQL_DATABASE` 三个环境变量。

### 4.1 路径 A：全新空库

```
Migrating schema `procurement_fresh` to version "1 - baseline schema"
Migrating schema `procurement_fresh` to version "2 - align legacy schema"
Migrating schema `procurement_fresh` to version "3 - add query indexes"
Migrating schema `procurement_fresh` to version "4 - seed reference data"
Successfully applied 4 migrations ... now at version v4 (execution time 00:01.051s)
Started ProcurementApplication in 8.898 seconds
```

结构核对（`information_schema`）：

- 19 张业务表 + `flyway_schema_history`，无遗留 `product` / `inventory` 旧表；
- `purchase_request.version / created_at / updated_at` 均为 `NOT NULL`，默认值 `0 / CURRENT_TIMESTAMP`；
- `purchase_order.creator_id` 可空、`version` 非空；
- `approval_record.action` 非空、`result` 可空；
- 4 个新索引全部存在。

种子数据：5 用户 / 5 角色 / 8 权限 / 16 条角色权限 / 4 菜单 / 2 商品 / 2 供应商 / 1 系统配置。

### 4.2 路径 B：存量库（旧脚本跑出来的残缺结构）

用退役脚本在 `procurement_legacy` 上复现"第 28 条失败"的真实状态（`purchase_request` 无 `version`，`approval_record` 无 `action`，`purchase_order.creator_id` 为 `NOT NULL`），再启动应用：

```
Creating Schema History table `procurement_legacy`.`flyway_schema_history` with baseline ...
Successfully baselined schema with version: 1
Current version of schema `procurement_legacy`: 1
Migrating schema `procurement_legacy` to version "2 - align legacy schema"
Migrating schema `procurement_legacy` to version "3 - add query indexes"
Migrating schema `procurement_legacy` to version "4 - seed reference data"
Successfully applied 3 migrations ... now at version v4
```

`V1` 被跳过，没有 DROP 任何表。对齐结果：

```
purchase_request : id, request_no, applicant_id, department_id, total_amount, reason, status,
                   apply_time, create_time, update_time, version, created_at, updated_at
purchase_order   : ... creator_id, status, create_time, update_time, version, created_at, updated_at
approval_record  : id, request_id, approver_id, result, comment, approval_time, action, created_at
sys_user         : ... create_time, update_time, display_name
version/created_at/updated_at -> NOT NULL       creator_id -> NULL       result -> NULL
```

### 4.3 路径 C：半对齐的脏库

`procurement_verify` 是之前测试留下的库，既有旧的 `product` / `supplier` / `inventory` / `inventory_record`，又已经手工补过一部分列。启动后同样 baselined 到 V1，V2 的判断全部命中"已存在"，实际是空跑，V3/V4 正常执行 → **幂等成立**。

### 4.4 业务闭环（两条路径都跑）

| 步骤 | 全新库 | 存量库 |
|---|---|---|
| `employee` 建草稿 | 200 `DRAFT` | 200 `DRAFT` |
| 提交 | 200 `PENDING` | 200 `PENDING` |
| 重复提交 | **400 申请状态已变更** | **400 申请状态已变更** |
| `manager` 审批 | 200 `APPROVED` | 200 `APPROVED` |
| 重复审批 | **400 申请状态已变更** | **400 申请状态已变更** |
| `purchaser` 下单 | 200 `CREATED` | 200 `CREATED` |
| `warehouse` 入库 | 200 `DELIVERED` | 200 `DELIVERED` |
| 重复入库 | **400 订单已处理** | **400 订单已处理** |
| 申请详情 | 200 `PURCHASING` | 200 `PURCHASING` |

落库核对：`inventory_stock` = `(product_id=1, quantity=2, version=1)`，`inventory_transaction` = 一条 `IN 2`，`reference_no` 等于订单号，`operator_id=5`；`purchase_request.version=3`（提交/审批/下单各 +1）；`approval_record.action=APPROVE`、`result=NULL`。

## 5. 过程中发现并顺手修掉的新问题

### D-26 存量库迁移后**没人能登录**（新发现，已修）

`DemoDataInitializer` 删掉之后，存量库暴露出一个被掩盖很久的问题：`sql/procurement.sql` 里写进 `sys_user` 的口令是一串**从未参与过真实散列**的占位串，`BCryptPasswordEncoder.matches()` 对它永远返回 `false`。以前靠启动时重置口令掩盖，初始化器一删，存量的 5 个账号全部登不进去。

实测对照（同一份 jar，只换库）：

```
port=8091 (全新库)  http=200 success=true   message=操作成功
port=8092 (存量库)  http=200 success=false  message=用户名或密码错误
```

修复方式是在 `V4` 末尾加一条**带条件的**更新，只在"口令仍等于旧占位值"时替换：

```sql
UPDATE sys_user SET password = '<已知口令的真实 BCrypt>'
WHERE password = '<旧占位值>';
```

为什么不直接 `UPDATE ... WHERE username='admin'`：那会把用户后来改过的口令也重置掉，是典型的"迁移脚本破坏生产数据"。带上旧值做条件，等价于只在"这行还是从没改过"时才修。

修复后两条路径都 `success=true`。

### 已知限制（本步不处理，留给后续）

1. **旧业务数据没有搬迁**。旧库的商品/供应商在 `product` / `supplier`，新代码读 `sys_product` / `sys_supplier`。本项目的旧数据是演示数据，V4 会重新种一遍，因此不做搬迁。若真实项目，需要补一个 `V5__migrate_legacy_catalog.sql` 做 `product → sys_product` 的映射（含 id 对照表），`purchase_request_item.product_name` 的回填也要改成"先查 `sys_product`，查不到回退 `product`"。
2. **`V4` 在存量库上会打印约 48 条 `Duplicate entry` WARN**。来源是 `INSERT IGNORE` 命中已有行，Flyway 把 MySQL 的 warning 打成 WARN，`success` 仍为 1。属预期噪音，已在 README 注明。想彻底安静可以改成 `INSERT ... SELECT ... WHERE NOT EXISTS (SELECT 1 FROM t)`，但可读性会明显下降，本步不做。
3. **改已执行过的迁移脚本会导致 checksum 校验失败**。本步开发过程中改过一次 `V4`（就是 D-26 的修复），两个测试库随即拒绝启动，只能重建。这条约束已写进 README。

## 6. 面试可以怎么讲

> 项目最初有两份 DDL，一份跑在 Docker 初始化，一份跑在应用启动，而且前者用了 MariaDB 的 `ADD COLUMN IF NOT EXISTS`，在 MySQL 8 上第 28 条就挂了，导致乐观锁的 `version` 列根本没建出来。表面看服务起来了，实际核心流程全是 500。
>
> 我用 Flyway 把结构收敛成唯一的版本化脚本：V1 是全新安装的权威结构，V2 专门处理存量库对齐，用 `information_schema` + 动态 SQL 做幂等，因为 MySQL 8 没有 `ADD COLUMN IF NOT EXISTS`。存量库通过 `baseline-on-migrate` 打基线，跳过 V1 只跑增量，不 DROP 表不丢数据。
>
> 三种库我都实测过：全新库、旧脚本跑残的库、半对齐的脏库。顺带发现迁移完存量库登不进去——旧脚本里的口令是个假哈希，以前靠启动时的初始化器重置掩盖了，删掉初始化器就暴露了。修法是带条件的 UPDATE，只在口令仍是旧占位值时才改，避免覆盖真实用户改过的口令。

**可能被追问的点：**

- *为什么不直接用 `docker-entrypoint-initdb.d`？* —— 它只在数据卷为空时执行一次，属于"一次性脚本"，没有版本记录，改表就要人工判断目标库代数；Flyway 有 `flyway_schema_history`，能回答"这个库现在是什么版本"。
- *`baseline-on-migrate` 会不会误判？* —— 它会跳过一个版本号以下的所有脚本。所以 `baseline-version` 必须等于"当前存量库最接近的那一版"，本项目取 1，且 V1 全部用 `CREATE TABLE IF NOT EXISTS`，即使被误执行也不会破坏数据。
- *如果 V2 在存量库上跑到一半失败？* —— MySQL 的 DDL 是隐式提交，做不到整段回滚。所以 V2 里每一步都必须是"可重复执行"的：补列幂等、回填带 `IS NULL` 条件、收紧 `NOT NULL` 前先 `UPDATE ... WHERE IS NULL`。失败后修好重跑即可，不需要人工回滚。
- *迁移和应用的启动顺序？* —— Flyway 在 `DataSource` 初始化后、MyBatis 使用前执行（Spring Boot 自动配置保证），失败会直接让 `ApplicationContext` 启动失败，不会出现"半套结构还在对外服务"。
