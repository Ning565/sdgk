# 山东高考志愿辅助平台 — 本地开发启动指南

## 环境要求

- Java 17+ (已安装 17.0.18)
- Maven 3.9+ (已安装 3.9.9)
- Node.js 20+ (已安装 20.20.2)
- pnpm (已安装 10.34.4)
- MySQL 9.x (已安装，运行中)
- Redis 7.x (已安装，运行中)

## 快速启动

### 1. 启动 MySQL 和 Redis

```bash
brew services start mysql
brew services start redis
```

验证:
```bash
mysql -u admission -padmission123 admission_platform -e "SELECT 1"
redis-cli ping  # 返回 PONG
```

### 2. 启动后端 (Java Spring Boot)

```bash
cd backend
mvn install -DskipTests -q
mvn spring-boot:run -pl admission-boot -DskipTests
```

首次启动会自动执行 Flyway 数据库迁移，等待看到:
```
Tomcat started on port 8080 (http)
```

验证后端:
```bash
curl 'http://localhost:8080/api/v1/configs/years'
curl 'http://localhost:8080/api/v1/schools?pageNo=1&pageSize=5'
curl 'http://localhost:8080/api/v1/score-ranks/resolve?year=2026&score=600'
```

### 3. 启动前端

```bash
cd frontend
pnpm install
pnpm build:user    # 构建用户端
pnpm build:admin   # 构建管理后台
pnpm dev:user      # 启动用户端开发服务器 (localhost:3000)
pnpm dev:admin     # 启动管理后台开发服务器 (localhost:3001)
```

### 4. 访问页面

| 应用 | 地址 | 说明 |
|------|------|------|
| 用户端 | http://localhost:3000 | 考生/家长使用 |
| 管理后台 | http://localhost:3001/admin | 数据运营/管理员使用 |
| API 文档 | http://localhost:8080/doc.html | OpenAPI/Swagger |
| 健康检查 | http://localhost:8080/actuator/health | 应用状态 |

## 当前数据状态

| 数据 | 数量 | 年份 |
|------|------|------|
| 院校 (school) | 2,113 | — |
| 招生计划 (enrollment_plan) | 65,354 | 2026 (默认) |
| 历史录取 (admission_history) | 95,586 | 2023-2025 |
| 一分一段表 (score_rank_segment) | 548 | 2026 only |

## 常用 API 端点

### 公开接口 (无需登录)
```
GET  /api/v1/configs/years              # 年度配置
GET  /api/v1/schools                    # 院校列表
GET  /api/v1/schools/{id}               # 院校详情
GET  /api/v1/plans/{id}                 # 专业详情
GET  /api/v1/score-ranks/resolve        # 分数匹配位次
```

### 需要登录的接口
```
POST /api/v1/auth/sms/send              # 发送验证码
POST /api/v1/auth/sms/login             # 验证码登录
GET  /api/v1/auth/me                    # 当前用户信息
GET  /api/v1/candidate-profiles/{year}  # 考生档案
PUT  /api/v1/candidate-profiles/{year}  # 更新考生档案
POST /api/v1/recommendations/search     # 推荐查询
POST /api/v1/prediction/batch           # 批量预测
GET  /api/v1/volunteer-forms            # 志愿表列表
POST /api/v1/volunteer-forms            # 创建志愿表
```

### 管理后台接口 (需要 ADMIN 角色)
```
POST /api/admin/v1/import-batches       # 创建导入批次
POST /api/admin/v1/import-batches/{id}/publish  # 发布数据
GET  /api/admin/v1/audit-logs           # 审计日志
```

## 项目结构

```
gaokao/
├── backend/              15 个 Maven 模块
│   ├── admission-boot/   主启动模块
│   ├── admission-common/ 通用返回/异常/分页
│   ├── admission-auth/   登录/短信/Session
│   ├── admission-candidate/ 考生档案/位次匹配
│   ├── admission-catalog/  院校/专业/招生计划/历史
│   ├── admission-recommendation/ 推荐引擎
│   ├── admission-prediction/  算法预测
│   ├── admission-volunteer/   志愿表
│   ├── admission-volunteercheck/ 志愿检查
│   ├── admission-dataimport/  数据导入/校验/发布
│   ├── admission-export/      Excel 导出
│   ├── admission-system/      年度配置/字典
│   ├── admission-audit/       审计日志
│   └── admission-analytics/   埋点
├── frontend/             pnpm monorepo
│   ├── apps/user-web/    用户端 (Vue 3)
│   ├── apps/admin-web/   管理后台 (Vue 3)
│   └── packages/          共享包
├── data/                  原始数据 + 处理后数据
├── scripts/               Python 数据处理脚本
├── database/migrations/   SQL 迁移文件
├── deploy/docker/         Docker Compose + Nginx
└── docs/                  文档
```

## 算法预测公式

简单 rank-based 算法:
- **rankRatio** = (考生位次 - 去年最低位次) / 考生位次
- **冲** (reach): rankRatio < -2%
- **稳** (match): rankRatio ∈ [-2%, 5%]
- **保** (safety): rankRatio > 5%
- **概率**: clamp(50 + rankRatio × 1000, 1, 99)
- **模型版本**: simple-v1.0

## 开发验证码

本地开发模式下，验证码会打印在后台日志中:
```
[短信验证码] 手机号: 138****0001, 验证码: 482917
```
