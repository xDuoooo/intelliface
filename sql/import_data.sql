-- 初始化演示数据（管理员账号 + 示例题库/题目）

use intelligent_interview_question_bank_system;

-- 默认管理员账号
insert into user (userAccount, userPassword, passwordConfigured, userName, userRole)
select 'admin', 'dd50d8eff62344ecb394e73af0dd1eb1', 1, '管理员', 'admin'
from dual
where not exists (
    select 1 from user where userAccount = 'admin'
);

-- 初始化系统配置
insert into system_config (id, siteName, seoKeywords, announcement, allowRegister, requireCaptcha, maintenanceMode, enableSiteNotification, enableEmailNotification, enableLearningGoalReminder, allowGuestViewQuestion, allowGuestViewPost)
values (1, 'IntelliFace 智面', '面试, 刷题, Java, 互联网', '欢迎来到智面 1.0 版本，体验 AI 智能面经！', 1, 1, 0, 1, 1, 1, 1, 1)
on duplicate key update
    siteName = values(siteName),
    seoKeywords = values(seoKeywords),
    announcement = values(announcement),
    allowRegister = values(allowRegister),
    requireCaptcha = values(requireCaptcha),
    maintenanceMode = values(maintenanceMode),
    enableSiteNotification = values(enableSiteNotification),
    enableEmailNotification = values(enableEmailNotification),
    enableLearningGoalReminder = values(enableLearningGoalReminder),
    allowGuestViewQuestion = values(allowGuestViewQuestion),
    allowGuestViewPost = values(allowGuestViewPost);

set @seed_user_id = (
    select id
    from user
    where userAccount = 'admin'
    order by id asc
    limit 1
);

-- 初始化题库
insert into question_bank (id, title, description, picture, userId)
values
    (1, 'Java 基础面试题', '涵盖 Java 核心语法、集合、多线程、JVM 等基础知识。', '/assets/logo.png', @seed_user_id),
    (2, 'MySQL 进阶实战', '性能优化、索引原理、事务隔离级别及锁机制深度解析。', '/assets/logo.png', @seed_user_id),
    (3, 'Spring Boot 全家桶', 'Spring Boot 常用注解、自动配置原理及微服务整合。', '/assets/logo.png', @seed_user_id),
    (4, 'Redis 核心技术', '高并发下的缓存实战、分布式锁原理、持久化机制及数据淘汰策略。', '/assets/logo.png', @seed_user_id),
    (5, 'Elasticsearch 搜索引擎', '倒排索引原理、DSL 语法、聚合查询及 Spring Data ES 实战。', '/assets/logo.png', @seed_user_id),
    (6, '消息队列 RabbitMQ', '发布订阅模式、死信队列、延迟队列及消息幂等性处理方案。', '/assets/logo.png', @seed_user_id)
on duplicate key update
    title = values(title),
    description = values(description),
    picture = values(picture),
    userId = values(userId);

-- 初始化题目
-- 演示题目由管理员初始化，默认都处于“已通过审核”状态
insert into question (id, title, content, tags, answer, difficulty, userId, reviewStatus, reviewUserId, reviewTime)
values
    (1, 'Java 中 HashMap 的原理？', 'HashMap 的底层实现是数组 + 链表/红黑树...', '["Java", "集合"]', '这是参考答案内容...', '中等', @seed_user_id, 1, @seed_user_id, now()),
    (2, 'Java 中的序列化和反序列化是什么？', '序列化是将对象转换为字节流的过程...', '["Java", "基础"]', '这是参考答案内容...', '简单', @seed_user_id, 1, @seed_user_id, now()),
    (3, 'MySQL 索引的最左前缀匹配原则是什么？', '最左前缀匹配指的是在联合索引中...', '["MySQL", "索引"]', '这是参考答案内容...', '中等', @seed_user_id, 1, @seed_user_id, now()),
    (4, '最近 OpenClaw 这么火，你知道它的原理吗？', 'OpenClaw 原理主要涉及...', '["AI", "大模型"]', '这是参考答案内容...', '困难', @seed_user_id, 1, @seed_user_id, now()),
    (5, 'Java 中 ConcurrentHashMap 1.7 和 1.8 之间有哪些区别？', '1.7 使用 Segment 分段锁...', '["Java", "并发"]', '这是参考答案内容...', '困难', @seed_user_id, 1, @seed_user_id, now()),
    (6, 'MySQL 的索引类型有哪些？', '普通索引、唯一索引、主键索引、组合索引...', '["MySQL", "索引"]', '这是参考答案内容...', '简单', @seed_user_id, 1, @seed_user_id, now()),
    (7, 'Java 中有哪些集合类？请简单介绍', 'List, Set, Map...', '["Java", "集合"]', '这是参考答案内容...', '简单', @seed_user_id, 1, @seed_user_id, now()),
    (8, '什么是 RAG？RAG 的主要流程是什么？', 'RAG 即检索增强生成...', '["AI", "RAG"]', '这是参考答案内容...', '中等', @seed_user_id, 1, @seed_user_id, now()),
    (9, '详细描述一条 SQL 语句在 MySQL 中的执行过程。', '连接器 -> 查询缓存 -> 分析器...', '["MySQL", "原理"]', '这是参考答案内容...', '困难', @seed_user_id, 1, @seed_user_id, now()),
    (10, 'MySQL 的存储引擎有哪些？它们之间有什么区别？', 'InnoDB, MyISAM...', '["MySQL", "存储引擎"]', '这是参考答案内容...', '中等', @seed_user_id, 1, @seed_user_id, now()),
    (11, 'Redis 的数据类型有哪些？分别的应用场景？', 'String, Hash, List, Set, ZSet...', '["Redis", "基础"]', '参考答案：String用于缓存，Hash用于存储对象，ZSet用于排行榜...', '简单', @seed_user_id, 1, @seed_user_id, now()),
    (12, '什么是 Redis 的雪崩、击穿、穿透？', '缓存雪崩是指大量Key同时过期...', '["Redis", "面试必备"]', '参考答案：穿透加布隆过滤器，雪崩设随机过期时间...', '中等', @seed_user_id, 1, @seed_user_id, now()),
    (13, 'Elasticsearch 为什么要用倒排索引？', '倒排索引（Inverted Index）是搜索引擎的基石...', '["ES", "搜索引擎"]', '参考答案：倒排索引通过词找文档，效率极高...', '中等', @seed_user_id, 1, @seed_user_id, now()),
    (14, 'RabbitMQ 如何保证消息不丢失？', '持久化、从节点确认、ACK机制...', '["RabbitMQ", "中间件"]', '参考答案：开启持久化，手动提交ACK...', '中等', @seed_user_id, 1, @seed_user_id, now()),
    (15, '什么是分布式锁？Redis 实现分布式锁有哪些坑？', '分布式锁是控制分布式系统访问共享资源的机制...', '["Redis", "分布式"]', '参考答案：需要保证原子性，可以利用 Redisson...', '困难', @seed_user_id, 1, @seed_user_id, now()),
    (16, 'JVM 的内存模型 (JMM) 是什么？', 'JMM 定义了主内存和工作内存...', '["Java", "JVM"]', '参考答案：JMM 解决了可见性、原子性和有序性问题...', '困难', @seed_user_id, 1, @seed_user_id, now()),
    (17, 'Spring AOP 的原理是什么？', '动态代理机制，JDK 代理和 CGLIB 代理...', '["Spring", "原理"]', '参考答案：在不修改源码的情况下增加功能...', '中等', @seed_user_id, 1, @seed_user_id, now()),
    (18, '谈谈你对 Spring Cloud 微服务的理解', '服务注册发现、负载均衡、配置中心...', '["Spring Cloud", "微服务"]', '参考答案：它是微服务架构的一站式解决方案...', '中等', @seed_user_id, 1, @seed_user_id, now()),
    (19, '什么是乐观锁和悲观锁？', '悲观锁假定会冲突，乐观锁假定不会...', '["Java", "并发"]', '参考答案：MySQL 用 Version 实现乐观锁...', '中等', @seed_user_id, 1, @seed_user_id, now()),
    (20, 'Kafka 与 RabbitMQ 的区别是什么？', 'Kafka 侧重吞吐量，RabbitMQ 侧重功能...', '["中间件", "架构"]', '参考答案：Kafka 是拉模式，适合日志处理...', '中等', @seed_user_id, 1, @seed_user_id, now()),
    (21, 'ES 中的分片 (Shard) 和副本 (Replica) 是什么？', '分片是水平扩展，副本是高可用...', '["ES", "原理"]', '参考答案：分片决定容量，副本决定容灾能力...', '中等', @seed_user_id, 1, @seed_user_id, now())
on duplicate key update
    title = values(title),
    content = values(content),
    tags = values(tags),
    answer = values(answer),
    difficulty = values(difficulty),
    userId = values(userId),
    reviewStatus = values(reviewStatus),
    reviewMessage = null,
    reviewUserId = values(reviewUserId),
    reviewTime = values(reviewTime);

-- 初始化题库与题目关联
insert ignore into question_bank_question (questionBankId, questionId, userId)
values
    (1, 1, @seed_user_id),
    (1, 2, @seed_user_id),
    (1, 5, @seed_user_id),
    (1, 7, @seed_user_id),
    (1, 16, @seed_user_id),
    (1, 19, @seed_user_id),
    (2, 3, @seed_user_id),
    (2, 6, @seed_user_id),
    (2, 9, @seed_user_id),
    (2, 10, @seed_user_id),
    (3, 4, @seed_user_id),
    (3, 8, @seed_user_id),
    (3, 17, @seed_user_id),
    (3, 18, @seed_user_id),
    (4, 11, @seed_user_id),
    (4, 12, @seed_user_id),
    (4, 15, @seed_user_id),
    (5, 13, @seed_user_id),
    (5, 21, @seed_user_id),
    (6, 14, @seed_user_id),
    (6, 20, @seed_user_id);
