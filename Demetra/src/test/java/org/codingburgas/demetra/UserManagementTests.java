package org.codingburgas.demetra;

import jakarta.servlet.http.Cookie;
import org.codingburgas.demetra.models.Role;
import org.codingburgas.demetra.models.User;
import org.codingburgas.demetra.repositories.EventRepository;
import org.codingburgas.demetra.repositories.RegistrationRepository;
import org.codingburgas.demetra.repositories.UserRepository;
import org.codingburgas.demetra.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:user-management;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserManagementTests {
    private static final String PASSWORD = "Passw0rdTest";
    private static final String ADMIN_EMAIL = "admin@example.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private UserService userService;
    @Autowired private PasswordEncoder passwordEncoder;
    private User admin;

    @BeforeEach
    void setUp() {
        registrationRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();
        admin = saveUser("Admin", ADMIN_EMAIL, Role.ADMIN);
    }

    // --- Creating users ---

    @ParameterizedTest
    @EnumSource(Role.class)
    void adminCreatesUsersWithEveryRoleAndTheyCanSignIn(Role role) throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/users").with(csrf()).with(asAdmin())
                        .param("name", "  Мария Петрова ").param("email", " Maria@Example.com ")
                        .param("phone", "+359  88 123 4567").param("password", PASSWORD).param("role", role.name()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/users/*"))
                .andExpect(flash().attribute("message", containsString("Мария Петрова")))
                .andReturn();

        User created = userRepository.findByEmail("maria@example.com").orElseThrow();
        assertEquals("/admin/users/" + created.getId(), result.getResponse().getRedirectedUrl());
        assertEquals("Мария Петрова", created.getName());
        assertEquals("+359 88 123 4567", created.getPhone());
        assertEquals(role, created.getRole());
        assertTrue(created.isActive());
        assertNotNull(login("maria@example.com", PASSWORD));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Admin Copy | admin@example.com | 0888123456 | Passw0rdTest | STUDENT   | Вече има потребител с този имейл.",
            "Ivan       | ivan@example.com  | 12-34      | Passw0rdTest | STUDENT   | Въведете валиден телефон",
            "Ivan       | ivan@example.com  | 0888abc123 | Passw0rdTest | STUDENT   | Въведете валиден телефон",
            "Ivan       | ivan@example.com  | 0888123456 | short        | STUDENT   | Паролата трябва да е между 8 и 128 символа.",
            "Ivan       | ivan@example.com  | 0888123456 | alllowercase | STUDENT   | поне една малка и една главна буква",
            "Ivan       | ivan@example.com  | 0888123456 | Passw0rdTest | SUPERUSER | Изберете роля.",
            "I          | ivan@example.com  | 0888123456 | Passw0rdTest | STUDENT   | Името трябва да е между 2 и 80 символа.",
            "Ivan       | not-an-email      | 0888123456 | Passw0rdTest | STUDENT   | Въведете валиден имейл адрес."})
    void createRejectsInvalidInputAndKeepsTheForm(String name, String email, String phone, String password,
                                                 String role, String error) throws Exception {
        mockMvc.perform(post("/admin/users").with(csrf()).with(asAdmin())
                        .param("name", name).param("email", email).param("phone", phone)
                        .param("password", password).param("role", role))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users/new"))
                .andExpect(model().attribute("error", containsString(error)))
                .andExpect(content().string(containsString("value=\"" + email + "\"")));
        assertEquals(1, userRepository.count());
    }

    @Test
    void publicRegistrationCannotGrantAdministratorRights() throws Exception {
        mockMvc.perform(post("/register").with(csrf()).param("name", "Sneaky").param("email", "sneaky@example.com")
                        .param("password", PASSWORD).param("role", "ADMIN"))
                .andExpect(redirectedUrl("/login?registered"));
        assertEquals(Role.STUDENT, userRepository.findByEmail("sneaky@example.com").orElseThrow().getRole());
    }

    // --- Access control ---

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"STUDENT", "ORGANIZER"})
    void onlyAdministratorsCanManageUsers(Role role) throws Exception {
        User other = saveUser("Other", "other@example.com", Role.STUDENT);
        mockMvc.perform(get("/admin/users").with(user("x@example.com").roles(role.name()))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/users/new").with(user("x@example.com").roles(role.name()))).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/users").with(csrf()).with(user("x@example.com").roles(role.name()))
                .param("name", "X Y").param("email", "new@example.com").param("password", PASSWORD).param("role", "ADMIN"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/users/{id}/role", other.getId()).with(csrf()).with(user("x@example.com").roles(role.name()))
                .param("role", "ADMIN")).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/users/{id}/deactivate", other.getId()).with(csrf())
                .with(user("x@example.com").roles(role.name()))).andExpect(status().isForbidden());
        assertEquals(Role.STUDENT, userRepository.findById(other.getId()).orElseThrow().getRole());
        assertTrue(userRepository.findById(other.getId()).orElseThrow().isActive());
        assertFalse(userRepository.existsByEmail("new@example.com"));
    }

    @Test
    void anonymousIsSentToLogin() throws Exception {
        mockMvc.perform(get("/admin/users")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/profile")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void navigationShowsAdministrationOnlyToAdministrators() throws Exception {
        mockMvc.perform(get("/events").with(asAdmin())).andExpect(content().string(containsString("href=\"/admin/users\"")));
        mockMvc.perform(get("/events").with(user("o@example.com").roles("ORGANIZER")))
                .andExpect(content().string(not(containsString("href=\"/admin/users\""))))
                .andExpect(content().string(containsString("href=\"/profile\"")));
    }

    // --- Listing ---

    @Test
    void adminListFiltersByStatusAndRoleAndPublicListHidesDeactivated() throws Exception {
        saveUser("Active Student", "active@example.com", Role.STUDENT);
        User gone = saveUser("Gone Organizer", "gone@example.com", Role.ORGANIZER);
        userService.deactivate(gone.getId(), ADMIN_EMAIL);

        mockMvc.perform(get("/admin/users").with(asAdmin()))
                .andExpect(content().string(containsString("Active Student")))
                .andExpect(content().string(containsString("Gone Organizer")))
                .andExpect(content().string(containsString("Деактивиран")));
        mockMvc.perform(get("/admin/users").param("status", "inactive").with(asAdmin()))
                .andExpect(content().string(containsString("Gone Organizer")))
                .andExpect(content().string(not(containsString("Active Student"))));
        mockMvc.perform(get("/admin/users").param("status", "active").param("role", "STUDENT").with(asAdmin()))
                .andExpect(content().string(containsString("Active Student")))
                .andExpect(content().string(not(containsString("Gone Organizer"))))
                .andExpect(content().string(not(containsString(">Admin<"))));
        mockMvc.perform(get("/users"))
                .andExpect(content().string(containsString("Active Student")))
                .andExpect(content().string(not(containsString("Gone Organizer"))));
    }

    // --- Own profile ---

    @Test
    void userUpdatesOwnProfileAndSignsInWithTheNewEmail() throws Exception {
        saveUser("Old Name", "old@example.com", Role.STUDENT);
        Cookie session = login("old@example.com", PASSWORD);

        mockMvc.perform(post("/profile").with(csrf()).cookie(session)
                        .param("name", "New Name").param("email", "New@Example.com").param("phone", "(02) 987-65-43"))
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("message", "Профилът е обновен."));

        User updated = userRepository.findByEmail("new@example.com").orElseThrow();
        assertEquals("New Name", updated.getName());
        assertEquals("(02) 987-65-43", updated.getPhone());
        // The open session continues under the new email instead of being signed out.
        mockMvc.perform(get("/profile").cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("new@example.com")))
                .andExpect(content().string(containsString("value=\"New Name\"")));
        assertNull(login("old@example.com", PASSWORD));
        assertNotNull(login("new@example.com", PASSWORD));
    }

    @Test
    @WithMockUser(username = "self@example.com")
    void ownProfileRejectsTakenEmailAndClearsBlankPhone() throws Exception {
        User self = saveUser("Self", "self@example.com", Role.STUDENT);
        self.setPhone("0888123456");
        userRepository.save(self);

        mockMvc.perform(post("/profile").with(csrf()).param("name", "Self").param("email", ADMIN_EMAIL).param("phone", ""))
                .andExpect(status().isOk()).andExpect(view().name("users/profile"))
                .andExpect(model().attribute("error", "Вече има потребител с този имейл."));
        assertEquals("self@example.com", userRepository.findById(self.getId()).orElseThrow().getEmail());

        mockMvc.perform(post("/profile").with(csrf()).param("name", "Self").param("email", "self@example.com").param("phone", "  "))
                .andExpect(redirectedUrl("/profile"));
        assertNull(userRepository.findById(self.getId()).orElseThrow().getPhone());
    }

    // --- Administrator editing others ---

    @Test
    void adminEditsAnotherProfileAndAnEmailChangeEndsTheirSession() throws Exception {
        User student = saveUser("Student", "student@example.com", Role.STUDENT);
        Cookie session = login("student@example.com", PASSWORD);

        mockMvc.perform(post("/admin/users/{id}/profile", student.getId()).with(csrf()).with(asAdmin())
                        .param("name", "Renamed").param("email", "renamed@example.com").param("phone", "0888 123 456"))
                .andExpect(redirectedUrl("/admin/users/" + student.getId()));

        User updated = userRepository.findById(student.getId()).orElseThrow();
        assertEquals("Renamed", updated.getName());
        assertEquals("renamed@example.com", updated.getEmail());
        assertEquals("0888 123 456", updated.getPhone());
        mockMvc.perform(get("/profile").cookie(session)).andExpect(redirectedUrl("/login"));
    }

    @Test
    void adminProfileEditShowsValidationErrors() throws Exception {
        User student = saveUser("Student", "student@example.com", Role.STUDENT);
        mockMvc.perform(post("/admin/users/{id}/profile", student.getId()).with(csrf()).with(asAdmin())
                        .param("name", "Student").param("email", "student@example.com").param("phone", "12"))
                .andExpect(status().isOk()).andExpect(view().name("admin/users/edit"))
                .andExpect(model().attribute("error", containsString("Въведете валиден телефон")));
    }

    // --- Roles ---

    @Test
    void roleChangeEndsTheSessionAndTheNewRoleAppliesAtNextSignIn() throws Exception {
        User student = saveUser("Student", "student@example.com", Role.STUDENT);
        Cookie session = login("student@example.com", PASSWORD);
        mockMvc.perform(get("/reports").cookie(session)).andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/users/{id}/role", student.getId()).with(csrf()).with(asAdmin()).param("role", "ORGANIZER"))
                .andExpect(redirectedUrl("/admin/users/" + student.getId()))
                .andExpect(flash().attribute("message", "Ролята е променена."));

        assertEquals(Role.ORGANIZER, userRepository.findById(student.getId()).orElseThrow().getRole());
        mockMvc.perform(get("/reports").cookie(session)).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/reports").cookie(login("student@example.com", PASSWORD))).andExpect(status().isOk());
    }

    @Test
    void unknownRoleIsRejected() throws Exception {
        User student = saveUser("Student", "student@example.com", Role.STUDENT);
        mockMvc.perform(post("/admin/users/{id}/role", student.getId()).with(csrf()).with(asAdmin()).param("role", "ROOT"))
                .andExpect(flash().attribute("error", "Изберете роля."));
        assertEquals(Role.STUDENT, userRepository.findById(student.getId()).orElseThrow().getRole());
    }

    @Test
    void administratorCannotChangeOwnRoleOrDeactivateThemselves() throws Exception {
        mockMvc.perform(post("/admin/users/{id}/role", admin.getId()).with(csrf()).with(asAdmin()).param("role", "STUDENT"))
                .andExpect(flash().attribute("error", "Не можете да промените собствената си роля."));
        mockMvc.perform(post("/admin/users/{id}/deactivate", admin.getId()).with(csrf()).with(asAdmin()))
                .andExpect(flash().attribute("error", "Не можете да деактивирате собствения си профил."));
        User reloaded = userRepository.findById(admin.getId()).orElseThrow();
        assertEquals(Role.ADMIN, reloaded.getRole());
        assertTrue(reloaded.isActive());
        mockMvc.perform(get("/admin/users/{id}", admin.getId()).with(asAdmin())).andExpect(redirectedUrl("/profile"));
    }

    // --- Soft delete ---

    @Test
    void deactivationKeepsTheRowEndsTheSessionAndBlocksSignInUntilReactivated() throws Exception {
        User student = saveUser("Student", "student@example.com", Role.STUDENT);
        Cookie session = login("student@example.com", PASSWORD);

        mockMvc.perform(post("/admin/users/{id}/deactivate", student.getId()).with(csrf()).with(asAdmin()))
                .andExpect(redirectedUrl("/admin/users/" + student.getId()));

        User deactivated = userRepository.findById(student.getId()).orElseThrow();
        assertFalse(deactivated.isActive());
        assertNotNull(deactivated.getDeactivatedAt());
        mockMvc.perform(get("/profile").cookie(session)).andExpect(redirectedUrl("/login"));
        assertNull(login("student@example.com", PASSWORD));
        mockMvc.perform(get("/admin/users/{id}", student.getId()).with(asAdmin()))
                .andExpect(content().string(containsString("Активирай отново")));

        mockMvc.perform(post("/admin/users/{id}/activate", student.getId()).with(csrf()).with(asAdmin()))
                .andExpect(flash().attribute("message", "Профилът е активиран отново."));
        assertTrue(userRepository.findById(student.getId()).orElseThrow().isActive());
        assertNotNull(login("student@example.com", PASSWORD));
    }

    @Test
    void unknownUserIsNotFound() throws Exception {
        mockMvc.perform(get("/admin/users/{id}", 999_999).with(asAdmin())).andExpect(status().isNotFound());
        mockMvc.perform(post("/admin/users/{id}/deactivate", 999_999).with(csrf()).with(asAdmin()))
                .andExpect(status().isNotFound());
    }

    // --- First administrator ---

    @Test
    void configuredAdministratorIsCreatedWithTheConfiguredPasswordEvenIfItIsShort() throws Exception {
        User created = userService.ensureAdministrator("Seed", "Seed@Example.com", "admin");
        assertEquals(Role.ADMIN, created.getRole());
        assertEquals("Seed", created.getName());
        assertEquals("seed@example.com", created.getEmail());
        assertNotNull(login("seed@example.com", "admin"));
        assertFalse(UserService.meetsPasswordRules("admin"));

        // Idempotent: a second startup changes nothing.
        String hash = created.getPasswordHash();
        assertEquals(hash, userService.ensureAdministrator("Seed", "seed@example.com", "admin").getPasswordHash());
    }

    @Test
    void configuredAdministratorTakesOverAnExistingAccountAndItsPassword() throws Exception {
        User existing = saveUser("Existing", "existing@example.com", Role.STUDENT);
        userService.deactivate(existing.getId(), ADMIN_EMAIL);

        User promoted = userService.ensureAdministrator("Ignored", "existing@example.com", "admin");
        assertEquals(existing.getId(), promoted.getId());
        assertEquals("Existing", promoted.getName());
        assertEquals(Role.ADMIN, promoted.getRole());
        assertTrue(promoted.isActive());
        assertNull(login("existing@example.com", PASSWORD));
        assertNotNull(login("existing@example.com", "admin"));
    }

    @Test
    void blankConfiguredPasswordIsRejected() {
        IllegalArgumentException error = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> userService.ensureAdministrator("Seed", "seed@example.com", " "));
        assertEquals("Паролата на администратора не може да е празна.", error.getMessage());
    }

    @Test
    void relaxedRulesApplyOnlyToTheConfiguredAdministrator() throws Exception {
        mockMvc.perform(post("/admin/users").with(csrf()).with(asAdmin())
                        .param("name", "Weak").param("email", "weak@example.com").param("phone", "")
                        .param("password", "admin").param("role", "ADMIN"))
                .andExpect(model().attribute("error", "Паролата трябва да е между 8 и 128 символа."));
        assertFalse(userRepository.existsByEmail("weak@example.com"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor asAdmin() {
        return user(ADMIN_EMAIL).roles("ADMIN");
    }

    /** Real form login; returns the Spring Session cookie, or null when sign-in failed. */
    private Cookie login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/login").with(csrf()).param("username", email).param("password", password))
                .andExpect(status().is3xxRedirection()).andReturn();
        if (!"/events".equals(result.getResponse().getRedirectedUrl())) {
            return null;
        }
        return result.getResponse().getCookie("SESSION");
    }

    private User saveUser(String name, String email, Role role) {
        return userRepository.save(new User(email, passwordEncoder.encode(PASSWORD), name, role));
    }
}
