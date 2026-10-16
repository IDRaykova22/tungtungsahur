package org.codingburgas.demetra;

import org.codingburgas.demetra.models.Role;
import org.codingburgas.demetra.models.User;
import org.codingburgas.demetra.models.stats.ChartBar;
import org.codingburgas.demetra.models.stats.PeriodSummary;
import org.codingburgas.demetra.models.stats.RegistrationDashboard;
import org.codingburgas.demetra.models.stats.RegistrationPeriod;
import org.codingburgas.demetra.models.stats.RoleShare;
import org.codingburgas.demetra.repositories.EventRepository;
import org.codingburgas.demetra.repositories.RegistrationRepository;
import org.codingburgas.demetra.repositories.UserRepository;
import org.codingburgas.demetra.services.RegistrationStatsService;
import org.codingburgas.demetra.services.intelligence.Insight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:registration-dashboard;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RegistrationDashboardTests.FixedTime.class)
class RegistrationDashboardTests {
    @TestConfiguration
    static class FixedTime {
        @Bean
        @Primary
        Clock registrationTestClock() {
            // 12.10.2026, 12:00 in Sofia
            return Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Sofia"));
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private RegistrationStatsService statsService;
    private int number;

    @BeforeEach
    void setUp() {
        registrationRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();
        number = 0;
    }

    @Test
    void eachPeriodCountsExactlyItsDaysAndComparesWithTheSameLengthBefore() {
        save("2026-10-12T00:00", Role.STUDENT);   // today
        save("2026-10-11T23:59", Role.STUDENT);   // yesterday: week, not today
        save("2026-10-06T00:00", Role.ORGANIZER); // first day of the 7
        save("2026-10-05T23:59", Role.STUDENT);   // previous 7 days
        save("2026-09-13T00:00", Role.STUDENT);   // first day of the month
        save("2026-09-12T23:59", Role.ADMIN);     // previous month
        save("2025-10-13T00:00", Role.STUDENT);   // first day of the year
        save("2025-10-12T23:59", Role.STUDENT);   // previous year
        save("2026-10-13T00:00", Role.STUDENT);   // tomorrow (clock skew) is never counted

        assertSummary(RegistrationPeriod.TODAY, 1, 1);
        assertSummary(RegistrationPeriod.WEEK, 3, 1);
        assertSummary(RegistrationPeriod.MONTH, 5, 1);
        assertSummary(RegistrationPeriod.YEAR, 7, 1);
    }

    @ParameterizedTest
    @CsvSource({"TODAY, 24, false", "WEEK, 7, true", "MONTH, 30, true", "YEAR, 13, true"})
    void chartColumnsAddUpToTheTotalAndOnlyLongerPeriodsGetAForecastColumn(RegistrationPeriod period, int columns,
                                                                         boolean forecast) {
        save("2026-10-12T10:15", Role.STUDENT);
        save("2026-10-12T10:45", Role.ORGANIZER);
        save("2026-10-09T08:00", Role.STUDENT);
        save("2026-09-20T08:00", Role.STUDENT);
        save("2026-01-05T08:00", Role.STUDENT);

        RegistrationDashboard dashboard = statsService.dashboard(period);
        List<ChartBar> actual = dashboard.getBars().stream().filter(b -> !b.forecast()).toList();
        assertEquals(columns, actual.size());
        assertEquals(forecast, dashboard.isHasForecast());
        assertEquals(dashboard.getSelected().count(), Math.round(actual.stream().mapToDouble(ChartBar::value).sum()));
        assertEquals(100, dashboard.getRoleShares().stream().mapToInt(RoleShare::percent).sum());
        assertEquals(dashboard.getSelected().count(),
                dashboard.getRoleShares().stream().mapToLong(RoleShare::count).sum());
        if (period == RegistrationPeriod.TODAY) {
            assertEquals(2, actual.get(10).value());   // both registrations in the 10:00–11:00 column
        }
    }

    @Test
    void aSpikeDayIsHighlightedAndExplained() {
        for (int day = 6; day <= 11; day++) {
            save(String.format("2026-10-%02dT09:00", day), Role.STUDENT);
        }
        for (int i = 0; i < 8; i++) {
            save("2026-10-08T1" + i + ":00", Role.STUDENT);
        }

        RegistrationDashboard dashboard = statsService.dashboard(RegistrationPeriod.WEEK);
        ChartBar spike = dashboard.getBars().get(2);
        assertTrue(spike.anomaly(), "08.10 should be flagged");
        assertEquals(1, dashboard.getBars().stream().filter(ChartBar::anomaly).count());
        Insight anomaly = insight(dashboard, Insight.Kind.ANOMALY);
        assertEquals(Insight.Tone.WARNING, anomaly.tone());
        assertTrue(anomaly.text().contains("четвъртък, 08.10.2026 (9 регистрации)"), anomaly.text());
    }

    @Test
    void anUnusuallyBusyTodayIsReportedAgainstTheLast29Days() {
        for (int day = 13; day <= 30; day += 3) {
            save(String.format("2026-09-%02dT12:00", day), Role.STUDENT);
        }
        for (int i = 0; i < 6; i++) {
            save("2026-10-12T0" + i + ":30", Role.STUDENT);
        }
        Insight anomaly = insight(statsService.dashboard(RegistrationPeriod.TODAY), Insight.Kind.ANOMALY);
        assertEquals(Insight.Tone.WARNING, anomaly.tone());
        assertTrue(anomaly.text().startsWith("Днес има 6 регистрации при средно 0,2 на ден за последните 29 дни"),
                anomaly.text());
    }

    @Test
    void steadyGrowthIsRecognisedAsARisingTrendWithAForecast() {
        for (int day = 0; day < 6; day++) {
            for (int i = 0; i <= day; i++) {
                save(String.format("2026-10-%02dT%02d:00", 6 + day, 8 + i), Role.STUDENT);
            }
        }
        RegistrationDashboard dashboard = statsService.dashboard(RegistrationPeriod.WEEK);
        Insight trend = insight(dashboard, Insight.Kind.TREND);
        assertEquals(Insight.Tone.POSITIVE, trend.tone());
        assertTrue(trend.text().startsWith("Възходяща"), trend.text());
        ChartBar tomorrow = dashboard.getBars().get(dashboard.getBars().size() - 1);
        assertTrue(tomorrow.forecast());
        assertEquals("утре", tomorrow.label());
        assertEquals(8, Math.round(tomorrow.value()));   // 1, 2, … 6 per day, today skipped → 8 tomorrow
    }

    @Test
    void pageShowsAllFourPeriodsTheChartAnalysisAndNewUsers() throws Exception {
        save("2026-10-12T10:00", Role.STUDENT, "Мария Петрова");
        save("2026-10-10T10:00", Role.ORGANIZER, "Иван Георгиев");

        mockMvc.perform(get("/admin/registrations").param("period", "week").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/registrations"))
                .andExpect(content().string(containsString("Нови регистрации")))
                .andExpect(content().string(containsString("href=\"/admin/registrations?period=today\"")))
                .andExpect(content().string(containsString("href=\"/admin/registrations?period=year\"")))
                .andExpect(content().string(containsString("aria-current=\"page\"")))
                .andExpect(content().string(containsString("Регистрации по дни")))
                .andExpect(content().string(containsString("Интелигентен анализ")))
                .andExpect(content().string(containsString("2 регистрации за последните 7 дни")))
                .andExpect(content().string(containsString("Мария Петрова")))
                .andExpect(content().string(containsString("Иван Георгиев")))
                .andExpect(content().string(containsString("conic-gradient(")));
        mockMvc.perform(get("/admin/registrations").with(asAdmin()))
                .andExpect(content().string(containsString("Регистрации по часове днес")))
                .andExpect(content().string(containsString("Мария Петрова")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Иван Георгиев"))));
    }

    @Test
    void newUsersAreListedNewestFirstOnPages() throws Exception {
        for (int i = 0; i < 15; i++) {
            save(String.format("2026-10-12T%02d:00", i), Role.STUDENT, "User " + i);
        }
        Page<User> first = statsService.newUsers(RegistrationPeriod.TODAY, 1, 12);
        assertEquals("User 14", first.getContent().get(0).getName());
        assertEquals(2, first.getTotalPages());

        mockMvc.perform(get("/admin/registrations").param("page", "2").with(asAdmin()))
                .andExpect(content().string(containsString("Показани 13–15 от 15")))
                .andExpect(content().string(containsString("href=\"/admin/registrations?period=today&amp;page=1\"")));
        mockMvc.perform(get("/admin/registrations").param("page", "99").with(asAdmin()))
                .andExpect(model().attribute("users", org.hamcrest.Matchers.hasProperty("number", org.hamcrest.Matchers.is(1))));
        mockMvc.perform(get("/admin/registrations").param("page", "abc").param("period", "???").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("users", org.hamcrest.Matchers.hasProperty("number", org.hamcrest.Matchers.is(0))));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"STUDENT", "ORGANIZER"})
    void onlyAdministratorsSeeTheDashboard(Role role) throws Exception {
        mockMvc.perform(get("/admin/registrations").with(user("x@example.com").roles(role.name())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/events").with(user("x@example.com").roles(role.name())))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("href=\"/admin/registrations\""))));
    }

    @Test
    void anonymousIsSentToLoginAndAdminsGetANavigationLink() throws Exception {
        mockMvc.perform(get("/admin/registrations")).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/events").with(asAdmin()))
                .andExpect(content().string(containsString("href=\"/admin/registrations\"")));
    }

    @Test
    void emptyPeriodsRenderWithoutErrors() throws Exception {
        for (RegistrationPeriod period : RegistrationPeriod.values()) {
            mockMvc.perform(get("/admin/registrations").param("period", period.getKey()).with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Няма нови потребители за " + period.getPhrase())));
        }
        assertFalse(statsService.dashboard(RegistrationPeriod.MONTH).isHasAnomaly());
    }

    private void assertSummary(RegistrationPeriod period, long count, long previous) {
        PeriodSummary summary = statsService.summarize(period);
        assertEquals(count, summary.count(), period + " count");
        assertEquals(previous, summary.previousCount(), period + " previous");
    }

    private static Insight insight(RegistrationDashboard dashboard, Insight.Kind kind) {
        return dashboard.getInsights().stream().filter(i -> i.kind() == kind).findFirst().orElseThrow();
    }

    private static RequestPostProcessor asAdmin() {
        return user("stats-admin@example.com").roles("ADMIN");
    }

    private User save(String createdAt, Role role) {
        return save(createdAt, role, "User " + (number + 1));
    }

    private User save(String createdAt, Role role, String name) {
        User user = new User("new" + (++number) + "@example.com", "test-hash", name, role);
        user.setCreatedAt(LocalDateTime.parse(createdAt));
        return userRepository.save(user);
    }
}
