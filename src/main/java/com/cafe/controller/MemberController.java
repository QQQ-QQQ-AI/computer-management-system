package com.cafe.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cafe.common.BizException;
import com.cafe.common.Constants;
import com.cafe.common.LoginUser;
import com.cafe.entity.Equipment;
import com.cafe.entity.EquipmentRental;
import com.cafe.entity.Member;
import com.cafe.entity.Notice;
import com.cafe.entity.ProductOrder;
import com.cafe.entity.Reservation;
import com.cafe.entity.SeatSession;
import com.cafe.service.BalanceService;
import com.cafe.service.EquipmentService;
import com.cafe.service.MemberService;
import com.cafe.service.NoticeService;
import com.cafe.service.ProductService;
import com.cafe.service.ReservationService;
import com.cafe.service.SeatService;
import com.cafe.service.SessionService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 会员自助端控制器（对应需求中的 8 个功能模块）。
 *
 * 安全约定：会员ID 一律从 Session 中取，绝不接受前端传入的 memberId。
 * 若从前端取，会员只要改一下参数就能查看或操作他人的账户，
 * 这是最典型的越权漏洞。
 */
@Controller
@RequestMapping("/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final SessionService sessionService;
    private final ProductService productService;
    private final EquipmentService equipmentService;
    private final ReservationService reservationService;
    private final NoticeService noticeService;
    private final SeatService seatService;
    private final BalanceService balanceService;

    /** 从 Session 取当前登录会员ID */
    private Long me(HttpSession session) {
        LoginUser user = (LoginUser) session.getAttribute(Constants.SESSION_LOGIN_USER);
        if (user == null || !Constants.ROLE_MEMBER.equals(user.getRole())) {
            throw new BizException("登录状态已失效，请重新登录");
        }
        return user.getId();
    }

    /* ==================== 1. 首页概览 ==================== */

    @GetMapping
    public String index(HttpSession session, Model model) {
        Long memberId = me(session);
        Member member = memberService.getById(memberId);

        model.addAttribute("member", member);
        // 近 5 条上机记录
        IPage<SeatSession> recent = sessionService.page(1, 5, memberId, null);
        model.addAttribute("recentSessions", recent.getRecords());
        // 进行中的上机（走联表查询，页面才能显示机位编号而不是裸 ID）
        model.addAttribute("activeSession", sessionService.findActiveByMember(memberId));
        // 只查一次，数量直接取列表长度，避免同一个查询跑两遍
        List<Notice> notices = noticeService.listPublished();
        model.addAttribute("notices", notices);
        model.addAttribute("noticeCount", notices.size());
        return "member/index";
    }

    /* ==================== 2. 余额查询（并入首页与账户流水） ==================== */

    @GetMapping("/balance")
    public String balance(HttpSession session, Model model,
                          @RequestParam(defaultValue = "1") long page) {
        Long memberId = me(session);
        model.addAttribute("member", memberService.getById(memberId));
        model.addAttribute("page", balanceService.pageRecords(page, 10, memberId, null));
        // 顺带展示账目是否一致：差额为 0 表示余额与流水完全吻合
        model.addAttribute("diff", balanceService.checkConsistency(memberId));
        return "member/balance";
    }

    /* ==================== 3. 在线充值 ==================== */

    @GetMapping("/recharge")
    public String rechargePage(HttpSession session, Model model,
                               @RequestParam(defaultValue = "1") long page) {
        Long memberId = me(session);
        model.addAttribute("member", memberService.getById(memberId));
        model.addAttribute("page", memberService.pageRecharge(page, 10, memberId));
        return "member/recharge";
    }

    @PostMapping("/recharge")
    public String doRecharge(HttpSession session,
                             @RequestParam BigDecimal amount,
                             RedirectAttributes ra) {
        Long memberId = me(session);
        try {
            BigDecimal after = memberService.recharge(memberId, amount, Constants.RECHARGE_ONLINE, null);
            ra.addFlashAttribute("msg", "充值成功，本次充值 " + amount + " 元，当前余额 " + after + " 元");
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/member/recharge";
    }

    /* ==================== 4. 上机记录查询 ==================== */

    @GetMapping("/sessions")
    public String sessions(HttpSession session, Model model,
                           @RequestParam(defaultValue = "1") long page,
                           @RequestParam(required = false) String status) {
        Long memberId = me(session);
        model.addAttribute("page", sessionService.page(page, 10, memberId, status));
        model.addAttribute("status", status);
        return "member/sessions";
    }

    /* ==================== 5. 上机费用与商品消费明细 ==================== */

    @GetMapping("/fees")
    public String fees(HttpSession session, Model model,
                       @RequestParam(defaultValue = "1") long page,
                       @RequestParam(defaultValue = "1") long orderPage) {
        Long memberId = me(session);
        IPage<SeatSession> sessions = sessionService.page(page, 10, memberId, Constants.SESSION_FINISHED);
        model.addAttribute("page", sessions);

        // 历史累计金额，走 SQL 汇总。
        // 不能拿当前页的记录求和：那样翻页时金额会跟着变，展示出来是错的。
        BigDecimal hourSum = nz(sessionService.sumHourFeeTotal(memberId));
        BigDecimal productSum = nz(productService.sumAmountByMember(memberId));
        model.addAttribute("hourSum", hourSum);
        model.addAttribute("productSum", productSum);
        model.addAttribute("totalSum", hourSum.add(productSum));

        // 商品消费明细
        IPage<ProductOrder> orders = productService.pageOrders(orderPage, 10, memberId, null);
        model.addAttribute("orders", orders.getRecords());
        model.addAttribute("orderPage", orders);
        return "member/fees";
    }

    /* ==================== 6. 设备租赁 ==================== */

    @GetMapping("/rentals")
    public String rentals(HttpSession session, Model model,
                          @RequestParam(defaultValue = "1") long page) {
        Long memberId = me(session);
        model.addAttribute("equipments", equipmentService.listAvailable());
        model.addAttribute("allEquipments", equipmentService.listAll());
        model.addAttribute("page", equipmentService.pageRentals(page, 10, memberId, null));
        model.addAttribute("member", memberService.getById(memberId));
        return "member/rentals";
    }

    /**
     * 提交租借申请。
     * 校验余额并冻结押金后即时生成租赁记录，顾客到前台领取设备即可。
     */
    @PostMapping("/rentals")
    public String doRent(HttpSession session,
                         @RequestParam Long equipmentId,
                         RedirectAttributes ra) {
        Long memberId = me(session);
        try {
            EquipmentRental rental = equipmentService.rent(memberId, equipmentId, null);
            Equipment equipment = equipmentService.getById(equipmentId);
            ra.addFlashAttribute("msg", String.format(
                    "租借申请成功：%s（%s），已冻结押金 %s 元。请到前台领取设备，归还后押金将原额解冻。",
                    equipment.getCategory(), equipment.getCode(), rental.getDeposit()));
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/member/rentals";
    }

    /* ==================== 7. 在线预约 ==================== */

    @GetMapping("/reservation")
    public String reservationPage(HttpSession session, Model model,
                                  @RequestParam(defaultValue = "1") long page) {
        Long memberId = me(session);
        model.addAttribute("seatList", seatService.listAll());
        model.addAttribute("activeList", reservationService.listActiveByMember(memberId));
        model.addAttribute("page", reservationService.page(page, 10, memberId, null));
        return "member/reservation";
    }

    @PostMapping("/reservation")
    public String doReservation(HttpSession session,
                                @RequestParam Long seatId,
                                @RequestParam String startTime,
                                @RequestParam String endTime,
                                RedirectAttributes ra) {
        Long memberId = me(session);
        try {
            // 页面时间输入框的格式为 yyyy-MM-ddTHH:mm（datetime-local 的标准格式）
            LocalDateTime start = LocalDateTime.parse(startTime.replace(" ", "T"));
            LocalDateTime end = LocalDateTime.parse(endTime.replace(" ", "T"));
            Reservation reservation = reservationService.create(memberId, seatId, start, end);
            ra.addFlashAttribute("msg", "预约成功，预约单号 " + reservation.getCode() + "，请按时到店");
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            ra.addFlashAttribute("error", "时间格式不正确，请重新选择");
        }
        return "redirect:/member/reservation";
    }

    @PostMapping("/reservation/cancel")
    public String cancelReservation(HttpSession session,
                                    @RequestParam Long id,
                                    RedirectAttributes ra) {
        Long memberId = me(session);
        try {
            // 传入本人ID，Service 会校验归属，防止取消他人预约
            reservationService.cancel(id, memberId);
            ra.addFlashAttribute("msg", "预约已取消");
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/member/reservation";
    }

    /* ==================== 8. 公告查看 ==================== */

    @GetMapping("/notices")
    public String notices(Model model) {
        List<Notice> notices = noticeService.listPublished();
        model.addAttribute("notices", notices);
        return "member/notices";
    }

    /* ==================== 附：个人资料与改密 ==================== */

    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {
        model.addAttribute("member", memberService.getById(me(session)));
        return "member/profile";
    }

    @PostMapping("/profile/password")
    public String changePassword(HttpSession session,
                                 @RequestParam String oldPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes ra) {
        Long memberId = me(session);
        if (!newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("error", "两次输入的新密码不一致");
            return "redirect:/member/profile";
        }
        try {
            memberService.changePassword(memberId, oldPassword, newPassword);
            session.invalidate();
            ra.addFlashAttribute("msg", "密码修改成功，请使用新密码重新登录");
            return "redirect:/login";
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/member/profile";
    }

    /** 商品明细查询（上机费用页的下钻） */
    @GetMapping("/orders")
    public String orders(HttpSession session, Model model,
                         @RequestParam(defaultValue = "1") long page,
                         @RequestParam(required = false) Long sessionId) {
        Long memberId = me(session);
        IPage<ProductOrder> orders = productService.pageOrders(page, 15, memberId, sessionId);
        model.addAttribute("page", orders);
        model.addAttribute("sessionId", sessionId);
        return "member/orders";
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
