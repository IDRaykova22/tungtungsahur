package org.codingburgas.demetra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithAnonymousUser
    void registerLoginAndReachEvents() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("name", "Ivan Ivanov")
                        .param("email", "ivan.student@example.com")
                        .param("password", "Passw0rd")
                        .param("role", "STUDENT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "ivan.student@example.com")
                        .param("password", "Passw0rd"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/events"));
    }

    @Test
    @WithAnonymousUser
    void rejectsWeakPassword() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("name", "Weak Password")
                        .param("email", "weak@example.com")
                        .param("password", "short")
                        .param("role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Password must be between 8 and 128 characters.")));
    }

    @Test
    void anonymousCanBrowseEvents() throws Exception {
        mockMvc.perform(get("/events"))
                .andExpect(status().isOk());
    }
}
