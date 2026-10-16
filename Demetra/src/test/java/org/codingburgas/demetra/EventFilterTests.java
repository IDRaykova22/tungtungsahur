package org.codingburgas.demetra;

import org.codingburgas.demetra.models.Event;
import org.codingburgas.demetra.models.EventStatus;
import org.codingburgas.demetra.models.Role;
import org.codingburgas.demetra.models.User;
import org.codingburgas.demetra.repositories.EventRepository;
import org.codingburgas.demetra.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:event-filter;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EventFilterTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    private User organizer;

    @BeforeEach
    void setUp() {
        organizer = userRepository.save(new User("filter@example.com", "test-hash", "Organizer", Role.ORGANIZER));
    }

    @Test
    void filtersByCreationMonthAndSortsByCreationTimeRegardlessOfScheduledDate() throws Exception {
        Event previous = saveEvent("Previous month", LocalDateTime.of(2025, 12, 31, 23, 59));
        previous.setStartsAt(LocalDateTime.of(2026, 1, 10, 12, 0));
        Event evening = saveEvent("Evening", LocalDateTime.of(2026, 1, 10, 18, 30));
        Event morning = saveEvent("Morning", LocalDateTime.of(2026, 1, 10, 9, 0));
        evening.setStartsAt(LocalDateTime.of(2026, 1, 10, 8, 0));
        morning.setStartsAt(LocalDateTime.of(2026, 1, 10, 20, 0));
        Event first = saveEvent("First", LocalDateTime.of(2026, 1, 1, 0, 0));
        first.setStartsAt(null);
        Event last = saveEvent("Last", LocalDateTime.of(2026, 1, 31, 23, 59, 59));
        saveEvent("Next month", LocalDateTime.of(2026, 2, 1, 0, 0));
        Event draft = saveEvent("Draft", LocalDateTime.of(2026, 1, 2, 12, 0));
        draft.setStatus(EventStatus.DRAFT);

        mockMvc.perform(get("/events").param("month", "2026-01"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("events", contains(first, morning, evening, last)))
                .andExpect(content().string(containsString("Created: 10 Jan 2026, 09:00")))
                .andExpect(content().string(containsString("Created: 10 Jan 2026, 18:30")))
                .andExpect(content().string(containsString("value=\"2026-01\"")));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void includesOnlyTheSelectedWeek(int week) throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0).plusWeeks(week - 1);
        LocalDateTime end = week == 5 ? LocalDateTime.of(2026, 2, 1, 0, 0) : start.plusWeeks(1);
        saveEvent("Before", start.minusSeconds(1));
        Event last = saveEvent("Last", end.minusSeconds(1));
        Event first = saveEvent("First", start);
        saveEvent("After", end);

        mockMvc.perform(get("/events").param("month", "2026-01").param("week", String.valueOf(week)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("events", contains(first, last)))
                .andExpect(content().string(containsString("value=\"" + week + "\" selected=\"selected\"")));
    }

    @ParameterizedTest
    @CsvSource({"2024-02, 29", "2026-04, 30", "2026-12, 31"})
    void lastWeekStopsAtTheEndOfTheMonth(String monthText, int lastDay) throws Exception {
        YearMonth month = YearMonth.parse(monthText);
        Event last = saveEvent("Last day", month.atDay(lastDay).atTime(23, 59));
        saveEvent("Next month", month.plusMonths(1).atDay(1).atStartOfDay());

        mockMvc.perform(get("/events").param("month", monthText).param("week", "5"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("events", contains(last)));
    }

    @Test
    void fifthWeekOfFebruaryWith28DaysIsEmpty() throws Exception {
        saveEvent("March", LocalDateTime.of(2026, 3, 1, 0, 0));

        mockMvc.perform(get("/events").param("month", "2026-02").param("week", "5"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("events", empty()))
                .andExpect(content().string(containsString("No published events were created in this period.")));
    }

    @ParameterizedTest
    @CsvSource({"2026-13, 0", "2026-01, -1", "2026-01, 6", "2026-01, abc", ", 1"})
    void rejectsInvalidFilters(String month, String week) throws Exception {
        mockMvc.perform(get("/events").param("month", month == null ? "" : month).param("week", week))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clearingFiltersSortsByCreationTimeAndIncludesEventsWithoutScheduledDates() throws Exception {
        Event later = saveEvent("Created later", LocalDateTime.of(2026, 2, 1, 12, 0));
        later.setStartsAt(LocalDateTime.of(2026, 3, 1, 12, 0));
        Event earlier = saveEvent("Created earlier", LocalDateTime.of(2026, 1, 1, 12, 0));
        earlier.setStartsAt(LocalDateTime.of(2026, 4, 1, 12, 0));
        Event undated = saveEvent("Date to be announced", LocalDateTime.of(2026, 1, 15, 12, 0));
        undated.setStartsAt(null);

        mockMvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("events", contains(earlier, undated, later)))
                .andExpect(content().string(containsString("Date TBA")));
    }

    private Event saveEvent(String title, LocalDateTime createdAt) {
        Event event = new Event();
        event.setTitle(title);
        event.setCreatedAt(createdAt);
        event.setStartsAt(createdAt.plusMonths(3));
        event.setCapacity(10);
        event.setOrganizer(organizer);
        return eventRepository.save(event);
    }
}
