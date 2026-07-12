package com.zhiven;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QATest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void qaPageContainsHtmx() throws Exception {
        // 验证未登录时重定向
        mockMvc.perform(get("/qa"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void qaPageLoadsAfterLogin() throws Exception {
        // 注册
        mockMvc.perform(post("/register")
                        .param("username", "testqa")
                        .param("password", "test123")
                        .param("email", "test@test.com")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        // 登录并访问 qa 页面
        var result = mockMvc.perform(formLogin("/login")
                        .user("username", "testqa")
                        .password("test123"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession();

        mockMvc.perform(get("/qa").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("htmx.min.js")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("hx-post=\"/qa/ask\"")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("qa-result")));
    }

    @Test
    void qaAskEndpointWorks() throws Exception {
        // 注册
        mockMvc.perform(post("/register")
                        .param("username", "qa_ask")
                        .param("password", "test123")
                        .param("email", "ask@test.com")
                        .with(csrf()));

        var result = mockMvc.perform(formLogin("/login")
                        .user("username", "qa_ask")
                        .password("test123"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession();

        // POST /qa/ask 应该返回 200
        mockMvc.perform(post("/qa/ask")
                        .param("question", "测试问题")
                        .session(session))
                .andExpect(status().isOk());
    }
}
