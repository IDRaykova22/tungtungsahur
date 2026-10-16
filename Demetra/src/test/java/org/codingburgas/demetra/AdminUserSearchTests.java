package org.codingburgas.demetra;

import org.codingburgas.demetra.models.Role;
import org.codingburgas.demetra.models.User;
import org.codingburgas.demetra.repositories.EventRepository;
import org.codingburgas.demetra.repositories.RegistrationRepository;
import org.codingburgas.demetra.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:user-search;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserSearchTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private RegistrationRepository registrationRepository;

    @BeforeEach
    void setUp() {
        registrationRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();
    }

    // --- Searching ---

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "мария            | Мария Петрова",
            "ПЕТРОВА          | Мария Петрова",
            "ivan@school      | Ivan Georgiev",
            "0888123          | Мария Петрова",
            "+359 88 765      | Ivan Georgiev",
            "мария петрова    | Мария Петрова",
            "georgiev school  | Ivan Georgiev"})
    void findsByAnyPartOfNameEmailOrPhoneIgnoringCaseAndPhoneFormatting(String query, String expected) throws Exception {
        seedPeople();
        assertEquals(List.of(expected), names(search("q", query)));
    }

    @Test
    void everyWordMustMatchAndWildcardsAreLiteral() throws Exception {
        seedPeople();
        assertEquals(List.of(), names(search("q", "мария georgiev")));
        assertEquals(List.of(), names(search("q", "%")));
        assertEquals(List.of(), names(search("q", "_")));
        save("Percent 100% Sure", "percent@example.com", Role.STUDENT, null, 1);
        assertEquals(List.of("Percent 100% Sure"), names(search("q", "100%")));
    }

    @Test
    void searchCombinesWithRoleAndStatusFilters() throws Exception {
        seedPeople();
        User inactive = save("Мария Иванова", "maria.i@example.com", Role.STUDENT, null, 5);
        inactive.setDeactivatedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        userRepository.save(inactive);

        assertEquals(List.of("Мария Иванова", "Мария Петрова"), names(search("q", "мария")));
        assertEquals(List.of("Мария Петрова"), names(search("q", "мария", "status", "active")));
        assertEquals(List.of("Мария Иванова"), names(search("q", "мария", "status", "inactive")));
        assertEquals(List.of(), names(search("q", "мария", "role", "ORGANIZER")));
        assertEquals(List.of("Ivan Georgiev"), names(search("role", "organizer")));
    }

    @Test
    void emptyResultExplainsAndOffersToClearTheSearch() throws Exception {
        seedPeople();
        mockMvc.perform(get("/admin/users").param("q", "nobody-matches").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Няма потребители по избраните критерии.")))
                .andExpect(content().string(containsString("Покажи всички")));
    }

    // --- Sorting ---

    @Test
    void sortsByEachColumnInBothDirectionsWithNameAsTieBreaker() throws Exception {
        save("boris", "c@example.com", Role.STUDENT, null, 2);
        save("Anna", "b@example.com", Role.ORGANIZER, null, 3);
        save("Anna", "a@example.com", Role.ADMIN, null, 1);
        save("Цветан", "d@example.com", Role.STUDENT, null, 4);

        // Two "Anna"s: equal names keep insertion (id) order, so b@ (saved first) comes before a@.
        assertEquals(List.of("b@example.com", "a@example.com", "c@example.com", "d@example.com"),
                emails(search()));
        assertEquals(List.of("d@example.com", "c@example.com", "b@example.com", "a@example.com"),
                emails(search("sort", "name", "dir", "desc")));
        assertEquals(List.of("d@example.com", "c@example.com", "b@example.com", "a@example.com"),
                emails(search("sort", "email", "dir", "desc")));
        assertEquals(List.of("a@example.com", "b@example.com", "c@example.com", "d@example.com"),
                emails(search("sort", "role")));
        // Registration date defaults to newest first.
        assertEquals(List.of("d@example.com", "b@example.com", "c@example.com", "a@example.com"),
                emails(search("sort", "date")));
        assertEquals(List.of("a@example.com", "c@example.com", "b@example.com", "d@example.com"),
                emails(search("sort", "date", "dir", "asc")));
        // Unknown values fall back to the default order instead of failing.
        assertEquals(emails(search()), emails(search("sort", "password", "dir", "sideways")));
    }

    @Test
    void columnHeadersLinkToTheOppositeDirectionAndKeepTheSearch() throws Exception {
        seedPeople();
        mockMvc.perform(get("/admin/users").param("q", "+359").param("role", "ORGANIZER").with(asAdmin()))
                .andExpect(content().string(containsString("href=\"/admin/users?q=%2B359&amp;role=ORGANIZER&amp;dir=desc\"")))
                .andExpect(content().string(containsString("href=\"/admin/users?q=%2B359&amp;role=ORGANIZER&amp;sort=date\"")))
                .andExpect(content().string(containsString("aria-sort=\"ascending\"")));
        mockMvc.perform(get("/admin/users").param("sort", "date").with(asAdmin()))
                .andExpect(content().string(containsString("aria-sort=\"descending\"")))
                .andExpect(content().string(containsString("href=\"/admin/users?sort=date&amp;dir=asc\"")));
    }

    // --- Paging ---

    @Test
    void pagesThroughResultsWithStableOrderAndSummary() throws Exception {
        for (int i = 1; i <= 23; i++) {
            save(String.format("Member %02d", i), "member" + i + "@example.com", Role.STUDENT, null, 1);
        }

        Page<User> first = search();
        assertEquals(23, first.getTotalElements());
        assertEquals(3, first.getTotalPages());
        assertEquals("Member 01", first.getContent().get(0).getName());
        Page<User> last = search("page", "3");
        assertEquals(List.of("Member 21", "Member 22", "Member 23"), names(last));

        mockMvc.perform(get("/admin/users").param("page", "2").with(asAdmin()))
                .andExpect(content().string(containsString("Намерени <strong>23</strong>")))
                .andExpect(content().string(containsString("показани <span>11</span>–<span>20</span>")))
                .andExpect(content().string(containsString("aria-current=\"page\">2<")))
                .andExpect(content().string(containsString("href=\"/admin/users\" class=\"page-link\" rel=\"prev\"")))
                .andExpect(content().string(containsString("href=\"/admin/users?page=3\" class=\"page-link\" rel=\"next\"")));
    }

    @Test
    void pageSizeAndPageNumbersAreSanitized() throws Exception {
        for (int i = 1; i <= 30; i++) {
            save(String.format("Member %02d", i), "member" + i + "@example.com", Role.STUDENT, null, 1);
        }
        assertEquals(25, search("size", "25").getContent().size());
        assertEquals(10, search("size", "1000").getContent().size());
        assertEquals(10, search("size", "abc").getContent().size());
        assertEquals(1, search("page", "-4").getNumber() + 1);
        assertEquals(1, search("page", "abc").getNumber() + 1);
        // Past the end: the last page is shown rather than an empty table.
        Page<User> beyond = search("page", "99");
        assertEquals(3, beyond.getNumber() + 1);
        assertEquals(List.of("Member 21", "Member 22", "Member 23", "Member 24", "Member 25", "Member 26",
                "Member 27", "Member 28", "Member 29", "Member 30"), names(beyond));
        // Paging links keep the chosen size and the search.
        mockMvc.perform(get("/admin/users").param("q", "member").param("size", "25").with(asAdmin()))
                .andExpect(content().string(containsString("href=\"/admin/users?q=member&amp;size=25&amp;page=2\"")));
    }

    @Test
    void longResultListsCollapseThePageNumbers() throws Exception {
        for (int i = 1; i <= 120; i++) {
            save(String.format("Member %03d", i), "member" + i + "@example.com", Role.STUDENT, null, 1);
        }
        mockMvc.perform(get("/admin/users").param("page", "6").with(asAdmin()))
                .andExpect(content().string(containsString("class=\"page-gap\"")))
                .andExpect(content().string(containsString("aria-label=\"Страница 12\"")))
                .andExpect(content().string(not(containsString("aria-label=\"Страница 9\""))))
                .andExpect(content().string(containsString("aria-label=\"Страница 8\"")));
    }

    @Test
    void onlyAdministratorsCanSearch() throws Exception {
        mockMvc.perform(get("/admin/users").param("q", "a").with(user("o@example.com").roles("ORGANIZER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/users").param("q", "a").with(user("s@example.com").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    private void seedPeople() {
        save("Мария Петрова", "maria@example.com", Role.STUDENT, "0888 123 456", 1);
        save("Ivan Georgiev", "ivan@school.example.com", Role.ORGANIZER, "+359 88 765 4321", 2);
    }

    @SuppressWarnings("unchecked")
    private Page<User> search(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/admin/users").with(asAdmin());
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return (Page<User>) result.getModelAndView().getModel().get("users");
    }

    private static List<String> names(Page<User> page) {
        return page.getContent().stream().map(User::getName).toList();
    }

    private static List<String> emails(Page<User> page) {
        return page.getContent().stream().map(User::getEmail).toList();
    }

    private static RequestPostProcessor asAdmin() {
        return user("search-admin@example.com").roles("ADMIN");
    }

    private User save(String name, String email, Role role, String phone, int day) {
        User user = new User(email, "test-hash", name, role);
        user.setPhone(phone);
        user.setCreatedAt(LocalDateTime.of(2026, 1, day, 10, 0));
        return userRepository.save(user);
    }
}
