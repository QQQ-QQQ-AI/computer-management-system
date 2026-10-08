-- =============================================================
--  基于 Java 的网咖管理系统  数据库结构脚本
--  数据库：MySQL 8.0
--  字符集：utf8mb4
--  说明：共 14 张表，对应系统 E-R 图中的实体与联系
-- =============================================================

CREATE DATABASE IF NOT EXISTS `cafe_db`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `cafe_db`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `product_order`;
DROP TABLE IF EXISTS `equipment_rental`;
DROP TABLE IF EXISTS `seat_session`;
DROP TABLE IF EXISTS `recharge_record`;
DROP TABLE IF EXISTS `reservation`;
DROP TABLE IF EXISTS `notice`;
DROP TABLE IF EXISTS `seat`;
DROP TABLE IF EXISTS `billing_rule`;
DROP TABLE IF EXISTS `product`;
DROP TABLE IF EXISTS `equipment`;
DROP TABLE IF EXISTS `balance_record`;
DROP TABLE IF EXISTS `member`;
DROP TABLE IF EXISTS `member_level`;
DROP TABLE IF EXISTS `sys_user`;

SET FOREIGN_KEY_CHECKS = 1;

-- -------------------------------------------------------------
-- 1. 系统用户表（管理员 / 收银员）
-- -------------------------------------------------------------
CREATE TABLE `sys_user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(32)  NOT NULL                COMMENT '登录账号',
    `password`    VARCHAR(64)  NOT NULL                COMMENT '密码摘要（BCrypt，兼容历史MD5）',
    `real_name`   VARCHAR(32)  NOT NULL                COMMENT '真实姓名',
    `role`        VARCHAR(16)  NOT NULL                COMMENT '角色：ADMIN 管理员 / CASHIER 收银员',
    `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 停用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统用户表';

-- -------------------------------------------------------------
-- 2. 会员等级配置表
--    让「会员等级」真正影响价格：等级折扣参与计费。
--    实际折扣 = min(计费规则里的区域会员折扣, 本表的等级折扣)
--    即会员享受两者中更优惠的一个。
-- -------------------------------------------------------------
CREATE TABLE `member_level` (
    `level`          TINYINT       NOT NULL                COMMENT '等级编码：1 普通 / 2 银卡 / 3 金卡',
    `level_name`     VARCHAR(16)   NOT NULL                COMMENT '等级名称',
    `discount`       DECIMAL(4,2)  NOT NULL DEFAULT 1.00   COMMENT '等级折扣，0.95 表示九五折',
    `upgrade_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '升级所需累计消费额',
    `description`    VARCHAR(128)  NULL                    COMMENT '等级说明',
    PRIMARY KEY (`level`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会员等级配置表';

-- -------------------------------------------------------------
-- 3. 会员表
-- -------------------------------------------------------------
CREATE TABLE `member` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `card_no`     VARCHAR(32)   NOT NULL                COMMENT '会员卡号',
    `name`        VARCHAR(32)   NOT NULL                COMMENT '姓名',
    `id_card`     VARCHAR(18)   NULL                    COMMENT '身份证号。会员自助注册可不填，收银员开卡时必填',
    `phone`       VARCHAR(20)   NOT NULL                COMMENT '手机号',
    `password`    VARCHAR(64)   NOT NULL                COMMENT '密码摘要（BCrypt，兼容历史MD5）',
    `balance`     DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '账户余额',
    `deposit`     DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '冻结押金',
    `level`       TINYINT       NOT NULL DEFAULT 1      COMMENT '会员等级：1 普通 / 2 银卡 / 3 金卡',
    `status`      TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 正常 / 0 冻结',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开卡时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_card_no` (`card_no`),
    UNIQUE KEY `uk_phone` (`phone`),
    KEY `idx_member_status` (`status`),
    CONSTRAINT `fk_member_level` FOREIGN KEY (`level`) REFERENCES `member_level` (`level`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会员表';

-- -------------------------------------------------------------
-- 4. 账户流水表
--    每一次余额变动都写入一条，记录变动前后余额。
--    作用：余额可由流水重算校验（当前余额 == 最后一条流水的 after_balance），
--          是验证「结算数据一致性」的直接依据。
-- -------------------------------------------------------------
CREATE TABLE `balance_record` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `member_id`      BIGINT        NOT NULL                COMMENT '会员ID',
    `change_type`    VARCHAR(24)   NOT NULL                COMMENT '变动类型：RECHARGE 充值 / HOUR_FEE 上机费 / PRODUCT 商品消费 / RENT_FEE 租赁费 / DEPOSIT_FREEZE 押金冻结 / DEPOSIT_UNFREEZE 押金解冻',
    `change_amount`  DECIMAL(10,2) NOT NULL                COMMENT '变动金额，正数增加、负数减少',
    `before_balance` DECIMAL(10,2) NOT NULL                COMMENT '变动前余额',
    `after_balance`  DECIMAL(10,2) NOT NULL                COMMENT '变动后余额',
    `biz_type`       VARCHAR(24)   NULL                    COMMENT '关联业务类型：SESSION / PRODUCT_ORDER / RENTAL',
    `biz_id`         BIGINT        NULL                    COMMENT '关联业务主键',
    `remark`         VARCHAR(128)  NULL                    COMMENT '备注',
    `operator_id`    BIGINT        NULL                    COMMENT '经办人（员工ID），会员自助操作时为空',
    `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (`id`),
    KEY `idx_balance_member` (`member_id`, `create_time`),
    CONSTRAINT `fk_balance_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '账户流水表';

-- -------------------------------------------------------------
-- 5. 计费规则表
-- -------------------------------------------------------------
CREATE TABLE `billing_rule` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `area`            VARCHAR(32)   NOT NULL                COMMENT '区域名称',
    `hour_price`      DECIMAL(10,2) NOT NULL                COMMENT '小时单价（元/小时）',
    `package_hours`   INT           NULL                    COMMENT '包时套餐时长（小时），为空表示不设套餐',
    `package_price`   DECIMAL(10,2) NULL                    COMMENT '包时套餐价格（元）',
    `member_discount` DECIMAL(4,2)  NOT NULL DEFAULT 1.00   COMMENT '会员折扣（0.90 表示九折）',
    `status`          TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 停用',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_area` (`area`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '计费规则表';

-- -------------------------------------------------------------
-- 6. 机位表
-- -------------------------------------------------------------
CREATE TABLE `seat` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `seat_no`     VARCHAR(16)  NOT NULL                COMMENT '机位编号',
    `area`        VARCHAR(32)  NOT NULL                COMMENT '所属区域',
    `config`      VARCHAR(128) NULL                    COMMENT '硬件配置',
    `status`      VARCHAR(16)  NOT NULL DEFAULT 'FREE' COMMENT '状态：FREE 空闲 / USING 使用中 / MAINTENANCE 维护中',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_seat_no` (`seat_no`),
    KEY `idx_seat_status` (`status`),
    KEY `idx_seat_area` (`area`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '机位表';

-- -------------------------------------------------------------
-- 7. 上机记录表
-- -------------------------------------------------------------
CREATE TABLE `seat_session` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `member_id`        BIGINT        NOT NULL                COMMENT '会员ID',
    `seat_id`          BIGINT        NOT NULL                COMMENT '机位ID',
    `start_time`       DATETIME      NOT NULL                COMMENT '开台时间',
    `end_time`         DATETIME      NULL                    COMMENT '下机时间',
    `duration_minutes` INT           NULL                    COMMENT '上机时长（分钟）',
    `hour_fee`         DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '上机费用',
    `product_fee`      DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '商品消费金额',
    `total_fee`        DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '结算总金额',
    `status`           VARCHAR(16)   NOT NULL DEFAULT 'USING' COMMENT '状态：USING 使用中 / FINISHED 已结算',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_member` (`member_id`),
    KEY `idx_session_seat` (`seat_id`),
    KEY `idx_session_status` (`status`),
    -- 营收统计固定按「状态 + 结算时间」过滤，建复合索引避免回表扫全表
    KEY `idx_session_settle` (`status`, `end_time`),
    CONSTRAINT `fk_session_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
    CONSTRAINT `fk_session_seat`   FOREIGN KEY (`seat_id`)   REFERENCES `seat` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '上机记录表';

-- -------------------------------------------------------------
-- 8. 充值记录表
-- -------------------------------------------------------------
CREATE TABLE `recharge_record` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `member_id`   BIGINT        NOT NULL                COMMENT '会员ID',
    `amount`      DECIMAL(10,2) NOT NULL                COMMENT '充值金额',
    `type`        VARCHAR(16)   NOT NULL                COMMENT '充值方式：ONLINE 线上自助 / CASH 收银台现金',
    `operator_id` BIGINT        NULL                    COMMENT '经办收银员ID（线上充值为空）',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '充值时间',
    PRIMARY KEY (`id`),
    KEY `idx_recharge_member` (`member_id`),
    KEY `idx_recharge_time` (`create_time`),
    CONSTRAINT `fk_recharge_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '充值记录表';

-- -------------------------------------------------------------
-- 9. 商品表
-- -------------------------------------------------------------
CREATE TABLE `product` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(64)   NOT NULL                COMMENT '商品名称',
    `price`       DECIMAL(10,2) NOT NULL                COMMENT '单价',
    `stock`       INT           NOT NULL DEFAULT 0      COMMENT '库存数量',
    `status`      TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 上架 / 0 下架',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_product_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品表';

-- -------------------------------------------------------------
-- 10. 商品消费记账表
-- -------------------------------------------------------------
CREATE TABLE `product_order` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_id`  BIGINT        NULL                    COMMENT '关联上机记录ID（可为空）',
    `member_id`   BIGINT        NOT NULL                COMMENT '会员ID',
    `product_id`  BIGINT        NOT NULL                COMMENT '商品ID',
    `quantity`    INT           NOT NULL DEFAULT 1      COMMENT '数量',
    `amount`      DECIMAL(10,2) NOT NULL                COMMENT '消费金额',
    `operator_id` BIGINT        NULL                    COMMENT '经办收银员ID',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记账时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_member` (`member_id`),
    KEY `idx_order_session` (`session_id`),
    KEY `idx_order_time` (`create_time`),
    CONSTRAINT `fk_order_member`  FOREIGN KEY (`member_id`)  REFERENCES `member` (`id`),
    CONSTRAINT `fk_order_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品消费记账表';

-- -------------------------------------------------------------
-- 11. 设备表
-- -------------------------------------------------------------
CREATE TABLE `equipment` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`        VARCHAR(32)   NOT NULL                COMMENT '设备编号',
    `category`    VARCHAR(32)   NOT NULL                COMMENT '设备类别',
    `rent_price`  DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '日租金',
    `deposit`     DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '押金',
    `status`      VARCHAR(16)   NOT NULL DEFAULT 'IN_STOCK' COMMENT '状态：IN_STOCK 在库 / RENTED 已租出 / REPAIR 维修中',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_equipment_code` (`code`),
    KEY `idx_equipment_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '设备表';

-- -------------------------------------------------------------
-- 12. 设备租赁表
-- -------------------------------------------------------------
CREATE TABLE `equipment_rental` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `member_id`    BIGINT        NOT NULL                COMMENT '会员ID',
    `equipment_id` BIGINT        NOT NULL                COMMENT '设备ID',
    `rent_time`    DATETIME      NOT NULL                COMMENT '租借时间',
    `return_time`  DATETIME      NULL                    COMMENT '归还时间',
    `rent_fee`     DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '实际租金（归还时按租期结算）',
    `deposit`      DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '冻结押金',
    `status`       VARCHAR(16)   NOT NULL DEFAULT 'RENTING' COMMENT '状态：RENTING 租借中 / RETURNED 已归还',
    `operator_id`  BIGINT        NULL                    COMMENT '经办收银员ID',
    `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_rental_member` (`member_id`),
    KEY `idx_rental_status` (`status`),
    -- 租赁收入按归还时间确认，同样建复合索引
    KEY `idx_rental_return` (`status`, `return_time`),
    CONSTRAINT `fk_rental_member`    FOREIGN KEY (`member_id`)    REFERENCES `member` (`id`),
    CONSTRAINT `fk_rental_equipment` FOREIGN KEY (`equipment_id`) REFERENCES `equipment` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '设备租赁表';

-- -------------------------------------------------------------
-- 13. 预约表
-- -------------------------------------------------------------
CREATE TABLE `reservation` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`        VARCHAR(20) NOT NULL                COMMENT '预约单号',
    `member_id`   BIGINT      NOT NULL                COMMENT '会员ID',
    `seat_id`     BIGINT      NOT NULL                COMMENT '机位ID',
    `start_time`  DATETIME    NOT NULL                COMMENT '预约到店时间',
    `end_time`    DATETIME    NOT NULL                COMMENT '预约结束时间',
    `status`      VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待核销 / USED 已核销 / CANCELED 已取消 / EXPIRED 已过期',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reservation_code` (`code`),
    KEY `idx_resv_member` (`member_id`),
    KEY `idx_resv_seat_time` (`seat_id`, `start_time`, `end_time`),
    KEY `idx_resv_status` (`status`),
    CONSTRAINT `fk_resv_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
    CONSTRAINT `fk_resv_seat`   FOREIGN KEY (`seat_id`)   REFERENCES `seat` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '预约表';

-- -------------------------------------------------------------
-- 14. 公告表
-- -------------------------------------------------------------
CREATE TABLE `notice` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`        VARCHAR(128) NOT NULL                COMMENT '公告标题',
    `content`      TEXT         NOT NULL                COMMENT '公告内容',
    `publisher_id` BIGINT       NULL                    COMMENT '发布人（管理员ID）',
    `status`       TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 已发布 / 0 已撤下 / -1 已删除',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    `update_time`  DATETIME     NULL                    COMMENT '最后修改时间',
    PRIMARY KEY (`id`),
    KEY `idx_notice_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '公告表';
