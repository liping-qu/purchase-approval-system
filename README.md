# 企业采购与审批管理系统

面向中小企业内部采购的前后端分离系统，覆盖「员工提交申请 → 部门负责人审批 → 采购下单 → 仓库入库 → 采购完成」全流程。

> **先看这一句**：主流程可跑通，升级 14–17（并发语义、幂等、错误码、分页/明细/留痕）已完成并有实测证据；
> **仍有 4 项没做**（审计字段、Service 拆分、死枚举、N 线程压测），详见「十三、未完成与已知限制」。
> 本文档只写已经落在代码里、且能被命令验证的行为，不描述"计划中"的能力。

---

## 一、现在能做到什么（每一条都有验证方式）

| 能力 | 状态 | 证据 |
| --- | --- | --- |
| 申请 → 审批 → 下单 → 入库 → 完成 全链路 | 可用 | 37 项 HTTP 冒烟全通 |
| 两人同时审批同一张单，只有一个成功 | 可用 | 冒烟第 3 组：并发请求一 200 一 409 |
| 重复点击入库，库存只加一次 | 可用 | 冒烟第 5 组：`qtyBefore → qtyAfter` 只 +1 |
| 重复点击建单，不会产生两张草稿 | 可用 | 冒烟第 1 组：同 key 两次返回同一 id |
| 提交后不能再编辑/删除 | 可用 | 冒烟第 2 组：均 409 |
| 订单/流水分页、订单明细、审批留痕 | 可用 | 冒烟第 4/6 组 |
| 参数越界返回字段级提示 | 可用 | 冒烟第 7 组：501 字事由 / 数量 10000 → 400 |
| 管理端 CRUD、操作日志、供应商 | 可用 | 早期升级 11–13 |
| 库存流水前端页面 | **无** | 接口已支持分页，页面只展示库存快照 |
| N 线程并发压测 / QPS 数字 | **未做** | 目前只有 2 并发冒烟 + 单测，见第十三节 |

---

## 二、技术栈

- 后端：Java 17、Spring Boot 3.3.5、Spring MVC、MyBatis-Plus 3.5.7、MySQL 8、Redis、Spring Security 6、JWT（jjwt 0.12）、BCrypt、Bean Validation、Spring AOP、Flyway、springdoc
- 前端：Vue 3、Vite 5、Element Plus、Axios、Pinia、Vue Router
- 部署：Docker Compose（MySQL + Redis + 后端 + Nginx）

---

## 三、目录结构

```text
backend/src/main/java/com/enterprise/procurement
  admin/          用户、部门、角色、权限、菜单、配置、操作日志
  business/       PurchaseBusinessService（主流程）、DocumentNoGenerator、IdempotencyService
  controller/     REST 接口（主流程在 controller/v2）
  dto/            ApiResponse、PageResult、请求体与展示态 DTO
  exception/      ErrorCode、BusinessException、ForbiddenException、StateConflictException、全局处理器
  persistence/    entity（表镜像 + 展示行）、mapper（含手写 SQL）
  security/       JWT 认证、鉴权快照缓存
  config/         SecurityConfig、MybatisPlusConfig、JacksonConfig、TraceIdFilter
  aspect/         @OperationLog 操作日志
  resources/db/migration/  Flyway 版本化脚本（表结构唯一来源）
frontend/src      Vue 页面、路由、Pinia、API 封装
```

---

## 四、接口清单

所有接口统一前缀 `/api`，除登录外都需要 `Authorization: Bearer <token>`。

### 4.1 认证

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/auth/login` | 返回**扁平**结构 `{token,userId,username,displayName,roles}` |
| GET | `/auth/me` | 当前用户 |

### 4.2 采购申请

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/purchase-requests?keyword=&status=` | 登录用户 | 只看**本人**的单 |
| GET | `/purchase-requests/{id}` | 见可见性规则 | 带明细 `items` |
| GET | `/purchase-requests/{id}/items` | 同上 | 只要明细 |
| GET | `/purchase-requests/{id}/approvals` | 同上 | **审批留痕**（谁、何时、通过/驳回、批注） |
| POST | `/purchase-requests` | 登录用户 | 建草稿；`submit:true` 时**同事务**完成提交；支持 `Idempotency-Key` |
| PUT | `/purchase-requests/{id}` | 本人 + 草稿 | 只改事由/明细，条件更新 |
| DELETE | `/purchase-requests/{id}` | 本人 + 草稿 | 条件删除 |
| POST | `/purchase-requests/{id}/submit` | 本人 | DRAFT→PENDING；已 PENDING 时**幂等成功** |
| POST | `/purchase-requests/{id}/withdraw` | 本人 | PENDING→DRAFT |

可见性（不满足一律 **404**，不暴露记录是否存在）：ADMIN 全部；申请人本人；同部门 MANAGER；PURCHASER 仅已审批通过（APPROVED/PURCHASING/COMPLETED）。

### 4.3 审批

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/approvals/pending?keyword=` | ADMIN/MANAGER | 本部门待审；**无部门 → 返回空**，不是全量 |
| POST | `/approvals/{id}` | ADMIN/MANAGER | body `{action, comment}`，action 只能是 APPROVE 或 REJECT；不能审本人、不能跨部门 |

### 4.4 订单

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/purchase-orders?page=1&size=20&status=&keyword=&supplierId=` | 登录用户 | 分页；采购/仓库/管理员看全部，其余按申请人/部门裁剪 |
| GET | `/purchase-orders/{id}` | 同上 | 详情 **含 items** |
| GET | `/purchase-orders/orderable` | ADMIN/PURCHASER | 已审批通过且尚无订单的申请 |
| POST | `/purchase-orders` | ADMIN/PURCHASER | body `{requestId,supplierId}`；body 缺 `requestId` 时兼容 `?requestId=` |
| POST | `/purchase-orders/{id}/delivered` | ADMIN/WAREHOUSE | 确认入库：订单状态 + 库存 + 流水 + 申请单完成，同一事务 |

### 4.5 库存

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/inventory/stock` | 登录用户 | 库存快照（含最低库存、更新时间） |
| GET | `/inventory/transactions?page=&size=&productId=&type=&from=&to=` | 登录用户 | 流水分页；`to` 含当天；带商品名/操作人 |
| POST | `/inventory/{productId}/adjust` | ADMIN/WAREHOUSE | 手工出入库，`quantity` 正入负出；支持 `Idempotency-Key` |

### 4.6 管理端

`/admin/users`、`/admin/departments`、`/admin/roles`、`/admin/permissions`、`/admin/menus`、`/admin/operation-logs`（分页 `{records,total,page,size}`）。

---

## 五、统一响应与错误码

```json
{
  "success": true,
  "code": "OK",
  "message": "操作成功",
  "data": {},
  "traceId": "9f3c1a2b4d5e"
}
```

- `traceId` **必须在最后一位**，同时出现在响应头 `X-Trace-Id` 与日志 MDC 中；有单测 `traceIdStaysLastInJson` 钉死字段顺序。
- 失败时 `data` 为空；Bean Validation 失败时 `data` 为字段级提示 `{字段: 文案}`。
- 前端按 `code` 决定交互，不再拿中文 message 做字符串匹配。

| code | HTTP | 含义 |
| --- | --- | --- |
| `OK` | 200 | 成功 |
| `BAD_REQUEST` | 400 | 参数不合法（手写校验） |
| `VALIDATION_FAILED` | 400 | Bean Validation 失败，`data` 带字段提示 |
| `AUTH_REQUIRED` | 401 | 未登录 / token 失效 |
| `FORBIDDEN` | 403 | 已登录但无权（非本人、跨部门、角色不足） |
| `NOT_FOUND` | 404 | 不存在，或存在但无权可见 |
| `STATE_CONFLICT` | 409 | 基于旧状态，被别人抢先 |
| `DUPLICATE_KEY` | 409 | 唯一键冲突兜底 |
| `ORDER_ALREADY_DELIVERED` | 409 | 订单已入库 |
| `REQUEST_ALREADY_ORDERED` | 409 | 该申请已生成订单 |
| `STOCK_INSUFFICIENT` | 409 | 库存不足（含并发抢输） |
| `IDEMPOTENCY_KEY_REUSED` | 409 | 同一幂等键用于不同请求内容 |
| `IDEMPOTENCY_IN_PROGRESS` | 409 | 同键请求正在处理 |
| `INTERNAL_ERROR` | 500 | 未预期异常，message 内嵌 traceId 并落 ERROR 堆栈 |

---

## 六、并发与一致性

### 6.1 四道防线

| 机制 | 位置 | 挡住什么 |
| --- | --- | --- |
| 状态机 `canGoTo` | `model/Enums` | 写错的代码（如 PENDING→COMPLETED） |
| CAS 条件更新 `WHERE status=? AND version=?` | `*Mapper.transition` | 并发下第二个写入者 |
| 唯一键 `uk_order_request`、`uk_inventory_tx_ref` | V6 迁移 | 绕过 service 的重复写入 |
| 原子累加 / 条件扣减 | `InventoryStockMapper` | 库存丢失更新、超卖 |

### 6.2 场景一：两人同时审批

```
A: SELECT version=2 (快照读，不加锁)
B: SELECT version=2
A: UPDATE ... WHERE status='PENDING' AND version=2  → 1 行，持行锁
B: 同一条 UPDATE 阻塞
A: INSERT approval_record; COMMIT
B: 锁释放后按最新版本重判 WHERE → 0 行 → 抛 StateConflictException → 409，整笔回滚
```

关键点：`UPDATE` 是**当前读**，不会用 B 一开始的快照；所以"两边都读到旧值"不影响判断。
失败方拿到 `409 STATE_CONFLICT`，文案与 `currentStatus` 可以直接展示。

### 6.3 场景二：两人同时入库

`delivered` 第一句就是 `CREATED→DELIVERED` 的 CAS，输的一方**不会执行任何库存/流水写入**。
重复点同一张已入库的单，前置检查直接返回 `409 ORDER_ALREADY_DELIVERED`，而不是"非法状态流转: DELIVERED → DELIVERED"。

### 6.4 场景三：多订单同时加同一商品

- 累加用 `INSERT ... ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity)`：行锁 + 服务端自增，不会丢更新。
- 明细先按 `productId` **归并**，再用 `TreeMap` **按商品号升序**入库：所有事务按同一顺序取行锁，消除交叉持锁导致的 InnoDB 死锁（ERROR 1213）。
- 出库用 `UPDATE ... WHERE quantity >= ?`：判断与扣减在同一条语句、同一个行锁里，返回值 0 即"抢输了"→ 409。

### 6.5 场景四：重复点击 / 超时重试

见第七节。

### 6.6 为什么没有 Redis 分布式锁

四个场景的正确性都能被**单行 CAS + 唯一索引 + 原子增量**覆盖，分布式锁只会新增故障点：
Redis 挂掉时审批/入库是否要停？主从异步复制下锁是否可能双持？这两个问题没有"加个锁"就能解决的答案。
真正需要锁的是"没有某一行可以 CAS"的场景（如定时任务互斥），本项目目前没有定时任务。
Redis 当前只用于**鉴权快照缓存**（300s TTL，写操作显式失效，异常降级查库），不参与任何正确性判断。

---

## 七、幂等设计

### 7.1 哪些接口需要

| 接口 | 幂等手段 | 原因 |
| --- | --- | --- |
| 建申请 | `Idempotency-Key` + `sys_idempotency` | 同一用户合法地可以有两张相同事由的单，没有天然唯一键 |
| 手工调库存 | 同上 | 每次生成新单号，`uk_inventory_tx_ref` 兜不住 |
| 提交申请 | 状态判断 | 已是 PENDING 且是本人 → 原样返回 200 |
| 创建订单 | `uk_order_request` | 一个申请最多一张订单 |
| 确认入库 | 订单状态 CAS + `uk_inventory_tx_ref` | 重复请求不会二次入库 |

### 7.2 幂等表

```sql
sys_idempotency(
  idem_key VARCHAR(64), user_id BIGINT,          -- 复合主键，作用域到人
  endpoint VARCHAR(48),                          -- CREATE_REQUEST / ADJUST_STOCK
  request_hash CHAR(64),                         -- 请求内容 SHA-256
  result_id BIGINT,                              -- 业务主键，重试时回放
  created_at DATETIME, expires_at DATETIME       -- 24h
)
```

流程：**同一事务内**先 `INSERT` 占位 → 执行业务 → 回写 `result_id` → 提交。
并发重复请求不会"先查后插"，而是阻塞在主键索引上，等前一个事务结束后拿到重复键，再回查：

- 命中且 `request_hash` 一致 → **回放**上次结果（建单返回原单据，调库存直接成功）；
- 命中但 hash 不同 → `409 IDEMPOTENCY_KEY_REUSED`（客户端 bug，不能当重试放过）；
- 记录在但 `result_id` 为空 → `409 IDEMPOTENCY_IN_PROGRESS`，绝不重复执行；
- 查不到 → 前一个事务已回滚，本次按新请求继续。

业务回滚时占位一起回滚，不会出现"键被占了但单没建成"。
前端在**打开弹窗时**生成 key（不是每次请求生成），失败重试复用同一个。

> **已知缺口**：过期行需要人工/定时清理（V10 里只有一条注释 SQL），目前没有定时任务。

---

## 八、分页

- `MybatisPlusConfig` 注册 `PaginationInnerInterceptor(DbType.MYSQL)`，`maxLimit = 200`。
  改造前项目**从未注册该插件**，而管理端两个接口都在用 `selectPage`：不拼 `LIMIT`、不执行 count，返回全表且 `total` 恒为 0。
- 统一响应形状：`{records,total,page,size}`，与前端分页组件一致。
- 服务层把 `size` 再夹一次 1–100，防止 `?size=999999`。
- 已分页：订单列表（状态/关键词/供应商过滤）、库存流水（商品/类型/日期区间）、管理端用户与操作日志。

---

## 九、数据库迁移（Flyway）

| 脚本 | 作用 |
| --- | --- |
| `V1__baseline_schema.sql` | 全新安装的权威结构 |
| `V2__align_legacy_schema.sql` | 存量库对齐，幂等可重复执行 |
| `V3__add_query_indexes.sql` | 查询索引 |
| `V4__seed_reference_data.sql` | 部门/角色/权限/演示账号/商品/供应商 |
| `V5__add_user_token_version.sql` | 改密/删用户后旧 JWT 立即失效 |
| `V6__add_business_sequence_and_unique_keys.sql` | 单号序列 + 两条业务唯一键 |
| `V7__extend_operation_log.sql` | 操作日志补 operator_name/biz_id/cost_ms |
| `V8__extend_inventory_stock.sql` | 库存补 min_quantity / updated_at |
| `V9__add_status_check_constraints.sql` | 11 个状态字段 CHECK 约束（应用层之外的第二道防线） |
| `V10__add_idempotency_table.sql` | 幂等键表 |

- 全新空库：V1→V10 依次执行（实测 1.9s）。
- 存量库（有表但无 `flyway_schema_history`）：`baseline-on-migrate` 记为 V1 基线，只跑 V2 之后，**不 DROP 表**。
- 已执行脚本不可修改（checksum 会拒绝启动）；开发期确需修改用 `flyway:repair` 或重建库。

---

## 十、启动与验证

### 10.1 本地启动（后端）

`JWT_SECRET` 没有默认值（默认密钥等于公开密钥），必须提供：

```powershell
cd backend
$env:JWT_SECRET = "local-dev-secret-0123456789-abcdefghijklmnop"
$env:MYSQL_DATABASE = "procurement"
$env:MYSQL_PASSWORD = "你的密码"
mvn spring-boot:run
```

若 `mvn package` 报 `Unable to rename ...jar.original`，说明有旧的 java 进程占着 jar：
`netstat -ano | findstr :8080` 找到 PID 后结束进程；或跳过打包直接跑 classes：

```bash
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp "target/classes;$(cat cp.txt)" com.enterprise.procurement.ProcurementApplication \
  --server.port=8097 --spring.datasource.password=*** --app.jwt.secret=***
```

### 10.2 前端

```bash
cd frontend && npm install && npm run dev
```

`vite.config.js` 已配置 `/api` 代理（默认 `127.0.0.1:8080`，可用 `VITE_PROXY_TARGET` 覆盖），不需要手设 `VITE_API_BASE_URL`。

### 10.3 Docker

```powershell
docker compose up -d --build   # 前端 http://localhost:8081，后端 http://localhost:8080
```

### 10.4 测试与验证

| 方式 | 规模 | 命令 |
| --- | --- | --- |
| 单元测试（Mockito，不连库） | **87 例** | `mvn -f backend/pom.xml test` |
| HTTP 端到端冒烟（真实 MySQL + 并发） | **37 项** | 见下 |

冒烟覆盖：幂等建单、建单即提交、提交后编辑/删除 409、并发审批一胜一 409、审批留痕、重复下单/重复入库精确码、库存只加一次、分页 total/size/过滤、订单明细、流水 JOIN、校验边界。

---

## 十一、演示账号

Flyway 首次启动写入，统一口令 `admin123`：

| 账号 | 角色 |
| --- | --- |
| `admin` | 系统管理员 |
| `employee` | 普通员工（部门 1） |
| `manager` | 部门负责人（部门 1，可审 employee 的单） |
| `purchaser` | 采购人员 |
| `warehouse` | 仓库管理员 |

生产环境请立即改密并更换 `JWT_SECRET`。

### 安全说明（公开仓库必读）

- `JWT_SECRET` **没有内置默认值**：不注入则启动失败。这是刻意的——默认密钥等于公开密钥。
- `docker-compose.yml` 的 MySQL 口令写成 `${MYSQL_ROOT_PASSWORD:-root}`：`root` 只是本地开发默认值，
  复制 `.env.example` 为 `.env` 并改成强口令即可覆盖，不需要改 compose 文件。
- `application.yml` 的 `MYSQL_PASSWORD:root` 同理，仅本地开发默认值；生产用环境变量覆盖。
- `V4` 种子里的 `admin123` 是**演示账号**的 BCrypt 哈希，不是明文口令泄露，但公开部署前必须改密。
- 仓库里没有 `.env`、私钥、访问令牌；`.gitignore` 已排除 `node_modules`、`target`、`dist`、`.env`。

---

## 十二、升级记录

| 提交 | 内容 |
| --- | --- |
| `d8144aa` 升级14 | 并发冲突语义：新增 `StateConflictException`→409；草稿编辑/删除改条件更新（修掉 PENDING 被写回 DRAFT 的状态覆盖）；入库按商品号升序加锁（消死锁） |
| `f4a4d4c` 升级15 | V10 幂等表 + `IdempotencyService`；建单/调库存支持 `Idempotency-Key`；提交改幂等成功 |
| `607e99e` 升级16 | `ErrorCode`/`BusinessException`/`ForbiddenException`；`ApiResponse` 加 `code`；越权 400→403；DTO 补 `@Size`/`@Digits` 等边界；建单 `submit:true` 同事务提交 |
| `f258b40` 升级17 | 注册分页插件（顺带修好管理端假分页）；订单与流水分页；订单详情含明细；审批留痕接口；两处错误码语义修正（冒烟实测发现） |

早期升级 1–13：删除内存假实现、Flyway 基线、索引、种子数据、token_version、单号序列与唯一键、操作日志补全、库存字段、CHECK 约束、主流程三页与供应商/管理页字段对齐、Vite 代理。

---

## 十三、未完成与已知限制

按优先级排列，**这些都不是"顺手就有的"，别当成已完成**：

1. **审计字段缺失**：`purchase_order.creator_id` 有列但从不写入；订单无 `delivered_at`/`delivered_by`；`inventory_transaction` 无 `balance_after`（对账要重算）。目前入库人只能从 `operation_log` 间接查。
2. **`PurchaseBusinessService` 约 600 行**，同时承担申请/审批/订单/库存/可见性/状态机六类职责，尚未拆分。
3. **死枚举**：`UserRole.APPROVER`、`ApplicationStatus.CANCELLED` 无任何入口。
4. **并发验证只到 2 并发**：37 项冒烟是真实 HTTP + 真实 MySQL，但没有 20/50 线程压测，也就没有 QPS、锁等待、死锁率这类数字。单测里的并发是用 Mockito 模拟 CAS 返回 0，不等价于真并发。
5. **库存流水没有前端页面**：接口支持分页与过滤，`Inventory.vue` 只展示库存快照。
6. **无金额分级审批**：只有一级（部门负责人），>N 万走总经理这类规则需要新增审批流表。
7. **幂等表无自动清理**：过期行会累积，需要定时任务（当前项目没有调度器）。
8. **仓库换行符不统一**：部分主文件是 CRLF、测试文件是 LF，尚未做全仓 normalize。
9. **没有 CI 配置**：测试与构建目前靠本地手动执行。

---

## 十四、Git

```bash
git log --oneline            # 能看到 升级1..升级17 的逐条提交
git show <hash> --stat       # 单次升级的改动范围
```

提交信息统一写「改前问题 / 改法 / 实测数字」，便于回溯每一步的动机与验证结果。
