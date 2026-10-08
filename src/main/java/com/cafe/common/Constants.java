package com.cafe.common;

/**
 * 系统常量：角色、状态枚举值。
 * 集中定义避免魔法字符串散落各处。
 */
public final class Constants {

    private Constants() {
    }

    /* ---------- 会话 key ---------- */
    public static final String SESSION_LOGIN_USER = "loginUser";

    /* ---------- 角色 ---------- */
    public static final String ROLE_MEMBER = "MEMBER";
    public static final String ROLE_CASHIER = "CASHIER";
    public static final String ROLE_ADMIN = "ADMIN";

    /* ---------- 机位状态（状态机，三态） ----------
     * 不设 RESERVED：预约通过 reservation 表的时段重叠检测控制，
     * 若预约即把机位锁死，会导致「预约今晚 8 点」从现在起就无法使用。 */
    public static final String SEAT_FREE = "FREE";
    public static final String SEAT_USING = "USING";
    public static final String SEAT_MAINTENANCE = "MAINTENANCE";

    /* ---------- 座位图展示态（派生值，非机位状态） ----------
     * 座位图需要区分「当前时刻有生效预约的机位」，但机位状态机里没有 RESERVED，
     * 因此新增一个仅用于展示的派生状态：由「机位状态 + 当前是否有生效预约」实时计算得出，
     * 不落库、不参与状态流转，机位表状态仍严格保持三态。
     * 优先级：维护中 > 使用中 > 预约中 > 空闲。即使用中的机器即使时段内有预约，
     * 也应显示为使用中（顾客正在上机是更强势的事实）。 */
    public static final String SEAT_DISPLAY_RESERVED = "RESERVED";

    /* ---------- 账户流水变动类型 ---------- */
    public static final String BAL_RECHARGE = "RECHARGE";
    public static final String BAL_HOUR_FEE = "HOUR_FEE";
    public static final String BAL_PRODUCT = "PRODUCT";
    public static final String BAL_RENT_FEE = "RENT_FEE";
    public static final String BAL_DEPOSIT_FREEZE = "DEPOSIT_FREEZE";
    public static final String BAL_DEPOSIT_UNFREEZE = "DEPOSIT_UNFREEZE";

    /* ---------- 上机记录状态 ---------- */
    public static final String SESSION_USING = "USING";
    public static final String SESSION_FINISHED = "FINISHED";

    /* ---------- 设备状态 ---------- */
    public static final String EQUIP_IN_STOCK = "IN_STOCK";
    public static final String EQUIP_RENTED = "RENTED";
    public static final String EQUIP_REPAIR = "REPAIR";

    /* ---------- 租赁状态 ---------- */
    public static final String RENTAL_RENTING = "RENTING";
    public static final String RENTAL_RETURNED = "RETURNED";

    /* ---------- 预约状态 ---------- */
    public static final String RESV_PENDING = "PENDING";
    public static final String RESV_USED = "USED";
    public static final String RESV_CANCELED = "CANCELED";
    public static final String RESV_EXPIRED = "EXPIRED";

    /* ---------- 充值方式 ---------- */
    public static final String RECHARGE_ONLINE = "ONLINE";
    public static final String RECHARGE_CASH = "CASH";

    /* ---------- 通用启用状态 ---------- */
    public static final int ENABLED = 1;
    public static final int DISABLED = 0;
}
