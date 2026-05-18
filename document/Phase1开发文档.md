# WeLink 第一阶段开发文档

## 1. 概述

本文档描述了 WeLink 即时通讯系统第一阶段的开发内容，涵盖项目结构搭建、依赖引入和核心模块框架搭建。

**开发日期：** 2026-05-05

**阶段目标：** 构建包含所有核心模块的基础项目结构，引入必要的依赖，建立后续开发的基础架构。

---

## 2. 项目结构

```
WeLink/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/epsilon/welink/
│   │   │       ├── WeLinkApplication.java          # 主程序入口
│   │   │       ├── common/                          # 公共模块
│   │   │       │   ├── config/
│   │   │       │   │   ├── CorsConfig.java          # 跨域配置
│   │   │       │   │   ├── JwtConfig.java           # JWT配置
│   │   │       │   │   ├── MinioConfig.java         # MinIO配置
│   │   │       │   │   ├── MybatisPlusConfig.java   # MyBatis-Plus分页插件
│   │   │       │   │   └── RedisConfig.java         # Redis序列化配置
│   │   │       │   ├── constant/
│   │   │       │   │   └── RedisConstants.java      # Redis键常量
│   │   │       │   ├── controller/
│   │   │       │   │   └── FileController.java      # 文件上传REST接口
│   │   │       │   ├── entity/
│   │   │       │   │   └── BaseEntity.java          # 基础实体类
│   │   │       │   ├── exception/
│   │   │       │   │   ├── BusinessException.java   # 自定义业务异常
│   │   │       │   │   └── GlobalExceptionHandler.java # 全局异常处理器
│   │   │       │   ├── result/
│   │   │       │   │   ├── Result.java              # 统一响应包装
│   │   │       │   │   └── ResultCode.java          # 响应码枚举
│   │   │       │   ├── service/
│   │   │       │   │   └── FileStorageService.java  # MinIO文件存储服务
│   │   │       │   └── util/
│   │   │       │       └── JwtUtil.java             # JWT工具类
│   │   │       ├── gateway/                         # 网关模块
│   │   │       │   ├── config/
│   │   │       │   │   └── WebMvcConfig.java        # 拦截器注册
│   │   │       │   └── interceptor/
│   │   │       │       └── JwtInterceptor.java      # JWT认证拦截器
│   │   │       ├── im/                              # 即时通讯模块
│   │   │       │   ├── consumer/
│   │   │       │   │   └── MessageConsumer.java     # Kafka消息消费者
│   │   │       │   ├── server/
│   │   │       │   │   ├── WebSocketChannelHandler.java # Netty通道处理器
│   │   │       │   │   └── WebSocketServer.java     # Netty WebSocket服务器
│   │   │       │   └── service/
│   │   │       │       └── IMService.java           # IM核心服务（认证、消息、心跳）
│   │   │       ├── message/                         # 消息模块
│   │   │       │   ├── controller/
│   │   │       │   │   └── MessageController.java   # 历史消息与离线消息接口
│   │   │       │   ├── dto/
│   │   │       │   │   ├── HistoryMessageRequest.java
│   │   │       │   │   └── MessageRequest.java
│   │   │       │   ├── entity/
│   │   │       │   │   └── Message.java             # 消息实体
│   │   │       │   ├── mapper/
│   │   │       │   │   └── MessageMapper.java       # MyBatis-Plus映射器
│   │   │       │   └── service/
│   │   │       │       └── MessageService.java      # 消息CRUD服务
│   │   │       ├── relation/                        # 关系模块
│   │   │       │   ├── controller/
│   │   │       │   │   └── RelationController.java  # 好友与群组REST接口
│   │   │       │   ├── dto/
│   │   │       │   │   └── CreateGroupRequest.java
│   │   │       │   ├── entity/
│   │   │       │   │   ├── FriendRelation.java
│   │   │       │   │   ├── GroupInfo.java
│   │   │       │   │   └── GroupMember.java
│   │   │       │   ├── mapper/
│   │   │       │   │   ├── FriendRelationMapper.java
│   │   │       │   │   ├── GroupInfoMapper.java
│   │   │       │   │   └── GroupMemberMapper.java
│   │   │       │   └── service/
│   │   │       │       └── RelationService.java     # 好友与群组管理服务
│   │   │       └── user/                            # 用户模块
│   │   │           ├── controller/
│   │   │           │   ├── AuthController.java      # 注册与登录接口
│   │   │           │   └── UserController.java      # 用户信息接口
│   │   │           ├── dto/
│   │   │           │   ├── LoginRequest.java
│   │   │           │   ├── LoginResponse.java
│   │   │           │   └── RegisterRequest.java
│   │   │           ├── entity/
│   │   │           │   └── User.java                # 用户实体
│   │   │           ├── mapper/
│   │   │           │   └── UserMapper.java          # MyBatis-Plus映射器
│   │   │           └── service/
│   │   │               └── UserService.java         # 用户认证服务
│   │   └── resources/
│   │       ├── application.properties               # 应用配置
│   │       └── sql/
│   │           └── init.sql                         # 数据库初始化脚本
│   └── test/
│       └── java/
│           └── com/epsilon/welink/
│               └── WeLinkApplicationTests.java
└── pom.xml                                          # Maven依赖
```

---

## 3. 技术栈

### 3.1 后端

| 类别           | 技术              | 版本    | 用途                           |
| :------------- | :---------------- | :------ | :----------------------------- |
| 开发语言       | Java              | 17      | 核心开发语言                   |
| 框架           | Spring Boot       | 3.2.5   | 应用框架                       |
| ORM框架        | MyBatis-Plus      | 3.5.5   | 数据库访问层                   |
| 网络框架       | Netty             | 4.1.108 | WebSocket服务器（IM）          |
| 缓存/会话      | Redis             | 7.x     | 在线状态、路由表、缓存         |
| 消息队列       | Kafka             | 3.x     | 跨实例消息路由                 |
| 分布式锁       | Redisson          | 3.27.2  | 分布式锁实现                   |
| 对象存储       | MinIO             | 8.5.9   | 文件/图片存储                  |
| 认证           | JJWT              | 0.12.5  | JWT令牌生成/验证               |
| 密码加密       | Spring Security Crypto | -  | BCrypt密码加密                 |
| 工具库         | Hutool            | 5.8.26  | 通用工具库                     |
| 代码简化       | Lombok            | -       | 代码生成（getter/setter等）    |

### 3.2 数据库

| 组件    | 版本  | 用途           |
| :------ | :---- | :------------- |
| MySQL   | 8.0   | 主数据存储     |

### 3.3 依赖版本（pom.xml）

```xml
<properties>
    <java.version>17</java.version>
    <mybatis-plus.version>3.5.5</mybatis-plus.version>
    <netty.version>4.1.108.Final</netty.version>
    <redisson.version>3.27.2</redisson.version>
    <minio.version>8.5.9</minio.version>
    <jwt.version>0.12.5</jwt.version>
</properties>
```

---

## 4. 模块说明

### 4.1 公共模块（`com.epsilon.welink.common`）

**用途：** 所有其他模块共享的基础设施。

**组件：**
- **配置类：** MyBatis-Plus分页、跨域、JWT、Redis序列化、MinIO客户端
- **响应类：** 统一 `Result<T>` 响应包装器和 `ResultCode` 枚举
- **异常类：** `BusinessException` 和 `GlobalExceptionHandler`
- **工具类：** `JwtUtil` 用于令牌生成/解析/验证
- **常量类：** `RedisConstants` 定义Redis键前缀和TTL值
- **服务类：** `FileStorageService` 用于MinIO文件上传/下载
- **控制器：** `FileController` 提供文件上传REST接口

### 4.2 网关模块（`com.epsilon.welink.gateway`）

**用途：** 请求入口、认证和授权。

**组件：**
- **拦截器：** `JwtInterceptor` 验证所有 `/api/v1/**` 请求的JWT令牌，除了 `/auth/register` 和 `/auth/login`
- **配置类：** `WebMvcConfig` 注册JWT拦截器

**认证流程：**
1. 客户端发送请求，携带 `Authorization: Bearer <token>` 请求头
2. `JwtInterceptor` 提取并验证令牌
3. 用户ID和用户名被设置为请求属性
4. 请求转发到目标控制器

### 4.3 用户模块（`com.epsilon.welink.user`）

**用途：** 用户注册、登录和个人信息管理。

**组件：**
- **实体类：** `User` 映射到 `user` 表
- **DTO类：** `RegisterRequest`、`LoginRequest`、`LoginResponse`
- **映射器：** `UserMapper` 继承MyBatis-Plus的 `BaseMapper`
- **服务类：** `UserService` 处理注册（BCrypt密码加密）、登录（JWT生成）和用户信息缓存
- **控制器：** `AuthController`（注册/登录）、`UserController`（获取用户信息）

**API接口：**
| 方法   | 路径                     | 需要认证 | 描述       |
| :----- | :----------------------- | :------- | :--------- |
| POST   | /api/v1/auth/register    | 否       | 用户注册   |
| POST   | /api/v1/auth/login       | 否       | 用户登录   |
| GET    | /api/v1/user/{userId}    | 是       | 获取用户信息 |

### 4.4 关系模块（`com.epsilon.welink.relation`）

**用途：** 好友系统和群组管理。

**组件：**
- **实体类：** `FriendRelation`、`GroupInfo`、`GroupMember`
- **DTO类：** `CreateGroupRequest`
- **映射器：** `FriendRelationMapper`、`GroupInfoMapper`、`GroupMemberMapper`
- **服务类：** `RelationService` 处理好友请求（发送/接受/拒绝/删除）、群组CRUD（创建/邀请/踢出/退出）
- **控制器：** `RelationController` 暴露所有好友和群组接口

**API接口：**
| 方法     | 路径                                  | 描述           |
| :------- | :------------------------------------ | :------------- |
| POST     | /api/v1/friend/apply/{friendId}       | 发送好友申请   |
| POST     | /api/v1/friend/accept/{friendId}      | 接受好友申请   |
| POST     | /api/v1/friend/reject/{friendId}      | 拒绝好友申请   |
| GET      | /api/v1/friend/list                   | 获取好友列表   |
| DELETE   | /api/v1/friend/{friendId}             | 删除好友       |
| POST     | /api/v1/group                         | 创建群组       |
| GET      | /api/v1/group/list                    | 获取群组列表   |
| GET      | /api/v1/group/{groupId}/members       | 获取群成员列表 |
| POST     | /api/v1/group/{groupId}/invite        | 邀请成员       |
| DELETE   | /api/v1/group/{groupId}/kick/{targetId}| 踢出成员      |
| DELETE   | /api/v1/group/{groupId}/quit          | 退出群组       |

### 4.5 消息模块（`com.epsilon.welink.message`）

**用途：** 消息持久化和历史消息查询。

**组件：**
- **实体类：** `Message` 映射到 `message` 表
- **DTO类：** `MessageRequest`、`HistoryMessageRequest`
- **映射器：** `MessageMapper`
- **服务类：** `MessageService` 处理消息保存（私聊/群聊）、历史消息查询（分页）、离线消息获取和已读回执
- **控制器：** `MessageController` 暴露历史消息和离线消息接口

**API接口：**
| 方法 | 路径                              | 描述              |
| :--- | :-------------------------------- | :---------------- |
| GET  | /api/v1/message/history/private   | 获取私聊历史消息  |
| GET  | /api/v1/message/history/group     | 获取群聊历史消息  |
| GET  | /api/v1/message/offline           | 获取离线消息      |
| POST | /api/v1/message/read/{msgId}      | 标记消息为已读    |

### 4.6 即时通讯模块（`com.epsilon.welink.im`）

**用途：** WebSocket连接管理、实时消息推送、心跳检测。

**组件：**
- **服务器：** `WebSocketServer` 在配置的端口上启动Netty WebSocket服务器
- **处理器：** `WebSocketChannelHandler` 处理WebSocket帧（认证、消息、心跳、确认）
- **服务类：** `IMService` IM核心逻辑，包括认证、消息路由、心跳、断开连接处理
- **消费者：** `MessageConsumer` Kafka消费者，用于跨实例消息投递

**WebSocket消息协议：**
```json
// 认证
{"type": "auth", "token": "<jwt_token>"}

// 发送消息（私聊）
{"type": "message", "toUserId": 123, "msgType": 1, "content": "你好"}

// 发送消息（群聊）
{"type": "message", "groupId": 456, "msgType": 1, "content": "大家好"}

// 心跳
{"type": "heartbeat"}

// 确认收到
{"type": "ack", "msgId": "uuid"}
```

**消息路由逻辑：**
1. **同一实例：** 直接通过WebSocket通道推送
2. **不同实例：** 发布到Kafka，目标实例消费并推送
3. **离线：** 存储到MySQL，用户下次登录时拉取

### 4.7 模块依赖关系图

```
网关模块 ──► 用户模块
        ──► 关系模块
        ──► 消息模块
        ──► IM模块

IM模块 ──► 用户模块（获取用户信息）
      ──► 关系模块（获取群成员）
      ──► 消息模块（保存/查询消息）

公共模块 ◄──（所有模块依赖）
```

---

## 5. 配置说明

### 5.1 application.properties

| 配置项                             | 默认值                          | 描述                    |
| :---------------------------------- | :------------------------------- | :---------------------- |
| server.port                         | 8080                             | REST API端口            |
| welink.websocket.port               | 8081                             | WebSocket端口           |
| spring.datasource.url               | jdbc:mysql://localhost:3306/welink | MySQL连接              |
| spring.data.redis.host              | localhost                        | Redis主机               |
| spring.kafka.bootstrap-servers      | localhost:9092                   | Kafka引导服务器         |
| welink.minio.endpoint               | http://localhost:9000            | MinIO端点               |
| welink.jwt.secret                   | （已配置）                       | JWT签名密钥             |
| welink.jwt.access-token-expiration  | 7200000（2小时）                 | 访问令牌有效期（毫秒）  |
| welink.jwt.refresh-token-expiration | 604800000（7天）                 | 刷新令牌有效期（毫秒）  |
| welink.instance.id                  | instance-1                       | 实例标识符              |
| welink.websocket.heartbeat-timeout  | 60                               | 心跳超时时间（秒）      |

### 5.2 Kafka主题

| 主题                 | 用途                      |
| :------------------- | :------------------------ |
| im-private-message   | 跨实例私聊消息            |
| im-group-message     | 跨实例群聊消息            |

### 5.3 Redis键

| 键模式                     | 用途                      | 过期时间 |
| :--------------------------- | :------------------------ | :------- |
| user:online:{userId}         | 在线状态                  | 120秒    |
| im:route:{userId}            | 实例路由映射              | 120秒    |
| user:info:{userId}           | 用户信息缓存              | 7天      |
| token:blacklist:{token}      | 令牌黑名单                | -        |
| token:refresh:{userId}       | 刷新令牌存储              | 7天      |

---

## 6. 数据库结构

完整的初始化脚本请参考 `src/main/resources/sql/init.sql`。

**核心数据表：**
- `user` - 用户账户表
- `friend_relation` - 好友关系表（双向）
- `group_info` - 群组信息表
- `group_member` - 群成员表（角色：0=普通成员，1=管理员，2=群主）
- `message` - 消息表（私聊和群聊）

---

## 7. 运行指南

### 7.1 前置条件

- JDK 17+
- MySQL 8.0+
- Redis 7.x+
- Kafka 3.x+
- MinIO（最新版）

### 7.2 部署步骤

1. **初始化数据库：**
   ```bash
   mysql -u root -p < src/main/resources/sql/init.sql
   ```

2. **启动中间件：**
   ```bash
   # Redis
   redis-server

   # Kafka（需要先启动Zookeeper）
   zookeeper-server-start.sh config/zookeeper.properties
   kafka-server-start.sh config/server.properties

   # MinIO
   minio server /data
   ```

3. **配置 application.properties：**
   根据需要更新数据库、Redis、Kafka和MinIO的连接字符串。

4. **构建并运行：**
   ```bash
   mvn clean package
   java -jar target/WeLink-0.0.1-SNAPSHOT.jar
   ```

### 7.3 验证

- REST API：`http://localhost:8080/api/v1/auth/register`
- WebSocket：`ws://localhost:8081/ws`

---

## 8. 下一步计划（第二阶段）

- [ ] 实现Redis发布/订阅机制，向好友推送在线状态通知
- [ ] 添加消息确认（ACK）和重试机制
- [ ] 实现分布式锁，处理并发好友/群组操作
- [ ] 添加单元测试和集成测试
- [ ] 实现客户端WebSocket重连逻辑
- [ ] 添加消息类型支持（图片、文件）
- [ ] 实现群公告功能
- [ ] 添加限流和请求校验

---

## 9. 已知问题

| 问题                          | 影响         | 计划修复时间 |
| :----------------------------- | :----------- | :----------- |
| 实例ID硬编码                   | 多实例路由无法正常工作 | 第二阶段 |
| 未实现分布式锁                 | 并发操作可能导致数据不一致 | 第二阶段 |
| Kafka主题未自动创建            | 需要手动创建主题 | 第二阶段 |
| 无单元测试                     | 代码质量未验证 | 第二阶段 |
| WebSocket认证未与通道生命周期绑定 | 潜在安全问题 | 第二阶段 |
