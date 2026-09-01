# eladmin modernized

[![CI](https://github.com/SovNodeAI/eladmin-modernized/actions/workflows/ci.yml/badge.svg)](https://github.com/SovNodeAI/eladmin-modernized/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-E76F00.svg)](https://adoptium.net/temurin/releases/?version=21)
[![Spring Boot 4.1.1](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

[English](README.md) | 简体中文

这是一个独立维护的 [eladmin](https://github.com/elunez/eladmin) 现代化分支，运行于 Java 21 和 Spring Boot 4.1.1。仓库同时保留带校验和的当前版与上游端点清单，两者的 139 条映射类型、HTTP 方法、路由、控制器和处理方法签名一致。

项目目标明确且可复核：保留原有五模块后端和已跟踪的接口声明，同时完成运行时、Jakarta API、接口文档、配置与验证体系迁移。本项目不是 eladmin 官方版本，也与上游网站、演示环境和社群无关。

## 可复现的发布证据

公开 CI 必须满足：

- 185 个行为测试
- 33 个静态迁移契约测试
- 1 个迁移快照测试
- 失败、错误和跳过均为 0
- 通过 Testcontainers 启动真实 MySQL 8.4 和 Redis 7.2 上下文
- 139 条端点签名与上游提交 `55fbf705956949697dbd68bf9003776609d3d029` 一致；原始清单分别保留各自的源码位置
- 141 个 OpenAPI 操作和 414 个 Schema
- 正向权限测试与低权限拒绝测试
- 依赖、校验和、归属和密钥扫描

三类测试必须分开报告；总数只是 CI 清单约束，不代表覆盖率。详细证据见[声明台账](docs/verification/CLAIMS_LEDGER.md)、[测试分类](docs/verification/TEST_TAXONOMY.md)、[风险图](docs/verification/RISK_MAP.md)和[对抗性验证](docs/verification/REFUTER_EVIDENCE.md)。

## 技术栈

| 范围 | 上游基线 | 本仓库 |
| --- | --- | --- |
| Java | 8 | 21 |
| Spring Boot | 2.7.18 | 4.1.1 |
| 接口文档 | Springfox | springdoc 3.1.0 |
| 企业 API | Java EE 命名空间 | Jakarta EE 命名空间 |
| MapStruct | 旧处理器链 | 1.6.3，显式处理器顺序 |
| 数据库连接池 | 旧 Druid starter | Druid Spring Boot 4 starter |
| 集成测试 | 依赖本机环境 | MySQL 与 Redis Testcontainers |

模块仍为 `eladmin-common`、`eladmin-logging`、`eladmin-system`、`eladmin-tools` 和 `eladmin-generator`。

## 明确的安全变更

- 生产环境缺少数据库、Redis、JWT、RSA 或 DES 参数时拒绝启动。
- Druid 控制台与 S3 默认关闭，仓库不再提供可用的控制台账号密码。
- 登录、`/auth/info` 和缓存序列化不再返回密码哈希。
- 新建用户与管理员重置密码不再使用共享密码 `123456`，而是生成一次性随机密码。
- 存储、支付、邮件、代码生成和验证码操作均有显式权限检查及低权限拒绝测试。
- XLSX 导出前会处理公式起始字符。
- Maven 全依赖图禁止 fastjson 1.x 和 Commons Configuration 1.x。

这些控制不代表完整安全认证。DES/CBC 仍是兼容性债务，远程部署和数据库连接测试也需要在实际环境中隔离。详情见[上游 CVE 状态表](docs/security/UPSTREAM_CVE_STATUS.md)和 [`SECURITY.md`](SECURITY.md)。

## 环境要求与验证

- JDK 21
- Maven 3.9 或更高版本
- MySQL 8
- Redis 7
- Docker，用于完整验证

```bash
mvn -B -ntp clean verify
java tools/endpoint-census/EndpointCensus.java . tools/endpoint-census/endpoint-census.txt verify
java tools/openapi-audit/OpenApiAudit.java .
```

应用默认监听 `8000` 端口。必须显式选择 Spring profile；打包后的应用不会自动启用开发环境。

## 本地初始化

```bash
bash tools/onboard-local.sh
```

脚本会生成被 Git 忽略的 `local.env`、`local-bootstrap.sql` 和 `local_rsa_public.b64`。使用 `sql/eladmin.sql` 初始化数据库，再应用 `local-bootstrap.sql`，然后启动：

```bash
set -a
. ./local.env
set +a
mvn -pl eladmin-system -am spring-boot:run
```

可用 `bash tools/onboard-local.sh --self-test` 在不启动应用的情况下验证初始化流程。

## 前端与行为兼容性

端点清单保留了控制器声明，但以下安全相关行为有意改变：

- 验证码路由要求 `email:send` 权限。
- 新建用户和重置密码会在 JSON 中仅返回一次随机密码。
- 种子账号必须完成本地初始化，不再使用共享默认密码。

原版 [eladmin-web](https://github.com/elunez/eladmin-web) 尚未展示一次性密码，在修改邮箱流程中也可能收到 HTTP 403。部署前必须适配这些流程。完整差异见 [`CHANGES.md`](CHANGES.md)。

## 归属与开发说明

本仓库派生自 Zheng Jie 创建并以 Apache License 2.0 发布的 [elunez/eladmin](https://github.com/elunez/eladmin)。上游版权声明均予以保留，SovNode 的修改也采用 Apache License 2.0。详见 [`NOTICE`](NOTICE) 和 [`LICENSE`](LICENSE)。

本次现代化工作使用了人工指导下的 AI 辅助工程。公开声明仅基于可复现的检查与独立复核，不能替代具体部署环境的测试、安全审查和运维判断。

## Spring 现代化服务

SovNode 为生产 Spring 应用提供 Java 8、11、17 与 Spring Boot 2.x、3.x 到受支持版本的迁移服务，包括兼容性评估、实施、安全复核、测试恢复、CI 现代化、前端协同和运行验证。

如需私密迁移评估，请联系 **admin1@sovnode.ai**。
