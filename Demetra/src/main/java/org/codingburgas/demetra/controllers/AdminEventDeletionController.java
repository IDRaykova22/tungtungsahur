package org.codingburgas.demetra.controllers;

import org.codingburgas.demetra.services.AppUserPrincipal;
import org.codingburgas.demetra.services.EventDeletionRequestService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminEventDeletionController {
    private final EventDeletionRequestService service;

    public AdminEventDeletionController(EventDeletionRequestService service) {
        this.service = service;
    }

    @GetMapping("/admin/event-deletion-requests")
    public String list(Model model) {
        model.addAttribute("requests", service.list());
        return "admin/event-deletion-requests";
    }

    @PostMapping("/admin/event-deletion-requests/{id}/approve")
    public String approve(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal,
                          RedirectAttributes redirect) {
        return review(id, principal, redirect, true);
    }

    @PostMapping("/admin/event-deletion-requests/{id}/reject")
    public String reject(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal,
                         RedirectAttributes redirect) {
        return review(id, principal, redirect, false);
    }

    private String review(Long id, AppUserPrincipal principal, RedirectAttributes redirect, boolean approve) {
        try {
            if (approve) service.approve(id, principal.getUser());
            else service.reject(id, principal.getUser());
            redirect.addFlashAttribute("message", approve ? "The request was approved." : "The request was rejected.");
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/event-deletion-requests";
    }
}
