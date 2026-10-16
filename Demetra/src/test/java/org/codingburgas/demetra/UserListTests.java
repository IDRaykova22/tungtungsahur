package org.codingburgas.demetra;

import org.codingburgas.demetra.models.Role;
import org.codingburgas.demetra.models.User;
import org.codingburgas.demetra.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:user-list;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserListTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    @WithMockUser
    void defaultsToAlphabeticalOrderIgnoringCaseAndThenRegistrationDate() throws Exception {
        User zara = saveUser("Zara", "zara@example.com", 1);
        User laterAnna = saveUser("anna", "anna2@example.com", 3);
        User earlierAnna = saveUser("Anna", "anna1@example.com", 2);

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(view().name("users/list"))
                .andExpect(model().attribute("users", contains(earlierAnna, laterAnna, zara)))
                .andExpect(content().string(containsString("02 Jan 2026, 10:00")))
                .andExpect(content().string(not(containsString("private-password-hash"))));
    }

    @Test
    @WithMockUser
    void sortsByRegistrationDateAndThenName() throws Exception {
        User laterAnna = saveUser("Anna", "anna@example.com", 3);
        User earlierZara = saveUser("Zara", "zara@example.com", 1);
        User earlierBoris = saveUser("boris", "boris@example.com", 1);

        mockMvc.perform(get("/users").param("sort", "date"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("users", contains(earlierBoris, earlierZara, laterAnna)))
                .andExpect(content().string(containsString("value=\"date\" selected=\"selected\"")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "unknown"})
    @WithMockUser
    void usesAlphabeticalOrderForNameOrUnknownSort(String sort) throws Exception {
        User zara = saveUser("Zara", "zara@example.com", 1);
        User anna = saveUser("Anna", "anna@example.com", 2);

        mockMvc.perform(get("/users").param("sort", sort))
                .andExpect(status().isOk())
                .andExpect(model().attribute("users", contains(anna, zara)));
    }

    @Test
    @WithMockUser
    void displaysEmptyListMessage() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No users have registered yet.")));
    }

    @Test
    void anonymousCanBrowseUsers() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(view().name("users/list"))
                .andExpect(content().string(containsString("href=\"/users\">Users</a>")));
    }

    private User saveUser(String name, String email, int day) {
        User user = new User(email, "private-password-hash", name, Role.STUDENT);
        user.setCreatedAt(LocalDateTime.of(2026, 1, day, 10, 0));
        return userRepository.save(user);
    }
}
