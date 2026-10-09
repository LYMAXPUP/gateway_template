# Devagent Studio Gateway Template

面向 `gateway` 拓扑的三模块 Java 网关 + Python Agent Runtime 模板。生成应用包含 `frontend/`、`backend/`、`gateway/`、`common/` 和 `agent-runtime/`；前端只访问 Gateway（8080），Gateway 统一认证后转发到 Spring Boot Backend（8081）和 Python Agent Runtime（8010）。

## 架构组成

```
客户端 → Gateway (:8080) ────→ Backend (:8081)
                          └──→ Agent Runtime (:8010)
```

| 模块                  | 技术栈                           | 端口 | 职责                                       |
| --------------------- | -------------------------------- | ---- | ------------------------------------------ |
| `backend-common`      | Java 8 / Spring Boot 2.7.2       | —    | 共享安全基元（XcodePrincipal、TokenCodec） |
| `backend-gateway`     | Spring Cloud Gateway 2021.0.8    | 8080 | 统一认证、路由转发、内部身份传递           |
| `backend-application` | Spring Boot Web 2.7.2            | 8081 | 业务 API、MyBatis-Plus、RBAC               |
| `agent-runtime`       | Python 3.12 / DeepAgents / AG-UI | 8010 | Agent 对话、checkpoint、AG-UI SSE          |

## 目录所有权

```text
backend-common/         共享类型和编解码器（XcodePrincipal、TokenCodec）
backend-gateway/        Gateway 服务
  ├── config/           GatewaySecurityConfiguration、CORS
  ├── security/         认证 WebFilter、认证适配器合同
  ├── filter/           内部身份传递 GlobalFilter
  ├── agent/            Agent 准入治理（可选）
  └── mock/             local Profile Mock 登录
backend-application/    业务后端
  ├── common/config/    CORS、MyBatis-Plus
  ├── common/security/  内部身份验证 Filter
  ├── common/exception/ 统一异常处理
  ├── common/page/      分页工具
  └── common/response/  统一响应体
agent-runtime/          Python Agent Runtime
  ├── src/app/agent/    Agent 创建与 RuntimeContext
  ├── src/app/security/ 认证适配器、local Mock、匿名会话
  ├── src/app/models/   项目默认模型初始化
  ├── src/app/tools/    Runtime-native Tool 扩展
  └── src/app/server/   Public Edge、Auth、Debug、AG-UI SSE
```

## 本地启动

### 环境要求

- Java 8 + Maven 3.6+
- Python 3.12 + uv

### 1. 编译 Java 模块

```powershell
cd "D:\code\devagent studio系列\springboot-template-gateway"
mvn --% install -DskipTests
```

### 2. 配置 Agent Runtime

```powershell
cd agent-runtime
cp .env.example .env
# 编辑 .env 填写 MODEL_NAME 和 MODEL_API_KEY
uv sync --frozen
cd ..
```

### 3. 启动服务（三个终端，按顺序）

**终端 1 — Gateway（先启动，鉴权就绪）**：

```powershell
cd backend-gateway
mvn --% spring-boot:run -Dspring-boot.run.profiles=local
```

**终端 2 — Backend（接口注册到网关路由表中）**：

```powershell
cd backend-application
mvn --% spring-boot:run -Dspring-boot.run.profiles=local
```

**终端 3 — Agent Runtime（接口注册到网关路由表中）**：

```powershell
cd agent-runtime
uv run agent-runtime
```

### 4. 验证

```powershell
# Gateway 自身健康
curl.exe http://127.0.0.1:9080/actuator/health

# Gateway → Backend 健康转发
curl.exe http://127.0.0.1:8080/health

# Gateway → Agent Runtime 健康转发
curl.exe http://127.0.0.1:8080/agent/health

# Mock 登录
curl.exe -X POST http://127.0.0.1:8080/_xcode/auth/mock/login
```

## 认证与转发

Gateway 采用 **行外 local Mock** 认证模式（`gateway.security.mode=local`），不依赖企业 SSO：

1. 客户端获取 Bearer Token
```
RESPONSE=$(curl -s -X POST http://127.0.0.1:8080/_xcode/auth/mock/login)
TOKEN=$(echo $RESPONSE | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
echo "Token: $TOKEN"
```
2. 客户端携带 Token 请求业务接口
java后端接口:
```
curl -s -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8080/api/demo/ping

curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"hello":"world"}' http://127.0.0.1:8080/api/demo/echo
```
agent runtime对话接口:
```
curl.exe -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Accept: text/event-stream" -d "{\"threadId\":\"test-1\",\"runId\":\"r1\",\"messages\":[{\"id\":\"m1\",\"role\":\"user\",\"content\":\"hello\"}],\"state\":{},\"tools\":[],\"context\":[],\"forwardedProps\":{}}" http://127.0.0.1:8080/api/agents/chat/run
```

后续可切换为 `enterprise` 模式，实现 `PublicAuthenticationAdapter` 对接企业 SSO。

## 行外本地认证

本地开发使用 `local` Profile + `gateway.security.mode=local`：

```http
POST /_xcode/auth/mock/login   → 获取 accessToken
GET  /_xcode/auth/me           → 查询当前用户
POST /_xcode/auth/logout       → 登出
```

Mock 用户从 `mock/users.json` 加载，Token 仅存进程内存，不落库、进程退出即失效。

调用接口时携带登录结果中的 Token：

```http
GET /api/demo/ping
Authorization: Bearer <accessToken>
```

## 路由配置

Gateway 路由定义在 `backend-gateway/src/main/resources/application.yml`：

| 路由           | 路径             | 转发到                         | 需要认证 |
| -------------- | ---------------- | ------------------------------ | -------- |
| backend-health | `/health`        | Backend `/actuator/health`     | 否       |
| agent-health   | `/agent/health`  | Agent Runtime `/health`        | 否       |
| business-api   | `/api/demo/**`   | Backend                        | 是       |
| agent-runtime  | `/api/agents/**` | Agent Runtime（StripPrefix=1） | 是       |

## 模型配置

Agent Runtime 模型配置在 `agent-runtime/.env`：

| 环境变量                | 说明       | 示例                                                        |
| ----------------------- | ---------- | ----------------------------------------------------------- |
| `MODEL_NAME`            | 模型名称   | `deepseek-chat`（无 provider 前缀时默认 openai compatible） |
| `MODEL_API_KEY`         | API Key    | `sk-xxx`                                                    |
| `MODEL_BASE_URL`        | 接口地址   | `https://api.deepseek.com`                                  |
| `MODEL_TIMEOUT_SECONDS` | 超时（秒） | `120`                                                       |
| `AGENT_TEMPERATURE`     | 温度       | `0.2`                                                       |
| `AGENT_MAX_TOKENS`      | 最大 Token | `4096`                                                      |

`MODEL_NAME` 支持两种格式：

| 格式             | 示例                     | 说明                           |
| ---------------- | ------------------------ | ------------------------------ |
| `provider:model` | `deepseek:deepseek-chat` | 显式指定 provider              |
| `model`          | `deepseek-chat`          | 无前缀时默认 openai compatible |

## Agent 对话

```bash
curl.exe -X POST -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"threadId":"t1","runId":"r1","messages":[{"id":"m1","role":"user","content":"你好"}],"state":{},"tools":[],"context":[],"forwardedProps":{}}' \
  http://127.0.0.1:8080/api/agents/chat/run
```

或直连 Agent Runtime 测试模型配置：

```bash
curl.exe -X POST \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"threadId":"t1","runId":"r1","messages":[{"id":"m1","role":"user","content":"你好"}],"state":{},"tools":[],"context":[],"forwardedProps":{}}' \
  http://127.0.0.1:8010/agents/chat/run
```

## 验证

```powershell
# Java 编译
mvn --% install -DskipTests

# Python 语法检查
cd agent-runtime
uv run python -m compileall -q src tests

# Gateway 验证
Invoke-RestMethod http://127.0.0.1:9080/actuator/health
```
