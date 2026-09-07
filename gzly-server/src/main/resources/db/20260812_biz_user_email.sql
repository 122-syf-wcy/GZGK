-- 邮箱注册登录：biz_user 增加 email 列（验证码注册/登录一体，替代已下线的卡密体系）

ALTER TABLE `biz_user`
    ADD COLUMN `email` VARCHAR(128) NULL COMMENT '注册邮箱（验证码登录）' AFTER `identifier`;

-- 唯一索引：同一邮箱只对应一个账号（NULL 不参与唯一约束，兼容历史卡密用户）
ALTER TABLE `biz_user`
    ADD UNIQUE KEY `uk_biz_user_email` (`email`);
