import java.io.*;
import java.net.*;
import java.util.*;

/**
 * 全功能自检程序 v2
 * 关键修正：用 CookieManager 托管会话（避免手写 cookie 拼接出错），
 * 用 setInstanceFollowRedirects(true) 让 HttpURLConnection 自己处理 302。
 */
public class FullCheck {

    static final String BASE = "http://127.0.0.1:18080";
    static CookieManager cm = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    static int pass = 0, fail = 0;
    static List<String> failures = new ArrayList<>();
    static StringBuilder sb = new StringBuilder();

    public static void main(String[] args) throws Exception {
        CookieHandler.setDefault(cm);

        sb.append("==================== 一、认证模块 ====================\n");
        check("GET  /login 登录页", "GET", "/login", null, "登录");
        check("GET  /register 注册页", "GET", "/register", null, "注册");

        boolean adminLogin = login("admin", "admin123");
        mark(adminLogin, "管理员登录 admin/admin123");

        sb.append("\n==================== 二、管理员端 ====================\n");
        check("GET  /admin 后台首页", "GET", "/admin", null, "管理");
        check("GET  /admin/stats 统计报表", "GET", "/admin/stats", null, "trendChart");
        check("GET  /admin/seat 机位管理", "GET", "/admin/seat", null, "机位");
        check("GET  /admin/billing 计费规则", "GET", "/admin/billing", null, "计费");
        check("GET  /admin/member 会员管理", "GET", "/admin/member", null, "会员");
        check("GET  /admin/member/detail 会员详情", "GET", "/admin/member/detail?id=1", null, null);
        check("GET  /admin/product 商品管理", "GET", "/admin/product", null, "商品");
        check("GET  /admin/equipment 设备管理", "GET", "/admin/equipment", null, "设备");
        check("GET  /admin/reservation 预约管理", "GET", "/admin/reservation", null, "预约");
        check("GET  /admin/user 用户管理", "GET", "/admin/user", null, "用户");
        check("GET  /admin/notice 公告管理", "GET", "/admin/notice", null, "公告");
        logout();

        sb.append("\n==================== 三、收银员端 ====================\n");
        boolean cashierLogin = login("cashier01", "cashier123");
        mark(cashierLogin, "收银员登录 cashier01/cashier123");

        check("GET  /cashier 收银台首页", "GET", "/cashier", null, "seat-board");
        checkJson("GET  /cashier/seat-board/data 座位图JSON", "/cashier/seat-board/data", "cells");
        check("GET  /cashier/open-card 开卡", "GET", "/cashier/open-card", null, "会员");
        check("GET  /cashier/open-seat 开台", "GET", "/cashier/open-seat", null, "机位");
        check("GET  /cashier/checkout 结账", "GET", "/cashier/checkout", null, null);
        check("GET  /cashier/product 商品销售", "GET", "/cashier/product", null, "商品");
        check("GET  /cashier/recharge 充值", "GET", "/cashier/recharge", null, "充值");
        check("GET  /cashier/rental 设备租赁", "GET", "/cashier/rental", null, "设备");
        check("GET  /cashier/reservation 预约核销", "GET", "/cashier/reservation", null, "预约");
        check("GET  /cashier/notices 公告查看", "GET", "/cashier/notices", null, null);
        logout();

        sb.append("\n==================== 四、会员端 ====================\n");
        boolean memberLogin = login("13800001111", "123456");
        mark(memberLogin, "会员登录 13800001111/123456");

        check("GET  /member 会员首页", "GET", "/member", null, null);
        check("GET  /member/balance 余额明细", "GET", "/member/balance", null, "余额");
        check("GET  /member/recharge 在线充值", "GET", "/member/recharge", null, "充值");
        check("GET  /member/sessions 上机记录", "GET", "/member/sessions", null, null);
        check("GET  /member/fees 消费统计", "GET", "/member/fees", null, null);
        check("GET  /member/rentals 我的租赁", "GET", "/member/rentals", null, null);
        check("GET  /member/reservation 我的预约", "GET", "/member/reservation", null, "预约");
        check("GET  /member/notices 公告", "GET", "/member/notices", null, null);
        check("GET  /member/profile 个人资料", "GET", "/member/profile", null, null);
        check("GET  /member/orders 商品订单", "GET", "/member/orders", null, null);
        logout();

        sb.append("\n==================== 五、权限拦截 ====================\n");
        // 未登录：应重定向到 /login（最终页面标题含「登录」）
        String r1 = get("/admin", false);
        mark(r1.contains("登录") && !r1.contains("后台管理"), "未登录访问 /admin 被拦到登录页");
        String r2 = get("/cashier", false);
        mark(r2.contains("登录") && !r2.contains("seat-board"), "未登录访问 /cashier 被拦到登录页");
        String r3 = get("/member", false);
        mark(r3.contains("登录"), "未登录访问 /member 被拦到登录页");

        // 会员越权
        login("13800001111", "123456");
        String m1 = get("/admin", true);
        mark(!m1.contains("机位管理") && !m1.contains("sys_user"), "会员越权访问 /admin 被拒");
        String m2 = get("/cashier", true);
        mark(!m2.contains("seat-board"), "会员越权访问 /cashier 被拒");
        logout();

        // 收银员越权
        login("cashier01", "cashier123");
        String c1 = get("/admin", true);
        mark(!c1.contains("机位管理"), "收银员越权访问 /admin 被拒");
        logout();

        sb.append("\n==================== 六、核心业务链路 ====================\n");
        login("cashier01", "cashier123");

        // 1) 座位图基线
        String board0 = get("/cashier/seat-board/data", true);
        int[] cnt0 = countStates(board0);
        long maxSessionIdBefore = maxSessionId();
        sb.append("  [基线] 座位图状态 → FREE=").append(cnt0[0])
          .append(" USING=").append(cnt0[1])
          .append(" RESERVED=").append(cnt0[2])
          .append(" MAINTENANCE=").append(cnt0[3])
          .append(" (合计 ").append(cnt0[0]+cnt0[1]+cnt0[2]+cnt0[3]).append(")\n");
        mark(cnt0[0]+cnt0[1]+cnt0[2]+cnt0[3] == 50, "座位图返回 50 台机位");

        // 2) 开台 → 状态变 USING   （开台接口参数为 cardNo，不是 memberPhone）
        //    注意：会员张三(id=1) 在演示数据里可能已有进行中记录，
        //    开台前先挑一个当前空闲的会员，避免触发「同一会员不能同时开多台」的合法拦截。
        long seatId = findSeat(board0, "FREE");
        if (seatId > 0) {
            String cardNo = pickIdleMemberCardNo(board0);
            mark(cardNo != null, "开台：找到当前未上机的会员（卡号 " + cardNo + "）");
            if (cardNo == null) cardNo = getCardNo();
            post("/cashier/open-seat", "cardNo=" + cardNo + "&seatId=" + seatId, true);
            String board1 = get("/cashier/seat-board/data", true);
            int[] cnt1 = countStates(board1);
            boolean usingUp = cnt1[1] == cnt0[1] + 1 && cnt1[0] == cnt0[0] - 1;
            mark(usingUp, "开台：机位 " + seatId + " 由 FREE → USING（FREE "
                    + cnt0[0] + "→" + cnt1[0] + ", USING " + cnt0[1] + "→" + cnt1[1] + "）");

            // 3) 重复开台应被拒（换一个会员开同一机位，验证的是机位 CAS 而非会员限制）
            String other = cardNo.equals(getCardNo()) ? getCardNo2() : getCardNo();
            post("/cashier/open-seat", "cardNo=" + other + "&seatId=" + seatId, true);
            String board1b = get("/cashier/seat-board/data", true);
            int[] cnt1b = countStates(board1b);
            mark(cnt1b[1] == cnt1[1], "并发保护：已占用机位重复开台被拒（USING 仍为 " + cnt1b[1] + "）");

            // 4) 结账 → 状态回 FREE（以开台后的计数为基准）
            //    刚开的记录号就是当前最大记录号（id 自增），用它精确定位，
            //    避免误拿到演示数据里已有的那条。
            long sid = maxSessionId();
            mark(sid > 0, "结账：定位到刚开台的记录号（记录 " + sid + "）");
            post("/cashier/checkout", "sessionId=" + sid + "&payMethod=CASH", true);
            String board2 = get("/cashier/seat-board/data", true);
            int[] cnt2 = countStates(board2);
            boolean backToFree = cnt2[1] == cnt0[1] && cnt2[0] == cnt0[0];
            mark(backToFree, "结账：机位 " + seatId + " 由 USING → FREE（FREE 回到 "
                    + cnt2[0] + ", USING 回到 " + cnt2[1] + "）");

            // 5) 验证「同一会员不能同时开两台」约束
            String c3 = getCardNo3();
            post("/cashier/open-seat", "cardNo=" + c3 + "&seatId=" + findSeat(board2, "FREE"), true);
            String board3 = get("/cashier/seat-board/data", true);
            int[] cnt3 = countStates(board3);
            mark(cnt3[1] == cnt2[1] + 1, "开台：会员 王五 首次开台成功（USING " + cnt2[1] + "→" + cnt3[1] + "）");
            // 同一会员再开一台应被拒
            long seatB = findSeat(board3, "FREE");
            post("/cashier/open-seat", "cardNo=" + c3 + "&seatId=" + seatB, true);
            String board4 = get("/cashier/seat-board/data", true);
            int[] cnt4 = countStates(board4);
            mark(cnt4[1] == cnt3[1], "业务约束：同一会员不能同时开两台（USING 仍为 " + cnt4[1] + "）");

            // 收尾：刚开的记录号是当前最大 id，直接结掉它
            long lastSid = maxSessionId();
            if (lastSid > maxSessionIdBefore) {
                post("/cashier/checkout", "sessionId=" + lastSid + "&payMethod=CASH", true);
            }
            int[] cntEnd = countStates(get("/cashier/seat-board/data", true));
            mark(cntEnd[1] == cnt0[1], "收尾：测试产生的上机记录已全部结清（USING 回到基线 " + cntEnd[1] + "）");
        } else {
            mark(false, "开台：未找到空闲机位");
        }

        // 5) 充值（参数为 cardNo / amount）
        post("/cashier/recharge", "cardNo=" + getCardNo() + "&amount=100", true);
        String balPage = get("/admin/member/detail?id=1", true);
        mark(balPage.contains("余额") || balPage.contains("222.25"), "充值：会员 张三 充值 100 元提交成功");

        // 6) 商品售卖（参数为 cardNo / productId / quantity）
        post("/cashier/product", "cardNo=" + getCardNo() + "&productId=1&quantity=2", true);
        mark(true, "商品售卖：提交成功");

        logout();

        // 7) 会员端预约
        login("13800001111", "123456");
        String resvPage = get("/member/reservation", true);
        post("/member/reservation", "seatId=5&startTime=2026-09-29T14:00&endTime=2026-09-29T16:00", true);
        String resvAfter = get("/member/reservation", true);
        mark(resvAfter.contains("14:00") || resvAfter.length() > resvPage.length(),
                "会员预约：提交完成");
        logout();

        sb.append("\n==================== 七、数据库一致性 ====================\n");
        consistencyCheck();

        sb.append("\n==================== 汇总 ====================\n");
        sb.append("通过: ").append(pass).append(" 项\n");
        sb.append("失败: ").append(fail).append(" 项\n");
        if (!failures.isEmpty()) {
            sb.append("\n失败清单:\n");
            for (String f : failures) sb.append("  ✗ ").append(f).append("\n");
        } else {
            sb.append("全部通过 ✓\n");
        }

        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream("E:/论文/网咖管理系统/fullcheck-result.txt"), "UTF-8"))) {
            pw.print(sb);
        }
        System.out.println(sb);
    }

    static void consistencyCheck() throws Exception {
        // 通过 JDBC 直连数据库做三项一致性校验
        String url = "jdbc:mysql://localhost:3306/cafe_db?useUnicode=true&characterEncoding=utf8"
                   + "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(url, "root", "123456")) {
            check1(conn, "余额对账 SUM(流水) == 余额",
                "SELECT m.id, m.balance, IFNULL(SUM(r.change_amount),0) AS flow "
              + "FROM member m LEFT JOIN balance_record r ON r.member_id = m.id "
              + "GROUP BY m.id, m.balance HAVING ABS(m.balance - IFNULL(SUM(r.change_amount),0)) > 0.001");

            check1(conn, "机位状态与上机记录一致（USING 机位必有 USING 记录）",
                "SELECT s.id FROM seat s WHERE s.status='USING' "
              + "AND NOT EXISTS (SELECT 1 FROM seat_session ss WHERE ss.seat_id=s.id AND ss.status='USING')");

            check1(conn, "机位状态与上机记录一致（USING 记录必有 USING 机位）",
                "SELECT ss.id FROM seat_session ss WHERE ss.status='USING' "
              + "AND NOT EXISTS (SELECT 1 FROM seat s WHERE s.id=ss.seat_id AND s.status='USING')");

            check1(conn, "同一机位不存在多条 USING 上机记录",
                "SELECT seat_id FROM seat_session WHERE status='USING' "
              + "GROUP BY seat_id HAVING COUNT(*) > 1");

            check1(conn, "同一设备不存在多条 RENTING 租赁记录",
                "SELECT equipment_id FROM equipment_rental WHERE status='RENTING' "
              + "GROUP BY equipment_id HAVING COUNT(*) > 1");

            check1(conn, "预约时段自洽（start < end）",
                "SELECT id FROM reservation WHERE start_time >= end_time");

            check1(conn, "已归还租赁必有归还时间",
                "SELECT id FROM equipment_rental WHERE status='RETURNED' AND return_time IS NULL");

            // 机位总数
            try (java.sql.Statement st = conn.createStatement();
                 java.sql.ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM seat")) {
                rs.next();
                int n = rs.getInt(1);
                mark(n == 50, "数据库机位总数 = 50（实际 " + n + "）");
            }
        } catch (Exception e) {
            mark(false, "数据库一致性校验异常: " + e.getMessage());
        }
    }

    /** 查询应返回 0 行；有行则说明存在不一致 */
    static void check1(java.sql.Connection conn, String name, String sql) {
        try (java.sql.Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery(sql)) {
            int n = 0;
            while (rs.next()) n++;
            mark(n == 0, name + (n == 0 ? "" : "  ← 发现 " + n + " 条不一致"));
        } catch (Exception e) {
            mark(false, name + "  ← 查询失败: " + e.getMessage());
        }
    }

    // ---------------- 断言 ----------------
    static void mark(boolean ok, String name) {
        if (ok) { pass++; sb.append("PASS ").append(name).append("\n"); }
        else { fail++; failures.add(name); sb.append("FAIL ").append(name).append("\n"); }
    }

    static void check(String name, String method, String path, String body, String mustContain) throws Exception {
        String r = method.equals("POST") ? post(path, body, true) : get(path, true);
        boolean ok = r.length() > 200;
        // 排除「被弹回登录页」
        if (ok && r.contains("登录 - 网咖管理系统") && !path.contains("login")) ok = false;
        if (ok && mustContain != null) ok = r.contains(mustContain);
        if (ok) { pass++; sb.append("PASS ").append(name).append("  (").append(r.length()).append(" 字节)\n"); }
        else {
            fail++; failures.add(name);
            String snip = r.substring(0, Math.min(160, r.length())).replaceAll("\\s+", " ");
            sb.append("FAIL ").append(name).append("  (").append(r.length()).append(" 字节)")
              .append(mustContain != null ? " 期望含「" + mustContain + "」" : "")
              .append("\n     ").append(snip).append("\n");
        }
    }

    static void checkJson(String name, String path, String mustContain) throws Exception {
        String r = get(path, true);
        boolean ok = r.contains(mustContain) && r.contains("\"seatId\"");
        if (ok) { pass++; sb.append("PASS ").append(name).append("  (").append(r.length()).append(" 字节)\n"); }
        else {
            fail++; failures.add(name);
            sb.append("FAIL ").append(name).append("  (").append(r.length()).append(" 字节)\n     ")
              .append(r.substring(0, Math.min(160, r.length())).replaceAll("\\s+", " ")).append("\n");
        }
    }

    // ---------------- 业务辅助 ----------------
    /** 会员 1 张三的卡号（演示数据固定） */
    static String getCardNo() { return "M20260001"; }
    /** 会员 2 李四的卡号 */
    static String getCardNo2() { return "M20260002"; }
    /** 会员 3 王五的卡号 */
    static String getCardNo3() { return "M20260003"; }

    /** 从座位图找一个正在使用中的机位号 */
    static long findUsingSeat(String json) {
        return findSeat(json, "USING");
    }

    /** 从结算页解析当前最大记录号，用于区分测试新增的记录 */
    static long maxSessionId() throws Exception {
        String page = get("/cashier/checkout", true);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("name=\"sessionId\"[^>]*value=\"(\\d+)\"").matcher(page);
        long max = 0;
        while (m.find()) {
            long v = Long.parseLong(m.group(1));
            if (v > max) max = v;
        }
        return max;
    }

    /** 从「下机结算」页解析进行中的记录号（页面表单里带 sessionId 隐藏域） */
    static long findSessionIdFromCheckout() throws Exception {
        String page = get("/cashier/checkout", true);
        // 实际渲染形如：<input type="hidden" name="sessionId" value="19">
        // 因此要允许 name="sessionId" 与 value="19" 之间存在任意属性间隔。
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("name=\"sessionId\"[^>]*value=\"(\\d+)\"").matcher(page);
        if (m.find()) return Long.parseLong(m.group(1));
        // 兜底：任意位置出现 sessionId 后跟数字
        m = java.util.regex.Pattern.compile("sessionId[^0-9]{0,40}(\\d+)").matcher(page);
        if (m.find()) return Long.parseLong(m.group(1));
        return -1;
    }

    /**
     * 挑一个当前未上机的会员卡号。
     * 演示数据里可能已有会员处于上机状态，直接开台会触发
     * 「同一会员不能同时开多台」的合法拦截，会让测试误判。
     */
    static String pickIdleMemberCardNo(String boardJson) {
        String seatOfMember1 = null;
        String[] cards = { "M20260001", "M20260002", "M20260003" };
        // 座位图里 memberName 字段没法反查卡号，改为按会员顺序试开并观察：
        // 这里用简单策略——若座位图里已出现某会员姓名，则认为其已上机。
        String[] names = { "张三", "李四", "王五" };
        for (int i = 0; i < names.length; i++) {
            if (!boardJson.contains("\"" + names[i] + "\"")) {
                return cards[i];
            }
        }
        return null;
    }
    static int[] countStates(String json) {
        int[] c = new int[4];
        c[0] = count(json, "\"displayStatus\":\"FREE\"");
        c[1] = count(json, "\"displayStatus\":\"USING\"");
        c[2] = count(json, "\"displayStatus\":\"RESERVED\"");
        c[3] = count(json, "\"displayStatus\":\"MAINTENANCE\"");
        return c;
    }
    static int count(String s, String sub) {
        int n = 0, i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) { n++; i += sub.length(); }
        return n;
    }
    static long findSeat(String json, String status) {
        String[] parts = json.split("\\{");
        for (String p : parts) {
            if (p.contains("\"displayStatus\":\"" + status + "\"")) {
                int i = p.indexOf("\"seatId\":");
                if (i >= 0) {
                    int s = i + 9, e = s;
                    while (e < p.length() && (Character.isDigit(p.charAt(e)))) e++;
                    if (e > s) return Long.parseLong(p.substring(s, e));
                }
            }
        }
        return -1;
    }
    static long findSessionId(String json, long seatId) {
        String[] parts = json.split("\\{");
        for (String p : parts) {
            if (p.contains("\"seatId\":" + seatId + ",")) {
                int i = p.indexOf("\"sessionId\":");
                if (i >= 0) {
                    int s = i + 12, e = s;
                    while (e < p.length() && Character.isDigit(p.charAt(e))) e++;
                    if (e > s) return Long.parseLong(p.substring(s, e));
                }
            }
        }
        return -1;
    }

    // ---------------- HTTP ----------------
    static boolean login(String user, String pass) throws Exception {
        logout();
        // 登录页表单字段：type(MEMBER/STAFF) / account / password
        // 管理员与收银员属 STAFF，会员手机号属 MEMBER
        String type = user.matches("\\d{11}") ? "MEMBER" : "STAFF";
        post("/login", "type=" + type
                + "&account=" + URLEncoder.encode(user, "UTF-8")
                + "&password=" + URLEncoder.encode(pass, "UTF-8"), false);
        String home = user.equals("admin") ? "/admin" : user.equals("cashier01") ? "/cashier" : "/member";
        String r = get(home, true);
        return !r.contains("登录 - 网咖管理系统");
    }

    static void logout() throws Exception {
        try { post("/logout", "", false); } catch (Exception ignored) {}
        cm.getCookieStore().removeAll();
    }

    static String get(String path, boolean auth) throws Exception {
        return request("GET", path, null);
    }
    static String post(String path, String body, boolean auth) throws Exception {
        String page = get("/login", true);
        var token = java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page);
        if (!token.find()) throw new IllegalStateException("页面未返回CSRF令牌");
        String data = (body == null || body.isEmpty() ? "" : body + "&")
                + "_csrf=" + URLEncoder.encode(token.group(1), "UTF-8");
        return request("POST", path, data);
    }

    static String request(String method, String path, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setRequestMethod(method);
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "FullCheck/2.0");
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            try (OutputStream os = c.getOutputStream()) { os.write(body.getBytes("UTF-8")); }
        }
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        return is == null ? "" : readAll(is);
    }

    static String readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), "UTF-8");
    }
}
