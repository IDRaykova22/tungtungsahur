package org.codingburgas.demetra.controllers;

import org.codingburgas.demetra.controllers.forms.EventForm;
import org.codingburgas.demetra.models.Event;
import org.codingburgas.demetra.models.Registration;
import org.codingburgas.demetra.models.User;
import org.codingburgas.demetra.services.AppUserPrincipal;
import org.codingburgas.demetra.services.EventService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Controller
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/events")
    public String list(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                       @RequestParam(defaultValue = "0") int week,
                       Model model, @AuthenticationPrincipal AppUserPrincipal principal) {
        List<Event> events;
        try {
            events = eventService.filterPublishedEvents(month, week);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        Map<Long, Long> confirmedCounts = events.stream()
                .collect(java.util.stream.Collectors.toMap(Event::getId, eventService::confirmedCount));
        model.addAttribute("events", events);
        model.addAttribute("confirmedCounts", confirmedCounts);
        model.addAttribute("month", month);
        model.addAttribute("week", week);

        if (principal != null) {
            User user = principal.getUser();
            Map<Long, String> myStatuses = eventService.myRegistrations(user).stream()
                    .filter(r -> r.getStatus() != org.codingburgas.demetra.models.RegistrationStatus.CANCELLED)
                    .collect(java.util.stream.Collectors.toMap(r -> r.getEvent().getId(), r -> r.getStatus().name()));
            model.addAttribute("myStatuses", myStatuses);
        }

        return "events/list";
    }

    @GetMapping("/events/new")
    public String newEventForm(Model model) {
        if (!model.containsAttribute("eventForm")) {
            model.addAttribute("eventForm", new EventForm());
        }
        return "events/form";
    }

    @PostMapping("/events/new")
    public String createEvent(@ModelAttribute("eventForm") EventForm form,
                               Model model,
                               @AuthenticationPrincipal AppUserPrincipal principal) {
        String error = validate(form);
        LocalDateTime startsAt = null;
        if (error == null && form.getStartsAt() != null && !form.getStartsAt().isBlank()) {
            try {
                startsAt = LocalDateTime.parse(form.getStartsAt());
            } catch (DateTimeParseException ex) {
                error = "Event date is invalid.";
            }
        }

        if (error != null) {
            model.addAttribute("error", error);
            return "events/form";
        }

        Event event = new Event();
        event.setTitle(form.getTitle().trim());
        event.setDescription(form.getDescription() == null ? null : form.getDescription().trim());
        event.setCategory(form.getCategory() == null ? null : form.getCategory().trim());
        event.setLocation(form.getLocation() == null ? null : form.getLocation().trim());
        event.setCapacity(form.getCapacity());
        event.setPrice(form.getPrice() == null ? 0.0 : form.getPrice());
        event.setStartsAt(startsAt);

        Event saved = eventService.createEvent(event, principal.getUser());
        return "redirect:/events/" + saved.getId();
    }

    @GetMapping("/events/{id}")
    public String view(@PathVariable Long id, Model model, @AuthenticationPrincipal AppUserPrincipal principal) {
        Event event = eventService.findById(id).orElse(null);
        if (event == null) {
            return "redirect:/events";
        }

        model.addAttribute("event", event);
        model.addAttribute("confirmedCount", eventService.confirmedCount(event));

        if (principal != null) {
            eventService.findRegistration(principal.getUser(), event).ifPresent(reg -> model.addAttribute("registration", reg));
        }

        return "events/view";
    }

    @PostMapping("/events/{id}/register")
    public String registerForEvent(@PathVariable Long id,
                                    @AuthenticationPrincipal AppUserPrincipal principal,
                                    RedirectAttributes redirectAttributes) {
        Event event = eventService.findById(id).orElse(null);
        if (event == null) {
            return "redirect:/events";
        }

        try {
            Registration registration = eventService.register(principal.getUser(), event);
            redirectAttributes.addFlashAttribute("message",
                    registration.getStatus() == org.codingburgas.demetra.models.RegistrationStatus.CONFIRMED
                            ? "You're registered for this event."
                            : "This event is full — you've been added to the waitlist.");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/events/" + id;
    }

    @PostMapping("/events/{id}/cancel")
    public String cancelRegistration(@PathVariable Long id,
                                      @AuthenticationPrincipal AppUserPrincipal principal,
                                      RedirectAttributes redirectAttributes) {
        Event event = eventService.findById(id).orElse(null);
        if (event == null) {
            return "redirect:/events";
        }

        try {
            eventService.cancelRegistration(principal.getUser(), event);
            redirectAttributes.addFlashAttribute("message", "Your registration was cancelled.");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/events/" + id;
    }

    private String validate(EventForm form) {
        String title = form.getTitle() == null ? "" : form.getTitle().trim();
        String description = form.getDescription() == null ? "" : form.getDescription().trim();
        String category = form.getCategory() == null ? "" : form.getCategory().trim();
        String location = form.getLocation() == null ? "" : form.getLocation().trim();

        if (title.length() < 2 || title.length() > 160) {
            return "Event title must be between 2 and 160 characters.";
        }
        if (description.length() > 5000) {
            return "Description must be 5000 characters or less.";
        }
        if (category.length() > 80) {
            return "Category must be 80 characters or less.";
        }
        if (location.length() > 180) {
            return "Location must be 180 characters or less.";
        }
        if (form.getCapacity() == null || form.getCapacity() < 1 || form.getCapacity() > 100000) {
            return "Capacity must be between 1 and 100000.";
        }
        if (form.getPrice() == null || form.getPrice() < 0 || form.getPrice() > 100000) {
            return "Event price must be between 0 and 100000.";
        }
        return null;
    }
}
