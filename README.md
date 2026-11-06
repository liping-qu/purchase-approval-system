# 企业采购与审批管理系统

一个面向企业内部采购场景的前后端分离系统，覆盖「员工提交申请 → 部门负责人审批 → 采购下单 → 仓库入库 → 采购完成」的完整流程，附带用户、角色、部门、菜单、操作日志等基础管理模块。

## 功能概览

- **采购申请**：草稿创建、编辑、提交、撤回，支持草稿与明细一次提交
- **审批流转**：部门负责人审批（通过 / 驳回 + 批注），审批记录可追溯
- **订单与入库**：申请转订单、确认入库，订单状态与库存联动
- **库存管理**：库存快照查询、出入库流水、手工调整库存
- **系统管理**：用户、部门、角色、权限、菜单、操作日志
- **权限控制**：基于角色的接口鉴权 + 数据可见范围（本人 / 本部门 / 全量）

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3、Spring Security、MyBatis-Plus、Flyway、JWT |
| 数据库 / 缓存 | MySQL 8、Redis |
| 前端 | Vue 3、Vite、Element Plus、Pinia、Vue Router、Axios |
| 部署 | Docker Compose（MySQL + Redis + 后端 + Nginx） |

## 目录结构

```text
backend/    Spring Boot 服务端（含 Flyway 数据库脚本）
frontend/   Vue 3 前端
docker-compose.yml
```

## 快速开始

### Docker 一键启动

```bash
cp .env.example .env          # 填写 JWT_SECRET 与 MYSQL_ROOT_PASSWORD
docker compose up -d --build
```

启动后访问：

- 前端：http://localhost:8081
- 后端：http://localhost:8080
- MySQL 宿主端口：3307（容器内仍为 3306）

### 本地开发

后端（需要可用的 MySQL 与 Redis）：

```bash
cd backend
export JWT_SECRET="local-dev-secret-please-change-me"
export MYSQL_PASSWORD="你的数据库口令"
mvn spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm run dev
```

前端已配置 `/api` 代理，默认指向 `127.0.0.1:8080`，可通过环境变量 `VITE_PROXY_TARGET` 覆盖。

### 测试

```bash
mvn -f backend/pom.xml test
```

## 演示账号

首次启动由数据库迁移脚本写入，统一初始口令 `admin123`：

| 账号 | 角色 |
| --- | --- |
| `admin` | 系统管理员 |
| `employee` | 普通员工 |
| `manager` | 部门负责人 |
| `purchaser` | 采购人员 |
| `warehouse` | 仓库管理员 |

> 这些是演示账号，正式使用前请修改口令并重新生成 `JWT_SECRET`。

## 接口约定

- 统一前缀 `/api`，除登录外均需请求头 `Authorization: Bearer <token>`
- 统一响应结构：

```json
{
  "success": true,
  "code": "OK",
  "message": "操作成功",
  "data": {},
  "traceId": "9f3c1a2b4d5e"
}
```

- 列表接口统一返回 `{ records, total, page, size }`
- 业务异常使用 4xx + 语义化 `code`（如 `STATE_CONFLICT`、`STOCK_INSUFFICIENT`、`NOT_FOUND`），服务端故障为 500 并在 `message` 中附带 `traceId`

## 数据库版本管理

表结构由 Flyway 脚本维护，位于 `backend/src/main/resources/db/migration/`，应用启动时自动执行，已执行的脚本不可修改。

## License

MIT
