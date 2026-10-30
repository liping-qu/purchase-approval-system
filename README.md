# 企业采购与审批管理系统

面向中小企业内部采购场景的前后端分离系统，覆盖“员工提交申请 → 部门负责人审批 → 采购下单 → 仓库入库 → 采购完成”完整流程。

## 技术栈

- 后端：Java 17、Spring Boot 3.3、Spring MVC、MyBatis-Plus、MySQL 8、Redis、Spring Security 6、JWT、BCrypt、Validation、Spring AOP、OpenAPI
- 前端：Vue 3、Vite、Element Plus、Axios、Pinia、Vue Router
- 部署：Docker Compose、MySQL、Redis、Spring Boot、Nginx

## 项目结构

```text
backend/src/main/java/com/enterprise/procurement
  admin/          用户、部门、角色、权限、菜单、配置、日志
  business/       采购申请、审批、订单、入库事务
  controller/     REST 接口
  persistence/    MyBatis-Plus 实体与 Mapper
  security/       JWT 认证与权限
  aspect/         @OperationLog 操作日志
  resources/db/migration/  Flyway 版本化迁移脚本（唯一的表结构来源）
frontend/src      Vue 页面、路由、状态与 API
backend/pom.xml   后端唯一的 Maven 工程（根目录不再保留聚合 pom，避免两份定义漂移）
backend/Dockerfile、frontend/Dockerfile   两个镜像各自的定义（根目录不再放 Dockerfile）
frontend/nginx.conf  唯一一份 nginx 配置，docker-compose 直接挂载它
```

## 业务接口

认证：`POST /api/auth/login`、`GET /api/auth/me`。

采购申请：`/api/purchase-requests` 支持草稿创建、编辑、删除、提交、撤回和查询；审批：`GET /api/approvals/pending`、`POST /api/approvals/{id}`；订单：`POST/GET /api/purchase-orders`、`POST /api/purchase-orders/{id}/delivered`；库存：`GET /api/inventory/stock`、`GET /api/inventory/transactions`。

管理端接口位于 `/api/admin`，覆盖用户、部门、角色、权限、菜单、配置和操作日志。订单创建、入库以及库存流水使用事务和版本条件更新，避免重复入库造成库存不一致。

## 数据库版本管理（Flyway）

表结构与种子数据全部由 `backend/src/main/resources/db/migration` 下的版本化脚本管理，应用启动时自动执行：

| 脚本 | 作用 |
| --- | --- |
| `V1__baseline_schema.sql` | 全新安装的权威结构（19 张表、外键、唯一键、utf8mb4） |
| `V2__align_legacy_schema.sql` | 存量库对齐：补齐历史脚本漏建的列/表，历史数据回填，幂等可重复执行 |
| `V3__add_query_indexes.sql` | 查询索引（列表页、看板、流水页） |
| `V4__seed_reference_data.sql` | 部门/角色/权限/菜单/演示账号/商品/供应商等参照数据 |
| `V5__add_user_token_version.sql` | `sys_user.token_version`：改密/删用户后旧 JWT 立即失效 |
| `V6__add_business_sequence_and_unique_keys.sql` | 单号序列表 + 业务唯一键（防并发撞号） |
| `V7__extend_operation_log.sql` | 操作日志补 `operator_name`/`biz_id`/`cost_ms` 与追责索引 |

- 全新空库：依次执行 V1→V7。
- 存量库（已有表但没有 `flyway_schema_history`）：`baseline-on-migrate` 会把当前状态记为 V1 基线，只执行 V2 之后的增量，**不会 DROP 表或丢数据**。
- 存量库首次迁移会打印一批 `Duplicate entry ...` 的 WARN，来源是 `INSERT IGNORE` 幂等种子写入命中已有行，属预期行为。
- 迁移失败会直接中止启动，避免"半套结构"继续对外提供服务。
- 已执行过的脚本不要修改内容（Flyway 会因 checksum 不一致拒绝启动）；开发期确需修改时执行 `mvn -f backend/pom.xml flyway:repair` 或重建库。


## Docker 启动（推荐）

需要 Docker Desktop 已启动。在 PowerShell 执行：

```powershell
cd <项目根目录>
docker compose up -d --build
```

打开 <http://localhost:8081>，后端接口文档为 <http://localhost:8080/swagger-ui/index.html>。

本项目将宿主机 MySQL 映射为 `3307`，容器内部仍然使用 `mysql:3306`，避免与 Windows 已运行的 MySQL 服务冲突。

停止服务：

```powershell
docker compose down
```

如需清空本地数据库数据，再执行 `docker compose down -v`。

## 本地开发启动

后端需要 JDK 17+ 和 Maven。**必须先提供 `JWT_SECRET`**（至少 32 字节），项目不再提供内置默认密钥，未设置时启动会直接失败：

```powershell
cd <项目根目录>\backend
$env:JWT_SECRET = "local-dev-secret-0123456789-abcdefghijklmnop"
mvn spring-boot:run
```

前端需要 Node.js 18+：

```powershell
cd <项目根目录>\frontend
npm install
npm run dev
```

本机当前环境已检测到 Docker Compose 和 IntelliJ 自带 Java 21；系统 PATH 中没有 Maven/npm，优先使用 Docker，或把 JDK 17、Maven 和 Node.js 加入 PATH。

## 演示账号

首次启动时 Flyway 会写入一批演示账号，统一口令 `admin123`：`admin`（系统管理员）、`employee`（普通员工）、`manager`（部门负责人）、`purchaser`（采购人员）、`warehouse`（仓库管理员）。生产环境请立即修改密码和 JWT 密钥。

## Git 提交与推送

在项目目录执行：

```powershell
cd D:\purchase-approval-system
git init
git add .
git commit -m "feat: initialize enterprise procurement approval system"
git branch -M main
git remote add origin https://github.com/<你的用户名>/enterprise-procurement-system.git
git push -u origin main
```

如果远程仓库已存在且已经配置 `origin`，使用：

```powershell
git add .
git commit -m "feat: complete procurement approval workflow"
git push origin main
```
