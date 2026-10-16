package org.codingburgas.demetra;

import org.codingburgas.demetra.models.Event;
import org.codingburgas.demetra.models.EventStatus;
import org.codingburgas.demetra.models.Registration;
import org.codingburgas.demetra.models.RegistrationStatus;
import org.codingburgas.demetra.models.Role;
import org.codingburgas.demetra.models.User;
import org.codingburgas.demetra.models.reports.EventReport;
import org.codingburgas.demetra.models.reports.EventReportRow;
import org.codingburgas.demetra.models.reports.ReportGroupRow;
import org.codingburgas.demetra.models.reports.ReportGrouping;
import org.codingburgas.demetra.repositories.EventRepository;
import org.codingburgas.demetra.repositories.RegistrationRepository;
import org.codingburgas.demetra.repositories.UserRepository;
import org.codingburgas.demetra.services.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:reports;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ReportFlowTests.FixedTime.class)
class ReportFlowTests {
    @TestConfiguration
    static class FixedTime {
        @Bean
        @Primary
        Clock reportTestClock() {
            return Clock.fixed(Instant.parse("2026-09-15T09:00:00Z"), ZoneId.of("Europe/Sofia"));
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ReportService reportService;
    @Autowired private EventRepository eventRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private UserRepository userRepository;
    private User organizer;
    private int participantNumber;

    @BeforeEach
    void setUp() {
        registrationRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();
        organizer = userRepository.save(new User("reports@example.com", "test-hash", "Organizer", Role.ORGANIZER));
        participantNumber = 0;
    }

    @ParameterizedTest
    @CsvSource({"2026-09-01, 2026-09-15, 1", "2026-04-01, 2026-09-15, 6", "2025-10-01, 2026-09-15, 12",
            "2026-04-10, 2026-04-20, 1"})
    void queryRespectsExactBoundariesAndUsesCreationRatherThanScheduledDate(LocalDate from, LocalDate to, int months) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        saveEvent("Before", start.minusSeconds(1), "Concert", 10, EventStatus.PUBLISHED);
        Event first = saveEvent("First", start, "Concert", 10, EventStatus.PUBLISHED);
        Event last = saveEvent("Last", end.minusNanos(1000), "Concert", 10, EventStatus.CLOSED);
        saveEvent("After", end, "Concert", 10, EventStatus.PUBLISHED);
        first.setStartsAt(start.minusYears(1));
        eventRepository.save(first);
        last.setStartsAt(null);
        eventRepository.save(last);

        EventReport report = reportService.generate(from, to, ReportGrouping.MONTH);
        assertEquals(List.of(first.getId(), last.getId()), report.getEvents().stream().map(EventReportRow::getId).toList());
        assertEquals(months, report.getGroupRows().size());
        assertEquals(2, report.getEventCount());
        assertEquals(0, report.getConfirmedCount());
        assertEquals(new BigDecimal("0.00"), report.getEstimatedAmount());
        assertEquals(to.equals(LocalDate.of(2026, 9, 15)), report.isTodayIncluded());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void aggregatesConfirmedRegistrationsByMonthAndCategoryAndRendersWithoutOpenSessionInView() throws Exception {
        Event concert = saveEvent("Autumn concert", LocalDateTime.of(2026, 4, 10, 12, 0), "Concert", 12.35, EventStatus.PUBLISHED);
        addRegistration(concert, RegistrationStatus.CONFIRMED);
        addRegistration(concert, RegistrationStatus.CONFIRMED);
        addRegistration(concert, RegistrationStatus.WAITLISTED);
        addRegistration(concert, RegistrationStatus.CANCELLED);
        Event recital = saveEvent("Recital", LocalDateTime.of(2026, 6, 2, 12, 0), "Concert", 0.10, EventStatus.CLOSED);
        addRegistration(recital, RegistrationStatus.CONFIRMED);
        Event free = saveEvent("Free workshop", LocalDateTime.of(2026, 9, 1, 12, 0), " ", 0, EventStatus.PUBLISHED);
        free.setStartsAt(null);
        eventRepository.save(free);
        addRegistration(free, RegistrationStatus.CONFIRMED);
        saveEvent("No participants", LocalDateTime.of(2026, 5, 1, 12, 0), null, 25, EventStatus.PUBLISHED);
        Event cancelled = saveEvent("Cancelled event", LocalDateTime.of(2026, 7, 1, 12, 0), "Concert", 100, EventStatus.CANCELLED);
        addRegistration(cancelled, RegistrationStatus.CONFIRMED);
        saveEvent("Draft event", LocalDateTime.of(2026, 8, 1, 12, 0), "Concert", 100, EventStatus.DRAFT);

        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2026, 9, 15);
        EventReport byCategory = reportService.generate(from, to, ReportGrouping.CATEGORY);
        assertEquals(4, byCategory.getEventCount());
        assertEquals(4, byCategory.getConfirmedCount());
        assertEquals(new BigDecimal("24.80"), byCategory.getEstimatedAmount());
        assertEquals(List.of("Concert", "Без категория"), labels(byCategory));
        assertEquals(2, byCategory.getGroupRows().get(0).getEventCount());
        assertEquals(new BigDecimal("24.80"), byCategory.getGroupRows().get(0).getEstimatedAmount());
        assertEquals(100, byCategory.sharePercent(byCategory.getGroupRows().get(0)));

        EventReport byMonth = reportService.generate(from, to, ReportGrouping.MONTH);
        assertEquals(List.of("април 2026", "май 2026", "юни 2026", "юли 2026", "август 2026", "септември 2026"),
                labels(byMonth));
        assertEquals(0, byMonth.getGroupRows().get(3).getEventCount());
        assertEquals(new BigDecimal("0.00"), byMonth.getGroupRows().get(3).getEstimatedAmount());
        for (EventReport report : List.of(byCategory, byMonth)) {
            assertEquals(report.getEstimatedAmount(), report.getGroupRows().stream()
                    .map(ReportGroupRow::getEstimatedAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
            assertEquals(report.getEventCount(), report.getGroupRows().stream().mapToLong(ReportGroupRow::getEventCount).sum());
        }

        mockMvc.perform(get("/reports").param("from", "2026-04-01").param("to", "2026-09-15").param("groupBy", "category"))
                .andExpect(status().isOk()).andExpect(view().name("reports/index"))
                .andExpect(content().string(containsString("Групиране по категории")))
                .andExpect(content().string(containsString("24,80 $")))
                .andExpect(content().string(containsString("Autumn concert")))
                .andExpect(content().string(containsString("Не е определено")))
                .andExpect(content().string(containsString("Няма регистрирани реални плащания")))
                .andExpect(content().string(not(containsString("Cancelled event"))))
                .andExpect(content().string(not(containsString("Draft event"))));
    }

    @Test
    void groupsByStatusAndByOrganizer() {
        saveEvent("Open", LocalDateTime.of(2026, 9, 2, 10, 0), "Concert", 1, EventStatus.PUBLISHED);
        saveEvent("Done", LocalDateTime.of(2026, 9, 3, 10, 0), "Concert", 1, EventStatus.CLOSED);
        User zora = userRepository.save(new User("zora@example.com", "test-hash", "Zora", Role.ORGANIZER));
        User anton = userRepository.save(new User("anton@example.com", "test-hash", "anton", Role.ORGANIZER));
        // Same name as the default organizer, but a different person: must stay a separate group.
        User namesake = userRepository.save(new User("namesake@example.com", "test-hash", "Organizer", Role.ORGANIZER));
        for (User owner : List.of(zora, anton, namesake)) {
            Event event = saveEvent("By " + owner.getEmail(), LocalDateTime.of(2026, 9, 4, 10, 0), "Concert", 1,
                    EventStatus.PUBLISHED);
            event.setOrganizer(owner);
            eventRepository.save(event);
        }
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 15);

        EventReport byStatus = reportService.generate(from, to, ReportGrouping.STATUS);
        assertEquals(List.of("Публикувано", "Приключило"), labels(byStatus));
        assertEquals(List.of(4L, 1L), byStatus.getGroupRows().stream().map(ReportGroupRow::getEventCount).toList());

        EventReport byOrganizer = reportService.generate(from, to, ReportGrouping.ORGANIZER);
        assertEquals(List.of("anton", "Organizer", "Organizer", "Zora"), labels(byOrganizer));
        assertEquals(5, byOrganizer.getGroupRows().stream().mapToLong(ReportGroupRow::getEventCount).sum());
    }

    @Test
    void roundsUnitPriceBeforeMultiplyingAndUsesCurrentRegistrationStatus() {
        Event event = saveEvent("Rounded price", LocalDateTime.of(2026, 9, 1, 0, 0), "Concert", 2.345, EventStatus.PUBLISHED);
        Registration registration = addRegistration(event, RegistrationStatus.CONFIRMED);
        addRegistration(event, RegistrationStatus.CONFIRMED);
        LocalDate day = LocalDate.of(2026, 9, 1);
        assertEquals(new BigDecimal("4.70"), reportService.generate(day, day, ReportGrouping.MONTH).getEstimatedAmount());
        registration.setStatus(RegistrationStatus.CANCELLED);
        registrationRepository.save(registration);
        assertEquals(new BigDecimal("2.35"), reportService.generate(day, day, ReportGrouping.MONTH).getEstimatedAmount());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void defaultReportUsesCurrentMonthGroupedByMonthAndShowsZeroesWhenEmpty() throws Exception {
        mockMvc.perform(get("/reports"))
                .andExpect(status().isOk()).andExpect(view().name("reports/index"))
                .andExpect(content().string(containsString("value=\"2026-09-01\"")))
                .andExpect(content().string(containsString("value=\"2026-09-15\"")))
                .andExpect(content().string(containsString("<option value=\"month\" selected=\"selected\">Месец</option>")))
                .andExpect(content().string(containsString("Последните 30 дни")))
                .andExpect(content().string(containsString("Няма публикувани или приключили събития")));
        EventReport report = reportService.generate(LocalDate.of(2025, 10, 1), LocalDate.of(2026, 9, 15), ReportGrouping.MONTH);
        assertEquals(12, report.getGroupRows().size());
        assertEquals(0, report.getEventCount());
        assertTrue(reportService.generate(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15), ReportGrouping.CATEGORY)
                .getGroupRows().isEmpty());
        assertEquals(new BigDecimal("0.00"), report.getEstimatedAmount());
    }

    @Test
    void pastRangeIsCompleteAndDoesNotIncludeToday() {
        saveEvent("End of August", LocalDateTime.of(2026, 8, 31, 23, 59), "Concert", 0, EventStatus.PUBLISHED);
        EventReport report = reportService.generate(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), ReportGrouping.MONTH);
        assertEquals(1, report.getEventCount());
        assertFalse(report.isTodayIncluded());
    }

    @ParameterizedTest
    @CsvSource({"2026-13-01, 2026-09-15", "not-a-date, 2026-09-15", "2026-09-01, abc", "2026-09-10, 2026-09-01",
            "2026-09-01, 2026-09-16", "2024-09-14, 2026-09-15"})
    @WithMockUser(roles = "ORGANIZER")
    void invalidFiltersReturnHelpfulErrorWithoutReport(String from, String to) throws Exception {
        mockMvc.perform(get("/reports").param("from", from).param("to", to))
                .andExpect(status().isBadRequest()).andExpect(view().name("reports/index"))
                .andExpect(model().attributeExists("error"))
                .andExpect(model().attributeDoesNotExist("report"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void unknownGroupingFallsBackToMonths() throws Exception {
        mockMvc.perform(get("/reports").param("from", "2026-09-01").param("to", "2026-09-15").param("groupBy", "colour"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Групиране по месеци")));
    }

    @Test
    void anonymousMustLogIn() throws Exception {
        mockMvc.perform(get("/reports")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/events")).andExpect(content().string(not(containsString("href=\"/reports\""))));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentsCannotOpenReportOrSeeReportButton() throws Exception {
        mockMvc.perform(get("/reports")).andExpect(status().isForbidden());
        mockMvc.perform(get("/events")).andExpect(content().string(not(containsString("href=\"/reports\""))));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void organizersSeeReportButtonAndTitlesAreHtmlEscaped() throws Exception {
        saveEvent("<script>alert('x')</script>", LocalDateTime.of(2026, 9, 1, 0, 0), "Concert", 0, EventStatus.PUBLISHED);
        mockMvc.perform(get("/events")).andExpect(content().string(containsString("href=\"/reports\"")));
        mockMvc.perform(get("/reports"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>"))));
    }

    private static List<String> labels(EventReport report) {
        return report.getGroupRows().stream().map(ReportGroupRow::getLabel).toList();
    }

    private Event saveEvent(String title, LocalDateTime createdAt, String category, double price, EventStatus status) {
        Event event = new Event();
        event.setTitle(title);
        event.setCreatedAt(createdAt);
        event.setStartsAt(createdAt.plusMonths(3));
        event.setCategory(category);
        event.setPrice(price);
        event.setCapacity(100);
        event.setStatus(status);
        event.setOrganizer(organizer);
        return eventRepository.save(event);
    }

    private Registration addRegistration(Event event, RegistrationStatus status) {
        User user = userRepository.save(new User("participant" + (++participantNumber) + "@example.com",
                "test-hash", "Participant", Role.STUDENT));
        return registrationRepository.save(new Registration(user, event, status));
    }
}
