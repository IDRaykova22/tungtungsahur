package org.codingburgas.demetra;

import jakarta.servlet.http.Cookie;
import org.codingburgas.demetra.models.Role;
import org.codingburgas.demetra.models.User;
import org.codingburgas.demetra.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:session-compat;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionCompatibilityTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    /** A session written by an older build (incompatible classes) must sign the visitor out, not fail with 500. */
    @Test
    void sessionStoredByAnOlderVersionSignsTheVisitorOutInsteadOfFailing() throws Exception {
        userRepository.save(new User("old-session@example.com", passwordEncoder.encode("Passw0rdTest"), "Old", Role.STUDENT));
        Cookie session = mockMvc.perform(post("/login").with(csrf())
                        .param("username", "old-session@example.com").param("password", "Passw0rdTest"))
                .andExpect(redirectedUrl("/events")).andReturn().getResponse().getCookie("SESSION");
        assertNotNull(session);
        mockMvc.perform(get("/profile").cookie(session)).andExpect(status().isOk());

        // Java serialization header followed by bytes no current class can read.
        byte[] unreadable = {(byte) 0xAC, (byte) 0xED, 0x00, 0x05, 0x73, 0x72, 0x00, 0x03, 'O', 'l', 'd'};
        int changed = jdbcTemplate.update("update SPRING_SESSION_ATTRIBUTES set ATTRIBUTE_BYTES = ?", (Object) unreadable);
        assertTrue(changed > 0);

        mockMvc.perform(get("/events").cookie(session)).andExpect(status().isOk());
        mockMvc.perform(get("/profile").cookie(session)).andExpect(redirectedUrl("/login"));
    }
}
