package com.cafe.controller;

import com.cafe.common.BizException;
import com.cafe.common.Constants;
import com.cafe.common.LoginUser;
import com.cafe.entity.Equipment;
import com.cafe.entity.Member;
import com.cafe.entity.Notice;
import com.cafe.entity.Product;
import com.cafe.entity.SysUser;
import com.cafe.service.BalanceService;
import com.cafe.service.EquipmentService;
import com.cafe.service.MemberService;
import com.cafe.service.NoticeService;
import com.cafe.service.ProductService;
import com.cafe.service.ReservationService;
import com.cafe.service.SessionService;
import com.cafe.service.SysUserService;
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

/**
 * 管理员端控制器（二）：会员管理、商品管理、设备管理、预约管理、用户与权限管理、公告管理。
 */
@Slf4j
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminManageController {

    private final MemberService memberService;
    private final ProductService productService;
    private final EquipmentService equipmentService;
    private final ReservationService reservationService;
    private final SysUserService sysUserService;
    private final NoticeService noticeService;
    private final BalanceService balanceService;
    private final SessionService sessionService;

    private Long me(HttpSession session) {
        LoginUser user = (LoginUser) session.getAttribute(Constants.SESSION_LOGIN_USER);
        if (user == null) {
            throw new BizException("登录状态已失效，请重新登录");
        }
        return user.getId();
    }

    /* ==================== 5. 会员管理 ==================== */

    @GetMapping("/member")
    public String memberPage(Model model,
                             @RequestParam(defaultValue = "1") long page,
                             @RequestParam(required = false) String keyword,
                             @RequestParam(required = false) Integer status) {
        model.addAttribute("page", memberService.page(page, 12, keyword, status));
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        model.addAttribute("levels", memberService.listLevels());
        return "admin/member";
    }

    /**
     * 会员详情：资料 + 账户流水 + 账目一致性校验。
     * 与流水核对差额是最直接的账务稽查手段。
     */
    @GetMapping("/member/detail")
    public String memberDetail(Model model,
                               @RequestParam Long id,
                               @RequestParam(defaultValue = "1") long page) {
        Member member = memberService.getById(id);
        model.addAttribute("member", member);
        model.addAttribute("page", balanceService.pageRecords(page, 15, id, null));
        // 余额与流水累加的差额，非 0 说明账务异常
        model.addAttribute("diff", balanceService.checkConsistency(id));
        model.addAttribute("sessions", sessionService.page(1, 10, id, null).getRecords());
        return "admin/member-detail";
    }

    @PostMapping("/member/update")
    public String updateMember(@RequestParam Long id,
                               @RequestParam(required = false) String name,
                               @RequestParam(required = false) String phone,
                               @RequestParam(required = false) Integer level,
                               RedirectAttributes ra) {
        try {
            memberService.updateProfile(id, name, phone, level);
            ra.addFlashAttribute("msg", "会员资料已更新");
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/member/detail?id=" + id;
    }

    @PostMapping("/member/status")
    public String changeMemberStatus(@RequestParam Long id,
                                     @RequestParam Integer status,
                                     RedirectAttributes ra) {
        try {
            memberService.changeStatus(id, status);
            ra.addFlashAttribute("msg", status == Constants.ENABLED
                    ? "会员账户已解冻" : "会员账户已冻结，冻结期间无法充值、消费与开台");
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/member/detail?id=" + id;
    }

    @PostMapping("/member/reset-password")
    public String resetMemberPassword(@RequestParam Long id,
                                      @RequestParam String newPassword,
                                      RedirectAttributes ra) {
        try {
            memberService.resetPassword(id, newPassword);
            ra.addFlashAttribute("msg", "会员密码已重置，请提醒本人尽快修改");
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/member/detail?id=" + id;
    }

    /* ==================== 6. 商品管理 ==================== */

    @GetMapping("/product")
    public String productPage(Model model,
                              @RequestParam(defaultValue = "1") long page,
                              @RequestParam(required = false) String keyword,
                              @RequestParam(required = false) Integer status) {
        model.addAttribute("page", productService.page(page, 12, keyword, status));
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        return "admin/product";
    }

    @PostMapping("/product/save")
    public String saveProduct(@RequestParam(required = false) Long id,
                              @RequestParam String name,
                              @RequestParam BigDecimal price,
                              @RequestParam(required = false) Integer stock,
                              RedirectAttributes ra) {
        try {
            if (id == null) {
                productService.create(name, price, stock);
                ra.addFlashAttribute("msg", "商品「" + name + "」已新增");
            } else {
                productService.update(id, name, price, stock, null);
                ra.addFlashAttribute("msg", "商品「" + name + "」已更新");
            }
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/product";
    }

    @PostMapping("/product/status")
    public String changeProductStatus(@RequestParam Long id,
                                      @RequestParam Integer status,
                                      RedirectAttributes ra) {
        try {
            productService.changeStatus(id, status);
            ra.addFlashAttribute("msg", status == Constants.ENABLED ? "商品已上架" : "商品已下架");
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/product";
    }

    /* ==================== 7. 设备管理 ==================== */

    @GetMapping("/equipment")
    public String equipmentPage(Model model,
                                @RequestParam(defaultValue = "1") long page,
                                @RequestParam(required = false) String keyword,
                                @RequestParam(required = false) String status) {
        model.addAttribute("page", equipmentService.page(page, 12, keyword, status));
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        model.addAttribute("renting", equipmentService.pageRentals(1, 20, null,
                Constants.RENTAL_RENTING).getRecords());
        return "admin/equipment";
    }

    @PostMapping("/equipment/save")
    public String saveEquipment(@RequestParam(required = false) Long id,
                                @RequestParam String code,
                                @RequestParam String category,
                                @RequestParam(required = false) BigDecimal rentPrice,
                                @RequestParam(required = false) BigDecimal deposit,
                                RedirectAttributes ra) {
        try {
            if (id == null) {
                equipmentService.create(code, category, rentPrice, deposit);
                ra.addFlashAttribute("msg", "设备 " + code + " 已新增");
            } else {
                equipmentService.update(id, code, category, rentPrice, deposit);
                ra.addFlashAttribute("msg", "设备 " + code + " 已更新");
            }
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/equipment";
    }

    @PostMapping("/equipment/status")
    public String changeEquipmentStatus(@RequestParam Long id,
                                        @RequestParam String status,
                                        RedirectAttributes ra) {
        try {
            equipmentService.changeStatus(id, status);
            ra.addFlashAttribute("msg", "设备状态已更新");
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/equipment";
    }

    /* ==================== 8. 预约管理 ==================== */

    @GetMapping("/reservation")
    public String reservationPage(Model model,
                                  @RequestParam(defaultValue = "1") long page,
                                  @RequestParam(required = false) String status) {
        model.addAttribute("page", reservationService.page(page, 15, null, status));
        model.addAttribute("status", status);
        return "admin/reservation";
    }

    /* ==================== 9. 用户与权限管理 ==================== */

    @GetMapping("/user")
    public String userPage(Model model) {
        model.addAttribute("cashiers", sysUserService.listAll(Constants.ROLE_CASHIER));
        model.addAttribute("admins", sysUserService.listAll(Constants.ROLE_ADMIN));
        return "admin/user";
    }

    @PostMapping("/user/save")
    public String saveUser(@RequestParam(required = false) Long id,
                           @RequestParam(required = false) String username,
                           @RequestParam(required = false) String password,
                           @RequestParam String realName,
                           @RequestParam String role,
                           RedirectAttributes ra) {
        try {
            if (id == null) {
                SysUser user = sysUserService.create(username, password, realName, role);
                ra.addFlashAttribute("msg", "账号「" + user.getUsername() + "」已创建");
            } else {
                sysUserService.updateProfile(id, realName, role);
                ra.addFlashAttribute("msg", "账号资料已更新");
            }
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/user";
    }

    /**
     * 停用或启用员工账号。
     * 账号一律停用而非删除：删除会丢失其历史业务记录的经办人关联，
     * 导致充值流水、上机记录无法追溯责任人。
     */
    @PostMapping("/user/status")
    public String changeUserStatus(HttpSession session,
                                   @RequestParam Long id,
                                   @RequestParam Integer status,
                                   RedirectAttributes ra) {
        try {
            if (id.equals(me(session))) {
                throw new BizException("不能停用当前登录的账号");
            }
            sysUserService.changeStatus(id, status);
            ra.addFlashAttribute("msg", status == Constants.ENABLED ? "账号已启用" : "账号已停用");
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/user";
    }

    @PostMapping("/user/reset-password")
    public String resetStaffPassword(@RequestParam Long id,
                                     @RequestParam String newPassword,
                                     RedirectAttributes ra) {
        try {
            sysUserService.resetPassword(id, newPassword);
            ra.addFlashAttribute("msg", "员工密码已重置");
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/user";
    }

    /* ==================== 10. 公告管理 ==================== */

    @GetMapping("/notice")
    public String noticePage(Model model,
                             @RequestParam(defaultValue = "1") long page,
                             @RequestParam(required = false) String keyword,
                             @RequestParam(required = false) Long editId) {
        model.addAttribute("page", noticeService.page(page, 10, keyword));
        model.addAttribute("keyword", keyword);
        if (editId != null) {
            model.addAttribute("editing", noticeService.getById(editId));
        }
        return "admin/notice";
    }

    @PostMapping("/notice/save")
    public String saveNotice(HttpSession session,
                             @RequestParam(required = false) Long id,
                             @RequestParam String title,
                             @RequestParam String content,
                             RedirectAttributes ra) {
        try {
            if (id == null) {
                noticeService.publish(title, content, me(session));
                ra.addFlashAttribute("msg", "公告已发布，会员端与收银员端同步可见");
            } else {
                noticeService.update(id, title, content);
                ra.addFlashAttribute("msg", "公告已更新");
            }
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/notice";
    }

    @PostMapping("/notice/delete")
    public String deleteNotice(HttpSession session, @RequestParam Long id, RedirectAttributes ra) {
        try { noticeService.delete(id, me(session)); ra.addFlashAttribute("msg", "公告已删除"); }
        catch (BizException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/notice";
    }

    /** 发布 / 撤下公告。撤下而非删除：历史公告可能是计费调整的依据，需可追溯 */
    @PostMapping("/notice/status")
    public String changeNoticeStatus(@RequestParam Long id,
                                     @RequestParam Integer status,
                                     RedirectAttributes ra) {
        try {
            noticeService.changeStatus(id, status);
            ra.addFlashAttribute("msg", status == Constants.ENABLED ? "公告已发布" : "公告已撤下");
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/notice";
    }
}
