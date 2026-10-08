package com.cafe.service;
import com.cafe.common.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="cafe.scheduling.enabled=false")
@AutoConfigureMockMvc
@Transactional
class WebSecurityTest extends TestFixtures {
    @Autowired MockMvc mvc;
    @BeforeEach void setup(){createFixtures();}
    MockHttpSession login(String role){
        MockHttpSession s=new MockHttpSession();
        var staff=users.create("U"+tag+role,"test123","test",role);
        s.setAttribute(Constants.SESSION_LOGIN_USER,auth.loginStaff(staff.getUsername(),"test123"));return s;
    }
    MockHttpSession memberLogin(){var s=new MockHttpSession();s.setAttribute(Constants.SESSION_LOGIN_USER,auth.loginMember(first.getPhone(),"test123"));return s;}
    @Test void formsHaveCsrfAndUnsafeRequestWithoutTokenIsDenied() throws Exception {
        var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        assertTrue(page.getResponse().getContentAsString().contains("name=\"_csrf\""));
        mvc.perform(post("/login").param("account","test").param("password","test")).andExpect(status().isForbidden());
    }
    @Test void realLoginWithRenderedTokenWorks() throws Exception {
        var page=mvc.perform(get("/login")).andReturn();var s=(MockHttpSession)page.getRequest().getSession();
        var match=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());
        assertTrue(match.find());
        mvc.perform(post("/login").session(s).param("_csrf",match.group(1)).param("type","MEMBER")
                .param("account",first.getPhone()).param("password","test123")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/member"));
    }
    @Test void adminPagesRenderWithUpdatedForms() throws Exception {
        var s=login("ADMIN");
        for(String p:new String[]{"/admin","/admin/stats","/admin/seat","/admin/billing","/admin/member","/admin/product","/admin/equipment","/admin/reservation","/admin/user","/admin/notice"})
            mvc.perform(get(p).session(s)).andExpect(status().isOk());
        mvc.perform(get("/admin/member/detail").param("id",first.getId().toString()).session(s)).andExpect(status().isOk());
    }
    @Test void cashierAndMemberPagesRender() throws Exception {
        var c=login("CASHIER");
        for(String p:new String[]{"/cashier","/cashier/open-card","/cashier/open-seat","/cashier/checkout","/cashier/product","/cashier/recharge","/cashier/rental","/cashier/reservation","/cashier/notices"})mvc.perform(get(p).session(c)).andExpect(status().isOk());
        var m=memberLogin();
        for(String p:new String[]{"/member","/member/balance","/member/recharge","/member/sessions","/member/fees","/member/rentals","/member/reservation","/member/notices","/member/profile","/member/orders"})mvc.perform(get(p).session(m)).andExpect(status().isOk());
    }
    @Test void disabledStaffSessionIsRevoked() throws Exception {
        var s=login("CASHIER");var u=(LoginUser)s.getAttribute(Constants.SESSION_LOGIN_USER);users.changeStatus(u.getId(),0);
        mvc.perform(get("/cashier").session(s)).andExpect(redirectedUrl("/login"));
    }
    @Test void demotedAdminSessionIsRevoked() throws Exception {
        var s=login("ADMIN");var u=(LoginUser)s.getAttribute(Constants.SESSION_LOGIN_USER);users.updateProfile(u.getId(),null,"CASHIER");
        mvc.perform(get("/admin").session(s)).andExpect(redirectedUrl("/login"));
    }
    @Test void passwordResetRevokesSession() throws Exception {
        var s=memberLogin();members.resetPassword(first.getId(),"changed123");mvc.perform(get("/member").session(s)).andExpect(redirectedUrl("/login"));
    }
    @Test void memberCannotAccessAdmin() throws Exception {mvc.perform(get("/admin").session(memberLogin())).andExpect(redirectedUrl("/member"));}
    @Test void encodedAdminPathStillRequiresAdmin() throws Exception {
        mvc.perform(get(java.net.URI.create("/%61dmin")).session(memberLogin())).andExpect(redirectedUrl("/member"));
    }
}
