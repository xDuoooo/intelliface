-- 数据库初始化（完整建表版）

create database if not exists intelligent_interview_question_bank_system;
use intelligent_interview_question_bank_system;

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    passwordConfigured tinyint                          null comment '是否已设置可用登录密码：0-未设置 1-已设置',
    mpOpenId     varchar(128)                           null comment '公众号 openId',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin/ban',
    phone        varchar(128)                           null comment '手机号',
    email        varchar(128)                           null comment '邮箱',
    city         varchar(128)                           null comment '所在城市',
    careerDirection varchar(128)                        null comment '就业方向',
    interestTags varchar(1024)                          null comment '兴趣标签（json 数组）',
    profileVisibleFields varchar(512)                   null comment '公开主页可见字段（json 数组，空值默认全部公开）',
    githubId     varchar(256)                           null comment 'GitHub 唯一标识',
    giteeId      varchar(256)                           null comment 'Gitee 唯一标识',
    googleId     varchar(256)                           null comment 'Google 唯一标识',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    index idx_userAccount (userAccount),
    index idx_userRole (userRole),
    index idx_createTime (createTime),
    index idx_phone (phone),
    index idx_email (email),
    unique key uk_mpOpenId (mpOpenId),
    index idx_githubId (githubId),
    index idx_giteeId (giteeId),
    index idx_googleId (googleId)
) comment '用户' collate = utf8mb4_unicode_ci;

-- 用户关注关系表
create table if not exists user_follow
(
    id           bigint auto_increment comment 'id' primary key,
    userId       bigint                             not null comment '关注者 id',
    followUserId bigint                             not null comment '被关注用户 id',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_user_follow (userId, followUserId),
    index idx_userId (userId),
    index idx_followUserId (followUserId),
    index idx_user_createTime (userId, createTime),
    index idx_follow_createTime (followUserId, createTime)
) comment '用户关注关系' collate = utf8mb4_unicode_ci;

-- ES 同步补偿任务
create table if not exists es_sync_task
(
    id            bigint auto_increment comment 'id' primary key,
    syncType      varchar(32)                        not null comment '同步类型：question / post',
    entityId      bigint                             not null comment '业务主键',
    operation     varchar(16)                        not null comment '操作类型：upsert / delete',
    payload       longtext                           null comment '待同步到 ES 的 DTO 快照',
    retryCount    int      default 0                 not null comment '已重试次数',
    lastError     varchar(1000)                      null comment '最近一次错误信息',
    nextRetryTime datetime default CURRENT_TIMESTAMP not null comment '下次允许重试时间',
    createTime    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_syncType_entityId (syncType, entityId),
    index idx_nextRetryTime (nextRetryTime)
) comment 'ES 同步补偿任务' collate = utf8mb4_unicode_ci;

-- 题库表
create table if not exists question_bank
(
    id           bigint auto_increment comment 'id' primary key,
    title        varchar(256)                       null comment '标题',
    description  text                               null comment '描述',
    picture      varchar(2048)                      null comment '图片',
    userId       bigint                             not null comment '创建用户 id',
    reviewStatus tinyint  default 3                 not null comment '审核状态：0-待审核 1-已通过 2-已驳回 3-私有',
    reviewMessage varchar(512)                      null comment '审核意见',
    reviewUserId bigint                             null comment '审核人 id',
    reviewTime   datetime                           null comment '审核时间',
    editTime    datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint  default 0                not null comment '是否删除',
    index idx_title (title),
    index idx_userId (userId),
    index idx_reviewStatus (reviewStatus),
    index idx_user_reviewStatus (userId, reviewStatus),
    index idx_reviewTime (reviewTime)
) comment '题库' collate = utf8mb4_unicode_ci;

-- 题目表
create table if not exists question
(
    id           bigint auto_increment comment 'id' primary key,
    title        varchar(256)                       null comment '标题',
    content      text                               null comment '内容',
    tags         varchar(1024)                      null comment '标签列表（json 数组）',
    answer       text                               null comment '推荐答案',
    difficulty   varchar(32)                        null comment '题目难度：简单 / 中等 / 困难',
    userId       bigint                             not null comment '创建用户 id',
    reviewStatus tinyint  default 3                 not null comment '审核状态：0-待审核 1-已通过 2-已驳回 3-私有',
    reviewMessage varchar(512)                      null comment '审核意见',
    reviewUserId bigint                             null comment '审核人 id',
    reviewTime   datetime                           null comment '审核时间',
    editTime     datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                 not null comment '是否删除',
    index idx_title (title),
    index idx_userId (userId),
    index idx_difficulty (difficulty),
    index idx_updateTime (updateTime),
    index idx_reviewStatus (reviewStatus),
    index idx_user_reviewStatus (userId, reviewStatus),
    index idx_reviewTime (reviewTime)
) comment '题目' collate = utf8mb4_unicode_ci;

-- 题库题目表
create table if not exists question_bank_question
(
    id             bigint auto_increment comment 'id' primary key,
    questionBankId bigint                             not null comment '题库 id',
    questionId     bigint                             not null comment '题目 id',
    userId         bigint                             not null comment '创建用户 id',
    createTime     datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime     datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_bank_question (questionBankId, questionId)
) comment '题库题目' collate = utf8mb4_unicode_ci;

-- 题目收藏表
create table if not exists question_favour
(
    id         bigint auto_increment comment 'id' primary key,
    questionId bigint                             not null comment '题目 id',
    userId     bigint                             not null comment '用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_question_user (questionId, userId),
    index idx_questionId (questionId),
    index idx_userId (userId),
    index idx_user_createTime (userId, createTime)
) comment '题目收藏' collate = utf8mb4_unicode_ci;

-- 用户刷题轨迹
create table if not exists user_question_history
(
    id         bigint auto_increment comment 'id' primary key,
    userId     bigint                             not null comment '用户 id',
    questionId bigint                             not null comment '题目 id',
    status     tinyint  default 0                 not null comment '作答状态：0-浏览, 1-掌握, 2-困难',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_user_question (userId, questionId),
    index idx_questionId (questionId),
    index idx_userId (userId),
    index idx_updateTime (updateTime),
    index idx_user_updateTime (userId, updateTime),
    index idx_user_status_updateTime (userId, status, updateTime)
) comment '用户刷题轨迹' collate = utf8mb4_unicode_ci;

-- 用户题目学习时长会话
create table if not exists user_question_study_session
(
    id              bigint auto_increment comment 'id' primary key,
    userId          bigint                             not null comment '用户 id',
    questionId      bigint                             not null comment '题目 id',
    durationSeconds int      default 0                 not null comment '本次学习时长（秒）',
    createTime      datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime      datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    index idx_userId (userId),
    index idx_questionId (questionId),
    index idx_createTime (createTime),
    index idx_user_createTime (userId, createTime)
) comment '用户题目学习时长会话' collate = utf8mb4_unicode_ci;

-- 帖子
create table if not exists post
(
    id           bigint                             not null comment 'id' primary key,
    title        varchar(80)                       not null comment '标题',
    content      text                              not null comment '内容',
    ipLocation   varchar(64)                       null comment '发布时 IP 归属地',
    tags         varchar(1024)                     null comment '标签列表 json',
    thumbNum     int      default 0                not null comment '点赞数',
    favourNum    int      default 0                not null comment '收藏数',
    reportNum    int      default 0                not null comment '举报数',
    userId       bigint                            not null comment '创建用户 id',
    reviewStatus tinyint  default 1                not null comment '审核状态：0-待审核 1-已通过 2-已驳回',
    reviewMessage varchar(512)                     null comment '审核意见',
    reviewUserId bigint                            null comment '审核人 id',
    reviewTime   datetime                          null comment '审核时间',
    isTop        tinyint  default 0                not null comment '是否置顶',
    isFeatured   tinyint  default 0                not null comment '是否精选',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                not null comment '是否删除',
    index idx_userId (userId),
    index idx_createTime (createTime),
    index idx_thumbNum (thumbNum),
    index idx_favourNum (favourNum),
    index idx_reviewStatus (reviewStatus),
    index idx_reportNum (reportNum),
    index idx_top_featured_createTime (isTop, isFeatured, createTime)
) comment '帖子' collate = utf8mb4_unicode_ci;

-- 帖子点赞
create table if not exists post_thumb
(
    id         bigint auto_increment comment 'id' primary key,
    postId     bigint                             not null comment '帖子 id',
    userId     bigint                             not null comment '用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_post_user (postId, userId),
    index idx_userId (userId),
    index idx_postId (postId)
) comment '帖子点赞' collate = utf8mb4_unicode_ci;

-- 帖子收藏
create table if not exists post_favour
(
    id         bigint auto_increment comment 'id' primary key,
    postId     bigint                             not null comment '帖子 id',
    userId     bigint                             not null comment '用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_post_user (postId, userId),
    index idx_userId (userId),
    index idx_postId (postId),
    index idx_user_createTime (userId, createTime)
) comment '帖子收藏' collate = utf8mb4_unicode_ci;

-- 帖子举报
create table if not exists post_report
(
    id         bigint auto_increment comment 'id' primary key,
    postId     bigint                             not null comment '帖子 id',
    userId     bigint                             not null comment '举报者 id',
    reason     varchar(200)                       not null comment '举报原因',
    status     tinyint  default 0                not null comment '处理状态：0待处理 1已驳回 2已采纳',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    unique key uk_post_user (postId, userId),
    index idx_postId (postId),
    index idx_status_createTime (status, createTime)
) comment '帖子举报' collate = utf8mb4_unicode_ci;

-- 帖子评论
create table if not exists post_comment
(
    id            bigint auto_increment comment 'id' primary key,
    postId        bigint                             not null comment '帖子 id',
    userId        bigint                             not null comment '发表者 id',
    parentId      bigint                             null comment '父评论 id（null=顶级评论）',
    replyToId     bigint                             null comment '回复的具体评论 id',
    content       text                               not null comment '内容',
    ipLocation    varchar(64)                        null comment '发布时 IP 归属地',
    status        tinyint  default 0                 not null comment '状态：0正常 1待审核 2已驳回',
    reviewMessage varchar(512)                       null comment '审核意见',
    reviewUserId  bigint                             null comment '审核人 id',
    reviewTime    datetime                           null comment '审核时间',
    editTime      datetime default CURRENT_TIMESTAMP null comment '编辑时间',
    createTime    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete      tinyint  default 0                 not null comment '是否已删除',
    index idx_postId (postId),
    index idx_parentId (parentId),
    index idx_userId (userId),
    index idx_createTime (createTime),
    index idx_post_parent_status_createTime (postId, parentId, status, createTime),
    index idx_parent_status_createTime (parentId, status, createTime)
) comment '帖子评论' collate = utf8mb4_unicode_ci;

-- 题目评论表
create table if not exists question_comment
(
    id         bigint auto_increment comment 'id' primary key,
    questionId bigint                             not null comment '题目 id',
    userId     bigint                             not null comment '发表者 id',
    parentId   bigint                             null comment '父评论 id（null=顶级评论）',
    replyToId  bigint                             null comment '回复的具体评论 id',
    content    text                               not null comment '内容',
    ipLocation varchar(64)                        null comment '发布时 IP 归属地',
    likeNum    int      default 0                 not null comment '点赞数',
    reportNum  int      default 0                 not null comment '被举报次数',
    isPinned   tinyint  default 0                 not null comment '是否置顶：0否 1是',
    isOfficial tinyint  default 0                 not null comment '是否官方解答：0否 1是',
    status     tinyint  default 0                 not null comment '状态：0正常 1待审核 2已隐藏',
    reviewMessage varchar(512)                    null comment '审核意见',
    reviewUserId bigint                           null comment '审核人 id',
    reviewTime   datetime                         null comment '审核时间',
    editTime   datetime default CURRENT_TIMESTAMP null comment '编辑时间',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否已删除',
    index idx_questionId (questionId),
    index idx_parentId (parentId),
    index idx_userId (userId),
    index idx_createTime (createTime),
    index idx_likeNum (likeNum),
    index idx_question_parent_status_createTime (questionId, parentId, status, createTime),
    index idx_parent_status_createTime (parentId, status, createTime)
) comment '题目评论' collate = utf8mb4_unicode_ci;

-- 评论点赞表
create table if not exists question_comment_like
(
    id         bigint auto_increment comment 'id' primary key,
    commentId  bigint                             not null comment '评论 id',
    userId     bigint                             not null comment '点赞用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '点赞时间',
    unique key uk_comment_user (commentId, userId)
) comment '评论点赞' collate = utf8mb4_unicode_ci;

-- 评论举报表
create table if not exists question_comment_report
(
    id         bigint auto_increment comment 'id' primary key,
    commentId  bigint                             not null comment '被举报评论 id',
    userId     bigint                             not null comment '举报者 id',
    reason     varchar(512)                       not null comment '举报原因',
    status     tinyint  default 0                 not null comment '处理状态：0待处理 1已驳回 2已删除',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    unique key uk_comment_report_user (commentId, userId),
    index idx_commentId (commentId)
) comment '评论举报' collate = utf8mb4_unicode_ci;

-- AI 模拟面试表
create table if not exists mock_interview
(
    id             bigint auto_increment comment 'id' primary key,
    jobPosition    varchar(256)                       not null comment '目标岗位',
    workExperience varchar(128)                       null comment '工作年限',
    interviewType  varchar(128)                       null comment '面试类型',
    techStack      varchar(512)                       null comment '技术方向 / 技术栈',
    resumeText     text                               null comment '简历 / 项目背景',
    difficulty     varchar(128)                       null comment '难度',
    expectedRounds int      default 5                 not null comment '计划轮次',
    currentRound   int      default 0                 not null comment '当前已完成轮次',
    messages       longtext                           null comment '消息记录（json 数组）',
    report         longtext                           null comment '结构化面试报告（json）',
    status         int      default 0                 not null comment '状态：0-待开始, 1-进行中, 2-已结束, 3-已暂停',
    userId         bigint                             not null comment '创建用户 id',
    createTime     datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime     datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete       tinyint  default 0                 not null comment '是否删除',
    index idx_userId (userId),
    index idx_status (status),
    index idx_jobPosition (jobPosition),
    index idx_createTime (createTime)
) comment 'AI 模拟面试' collate = utf8mb4_unicode_ci;

-- 用户学习目标表
create table if not exists user_learning_goal
(
    id               bigint auto_increment comment 'id' primary key,
    userId           bigint                             not null comment '用户 id',
    dailyTarget      int      default 3                 not null comment '每日刷题目标',
    reminderEnabled  tinyint  default 1                 not null comment '是否开启提醒',
    lastReminderTime datetime                           null comment '上次提醒时间',
    createTime       datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime       datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete         tinyint  default 0                 not null comment '是否删除',
    unique key uk_userId (userId)
) comment '用户学习目标' collate = utf8mb4_unicode_ci;

-- 用户题目私有笔记表
create table if not exists user_question_note
(
    id         bigint auto_increment comment 'id' primary key,
    userId     bigint                             not null comment '用户 id',
    questionId bigint                             not null comment '题目 id',
    content    longtext                           not null comment '私有笔记内容',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',
    unique key uk_user_question_note (userId, questionId),
    index idx_userId (userId),
    index idx_questionId (questionId),
    index idx_updateTime (updateTime)
) comment '用户题目私有笔记' collate = utf8mb4_unicode_ci;

-- 题目推荐日志表
create table if not exists question_recommend_log
(
    id         bigint auto_increment comment 'id' primary key,
    userId     bigint                             null comment '用户 id',
    questionId bigint                             not null comment '被推荐题目 id',
    source     varchar(64)                        not null comment '推荐来源：personal / related / resume',
    action     varchar(32)                        not null comment '行为：exposure / click',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',
    index idx_questionId (questionId),
    index idx_source (source),
    index idx_action (action),
    index idx_createTime (createTime),
    index idx_source_action_createTime (source, action, createTime)
) comment '题目推荐效果日志' collate = utf8mb4_unicode_ci;

-- 系统配置表
create table if not exists system_config
(
    id              bigint auto_increment comment 'id' primary key,
    siteName        varchar(256)                       not null comment '站点名称',
    seoKeywords     varchar(512)                       null comment 'SEO 关键词',
    announcement    varchar(1024)                      null comment '系统公告',
    allowRegister   tinyint  default 1                 not null comment '是否开放注册',
    requireCaptcha  tinyint  default 1                 not null comment '是否强制图形验证码',
    maintenanceMode tinyint  default 0                 not null comment '是否开启维护模式',
    enableSiteNotification tinyint default 1           not null comment '是否开启站内通知',
    enableEmailNotification tinyint default 1          not null comment '是否开启邮件提醒',
    enableLearningGoalReminder tinyint default 1       not null comment '是否开启学习目标提醒任务',
    allowGuestViewQuestion tinyint default 1           not null comment '是否允许未登录用户访问题目模块',
    allowGuestViewPost tinyint default 1               not null comment '是否允许未登录用户访问论坛模块',
    createTime      datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime      datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间'
) comment '系统配置' collate = utf8mb4_unicode_ci;

-- 通知表
create table if not exists notification
(
    id         bigint auto_increment comment 'id' primary key,
    userId     bigint                             not null comment '获知通知的用户 id',
    title      varchar(512)                       not null comment '标题',
    content    text                               not null comment '内容',
    type       varchar(256)                       null comment '类型：system, user, post, etc.',
    status     int      default 0                 not null comment '状态（0-未读, 1-已读）',
    targetId   bigint                             null comment '关联业务 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',
    index idx_userId (userId),
    index idx_user_status_createTime (userId, status, createTime)
) comment '通知' collate = utf8mb4_unicode_ci;

-- 管理员操作日志表
create table if not exists admin_operation_log
(
    id         bigint auto_increment comment 'id' primary key,
    userId     bigint                             not null comment '管理员 id',
    userName   varchar(256)                       null comment '管理员名称',
    operation  varchar(512)                       null comment '操作描述',
    method     varchar(512)                       null comment '方法名',
    params     longtext                           null comment '请求参数',
    ip         varchar(128)                       null comment 'IP 地址',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',
    index idx_userId (userId),
    index idx_createTime (createTime)
) comment '管理员操作日志' collate = utf8mb4_unicode_ci;

-- 题目搜索日志表
create table if not exists question_search_log
(
    id          bigint auto_increment comment 'id' primary key,
    userId      bigint                             null comment '搜索用户 id',
    searchText  varchar(128)                       not null comment '搜索关键词',
    source      varchar(64)  default 'question'    not null comment '搜索来源',
    resultCount int          default 0             not null comment '搜索命中数量',
    hasNoResult tinyint      default 0             not null comment '是否无结果：0否 1是',
    ip          varchar(128)                       null comment 'IP 地址',
    createTime  datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint      default 0             not null comment '是否删除',
    index idx_searchText (searchText),
    index idx_createTime (createTime),
    index idx_source (source)
) comment '题目搜索日志' collate = utf8mb4_unicode_ci;

-- 安全告警表
create table if not exists security_alert
(
    id            bigint auto_increment comment 'id' primary key,
    userId        bigint                             null comment '关联用户 id',
    userName      varchar(256)                       null comment '关联用户名',
    alertType     varchar(128)                       not null comment '告警类型',
    riskLevel     varchar(64)  default 'medium'      not null comment '风险等级',
    reason        varchar(512)                       null comment '告警原因',
    detail        longtext                           null comment '告警详情',
    ip            varchar(128)                       null comment 'IP 地址',
    status        int          default 0             not null comment '状态：0-待处理 1-已处理 2-已忽略',
    handlerUserId bigint                             null comment '处理人 id',
    handleAction  varchar(128)                       null comment '处理动作',
    handleTime    datetime                           null comment '处理时间',
    createTime    datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime    datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete      tinyint      default 0             not null comment '是否删除',
    index idx_userId (userId),
    index idx_status (status),
    index idx_alertType (alertType),
    index idx_createTime (createTime),
    index idx_status_createTime (status, createTime)
) comment '安全告警' collate = utf8mb4_unicode_ci;
