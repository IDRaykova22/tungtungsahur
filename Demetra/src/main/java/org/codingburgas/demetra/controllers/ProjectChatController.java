package org.codingburgas.demetra.controllers;

import org.codingburgas.demetra.models.Event;
import org.codingburgas.demetra.models.ProjectQuestion;
import org.codingburgas.demetra.models.Role;
import org.codingburgas.demetra.services.AppUserPrincipal;
import org.codingburgas.demetra.services.EventService;
import org.codingburgas.demetra.services.ProjectQuestionService;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProjectChatController {
    private final EventService eventService;
    private final ProjectQuestionService questionService;

    public ProjectChatController(EventService eventService, ProjectQuestionService questionService) {
        this.eventService = eventService;
        this.questionService = questionService;
    }

    @GetMapping("/chat")
    public String inbox(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        Role role = principal.getUser().getRole();
        model.addAttribute("questions", questionService.inboxFor(principal.getUser()));
        model.addAttribute("isResponder", role == Role.MANAGER || role == Role.ADMIN || role == Role.ORGANIZER);
        model.addAttribute("isOrganizer", role == Role.ORGANIZER);
        model.addAttribute("isManager", role == Role.MANAGER || role == Role.ADMIN);
        return "chat/inbox";
    }

    @GetMapping("/events/{id}/chat")
    public String chat(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        Event event = eventService.findById(id).orElse(null);
        if (event == null) return "redirect:/events";
        model.addAttribute("event", event);
        model.addAttribute("questions", questionService.forChat(event, principal.getUser()));
        model.addAttribute("pendingQuestions", questionService.pending());
        model.addAttribute("pendingForEvent", questionService.pending().stream()
                .filter(question -> question.getEvent().getId().equals(event.getId())).toList());
        model.addAttribute("isResponder", questionService.canAnswer(event, principal.getUser()));
        return "events/chat";
    }

    @PostMapping("/events/{id}/chat/questions")
    public String ask(@PathVariable Long id, @RequestParam String question,
                      @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirectAttributes) {
        Event event = eventService.findById(id).orElse(null);
        if (event == null) return "redirect:/events";
        try {
            questionService.ask(event, principal.getUser(), question);
            redirectAttributes.addFlashAttribute("message", "The question was sent to the event team.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/events/" + id + "/chat";
    }

    @PostMapping("/events/chat/questions/{questionId}/answer")
    public String answer(@PathVariable Long questionId, @RequestParam String answer,
                         @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirectAttributes) {
        try {
            ProjectQuestion question = questionService.answer(questionId, principal.getUser(), answer);
            redirectAttributes.addFlashAttribute("message", "The reply was posted in the chat.");
            return "redirect:/events/" + question.getEvent().getId() + "/chat";
        } catch (IllegalArgumentException | SecurityException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/events";
        }
    }
}
