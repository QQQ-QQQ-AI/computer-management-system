package com.cafe.controller;

import com.cafe.common.BizException;
import com.cafe.common.Constants;
import com.cafe.common.LoginUser;
import com.cafe.dto.BillingResult;
import com.cafe.entity.BillingRule;
import com.cafe.entity.Seat;
import com.cafe.service.BillingRuleService;
import com.cafe.service.BillingService;
import com.cafe.service.MemberService;
import com.cafe.service.ProductService;
import com.cafe.service.SeatService;
import com.cafe.service.SessionService;
import com.cafe.service.StatsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员端控制器（一）：经营概览、营收统计报表、机位管理、计费规则配置。
 *
 * 报表页的图表数据统一在控制器里序列化成 JSON 字符串，页面通过 data 属性读取。
 * 不使用 Thymeleaf 的 JS 内联：内联会把 JSON 字符串再包一层引号变成 JS 字符串，
 * 反而还要额外解析一次。
 */
@Slf4j
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    /** 计费试算展示的常用上机时长（分钟） */
    private static final int[] PREVIEW_MINUTES = {60, 120, 180, 300, 420};

    private final SeatService seatService;
    private final SessionService sessionService;
    private final MemberService memberService;
    private final ProductService productService;
    private final BillingRuleService billingRuleService;
    private final BillingService billingService;
    private final StatsService statsService;
    private final ObjectMapper objectMapper;

    /** 当前登录管理员ID */
    private Long me(HttpSession session) {
        LoginUser user = (LoginUser) session.getAttribute(Constants.SESSION_LOGIN_USER);
        if (user == null) {
            throw new BizException("登录状态已失效，请重新登录");
        }
        return user.getId();
    }

    /* ==================== 1. 经营概览 ==================== */

    @GetMapping
    public String index(Model model) {
        model.addAttribute("today", statsService.todaySummary());
        model.addAttribute("statusCount", seatService.countByStatus());
        model.addAttribute("seats", seatService.listAll());
        model.addAttribute("memberCount", memberService.page(1, 1, null, null).getTotal());
        model.addAttribute("productCount", productService.listAll().size());
        model.addAttribute("areaStats", seatService.countByArea());

        // 使用中的记录附上已上机时长，页面直接展示，不在模板里做时间运算
        List<com.cafe.entity.SeatSession> usingList = sessionService.listUsing();
        Map<Long, String> elapsed = new LinkedHashMap<>();
        for (com.cafe.entity.SeatSession s : usingList) {
            elapsed.put(s.getId(), com.cafe.common.TimeUtil.elapsedFrom(s.getStartTime()));
        }
        model.addAttribute("usingList", usingList);
        model.addAttribute("elapsed", elapsed);

        model.addAttribute("trendJson", toJson(statsService.trend(7)));
        model.addAttribute("areaJson", toJson(
                statsService.areaRevenue(LocalDate.now().minusDays(6), LocalDate.now())));
        return "admin/index";
    }

    /* ==================== 2. 营收统计报表 ==================== */

    @GetMapping("/stats")
    public String stats(Model model,
                        @RequestParam(required = false) String startDate,
                        @RequestParam(required = false) String endDate,
                        @RequestParam(defaultValue = "7") int days) {
        LocalDate end = (endDate == null || endDate.isBlank())
                ? LocalDate.now() : LocalDate.parse(endDate);
        LocalDate start = (startDate == null || startDate.isBlank())
                ? end.minusDays(Math.max(1, Math.min(days, 90)) - 1L) : LocalDate.parse(startDate);
        // 用户可能把起止填反，交换后继续，不报错打断
        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }
        int safeDays = Math.max(1, Math.min(days, 90));

        model.addAttribute("summary", statsService.summary(start, end));
        model.addAttribute("startDate", start.toString());
        model.addAttribute("endDate", end.toString());
        model.addAttribute("days", safeDays);
        // 可选天数由控制器提供，不在模板里写 SpEL 列表字面量，避免符号歧义
        model.addAttribute("dayOptions", List.of(7, 14, 30, 60));

        model.addAttribute("trendJson", toJson(statsService.trend(start, end)));
        model.addAttribute("areaJson", toJson(statsService.areaRevenue(start, end)));
        model.addAttribute("productJson", toJson(statsService.topProducts(6, start, end)));
        return "admin/stats";
    }

    /* ==================== 3. 机位管理 ==================== */

    @GetMapping("/seat")
    public String seatPage(Model model) {
        model.addAttribute("seats", seatService.listAll());
        model.addAttribute("statusCount", seatService.countByStatus());
        // 座位图用派生展示态（含「预约」），与收银台看板同一套口径
        model.addAttribute("board", seatService.buildBoard());
        model.addAttribute("boardCount", seatService.countByDisplayStatus());
        model.addAttribute("areaStats", seatService.countByArea());
        model.addAttribute("usingList", sessionService.listUsing());
        return "admin/seat";
    }

    @PostMapping("/seat/save")
    public String saveSeat(@RequestParam(required = false) Long id,
                           @RequestParam String seatNo,
                           @RequestParam String area,
                           @RequestParam(required = false) String config,
                           RedirectAttributes ra) {
        try {
            Seat seat = new Seat();
            seat.setId(id);
            seat.setSeatNo(seatNo == null ? null : seatNo.trim().toUpperCase());
            seat.setArea(area == null ? null : area.trim());
            seat.setConfig(config);
            if (id == null) {
                seatService.create(seat);
                ra.addFlashAttribute("msg", "机位 " + seat.getSeatNo() + " 已新增");
            } else {
                seatService.update(seat);
                ra.addFlashAttribute("msg", "机位 " + seat.getSeatNo() + " 已更新");
            }
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/seat";
    }

    /**
     * 变更机位状态。统一走 SeatService 的状态机，
     * 非法流转（例如「维护中」直接切到「使用中」）会被拦下并给出原因。
     */
    @PostMapping("/seat/status")
    public String changeSeatStatus(HttpSession session,
                                   @RequestParam Long id,
                                   @RequestParam String status,
                                   RedirectAttributes ra) {
        try {
            seatService.transit(id, status, "管理员手工调整，操作人ID=" + me(session));
            ra.addFlashAttribute("msg", "机位状态已变更为「" + seatService.name(status) + "」");
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/seat";
    }

    /* ==================== 4. 计费规则配置 ==================== */

    @GetMapping("/billing")
    public String billingPage(Model model) {
        model.addAttribute("previewMinutes", PREVIEW_MINUTES);
        model.addAttribute("preview", buildPreview(billingRuleService.listAll()));
        return "admin/billing";
    }

    /** 新增或修改计费规则：调价只改数据，不需要改代码重新部署 */
    @PostMapping("/billing/save")
    public String saveBilling(@RequestParam(required = false) Long id,
                              @RequestParam String area,
                              @RequestParam BigDecimal hourPrice,
                              @RequestParam(required = false) Integer packageHours,
                              @RequestParam(required = false) BigDecimal packagePrice,
                              @RequestParam(required = false) BigDecimal memberDiscount,
                              RedirectAttributes ra) {
        try {
            if (id == null) {
                billingRuleService.create(area, hourPrice, packageHours, packagePrice, memberDiscount);
                ra.addFlashAttribute("msg", "区域「" + area + "」的计费规则已新增");
            } else {
                billingRuleService.update(id, hourPrice, packageHours, packagePrice,
                        memberDiscount, null);
                ra.addFlashAttribute("msg",
                        "区域「" + area + "」的计费规则已更新，后续结算立即生效");
            }
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/billing";
    }

    @PostMapping("/billing/status")
    public String changeBillingStatus(@RequestParam Long id,
                                      @RequestParam Integer status,
                                      RedirectAttributes ra) {
        try {
            billingRuleService.changeStatus(id, status);
            ra.addFlashAttribute("msg", "计费规则状态已更新");
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/billing";
    }

    /**
     * 计费试算：按停用状态分别列出各区域在常用时长下的应收金额。
     * 管理员调价后可以立刻确认「按小时」与「包时套餐」哪个更优惠、结果是否符合预期，
     * 不必等到真实结算才发现价格配错。
     */
    private List<Map<String, Object>> buildPreview(List<BillingRule> rules) {
        List<Map<String, Object>> preview = new ArrayList<>();
        for (BillingRule rule : rules) {
            Map<String, Object> row = new LinkedHashMap<>();
            // 规则本身的信息一并放进同一行，页面只遍历这一个列表即可。
            // 若在模板里靠下标回查原始列表，一旦两个列表顺序不一致就会张冠李戴。
            row.put("id", rule.getId());
            row.put("area", rule.getArea());
            row.put("hourPrice", rule.getHourPrice());
            row.put("packageHours", rule.getPackageHours());
            row.put("packagePrice", rule.getPackagePrice());
            row.put("memberDiscount", rule.getMemberDiscount());
            row.put("status", rule.getStatus());
            row.put("enabled", rule.getStatus() != null && rule.getStatus() == Constants.ENABLED);
            List<Map<String, Object>> cells = new ArrayList<>();
            for (int minutes : PREVIEW_MINUTES) {
                Map<String, Object> cell = new LinkedHashMap<>();
                cell.put("minutes", minutes);
                try {
                    // 按不打折试算，便于管理员看清基准价；实际结算还会叠加会员折扣
                    BillingResult r = billingService.calculate(minutes, rule, BigDecimal.ONE);
                    cell.put("fee", r.getFinalFee());
                    cell.put("hitPackage", r.isHitPackage());
                } catch (Exception e) {
                    cell.put("fee", null);
                    cell.put("hitPackage", false);
                }
                cells.add(cell);
            }
            row.put("cells", cells);
            preview.add(row);
        }
        return preview;
    }

    /* ==================== 工具 ==================== */

    /** 序列化为 JSON；失败时返回空数组，保证页面不会因图表数据问题整体打不开 */
    private String toJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.error("图表数据序列化失败", e);
            return "[]";
        }
    }
}
