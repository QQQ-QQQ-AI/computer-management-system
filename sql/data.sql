-- =============================================================
--  基于 Java 的网咖管理系统  演示数据
--  ⚠ 本文件由 gen_seed.py 自动生成，请勿手工修改；
--    改计费规则或业务场景请改脚本后重新运行。
--  数据自洽性：会员余额 == 账户流水逐笔累加 == 充值 − 上机费 − 商品费 − 租金
-- =============================================================

USE `cafe_db`;
SET NAMES utf8mb4;

-- 系统用户（admin/admin123，cashier01|cashier02/cashier123）
INSERT INTO `sys_user` (`id`,`username`,`password`,`real_name`,`role`,`status`) VALUES
(1,'admin','0192023a7bbd73250516f069df18b500','系统管理员','ADMIN',1),
(2,'cashier01','dbb8c54ee649f8af049357a5f99cede6','李婷','CASHIER',1),
(3,'cashier02','dbb8c54ee649f8af049357a5f99cede6','王强','CASHIER',1);

-- 会员等级配置
INSERT INTO `member_level` (`level`,`level_name`,`discount`,`upgrade_amount`,`description`) VALUES
(1,'普通',1.00,0.00,'注册即为普通会员，无折扣'),
(2,'银卡',0.95,500.00,'累计消费满 500 元升级，享九五折'),
(3,'金卡',0.90,2000.00,'累计消费满 2000 元升级，享九折');

-- 计费规则（区域会员折扣；实际折扣取它与等级折扣中更优惠的一个）
INSERT INTO `billing_rule` (`id`,`area`,`hour_price`,`package_hours`,`package_price`,`member_discount`,`status`) VALUES
(1,'普通区',6.00,5,25.00,1.00,1),
(2,'竞技区',10.00,5,45.00,0.95,1),
(3,'包厢区',15.00,NULL,NULL,1.00,1);

-- 会员（余额与冻结押金均由流水推演得出）
INSERT INTO `member` (`id`,`card_no`,`name`,`id_card`,`phone`,`password`,`balance`,`deposit`,`level`,`status`) VALUES
(1,'M20260001','张三','360102200001011234','13800001111','e10adc3949ba59abbe56e057f20f883e',122.25,0.00,1,1),
(2,'M20260002','李四','360102200002022345','13800002222','e10adc3949ba59abbe56e057f20f883e',180.65,60.00,2,1),
(3,'M20260003','王五','360102200003033456','13800003333','e10adc3949ba59abbe56e057f20f883e',126.00,0.00,3,1);

-- 机位
INSERT INTO `seat` (`id`,`seat_no`,`area`,`config`,`status`) VALUES
(1,'A01','普通区','i5-12400F / 16G / GTX1660S','FREE'),
(2,'A02','普通区','i5-12400F / 16G / GTX1660S','FREE'),
(3,'A03','普通区','i5-12400F / 16G / GTX1660S','FREE'),
(4,'A04','普通区','i5-12400F / 16G / GTX1660S','FREE'),
(5,'A05','普通区','i5-12400F / 16G / GTX1660S','USING'),
(6,'A06','普通区','i5-12400F / 16G / GTX1660S','FREE'),
(7,'A07','普通区','i5-12400F / 16G / GTX1660S','FREE'),
(8,'A08','普通区','i5-12400F / 16G / GTX1660S','FREE'),
(9,'A09','普通区','i5-12400F / 16G / GTX1660S','FREE'),
(10,'A10','普通区','i5-12400F / 16G / GTX1660S','FREE'),
(11,'B01','竞技区','i7-13700KF / 32G / RTX4070','FREE'),
(12,'B02','竞技区','i7-13700KF / 32G / RTX4070','MAINTENANCE'),
(13,'B03','竞技区','i7-13700KF / 32G / RTX4070','FREE'),
(14,'B04','竞技区','i7-13700KF / 32G / RTX4070','FREE'),
(15,'B05','竞技区','i7-13700KF / 32G / RTX4070','FREE'),
(16,'B06','竞技区','i7-13700KF / 32G / RTX4070','FREE'),
(17,'B07','竞技区','i7-13700KF / 32G / RTX4070','FREE'),
(18,'B08','竞技区','i7-13700KF / 32G / RTX4070','FREE'),
(19,'C01','包厢区','i7-13700KF / 32G / RTX4070 / 独立空调','FREE'),
(20,'C02','包厢区','i7-13700KF / 32G / RTX4070 / 独立空调','FREE'),
(21,'C03','包厢区','i7-13700KF / 32G / RTX4070 / 独立空调','FREE'),
(22,'C04','包厢区','i7-13700KF / 32G / RTX4070 / 独立空调','FREE'),
(23,'A11','普通区','i5-12400F / 16G / GTX1660S','FREE'),
(24,'A12','普通区','i5-12400F / 16G / GTX1660S','FREE'),
(25,'A13','普通区','i5-12400F / 16G / GTX1660S','FREE'),
(26,'A14','普通区','i5-12400F / 16G / GTX1660S','FREE'),
(27,'A15','普通区','i5-12400F / 16G / GTX1660S','FREE'),
(28,'A16','普通区','i5-12400F / 16G / GTX1660S','FREE'),
(29,'A17','普通区','i5-12400F / 16G / GTX1660S','FREE'),
(30,'A18','普通区','i5-12400F / 16G / GTX1660S','FREE'),
(31,'A19','普通区','i5-12400F / 16G / GTX1660S','FREE'),
(32,'A20','普通区','i5-12400F / 16G / GTX1660S','FREE'),
(33,'A21','普通区','i5-12400F / 16G / GTX1660S','FREE'),
(34,'A22','普通区','i5-12400F / 16G / GTX1660S','FREE'),
(35,'A23','普通区','i5-12400F / 16G / GTX1660S','FREE'),
(36,'A24','普通区','i5-12400F / 16G / GTX1660S','FREE'),
(37,'A25','普通区','i5-12400F / 16G / GTX1660S','FREE'),
(38,'B09','竞技区','i7-13700KF / 32G / RTX4070','FREE'),
(39,'B10','竞技区','i7-13700KF / 32G / RTX4070','FREE'),
(40,'B11','竞技区','i7-13700KF / 32G / RTX4070','FREE'),
(41,'B12','竞技区','i7-13700KF / 32G / RTX4070','FREE'),
(42,'B13','竞技区','i7-13700KF / 32G / RTX4070','FREE'),
(43,'B14','竞技区','i7-13700KF / 32G / RTX4070','FREE'),
(44,'B15','竞技区','i7-13700KF / 32G / RTX4070','FREE'),
(45,'B16','竞技区','i7-13700KF / 32G / RTX4070','FREE'),
(46,'B17','竞技区','i7-13700KF / 32G / RTX4070','FREE'),
(47,'B18','竞技区','i7-13700KF / 32G / RTX4070','FREE'),
(48,'B19','竞技区','i7-13700KF / 32G / RTX4070','FREE'),
(49,'B20','竞技区','i7-13700KF / 32G / RTX4070','FREE'),
(50,'C05','包厢区','i7-13700KF / 32G / RTX4070 / 独立空调','FREE');

-- 商品（库存已扣除历史销售）
INSERT INTO `product` (`id`,`name`,`price`,`stock`,`status`) VALUES
(1,'可乐',4.00,110,1),
(2,'矿泉水',3.00,200,1),
(3,'泡面',8.00,55,1),
(4,'红牛',8.00,45,1),
(5,'薯片',6.00,73,1),
(6,'冰红茶',5.00,90,0);

-- 设备
INSERT INTO `equipment` (`id`,`code`,`category`,`rent_price`,`deposit`,`status`) VALUES
(1,'EQ-001','机械键盘',10.00,100.00,'IN_STOCK'),
(2,'EQ-002','游戏鼠标',8.00,80.00,'IN_STOCK'),
(3,'EQ-003','电竞耳机',6.00,60.00,'RENTED'),
(4,'EQ-004','电竞显示器',20.00,200.00,'IN_STOCK'),
(5,'EQ-005','机械键盘',10.00,100.00,'REPAIR');

-- 公告
INSERT INTO `notice` (`id`,`title`,`content`,`publisher_id`,`status`) VALUES
(1,'关于调整竞技区计费标准的公告','自本月起，竞技区小时单价为 10 元/小时，5 小时包时套餐 45 元；区域会员折扣九折。会员在此基础上再叠加等级折扣，实际按两者中更优惠的一档结算。',1,1),
(2,'周末会员充值满百赠送活动','本周六、周日到店充值满 100 元，额外赠送 10 元余额，每位会员限参与一次。',1,1),
(3,'设备租赁押金说明','外设租赁将按标准冻结相应押金，归还设备并确认无损坏后押金即时解冻，可继续用于消费。',1,1);

-- 上机记录（时长与费用由计费规则算出）
INSERT INTO `seat_session` (`id`,`member_id`,`seat_id`,`start_time`,`end_time`,`duration_minutes`,`hour_fee`,`product_fee`,`total_fee`,`status`) VALUES
(1,1,1,(NOW() - INTERVAL 8700 MINUTE),(NOW() - INTERVAL 8520 MINUTE),180,18.00,8.00,26.00,'FINISHED'),
(2,2,11,(NOW() - INTERVAL 8580 MINUTE),(NOW() - INTERVAL 8280 MINUTE),300,42.75,12.00,54.75,'FINISHED'),
(3,3,2,(NOW() - INTERVAL 7260 MINUTE),(NOW() - INTERVAL 7140 MINUTE),120,10.80,0.00,10.80,'FINISHED'),
(4,1,13,(NOW() - INTERVAL 7140 MINUTE),(NOW() - INTERVAL 6900 MINUTE),240,38.00,16.00,54.00,'FINISHED'),
(5,2,3,(NOW() - INTERVAL 5820 MINUTE),(NOW() - INTERVAL 5670 MINUTE),150,14.25,4.00,18.25,'FINISHED'),
(6,1,19,(NOW() - INTERVAL 5700 MINUTE),(NOW() - INTERVAL 5340 MINUTE),360,90.00,14.00,104.00,'FINISHED'),
(7,3,14,(NOW() - INTERVAL 4380 MINUTE),(NOW() - INTERVAL 4290 MINUTE),90,13.50,0.00,13.50,'FINISHED'),
(8,2,11,(NOW() - INTERVAL 4260 MINUTE),(NOW() - INTERVAL 4050 MINUTE),210,33.25,10.00,43.25,'FINISHED'),
(9,1,4,(NOW() - INTERVAL 2940 MINUTE),(NOW() - INTERVAL 2820 MINUTE),120,12.00,4.00,16.00,'FINISHED'),
(10,3,20,(NOW() - INTERVAL 2820 MINUTE),(NOW() - INTERVAL 2520 MINUTE),300,67.50,12.00,79.50,'FINISHED'),
(11,2,6,(NOW() - INTERVAL 1500 MINUTE),(NOW() - INTERVAL 1320 MINUTE),180,17.10,16.00,33.10,'FINISHED'),
(12,1,15,(NOW() - INTERVAL 1380 MINUTE),(NOW() - INTERVAL 960 MINUTE),420,61.75,16.00,77.75,'FINISHED'),
(13,3,7,(NOW() - INTERVAL 300 MINUTE),(NOW() - INTERVAL 120 MINUTE),180,16.20,4.00,20.20,'FINISHED'),
(14,2,5,(NOW() - INTERVAL 95 MINUTE),NULL,NULL,0.00,0.00,0.00,'USING');

-- 商品消费记账
INSERT INTO `product_order` (`session_id`,`member_id`,`product_id`,`quantity`,`amount`,`operator_id`,`create_time`) VALUES
(1,1,1,2,8.00,2,(NOW() - INTERVAL 8520 MINUTE)),
(2,2,3,1,8.00,2,(NOW() - INTERVAL 8280 MINUTE)),
(2,2,1,1,4.00,2,(NOW() - INTERVAL 8280 MINUTE)),
(4,1,4,2,16.00,2,(NOW() - INTERVAL 6900 MINUTE)),
(5,2,1,1,4.00,2,(NOW() - INTERVAL 5670 MINUTE)),
(6,1,3,1,8.00,2,(NOW() - INTERVAL 5340 MINUTE)),
(6,1,5,1,6.00,2,(NOW() - INTERVAL 5340 MINUTE)),
(8,2,1,1,4.00,2,(NOW() - INTERVAL 4050 MINUTE)),
(8,2,5,1,6.00,2,(NOW() - INTERVAL 4050 MINUTE)),
(9,1,1,1,4.00,2,(NOW() - INTERVAL 2820 MINUTE)),
(10,3,4,1,8.00,2,(NOW() - INTERVAL 2520 MINUTE)),
(10,3,1,1,4.00,2,(NOW() - INTERVAL 2520 MINUTE)),
(11,2,1,2,8.00,2,(NOW() - INTERVAL 1320 MINUTE)),
(11,2,3,1,8.00,2,(NOW() - INTERVAL 1320 MINUTE)),
(12,1,3,2,16.00,2,(NOW() - INTERVAL 960 MINUTE)),
(13,3,1,1,4.00,2,(NOW() - INTERVAL 120 MINUTE));

-- 充值记录
INSERT INTO `recharge_record` (`member_id`,`amount`,`type`,`operator_id`,`create_time`) VALUES
(1,300.00,'CASH',2,(NOW() - INTERVAL 8760 MINUTE)),
(2,300.00,'CASH',2,(NOW() - INTERVAL 8700 MINUTE)),
(3,150.00,'CASH',3,(NOW() - INTERVAL 7320 MINUTE)),
(1,100.00,'ONLINE',NULL,(NOW() - INTERVAL 3000 MINUTE)),
(3,100.00,'ONLINE',NULL,(NOW() - INTERVAL 1560 MINUTE)),
(2,100.00,'CASH',2,(NOW() - INTERVAL 120 MINUTE));

-- 设备租赁
INSERT INTO `equipment_rental` (`member_id`,`equipment_id`,`rent_time`,`return_time`,`rent_fee`,`deposit`,`status`,`operator_id`) VALUES
(2,1,(NOW() - INTERVAL 2880 MINUTE),(NOW() - INTERVAL 1440 MINUTE),10.00,100.00,'RETURNED',2),
(2,3,(NOW() - INTERVAL 90 MINUTE),NULL,0.00,60.00,'RENTING',2);

-- 预约
INSERT INTO `reservation` (`code`,`member_id`,`seat_id`,`start_time`,`end_time`,`status`) VALUES
('R20260928001',1,11,(NOW() + INTERVAL 120 MINUTE),(NOW() + INTERVAL 300 MINUTE),'PENDING'),
('R20260928002',3,19,(NOW() + INTERVAL 240 MINUTE),(NOW() + INTERVAL 480 MINUTE),'PENDING');

-- 账户流水（与上方所有业务记录一一对应，可用于重算余额校验）
INSERT INTO `balance_record` (`member_id`,`change_type`,`change_amount`,`before_balance`,`after_balance`,`biz_type`,`biz_id`,`remark`,`operator_id`,`create_time`) VALUES
(1,'RECHARGE',300.00,0.00,300.00,NULL,NULL,'会员充值',2,(NOW() - INTERVAL 8760 MINUTE)),
(2,'RECHARGE',300.00,0.00,300.00,NULL,NULL,'会员充值',2,(NOW() - INTERVAL 8700 MINUTE)),
(1,'HOUR_FEE',-18.00,300.00,282.00,'SESSION',1,'上机费结算',2,(NOW() - INTERVAL 8520 MINUTE)),
(1,'PRODUCT',-8.00,282.00,274.00,'SESSION',1,'随上机结算的商品消费',2,(NOW() - INTERVAL 8520 MINUTE)),
(2,'HOUR_FEE',-42.75,300.00,257.25,'SESSION',2,'上机费结算',2,(NOW() - INTERVAL 8280 MINUTE)),
(2,'PRODUCT',-12.00,257.25,245.25,'SESSION',2,'随上机结算的商品消费',2,(NOW() - INTERVAL 8280 MINUTE)),
(3,'RECHARGE',150.00,0.00,150.00,NULL,NULL,'会员充值',3,(NOW() - INTERVAL 7320 MINUTE)),
(3,'HOUR_FEE',-10.80,150.00,139.20,'SESSION',3,'上机费结算',2,(NOW() - INTERVAL 7140 MINUTE)),
(1,'HOUR_FEE',-38.00,274.00,236.00,'SESSION',4,'上机费结算',2,(NOW() - INTERVAL 6900 MINUTE)),
(1,'PRODUCT',-16.00,236.00,220.00,'SESSION',4,'随上机结算的商品消费',2,(NOW() - INTERVAL 6900 MINUTE)),
(2,'HOUR_FEE',-14.25,245.25,231.00,'SESSION',5,'上机费结算',2,(NOW() - INTERVAL 5670 MINUTE)),
(2,'PRODUCT',-4.00,231.00,227.00,'SESSION',5,'随上机结算的商品消费',2,(NOW() - INTERVAL 5670 MINUTE)),
(1,'HOUR_FEE',-90.00,220.00,130.00,'SESSION',6,'上机费结算',2,(NOW() - INTERVAL 5340 MINUTE)),
(1,'PRODUCT',-14.00,130.00,116.00,'SESSION',6,'随上机结算的商品消费',2,(NOW() - INTERVAL 5340 MINUTE)),
(3,'HOUR_FEE',-13.50,139.20,125.70,'SESSION',7,'上机费结算',2,(NOW() - INTERVAL 4290 MINUTE)),
(2,'HOUR_FEE',-33.25,227.00,193.75,'SESSION',8,'上机费结算',2,(NOW() - INTERVAL 4050 MINUTE)),
(2,'PRODUCT',-10.00,193.75,183.75,'SESSION',8,'随上机结算的商品消费',2,(NOW() - INTERVAL 4050 MINUTE)),
(1,'RECHARGE',100.00,116.00,216.00,NULL,NULL,'会员充值',NULL,(NOW() - INTERVAL 3000 MINUTE)),
(2,'DEPOSIT_FREEZE',-100.00,183.75,83.75,'RENTAL',NULL,'设备租赁押金冻结',2,(NOW() - INTERVAL 2880 MINUTE)),
(1,'HOUR_FEE',-12.00,216.00,204.00,'SESSION',9,'上机费结算',2,(NOW() - INTERVAL 2820 MINUTE)),
(1,'PRODUCT',-4.00,204.00,200.00,'SESSION',9,'随上机结算的商品消费',2,(NOW() - INTERVAL 2820 MINUTE)),
(3,'HOUR_FEE',-67.50,125.70,58.20,'SESSION',10,'上机费结算',2,(NOW() - INTERVAL 2520 MINUTE)),
(3,'PRODUCT',-12.00,58.20,46.20,'SESSION',10,'随上机结算的商品消费',2,(NOW() - INTERVAL 2520 MINUTE)),
(3,'RECHARGE',100.00,46.20,146.20,NULL,NULL,'会员充值',NULL,(NOW() - INTERVAL 1560 MINUTE)),
(2,'DEPOSIT_UNFREEZE',100.00,83.75,183.75,'RENTAL',NULL,'设备归还押金解冻',2,(NOW() - INTERVAL 1440 MINUTE)),
(2,'RENT_FEE',-10.00,183.75,173.75,'RENTAL',NULL,'设备租金',2,(NOW() - INTERVAL 1440 MINUTE)),
(2,'HOUR_FEE',-17.10,173.75,156.65,'SESSION',11,'上机费结算',2,(NOW() - INTERVAL 1320 MINUTE)),
(2,'PRODUCT',-16.00,156.65,140.65,'SESSION',11,'随上机结算的商品消费',2,(NOW() - INTERVAL 1320 MINUTE)),
(1,'HOUR_FEE',-61.75,200.00,138.25,'SESSION',12,'上机费结算',2,(NOW() - INTERVAL 960 MINUTE)),
(1,'PRODUCT',-16.00,138.25,122.25,'SESSION',12,'随上机结算的商品消费',2,(NOW() - INTERVAL 960 MINUTE)),
(2,'RECHARGE',100.00,140.65,240.65,NULL,NULL,'会员充值',2,(NOW() - INTERVAL 120 MINUTE)),
(3,'HOUR_FEE',-16.20,146.20,130.00,'SESSION',13,'上机费结算',2,(NOW() - INTERVAL 120 MINUTE)),
(3,'PRODUCT',-4.00,130.00,126.00,'SESSION',13,'随上机结算的商品消费',2,(NOW() - INTERVAL 120 MINUTE)),
(2,'DEPOSIT_FREEZE',-60.00,240.65,180.65,'RENTAL',NULL,'设备租赁押金冻结',2,(NOW() - INTERVAL 90 MINUTE));
