package com.cafe.common;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;

/**
 * 向所有页面注入公共模型数据。
 * 页面头部（右上角用户名、菜单高亮）直接读取 loginUser，无需每个 Controller 重复塞。
 */
@ControllerAdvice
public class GlobalModelAdvice {

    @ModelAttribute
    public void injectLoginUser(HttpSession session, Model model) {
        Object user = session.getAttribute(Constants.SESSION_LOGIN_USER);
        if (user != null) {
            model.addAttribute("loginUser", user);
        }
    }
}
