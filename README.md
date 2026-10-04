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
frontend/src      Vue 页面、路由、状态与 API
sql               Docker 初始化脚本
```

## 业务接口

认证：`POST /api/auth/login`、`GET /api/auth/me`。

采购申请：`/api/purchase-requests` 支持草稿创建、编辑、删除、提交、撤回和查询；审批：`GET /api/approvals/pending`、`POST /api/approvals/{id}`；订单：`POST/GET /api/purchase-orders`、`POST /api/purchase-orders/{id}/delivered`；库存：`GET /api/inventory/stock`、`GET /api/inventory/transactions`。

管理端接口位于 `/api/admin`，覆盖用户、部门、角色、权限、菜单、配置和操作日志。订单创建、入库以及库存流水使用事务和版本条件更新，避免重复入库造成库存不一致。

## Docker 启动（推荐）

需要 Docker Desktop 已启动。在 PowerShell 执行：

```powershell
cd D:\purchase-approval-system
docker compose up -d --build
```

打开 <http://localhost>，后端接口文档为 <http://localhost:8080/swagger-ui.html>。

停止服务：

```powershell
docker compose down
```

如需清空本地数据库数据，再执行 `docker compose down -v`。

## 本地开发启动

后端需要 JDK 17+ 和 Maven：

```powershell
cd D:\purchase-approval-system\backend
mvn spring-boot:run
```

前端需要 Node.js 18+：

```powershell
cd D:\purchase-approval-system\frontend
npm install
npm run dev
```

本机当前环境已检测到 Docker Compose 和 IntelliJ 自带 Java 21；系统 PATH 中没有 Maven/npm，优先使用 Docker，或把 JDK 17、Maven 和 Node.js 加入 PATH。

## 演示账号

首次启动时系统会为以下账号设置演示密码 `admin123`：`admin`（系统管理员）、`employee`（普通员工）、`manager`（部门负责人）、`purchaser`（采购人员）、`warehouse`（仓库管理员）。生产环境请立即修改密码和 JWT 密钥。

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
