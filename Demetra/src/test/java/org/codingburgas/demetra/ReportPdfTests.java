package org.codingburgas.demetra;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.codingburgas.demetra.models.Event;
import org.codingburgas.demetra.models.EventStatus;
import org.codingburgas.demetra.models.Registration;
import org.codingburgas.demetra.models.RegistrationStatus;
import org.codingburgas.demetra.models.Role;
import org.codingburgas.demetra.models.User;
import org.codingburgas.demetra.repositories.EventRepository;
import org.codingburgas.demetra.repositories.RegistrationRepository;
import org.codingburgas.demetra.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:report-pdf;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ReportPdfTests.FixedTime.class)
class ReportPdfTests {
    @TestConfiguration
    static class FixedTime {
        @Bean
        @Primary
        Clock reportTestClock() {
            return Clock.fixed(Instant.parse("2026-09-15T09:00:00Z"), ZoneId.of("Europe/Sofia"));
        }
    }

    @Autowired private MockMvc mockMvc;
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
        organizer = userRepository.save(new User("pdf@example.com", "test-hash", "Organizer", Role.ORGANIZER));
        participantNumber = 0;
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void downloadContainsEverySectionWithTheSameNumbersAsThePage() throws Exception {
        Event concert = saveEvent("Есенен концерт", LocalDateTime.of(2026, 4, 10, 12, 0), "Концерт", 12.35, EventStatus.PUBLISHED);
        addRegistration(concert, RegistrationStatus.CONFIRMED);
        addRegistration(concert, RegistrationStatus.CONFIRMED);
        addRegistration(concert, RegistrationStatus.WAITLISTED);
        Event recital = saveEvent("Recital", LocalDateTime.of(2026, 6, 2, 12, 0), "Концерт", 0.10, EventStatus.CLOSED);
        addRegistration(recital, RegistrationStatus.CONFIRMED);
        Event free = saveEvent("Free workshop", LocalDateTime.of(2026, 9, 1, 12, 0), " ", 0, EventStatus.PUBLISHED);
        free.setStartsAt(null);
        eventRepository.save(free);
        saveEvent("Draft event", LocalDateTime.of(2026, 8, 1, 12, 0), "Концерт", 100, EventStatus.DRAFT);

        MvcResult result = mockMvc.perform(get("/reports/pdf")
                        .param("from", "2026-04-01").param("to", "2026-09-15").param("groupBy", "category")
                        .param("download", "true"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("demetra-otchet-2026-04-01_2026-09-15.pdf")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn();

        String text = pdfText(result.getResponse().getContentAsByteArray());
        assertTrue(text.contains("Отчет за събитията"));
        assertTrue(text.contains("01.04.2026 – 15.09.2026"));
        assertTrue(text.contains("168 дни"));
        assertTrue(text.contains("Периодът включва днешния ден"));
        assertTrue(text.contains("Изчислена сума по категории"));
        assertTrue(text.contains("Групиране по категории"));
        assertTrue(text.contains("Включени събития"));
        assertTrue(text.contains("Есенен концерт"));
        assertTrue(text.contains("Без категория"));
        assertTrue(text.contains("Не е определено"));
        assertTrue(text.contains("24,80 $"));
        assertTrue(text.contains("100,0 %"));
        assertTrue(text.contains("Няма регистрирани реални плащания"));
        assertFalse(text.contains("Draft event"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void previewOpensInlineAndDefaultsToCurrentMonth() throws Exception {
        MvcResult result = mockMvc.perform(get("/reports/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(header().string("Content-Disposition", containsString("demetra-otchet-2026-09-01_2026-09-15.pdf")))
                .andReturn();

        String text = pdfText(result.getResponse().getContentAsByteArray());
        assertTrue(text.contains("01.09.2026 – 15.09.2026"));
        assertTrue(text.contains("групиране по месеци"));
        assertTrue(text.contains("Няма публикувани или приключили събития"));
        assertTrue(text.contains("Няма изчислена сума за периода"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void longEventListContinuesOnNumberedPages() throws Exception {
        for (int i = 1; i <= 90; i++) {
            saveEvent("Събитие " + i, LocalDateTime.of(2026, 9, 1, 8, 0).plusHours(i), "Курс", 5, EventStatus.PUBLISHED);
        }

        MvcResult result = mockMvc.perform(get("/reports/pdf").param("from", "2026-09-01").param("to", "2026-09-15"))
                .andExpect(status().isOk()).andReturn();

        try (PDDocument pdf = Loader.loadPDF(result.getResponse().getContentAsByteArray())) {
            assertTrue(pdf.getNumberOfPages() > 1);
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Страница " + pdf.getNumberOfPages()));
            assertTrue(text.contains("Събитие 90"));
        }
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void invalidFiltersSendTheUserBackToTheReportPageWithTheError() throws Exception {
        mockMvc.perform(get("/reports/pdf").param("from", "2026-09-15").param("to", "2026-09-01").param("download", "true"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/reports?from=2026-09-15&to=2026-09-01"));
        mockMvc.perform(get("/reports/pdf").param("from", "not-a-date").param("to", "2026-09-15"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/reports?from=not-a-date&to=2026-09-15"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void reportPageLinksToPreviewAndDownloadForTheSelectedPeriod() throws Exception {
        mockMvc.perform(get("/reports").param("from", "2026-08-10").param("to", "2026-09-10").param("groupBy", "status"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "href=\"/reports/pdf?from=2026-08-10&amp;to=2026-09-10&amp;groupBy=status&amp;download=true\"")))
                .andExpect(content().string(containsString(
                        "href=\"/reports/pdf?from=2026-08-10&amp;to=2026-09-10&amp;groupBy=status\"")));
    }

    @Test
    void anonymousMustLogIn() throws Exception {
        mockMvc.perform(get("/reports/pdf")).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentsCannotDownload() throws Exception {
        mockMvc.perform(get("/reports/pdf")).andExpect(status().isForbidden());
    }

    private static String pdfText(byte[] bytes) throws Exception {
        assertEquals("%PDF", new String(bytes, 0, 4));
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(pdf);
        }
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
        User user = userRepository.save(new User("pdf-participant" + (++participantNumber) + "@example.com",
                "test-hash", "Participant", Role.STUDENT));
        return registrationRepository.save(new Registration(user, event, status));
    }
}
