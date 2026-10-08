# -*- coding: utf-8 -*-
"""
网咖管理系统 —— 演示数据生成器

为什么用脚本生成而不是手写 data.sql：
    余额、账户流水、上机费、商品消费、库存之间存在连锁关系，
    手写极易出现「product_fee 与明细对不上」「余额推不出流水」这类自相矛盾。
    本脚本用与系统 Service 层完全一致的计费规则统一推导，
    保证「会员余额 == 账户流水逐笔累加 == 充值 − 上机费 − 商品费 − 租金」。
    改动计费规则后重新运行本脚本即可刷新演示数据。

运行：python gen_seed.py    →  覆盖生成同目录下的 data.sql
"""
import math
import os
from datetime import date
from decimal import Decimal, ROUND_HALF_UP

# ============================================================
# 一、基础配置（与系统内规则保持一致）
# ============================================================

# 会员等级：编码 / 名称 / 等级折扣 / 升级门槛 / 说明
LEVELS = [
    (1, '普通', '1.00', '0.00',    '注册即为普通会员，无折扣'),
    (2, '银卡', '0.95', '500.00',  '累计消费满 500 元升级，享九五折'),
    (3, '金卡', '0.90', '2000.00', '累计消费满 2000 元升级，享九折'),
]
LEVEL_DISCOUNT = {code: Decimal(d) for code, _, d, _, _ in LEVELS}

# 计费规则：id / 区域 / 小时单价 / 包时小时 / 包时价 / 区域会员折扣
RULES = [
    (1, '普通区', '6.00',  5,    '25.00', '1.00'),
    (2, '竞技区', '10.00', 5,    '45.00', '0.95'),
    (3, '包厢区', '15.00', None, None,    '1.00'),
]
RULE = {r[1]: dict(hour=Decimal(r[2]), pkg_h=r[3], pkg_p=(Decimal(r[4]) if r[4] else None),
                   area_disc=Decimal(r[5])) for r in RULES}

# 商品：id / 名称 / 单价 / 初始库存 / 状态
PRODUCTS = [
    (1, '可乐',   '4.00', 120, 1),
    (2, '矿泉水', '3.00', 200, 1),
    (3, '泡面',   '8.00', 60,  1),
    (4, '红牛',   '8.00', 48,  1),
    (5, '薯片',   '6.00', 75,  1),
    (6, '冰红茶', '5.00', 90,  0),
]
PRODUCT_PRICE = {p[0]: Decimal(p[2]) for p in PRODUCTS}

# 会员：id / 卡号 / 姓名 / 身份证 / 手机 / 等级
MEMBERS = [
    (1, 'M20260001', '张三', '360102200001011234', '13800001111', 1),
    (2, 'M20260002', '李四', '360102200002022345', '13800002222', 2),
    (3, 'M20260003', '王五', '360102200003033456', '13800003333', 3),
]
MEMBER_PWD = 'e10adc3949ba59abbe56e057f20f883e'   # 明文 123456 的 MD5
STAFF_PWD_ADMIN = '0192023a7bbd73250516f069df18b500'   # admin123
STAFF_PWD_CASHIER = 'dbb8c54ee649f8af049357a5f99cede6'  # cashier123

# 机位：普通区 25 + 竞技区 20 + 包厢区 5 = 50
#
# 编号与 ID 的分配原则：**已有编号的 ID 必须保持不变**。
# 历史上机记录、预约、流水都通过 seat_id 关联机位，若因为扩容而挪动了原有 ID，
# 这些历史记录会指向错误的区域，进而让按区域计费的金额全部变化 —— 演示数据就自相矛盾了。
# 因此保持 1~22 的原分配不动，新增的 28 台从 ID 23 起追加。
SEATS = []

# 原有：普通区 A01-A10（ID 1-10）
for i in range(1, 11):
    SEATS.append((i, 'A%02d' % i, '普通区', 'i5-12400F / 16G / GTX1660S'))
# 原有：竞技区 B01-B08（ID 11-18）
for i in range(1, 9):
    SEATS.append((10 + i, 'B%02d' % i, '竞技区', 'i7-13700KF / 32G / RTX4070'))
# 原有：包厢区 C01-C04（ID 19-22）
for i in range(1, 5):
    SEATS.append((18 + i, 'C%02d' % i, '包厢区', 'i7-13700KF / 32G / RTX4070 / 独立空调'))

# 新增：普通区 A11-A25（15 台，ID 23-37）
for n, i in enumerate(range(11, 26), start=23):
    SEATS.append((n, 'A%02d' % i, '普通区', 'i5-12400F / 16G / GTX1660S'))
# 新增：竞技区 B09-B20（12 台，ID 38-49）
for n, i in enumerate(range(9, 21), start=38):
    SEATS.append((n, 'B%02d' % i, '竞技区', 'i7-13700KF / 32G / RTX4070'))
# 新增：包厢区 C05（1 台，ID 50）
SEATS.append((50, 'C05', '包厢区', 'i7-13700KF / 32G / RTX4070 / 独立空调'))

SEAT_AREA = {s[0]: s[2] for s in SEATS}
SEAT_NO = {s[0]: s[1] for s in SEATS}
SEAT_STATUS = {5: 'USING', 12: 'MAINTENANCE'}      # 其余 FREE

# 设备：id / 编号 / 类别 / 日租金 / 押金 / 状态
EQUIPMENTS = [
    (1, 'EQ-001', '机械键盘',   '10.00', '100.00', 'IN_STOCK'),
    (2, 'EQ-002', '游戏鼠标',   '8.00',  '80.00',  'IN_STOCK'),
    (3, 'EQ-003', '电竞耳机',   '6.00',  '60.00',  'RENTED'),
    (4, 'EQ-004', '电竞显示器', '20.00', '200.00', 'IN_STOCK'),
    (5, 'EQ-005', '机械键盘',   '10.00', '100.00', 'REPAIR'),
]

# ============================================================
# 二、计费核心算法（必须与 Service 层 BillingService 保持一致）
# ============================================================

def calc_hour_fee(minutes, area, member_level):
    """
    计算上机费。

    规则：
      1. 计费时长向上取整到 5 分钟（不满 5 分钟按 5 分钟计）；
      2. 折扣 = min(区域会员折扣, 等级折扣)，会员享受两者中更优惠的一个；
      3. 按小时计费与包时套餐分别计算，取金额更低的方案（自动取优）。

    返回 (最终费用, 按小时费用, 是否命中套餐)
    """
    rule = RULE[area]
    discount = min(rule['area_disc'], LEVEL_DISCOUNT[member_level])

    billable = math.ceil(minutes / 5.0) * 5                 # 向上取整到 5 分钟
    hourly_total = rule['hour'] * discount * Decimal(billable) / Decimal(60)

    best = hourly_total
    hit_package = False
    if rule['pkg_h'] and rule['pkg_p']:
        pkg_min = rule['pkg_h'] * 60
        whole = billable // pkg_min                          # 完整套餐个数
        rest = billable % pkg_min
        pkg_total = (rule['pkg_p'] * discount * whole
                     + rule['hour'] * discount * Decimal(rest) / Decimal(60))
        if pkg_total < best:
            best = pkg_total
            hit_package = True

    return (q2(best), q2(hourly_total), hit_package)


def q2(v):
    """金额四舍五入保留两位小数"""
    return Decimal(v).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)


# ============================================================
# 三、业务场景（相对当前时间，单位为「多少分钟以前」）
# ============================================================

# 上机记录：id / 会员 / 机位 / 开台(分钟前) / 时长 / 商品购买[(商品id, 数量)] / 状态
SESSIONS = [
    (1,  1, 1,  8700, 180, [(1, 2)],           'FINISHED'),
    (2,  2, 11, 8580, 300, [(3, 1), (1, 1)],   'FINISHED'),
    (3,  3, 2,  7260, 120, [],                 'FINISHED'),
    (4,  1, 13, 7140, 240, [(4, 2)],           'FINISHED'),
    (5,  2, 3,  5820, 150, [(1, 1)],           'FINISHED'),
    (6,  1, 19, 5700, 360, [(3, 1), (5, 1)],   'FINISHED'),
    (7,  3, 14, 4380, 90,  [],                 'FINISHED'),
    (8,  2, 11, 4260, 210, [(1, 1), (5, 1)],   'FINISHED'),
    (9,  1, 4,  2940, 120, [(1, 1)],           'FINISHED'),
    (10, 3, 20, 2820, 300, [(4, 1), (1, 1)],   'FINISHED'),
    (11, 2, 6,  1500, 180, [(1, 2), (3, 1)],   'FINISHED'),
    (12, 1, 15, 1380, 420, [(3, 2)],           'FINISHED'),
    (13, 3, 7,   300, 180, [(1, 1)],           'FINISHED'),
    (14, 2, 5,    95, None, [],                'USING'),      # 进行中，未结算
]

# 充值：会员 / 金额 / 方式 / 经办员工 / 分钟前
RECHARGES = [
    (1, '300.00', 'CASH',   2,    8760),
    (2, '300.00', 'CASH',   2,    8700),
    (3, '150.00', 'CASH',   3,    7320),
    (1, '100.00', 'ONLINE', None, 3000),
    (3, '100.00', 'ONLINE', None, 1560),
    (2, '100.00', 'CASH',   2,    120),
]

# 设备租赁：会员 / 设备 / 租借(分钟前) / 归还(分钟前或None) / 经办
RENTALS = [
    (2, 1, 2880, 1440, 2),
    (2, 3,   90, None, 2),
]

# 预约：单号 / 会员 / 机位 / 开始(分钟前,负数为未来) / 结束 / 状态
# 单号格式必须与 ReservationService.generateCode() 一致（R + 日期 + 3 位流水），
# 否则演示数据与运行时新建的单号看起来像两套规则。
_CODE_PREFIX = 'R' + date.today().strftime('%Y%m%d')
RESERVATIONS = [
    (_CODE_PREFIX + '001', 1, 11, -120, -300, 'PENDING'),
    (_CODE_PREFIX + '002', 3, 19, -240, -480, 'PENDING'),
]

NOTICES = [
    ('关于调整竞技区计费标准的公告',
     '自本月起，竞技区小时单价为 10 元/小时，5 小时包时套餐 45 元；区域会员折扣九折。'
     '会员在此基础上再叠加等级折扣，实际按两者中更优惠的一档结算。'),
    ('周末会员充值满百赠送活动',
     '本周六、周日到店充值满 100 元，额外赠送 10 元余额，每位会员限参与一次。'),
    ('设备租赁押金说明',
     '外设租赁将按标准冻结相应押金，归还设备并确认无损坏后押金即时解冻，可继续用于消费。'),
]


def sql_time(minutes_ago):
    """分钟前 → MySQL 时间表达式；负值表示未来"""
    if minutes_ago >= 0:
        return "(NOW() - INTERVAL %d MINUTE)" % minutes_ago
    return "(NOW() + INTERVAL %d MINUTE)" % (-minutes_ago)


def esc(s):
    return s.replace("'", "''")


# ============================================================
# 四、生成
# ============================================================

def build():
    # ---------- 1. 逐笔构造账务事件，按真实时间正序 ----------
    events = []          # (时间键, 类型, 数据)  时间键越小越早（分钟前越大越早）
    sold = {p[0]: 0 for p in PRODUCTS}

    for mid, amount, rtype, op, ago in RECHARGES:
        events.append((ago, 'RECHARGE', dict(member=mid, amount=Decimal(amount),
                                             rtype=rtype, op=op, ago=ago)))

    session_fee = {}     # sid -> dict(hour_fee, product_fee, total, minutes, end_ago)
    for sid, mid, seat, start_ago, dur, buys, status in SESSIONS:
        if status != 'FINISHED':
            continue
        area = SEAT_AREA[seat]
        level = next(m[5] for m in MEMBERS if m[0] == mid)
        hour_fee, hourly_only, hit_pkg = calc_hour_fee(dur, area, level)
        prod_fee = sum(PRODUCT_PRICE[pid] * qty for pid, qty in buys)
        end_ago = start_ago - dur
        session_fee[sid] = dict(hour_fee=hour_fee, product_fee=q2(prod_fee),
                                total=q2(hour_fee + prod_fee), minutes=dur,
                                end_ago=end_ago, start_ago=start_ago, member=mid,
                                hourly_only=hourly_only, hit_package=hit_pkg)
        events.append((end_ago, 'HOUR_FEE', dict(sid=sid, member=mid,
                                                 amount=hour_fee, ago=end_ago)))
        if prod_fee > 0:
            events.append((end_ago, 'PRODUCT', dict(sid=sid, member=mid,
                                                    amount=q2(prod_fee), ago=end_ago)))
        for pid, qty in buys:
            sold[pid] += qty

    for mid, eqid, rent_ago, ret_ago, op in RENTALS:
        eq = next(e for e in EQUIPMENTS if e[0] == eqid)
        deposit = Decimal(eq[4])
        events.append((rent_ago, 'DEPOSIT_FREEZE',
                       dict(member=mid, amount=deposit, eqid=eqid, op=op, ago=rent_ago)))
        if ret_ago is not None:
            days = max(1, int(round((rent_ago - ret_ago) / 1440.0)))
            rent_fee = q2(Decimal(eq[3]) * days)
            events.append((ret_ago, 'DEPOSIT_UNFREEZE',
                           dict(member=mid, amount=deposit, eqid=eqid, op=op, ago=ret_ago)))
            events.append((ret_ago - 1, 'RENT_FEE',
                           dict(member=mid, amount=rent_fee, eqid=eqid, op=op, ago=ret_ago)))

    # 时间正序：分钟前越大越早。同一时刻按类型固定顺序，保证可复现。
    order = {'RECHARGE': 0, 'RENT_FEE': 1, 'HOUR_FEE': 2, 'PRODUCT': 3,
             'DEPOSIT_UNFREEZE': 4, 'DEPOSIT_FREEZE': 5}
    events.sort(key=lambda e: (-e[0], order[e[1]]))

    # ---------- 2. 按事件推演余额，生成账户流水 ----------
    balance = {m[0]: Decimal('0.00') for m in MEMBERS}
    deposit = {m[0]: Decimal('0.00') for m in MEMBERS}
    ledger = []          # (member, type, amount, before, after, biz_type, biz_id, remark, op, ago)

    for _, etype, d in events:
        mid = d['member']
        before = balance[mid]

        if etype == 'RECHARGE':
            delta = d['amount']
            biz, bid, remark = None, None, '会员充值'
            op = d['op']
        elif etype == 'HOUR_FEE':
            delta = -d['amount']
            biz, bid, remark = 'SESSION', d['sid'], '上机费结算'
            op = 2
        elif etype == 'PRODUCT':
            delta = -d['amount']
            biz, bid, remark = 'SESSION', d['sid'], '随上机结算的商品消费'
            op = 2
        elif etype == 'RENT_FEE':
            delta = -d['amount']
            biz, bid, remark = 'RENTAL', None, '设备租金'
            op = d['op']
        elif etype == 'DEPOSIT_FREEZE':
            delta = -d['amount']
            biz, bid, remark = 'RENTAL', None, '设备租赁押金冻结'
            op = d['op']
        else:  # DEPOSIT_UNFREEZE
            delta = d['amount']
            biz, bid, remark = 'RENTAL', None, '设备归还押金解冻'
            op = d['op']

        after = q2(before + delta)
        if after < 0:
            raise SystemExit('余额不足：会员 %d 在事件 %s 后余额 %s，请调整充值时序' % (mid, etype, after))

        balance[mid] = after
        if etype == 'DEPOSIT_FREEZE':
            deposit[mid] = q2(deposit[mid] + d['amount'])
        elif etype == 'DEPOSIT_UNFREEZE':
            deposit[mid] = q2(deposit[mid] - d['amount'])

        # change_amount 必须保留符号：正数表示余额增加，负数表示减少
        ledger.append(dict(member=mid, type=etype, amount=q2(delta), before=before,
                           after=after, biz=biz, bid=bid, remark=remark, op=op, ago=d['ago']))

    # ---------- 2.5 自校验：流水累加必须等于推演出的余额，否则直接失败 ----------
    for mid in balance:
        ledger_sum = q2(sum((x['amount'] for x in ledger if x['member'] == mid), Decimal('0')))
        if ledger_sum != balance[mid]:
            raise SystemExit('自校验失败：会员 %d 余额 %s ≠ 流水累加 %s'
                             % (mid, balance[mid], ledger_sum))
        # 前后余额必须首尾衔接
        seq = [x for x in ledger if x['member'] == mid]
        for i in range(1, len(seq)):
            if seq[i]['before'] != seq[i - 1]['after']:
                raise SystemExit('自校验失败：会员 %d 第 %d 笔流水前后余额不衔接' % (mid, i + 1))

    # ---------- 3. 输出 SQL ----------
    L = []
    w = L.append
    w('-- =============================================================')
    w('--  基于 Java 的网咖管理系统  演示数据')
    w('--  ⚠ 本文件由 gen_seed.py 自动生成，请勿手工修改；')
    w('--    改计费规则或业务场景请改脚本后重新运行。')
    w('--  数据自洽性：会员余额 == 账户流水逐笔累加 == 充值 − 上机费 − 商品费 − 租金')
    w('-- =============================================================')
    w('')
    w('USE `cafe_db`;')
    w('SET NAMES utf8mb4;')
    w('')

    # 系统用户
    w('-- 系统用户（admin/admin123，cashier01|cashier02/cashier123）')
    w('INSERT INTO `sys_user` (`id`,`username`,`password`,`real_name`,`role`,`status`) VALUES')
    w("(1,'admin','%s','系统管理员','ADMIN',1)," % STAFF_PWD_ADMIN)
    w("(2,'cashier01','%s','李婷','CASHIER',1)," % STAFF_PWD_CASHIER)
    w("(3,'cashier02','%s','王强','CASHIER',1);" % STAFF_PWD_CASHIER)
    w('')

    # 会员等级
    w('-- 会员等级配置')
    w('INSERT INTO `member_level` (`level`,`level_name`,`discount`,`upgrade_amount`,`description`) VALUES')
    rows = ["(%d,'%s',%s,%s,'%s')" % (c, n, d, u, esc(desc)) for c, n, d, u, desc in LEVELS]
    w(',\n'.join(rows) + ';')
    w('')

    # 计费规则
    w('-- 计费规则（区域会员折扣；实际折扣取它与等级折扣中更优惠的一个）')
    w('INSERT INTO `billing_rule` (`id`,`area`,`hour_price`,`package_hours`,`package_price`,`member_discount`,`status`) VALUES')
    rows = []
    for rid, area, hp, ph, pp, md in RULES:
        rows.append("(%d,'%s',%s,%s,%s,%s,1)" % (rid, area, hp,
                                                 'NULL' if ph is None else ph,
                                                 'NULL' if pp is None else pp, md))
    w(',\n'.join(rows) + ';')
    w('')

    # 会员
    w('-- 会员（余额与冻结押金均由流水推演得出）')
    w('INSERT INTO `member` (`id`,`card_no`,`name`,`id_card`,`phone`,`password`,`balance`,`deposit`,`level`,`status`) VALUES')
    rows = []
    for mid, card, name, idc, phone, lvl in MEMBERS:
        rows.append("(%d,'%s','%s','%s','%s','%s',%s,%s,%d,1)"
                    % (mid, card, name, idc, phone, MEMBER_PWD,
                       balance[mid], deposit[mid], lvl))
    w(',\n'.join(rows) + ';')
    w('')

    # 机位
    w('-- 机位')
    w('INSERT INTO `seat` (`id`,`seat_no`,`area`,`config`,`status`) VALUES')
    rows = []
    for sid, no, area, cfg in SEATS:
        rows.append("(%d,'%s','%s','%s','%s')" % (sid, no, area, cfg, SEAT_STATUS.get(sid, 'FREE')))
    w(',\n'.join(rows) + ';')
    w('')

    # 商品（库存扣除已售出）
    w('-- 商品（库存已扣除历史销售）')
    w('INSERT INTO `product` (`id`,`name`,`price`,`stock`,`status`) VALUES')
    rows = []
    for pid, name, price, stock, st in PRODUCTS:
        rows.append("(%d,'%s',%s,%d,%d)" % (pid, name, price, max(0, stock - sold[pid]), st))
    w(',\n'.join(rows) + ';')
    w('')

    # 设备
    w('-- 设备')
    w('INSERT INTO `equipment` (`id`,`code`,`category`,`rent_price`,`deposit`,`status`) VALUES')
    rows = ["(%d,'%s','%s',%s,%s,'%s')" % e for e in EQUIPMENTS]
    w(',\n'.join(rows) + ';')
    w('')

    # 公告
    w('-- 公告')
    w('INSERT INTO `notice` (`id`,`title`,`content`,`publisher_id`,`status`) VALUES')
    rows = ["(%d,'%s','%s',1,1)" % (i + 1, esc(t), esc(c)) for i, (t, c) in enumerate(NOTICES)]
    w(',\n'.join(rows) + ';')
    w('')

    # 上机记录
    w('-- 上机记录（时长与费用由计费规则算出）')
    w('INSERT INTO `seat_session` (`id`,`member_id`,`seat_id`,`start_time`,`end_time`,`duration_minutes`,'
      '`hour_fee`,`product_fee`,`total_fee`,`status`) VALUES')
    rows = []
    for sid, mid, seat, start_ago, dur, buys, status in SESSIONS:
        if status == 'FINISHED':
            f = session_fee[sid]
            rows.append("(%d,%d,%d,%s,%s,%d,%s,%s,%s,'FINISHED')"
                        % (sid, mid, seat, sql_time(start_ago), sql_time(f['end_ago']),
                           dur, f['hour_fee'], f['product_fee'], f['total']))
        else:
            rows.append("(%d,%d,%d,%s,NULL,NULL,0.00,0.00,0.00,'USING')"
                        % (sid, mid, seat, sql_time(start_ago)))
    w(',\n'.join(rows) + ';')
    w('')

    # 商品记账
    w('-- 商品消费记账')
    w('INSERT INTO `product_order` (`session_id`,`member_id`,`product_id`,`quantity`,`amount`,`operator_id`,`create_time`) VALUES')
    rows = []
    for sid, mid, seat, start_ago, dur, buys, status in SESSIONS:
        if status != 'FINISHED':
            continue
        ago = session_fee[sid]['end_ago']
        for pid, qty in buys:
            rows.append("(%d,%d,%d,%d,%s,2,%s)" % (sid, mid, pid, qty,
                                                   q2(PRODUCT_PRICE[pid] * qty), sql_time(ago)))
    w(',\n'.join(rows) + ';')
    w('')

    # 充值记录
    w('-- 充值记录')
    w('INSERT INTO `recharge_record` (`member_id`,`amount`,`type`,`operator_id`,`create_time`) VALUES')
    rows = []
    for mid, amount, rtype, op, ago in RECHARGES:
        rows.append("(%d,%s,'%s',%s,%s)" % (mid, amount, rtype,
                                            'NULL' if op is None else op, sql_time(ago)))
    w(',\n'.join(rows) + ';')
    w('')

    # 设备租赁
    w('-- 设备租赁')
    w('INSERT INTO `equipment_rental` (`member_id`,`equipment_id`,`rent_time`,`return_time`,`rent_fee`,`deposit`,`status`,`operator_id`) VALUES')
    rows = []
    for mid, eqid, rent_ago, ret_ago, op in RENTALS:
        eq = next(e for e in EQUIPMENTS if e[0] == eqid)
        if ret_ago is None:
            rows.append("(%d,%d,%s,NULL,0.00,%s,'RENTING',%d)" % (mid, eqid, sql_time(rent_ago), eq[4], op))
        else:
            days = max(1, int(round((rent_ago - ret_ago) / 1440.0)))
            rows.append("(%d,%d,%s,%s,%s,%s,'RETURNED',%d)"
                        % (mid, eqid, sql_time(rent_ago), sql_time(ret_ago),
                           q2(Decimal(eq[3]) * days), eq[4], op))
    w(',\n'.join(rows) + ';')
    w('')

    # 预约
    w('-- 预约')
    w('INSERT INTO `reservation` (`code`,`member_id`,`seat_id`,`start_time`,`end_time`,`status`) VALUES')
    rows = ["('%s',%d,%d,%s,%s,'%s')" % (c, mid, seat, sql_time(s), sql_time(e), st)
            for c, mid, seat, s, e, st in RESERVATIONS]
    w(',\n'.join(rows) + ';')
    w('')

    # 账户流水
    w('-- 账户流水（与上方所有业务记录一一对应，可用于重算余额校验）')
    w('INSERT INTO `balance_record` (`member_id`,`change_type`,`change_amount`,`before_balance`,'
      '`after_balance`,`biz_type`,`biz_id`,`remark`,`operator_id`,`create_time`) VALUES')
    rows = []
    for x in ledger:
        rows.append("(%d,'%s',%s,%s,%s,%s,%s,'%s',%s,%s)"
                    % (x['member'], x['type'], x['amount'], x['before'], x['after'],
                       'NULL' if x['biz'] is None else "'%s'" % x['biz'],
                       'NULL' if x['bid'] is None else x['bid'],
                       esc(x['remark']),
                       'NULL' if x['op'] is None else x['op'],
                       sql_time(x['ago'])))
    w(',\n'.join(rows) + ';')
    w('')

    return '\n'.join(L), balance, deposit, session_fee, ledger


if __name__ == '__main__':
    content, balance, deposit, session_fee, ledger = build()
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'data.sql')
    with open(out, 'w', encoding='utf-8', newline='\n') as f:
        f.write(content)

    print('已生成 %s' % out)
    print('-' * 58)
    print('会员余额 / 冻结押金（由流水推演）：')
    for mid, card, name, _, _, lvl in MEMBERS:
        print('  %-6s %-4s 等级%d  余额 %8s  押金 %7s'
              % (card, name, lvl, balance[mid], deposit[mid]))
    print('-' * 58)
    print('账户流水共 %d 条' % len(ledger))
    print('上机记录共 %d 条（已结算 %d 条）' % (len(SESSIONS), len(session_fee)))
    print('-' * 58)
    print('套餐命中情况（验证「自动取优」逻辑）：')
    for sid in sorted(session_fee):
        f = session_fee[sid]
        print('  记录%-3d 会员%d %4d分钟  按小时 %8s  实收 %8s  %s'
              % (sid, f['member'], f['minutes'], f['hourly_only'], f['hour_fee'],
                 '← 命中包时套餐' if f['hit_package'] else ''))
