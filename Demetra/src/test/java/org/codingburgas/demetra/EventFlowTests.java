package org.codingburgas.demetra;

import jakarta.servlet.http.Cookie;
import org.codingburgas.demetra.models.Role;
import org.codingburgas.demetra.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @BeforeEach
    void setUp() {
        registerIfAbsent("Prof. Antonov", "organizer@example.com", Role.ORGANIZER);
        registerIfAbsent("Student One", "student@example.com", Role.STUDENT);
        registerIfAbsent("Student Two", "student2@example.com", Role.STUDENT);
    }

    private void registerIfAbsent(String name, String email, Role role) {
        try {
            userService.register(name, email, "Passw0rd", role);
        } catch (IllegalArgumentException ignored) {
            // already registered by a previous test
        }
    }

    /** Spring Session stores the session server-side; the client only holds the SESSION cookie. */
    private Cookie loginAs(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", email)
                        .param("password", "Passw0rd"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Cookie sessionCookie = result.getResponse().getCookie("SESSION");
        assertTrue(sessionCookie != null, "Expected a SESSION cookie after login");
        return sessionCookie;
    }

    @Test
    void organizerCanCreateEventAndStudentCanRegister() throws Exception {
        Cookie organizerCookie = loginAs("organizer@example.com");

        MvcResult createResult = mockMvc.perform(post("/events/new")
                        .cookie(organizerCookie)
                        .with(csrf())
                        .param("title", "Spring Recital")
                        .param("description", "An evening of piano pieces.")
                        .param("category", "Recital")
                        .param("location", "Concert Hall A")
                        .param("capacity", "1")
                        .param("price", "0"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String redirectUrl = createResult.getResponse().getRedirectedUrl();
        assertTrue(redirectUrl.matches("/events/\\d+"));
        String eventId = redirectUrl.substring("/events/".length());

        mockMvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Spring Recital")));

        Cookie studentCookie = loginAs("student@example.com");
        mockMvc.perform(post("/events/" + eventId + "/register")
                        .cookie(studentCookie)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/events/" + eventId));

        mockMvc.perform(get("/events/" + eventId).cookie(studentCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("You&#39;re registered")));

        // Capacity is 1, so the second student is waitlisted instead of confirmed.
        Cookie student2Cookie = loginAs("student2@example.com");
        mockMvc.perform(post("/events/" + eventId + "/register")
                        .cookie(student2Cookie)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/events/" + eventId).cookie(student2Cookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("waitlist")));
    }

    @Test
    void studentCannotCreateEvent() throws Exception {
        Cookie studentCookie = loginAs("student@example.com");

        mockMvc.perform(post("/events/new")
                        .cookie(studentCookie)
                        .with(csrf())
                        .param("title", "Unauthorized Event")
                        .param("capacity", "10")
                        .param("price", "0"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsRedirectedToLoginWhenCreatingEvent() throws Exception {
        mockMvc.perform(get("/events/new"))
                .andExpect(status().is3xxRedirection());
    }
}
