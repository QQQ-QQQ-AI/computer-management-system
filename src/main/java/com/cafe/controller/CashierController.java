package com.cafe.controller;

import com.cafe.common.BizException;
import com.cafe.common.Constants;
import com.cafe.common.LoginUser;
import com.cafe.dto.BillingResult;
import com.cafe.dto.SeatView;
import com.cafe.entity.Equipment;
import com.cafe.entity.EquipmentRental;
import com.cafe.entity.Member;
import com.cafe.entity.Product;
import com.cafe.entity.Reservation;
import com.cafe.entity.Seat;
import com.cafe.entity.SeatSession;
import com.cafe.service.EquipmentService;
import com.cafe.service.MemberService;
import com.cafe.service.NoticeService;
import com.cafe.service.ProductService;
import com.cafe.service.ReservationService;
import com.cafe.service.SeatService;
import com.cafe.service.SessionService;
import com.cafe.service.StatsService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 收银员端控制器（对应需求中的 8 个功能模块）。
 *
 * 经办人一律取自 Session 中的登录用户，不接受前端传参：
 * 充值、开卡等操作都会在流水中记录经办人，若由前端传入，
 * 收银员就能伪造成他人的经办记录，交接班对账将失去意义。
 */
@Slf4j
@Controller
@RequestMapping("/cashier")
@RequiredArgsConstructor
public class CashierController {

    private final SeatService seatService;
    private final SessionService sessionService;
    private final MemberService memberService;
    private final ProductService productService;
    private final EquipmentService equipmentService;
    private final ReservationService reservationService;
    private final NoticeService noticeService;
    private final StatsService statsService;

    /** 当前登录员工的ID，作为业务记录的经办人 */
    private Long me(HttpSession session) {
        LoginUser user = (LoginUser) session.getAttribute(Constants.SESSION_LOGIN_USER);
        if (user == null) {
            throw new BizException("登录状态已失效，请重新登录");
        }
        return user.getId();
    }

    /* ==================== 1. 收银工作台 ==================== */

    @GetMapping
    public String index(Model model) {
        // 座位图：把机位状态与当前生效预约合并为四种展示态（红/绿/黄/蓝）
        model.addAttribute("board", seatService.buildBoard());
        model.addAttribute("boardCount", seatService.countByDisplayStatus());
        // 保留原始三态计数：顶部统计卡片只统计真实机位状态，
        // 「预约」是派生展示态，混进机位统计会让维护中/使用中的数字失真
        model.addAttribute("statusCount", seatService.countByStatus());

        // 使用中的上机记录，并逐条算出当前应收金额，方便收银员直接报价
        List<SeatSession> usingList = sessionService.listUsing();
        Map<Long, String> elapsed = new LinkedHashMap<>();
        for (SeatSession s : usingList) {
            elapsed.put(s.getId(), formatElapsed(s.getStartTime()));
        }
        model.addAttribute("usingList", usingList);
        model.addAttribute("estimates", buildEstimates(usingList));
        model.addAttribute("elapsed", elapsed);
        model.addAttribute("today", statsService.todaySummary());
        model.addAttribute("notices", noticeService.listPublished());
        return "cashier/index";
    }

    /**
     * 座位图实时数据接口 —— 供前端定时轮询。
     *
     * <p>为什么用轮询而不是 WebSocket / SSE：本项目部署在内网单机，
     * 座位图刷新间隔 5 秒，轮询实现成本最低且不需要额外的长连接基础设施；
     * WebSocket 会引入一个游走在 MVC 之外的通道，与「Session 统一鉴权」的设计相冲突。
     * 座位图一次查询仅 3 条 SQL，5 秒轮询的负载可以忽略。</p>
     *
     * <p>返回体只包含渲染座位图必需的字段，不返回完整实体，
     * 避免把配置、内部 ID 等无关信息暴露到前端。</p>
     */
    @GetMapping("/seat-board/data")
    @ResponseBody
    public Map<String, Object> seatBoardData() {
        List<SeatView> board = seatService.buildBoard();
        // 只挑前端渲染需要的字段，顺带统一时间格式，前端无需再做时间解析
        List<Map<String, Object>> cells = new ArrayList<>(board.size());
        for (SeatView v : board) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("seatId", v.getSeatId());
            c.put("seatNo", v.getSeatNo());
            c.put("area", v.getArea());
            c.put("displayStatus", v.getDisplayStatus());
            c.put("displayStatusName", v.getDisplayStatusName());
            c.put("openable", v.isOpenable());
            c.put("memberName", v.getMemberName());
            c.put("elapsed", v.getElapsed());
            c.put("reservationMemberName", v.getReservationMemberName());
            c.put("reservationHint", v.getReservationHint());
            c.put("tooltip", v.getTooltip());
            cells.add(c);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("time", LocalDateTime.now().withNano(0).toString());
        result.put("cells", cells);
        result.put("count", seatService.countByDisplayStatus());
        return result;
    }

    /* ==================== 2. 会员开卡 ==================== */

    @GetMapping("/open-card")
    public String openCardPage(Model model) {
        model.addAttribute("levels", memberService.listLevels());
        model.addAttribute("nextCardNo", memberService.generateCardNo());
        return "cashier/open-card";
    }

    @PostMapping("/open-card")
    public String doOpenCard(HttpSession session,
                             @RequestParam String name,
                             @RequestParam String idCard,
                             @RequestParam String phone,
                             @RequestParam(required = false) Integer level,
                             @RequestParam(required = false) String password,
                             @RequestParam(required = false) BigDecimal initialAmount,
                             RedirectAttributes ra) {
        try {
            Member member = memberService.openCard(name, idCard, phone, level,
                    password, initialAmount, me(session));
            ra.addFlashAttribute("msg", String.format(
                    "开卡成功：%s（%s），卡号 %s%s", member.getName(), member.getPhone(), member.getCardNo(),
                    member.getBalance().signum() > 0
                            ? "，已充值 " + member.getBalance() + " 元" : ""));
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cashier/open-card";
    }

    /* ==================== 3. 开台上机 ==================== */

    @GetMapping("/open-seat")
    public String openSeatPage(Model model, @RequestParam(required = false) String cardNo) {
        model.addAttribute("freeSeats", seatService.listFree());
        model.addAttribute("cardNo", cardNo);
        if (cardNo != null && !cardNo.isBlank()) {
            try {
                model.addAttribute("member", memberService.getByCardNo(cardNo.trim()));
            } catch (BizException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        return "cashier/open-seat";
    }

    @PostMapping("/open-seat")
    public String doOpenSeat(HttpSession session,
                             @RequestParam String cardNo,
                             @RequestParam Long seatId,
                             RedirectAttributes ra) {
        try {
            Member member = memberService.getByCardNo(cardNo.trim());
            SeatSession sess = sessionService.open(member.getId(), seatId, me(session));
            Seat seat = seatService.getById(seatId);
            ra.addFlashAttribute("msg", String.format(
                    "开台成功：%s（%s）已上机，机位 %s，记录号 %d",
                    member.getName(), member.getCardNo(), seat.getSeatNo(), sess.getId()));
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cashier/open-seat";
    }

    /* ==================== 4. 下机结算 ==================== */

    @GetMapping("/checkout")
    public String checkoutPage(Model model, @RequestParam(required = false) String cardNo) {
        List<SeatSession> usingList = sessionService.listUsing();
        model.addAttribute("usingList", usingList);
        model.addAttribute("estimates", buildEstimates(usingList));
        model.addAttribute("cardNo", cardNo);
        return "cashier/checkout";
    }

    @PostMapping("/checkout")
    public String doCheckout(HttpSession session,
                             @RequestParam Long sessionId,
                             RedirectAttributes ra) {
        try {
            SeatSession settled = sessionService.checkout(sessionId, me(session));
            ra.addFlashAttribute("msg", String.format(
                    "结算成功：记录号 %d，时长 %d 分钟，上机费 %s 元 + 商品费 %s 元 = 合计 %s 元",
                    settled.getId(), settled.getDurationMinutes(),
                    settled.getHourFee().toPlainString(), settled.getProductFee().toPlainString(),
                    settled.getTotalFee().toPlainString()));
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cashier/checkout";
    }

    /* ==================== 5. 商品记账 ==================== */

    @GetMapping("/product")
    public String productPage(Model model, @RequestParam(required = false) String cardNo) {
        model.addAttribute("products", productService.listOnSale());
        model.addAttribute("cardNo", cardNo);
        if (cardNo != null && !cardNo.isBlank()) {
            try {
                // 只查一次会员，避免为了取ID而重复查询同一张表
                Member member = memberService.getByCardNo(cardNo.trim());
                model.addAttribute("member", member);
                model.addAttribute("activeSession",
                        sessionService.findActiveByMember(member.getId()));
            } catch (BizException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        return "cashier/product";
    }

    @PostMapping("/product")
    public String doRecordProduct(HttpSession session,
                                  @RequestParam String cardNo,
                                  @RequestParam Long productId,
                                  @RequestParam Integer quantity,
                                  RedirectAttributes ra) {
        try {
            Member member = memberService.getByCardNo(cardNo.trim());
            // sessionId 传 null，由服务层自动判断该会员是否正在上机：
            // 上机中则挂账到下机时结算，未上机则当场扣款
            productService.recordOrder(member.getId(), productId, quantity, null, me(session));
            Product product = productService.getById(productId);
            ra.addFlashAttribute("msg", String.format(
                    "记账成功：%s 为 %s 购买 %s × %d",
                    member.getCardNo(), member.getName(), product.getName(), quantity));
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cashier/product";
    }

    /* ==================== 6. 充值办理 ==================== */

    @GetMapping("/recharge")
    public String rechargePage(HttpSession session, Model model,
                               @RequestParam(required = false) String cardNo) {
        model.addAttribute("cardNo", cardNo);
        if (cardNo != null && !cardNo.isBlank()) {
            try {
                model.addAttribute("member", memberService.getByCardNo(cardNo.trim()));
            } catch (BizException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        model.addAttribute("today", statsService.todaySummary());
        // 交接班对账要看的是「本人今日经办的现金充值」，而不是全店充值总额
        model.addAttribute("myCashRecharge", statsService.todayCashRechargeBy(me(session)));
        return "cashier/recharge";
    }

    @PostMapping("/recharge")
    public String doRecharge(HttpSession session,
                             @RequestParam String cardNo,
                             @RequestParam BigDecimal amount,
                             RedirectAttributes ra) {
        try {
            Member member = memberService.getByCardNo(cardNo.trim());
            BigDecimal after = memberService.recharge(member.getId(), amount,
                    Constants.RECHARGE_CASH, me(session));
            ra.addFlashAttribute("msg", String.format(
                    "充值成功：%s（%s）充值 %s 元，当前余额 %s 元",
                    member.getName(), member.getCardNo(), amount, after));
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cashier/recharge";
    }

    /* ==================== 7. 租赁办理 ==================== */

    @GetMapping("/rental")
    public String rentalPage(Model model, @RequestParam(required = false) String cardNo) {
        model.addAttribute("available", equipmentService.listAvailable());
        model.addAttribute("renting", equipmentService.pageRentals(1, 30, null,
                Constants.RENTAL_RENTING).getRecords());
        model.addAttribute("cardNo", cardNo);
        if (cardNo != null && !cardNo.isBlank()) {
            try {
                model.addAttribute("member", memberService.getByCardNo(cardNo.trim()));
            } catch (BizException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        return "cashier/rental";
    }

    @PostMapping("/rental/rent")
    public String doRent(HttpSession session,
                         @RequestParam String cardNo,
                         @RequestParam Long equipmentId,
                         RedirectAttributes ra) {
        try {
            Member member = memberService.getByCardNo(cardNo.trim());
            EquipmentRental rental = equipmentService.rent(member.getId(), equipmentId, me(session));
            Equipment equipment = equipmentService.getById(equipmentId);
            ra.addFlashAttribute("msg", String.format(
                    "租借成功：%s 租用 %s（%s），冻结押金 %s 元",
                    member.getName(), equipment.getCategory(), equipment.getCode(),
                    rental.getDeposit().toPlainString()));
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cashier/rental";
    }

    @PostMapping("/rental/return")
    public String doReturn(HttpSession session,
                           @RequestParam Long rentalId,
                           RedirectAttributes ra) {
        try {
            EquipmentRental rental = equipmentService.returnEquipment(rentalId, me(session));
            ra.addFlashAttribute("msg", String.format(
                    "归还成功：解冻押金 %s 元，收取租金 %s 元",
                    rental.getDeposit().toPlainString(), rental.getRentFee().toPlainString()));
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cashier/rental";
    }

    /* ==================== 8. 预约核销 ==================== */

    @GetMapping("/reservation")
    public String reservationPage(Model model,
                                  @RequestParam(defaultValue = "1") long page,
                                  @RequestParam(required = false) String status) {
        if (status == null || status.isBlank()) {
            status = Constants.RESV_PENDING;
        }
        model.addAttribute("page", reservationService.page(page, 15, null, status));
        model.addAttribute("status", status);
        return "cashier/reservation";
    }

    @PostMapping("/reservation/verify")
    public String doVerify(HttpSession session,
                           @RequestParam Long id,
                           RedirectAttributes ra) {
        try {
            Reservation reservation = reservationService.verify(id, me(session));
            ra.addFlashAttribute("msg", "核销成功：预约单号 " + reservation.getCode()
                    + "，已为预约会员在原机位开台");
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cashier/reservation";
    }

    /* ==================== 公告查看 ==================== */

    @GetMapping("/notices")
    public String notices(Model model) {
        model.addAttribute("notices", noticeService.listPublished());
        return "cashier/notices";
    }

    /**
     * 批量预估使用中记录的当前应收金额。
     * 单条预估失败（例如某区域尚未配置计费规则）只记日志、不影响其余记录展示，
     * 否则一个配置缺失就会让整个工作台打不开。
     */
    private Map<Long, BillingResult> buildEstimates(List<SeatSession> usingList) {
        Map<Long, BillingResult> estimates = new LinkedHashMap<>();
        for (SeatSession s : usingList) {
            try {
                estimates.put(s.getId(), sessionService.estimate(s.getId()));
            } catch (BizException e) {
                log.warn("预估费用失败 记录号={} {}", s.getId(), e.getMessage());
            }
        }
        return estimates;
    }

    /**
     * 把开台至今的时长格式化为「X 时 Y 分」。
     * 刻意在控制器里算好再交给页面：Thymeleaf 的 #temporals 工具类没有区间计算的方法，
     * 在模板里做时间运算既不可靠也不好读。
     */
    private String formatElapsed(java.time.LocalDateTime start) {
        return com.cafe.common.TimeUtil.elapsedFrom(start);
    }
}
