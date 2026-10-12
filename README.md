# 生活优选（hm-dianping）

一个本地生活服务类平台的后端实现，涵盖商户浏览、优惠券秒杀、探店笔记、关注点赞、附近商户等典型业务场景。

> **项目说明**：本项目参考开源教程《黑马点评》实现，主要用于系统学习 Redis 在实际业务中的落地方式。在此基础上我做了一些自己的改造（见下方「我的改造」）。
> 把这些写清楚，是因为我希望面试官看到的不是"又一个教程项目"，而是"一个能把技术讲明白的人"。

---

## 技术栈

| 分类 | 技术 |
|---|---|
| 语言 / 框架 | Java 8、SpringBoot 2.3.12、MyBatis-Plus |
| 数据库 | MySQL 8（InnoDB）|
| 缓存 / 中间件 | Redis（Lettuce 连接池）、Redisson、Redis Stream |
| 工具 | Lombok、Hutool、AspectJ |

---

## 功能模块

| 模块 | 说明 |
|---|---|
| 登录认证 | 基于 Redis 的短信验证码登录；双拦截器配合刷新 Token 有效期 |
| 商户查询与缓存 | 商户、商户类型查询，完整处理缓存穿透 / 击穿 / 雪崩 |
| 优惠券秒杀 | 优惠券发放与秒杀下单，解决超卖与一人一单问题 |
| 探店笔记 | 笔记的发布、查看、点赞、点赞排行榜 |
| 关注与 Feed 流 | 关注 / 取关、共同关注、基于 SortedSet 的滚动分页 Feed |
| 附近商户 | 基于 Redis GEO 实现按距离查询附近商铺 |
| 用户签到 | 基于 Redis BitMap 实现签到与连续签到天数统计 |
| 文件上传 | 图片上传 |

---

## 快速开始

### 1. 环境要求

- JDK **8**
- Maven 3.6+
- MySQL **8.x**（默认 3306 端口）
- Redis **6.x**（本项目配置为 **6380** 端口，注意不是 6379）

### 2. 初始化数据库

```sql
CREATE DATABASE hmdp DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

然后导入表结构与初始数据：

```bash
mysql -u root -p hmdp < src/main/resources/db/hmdp.sql
```

### 3. 配置连接信息（重要）

出于安全考虑，**数据库和 Redis 密码没有写死在配置文件里**，而是通过环境变量注入。

请先设置环境变量（把值换成你自己的密码）：

```powershell
# Windows PowerShell
setx MYSQL_PASSWORD "你的MySQL密码"
setx REDIS_PASSWORD "你的Redis密码"
```

```bash
# macOS / Linux
export MYSQL_PASSWORD="你的MySQL密码"
export REDIS_PASSWORD="你的Redis密码"
```

> 设置环境变量后需要**重启 IDEA 或终端**才会生效。

### 4. 启动

```bash
mvn spring-boot:run
```

启动成功后访问：`http://localhost:8081`

---

## 关键技术实现

### 缓存穿透 / 击穿 / 雪崩

| 问题 | 方案 |
|---|---|
| **穿透**（查不存在的数据，绕过缓存直打 DB）| 缓存空对象（`""`）并设置较短 TTL；配合布隆过滤器 |
| **击穿**（热点 key 过期瞬间大量请求打到 DB）| 互斥锁重建；或逻辑过期 + 异步重建，保证可用性 |
| **雪崩**（大批 key 同时过期）| 给 TTL 加随机值，打散过期时间 |

### 优惠券秒杀

1. **一人一单**：先用 Redis 做用户维度的快速去重，避免无效请求进到数据库
2. **库存原子扣减**：把「判断库存 → 扣减库存 → 记录用户」写成 **Lua 脚本**，利用 Redis 单线程特性保证原子性，解决集群下的超卖
3. **异步下单**：Lua 校验通过后把订单丢进 **Redis Stream**，由独立线程异步消费落库，接口不用等数据库，大幅降低响应时间
4. **消息可靠性**：消费完成后 ACK；消费异常时从 pending-list 重新读取并重试，避免消息丢失

### 分布式锁的演进

`手写 SETNX 锁` → `解决误删（Lua 校验 value）` → `解决不可重入` → `Redisson 看门狗自动续期`

### 点赞与排行

用 Redis **Set** 保证一人一赞，用 **ZSet** 按时间戳做点赞排行榜，避免直接对数据库做 count。

### Feed 流（关注的人发的笔记）

采用 **推模式**：发笔记时写入所有粉丝的收件箱（SortedSet，score 为时间戳），拉取时用 `ZREVRANGEBYSCORE` 做滚动分页，解决分页时数据重复的问题。

---

## 我的改造

> 这一节记录我在教程基础上自己做的改动，以及过程中踩到的坑。

- [ ] 待补充：例如「秒杀结果通过 WebSocket 主动推送给前端」
- [ ] 待补充：例如「部署到云服务器时遇到的问题及排查过程」

---

## 项目结构

```
src/main/java/com/hmdp/
├── config/        配置类（Redis、MyBatis-Plus、拦截器等）
├── controller/    接口层
├── dto/           数据传输对象
├── entity/        实体类
├── mapper/        数据访问层
├── service/       业务逻辑层
│   └── impl/      业务实现
└── utils/         工具类（Redis 锁、ID 生成器等）

src/main/resources/
├── db/hmdp.sql    数据库表结构与初始数据
├── seckill.lua    秒杀库存扣减脚本
├── unlock.lua     分布式锁释放脚本
└── application.yaml
```

---

## 后续计划

- [ ] 秒杀结果异步通知（WebSocket / SSE 主动推送，替代前端轮询）
- [ ] 补充单元测试
- [ ] 用 Docker Compose 一键部署
