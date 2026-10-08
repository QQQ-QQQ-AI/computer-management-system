package com.cafe.controller;

import com.cafe.common.BizException;
import com.cafe.common.Constants;
import com.cafe.common.LoginUser;
import com.cafe.entity.Member;
import com.cafe.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 认证控制器：登录、注册、退出。
 *
 * 本控制器路径不在拦截器保护范围内（否则会形成「要登录先登录」的死循环）。
 */
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 登录页；已登录则直接进入对应角色首页 */
    @GetMapping({"/", "/login"})
    public String loginPage(HttpSession session) {
        LoginUser user = (LoginUser) session.getAttribute(Constants.SESSION_LOGIN_USER);
        if (user != null) {
            return "redirect:" + homeOf(user.getRole());
        }
        return "login";
    }

    /**
     * 登录提交。
     *
     * 各参数均设为非必填并在方法内自行校验。原因：若用 @RequestParam 默认的
     * required=true，任何一次缺参提交（浏览器自动填充异常、脚本直接调用、
     * 表单被篡改）都会抛 MissingServletRequestParameterException，
     * 用户看到的是 500 错误页而不是「请填写账号密码」的友好提示。
     *
     * @param type 登录身份：MEMBER 会员 / STAFF 员工
     */
    @PostMapping("/login")
    public String doLogin(@RequestParam(required = false, defaultValue = "") String type,
                          @RequestParam(required = false, defaultValue = "") String account,
                          @RequestParam(required = false, defaultValue = "") String password,
                          HttpServletRequest request,
                          Model model) {

        String t = type.isEmpty() ? "MEMBER" : type;
        model.addAttribute("type", t);
        model.addAttribute("account", account);

        if (account.isBlank() || password.isBlank()) {
            model.addAttribute("error", "请输入账号和密码");
            return "login";
        }

        try {
            LoginUser loginUser = "MEMBER".equalsIgnoreCase(t)
                    ? authService.loginMember(account, password)
                    : authService.loginStaff(account, password);

            /*
             * 防御会话固定攻击：登录成功先作废旧 Session 再创建新的。
             * 若沿用登录前的 Session，攻击者可预先诱导受害者使用一个自己已知的
             * SessionID，受害者登录后该 ID 即成为已认证会话，攻击者可直接冒用。
             */
            HttpSession old = request.getSession(false);
            if (old != null) {
                old.invalidate();
            }
            request.getSession(true).setAttribute(Constants.SESSION_LOGIN_USER, loginUser);

            return "redirect:" + homeOf(loginUser.getRole());

        } catch (BizException e) {
            model.addAttribute("error", e.getMessage());
            return "login";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes ra) {
        session.invalidate();
        ra.addFlashAttribute("msg", "已安全退出");
        return "redirect:/login";
    }

    /* ==================== 会员自助注册 ==================== */

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String doRegister(@RequestParam(required = false, defaultValue = "") String phone,
                             @RequestParam(required = false, defaultValue = "") String password,
                             @RequestParam(required = false, defaultValue = "") String confirmPassword,
                             @RequestParam(required = false) String name,
                             Model model,
                             RedirectAttributes ra) {
        // 回填已填内容，避免校验失败后用户要重新输入全部字段
        model.addAttribute("phone", phone);
        model.addAttribute("name", name);

        if (phone.isBlank() || password.isBlank()) {
            model.addAttribute("error", "请填写手机号与密码");
            return "register";
        }
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "两次输入的密码不一致");
            return "register";
        }
        try {
            Member member = authService.register(phone, password, name);
            ra.addFlashAttribute("msg", "注册成功，您的会员卡号为 " + member.getCardNo() + "，请登录");
            return "redirect:/login";
        } catch (BizException e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    /** 各角色登录后的默认首页 */
    private String homeOf(String role) {
        return switch (role) {
            case Constants.ROLE_ADMIN -> "/admin";
            case Constants.ROLE_CASHIER -> "/cashier";
            default -> "/member";
        };
    }
}
