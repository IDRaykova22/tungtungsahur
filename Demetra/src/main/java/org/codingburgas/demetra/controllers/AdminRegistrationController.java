package org.codingburgas.demetra.controllers;

import org.codingburgas.demetra.models.User;
import org.codingburgas.demetra.models.stats.RegistrationPeriod;
import org.codingburgas.demetra.services.RegistrationStatsService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** New registrations dashboard; /admin/** is restricted to ADMIN in SecurityConfig. */
@Controller
public class AdminRegistrationController {
    static final int PAGE_SIZE = 12;

    private final RegistrationStatsService statsService;

    public AdminRegistrationController(RegistrationStatsService statsService) {
        this.statsService = statsService;
    }

    /** Text parameters so that a malformed page number falls back to page 1 instead of an error page. */
    @GetMapping("/admin/registrations")
    public String dashboard(@RequestParam(required = false) String period,
                            @RequestParam(required = false) String page, Model model) {
        RegistrationPeriod selected = RegistrationPeriod.fromKey(period);
        int requested = parsePage(page);
        Page<User> users = statsService.newUsers(selected, requested, PAGE_SIZE);
        if (users.getTotalPages() > 0 && requested > users.getTotalPages()) {
            users = statsService.newUsers(selected, users.getTotalPages(), PAGE_SIZE);
        }
        model.addAttribute("dashboard", statsService.dashboard(selected));
        model.addAttribute("users", users);
        model.addAttribute("pageItems", Pagination.pageItems(users.getNumber() + 1, users.getTotalPages()));
        model.addAttribute("periods", RegistrationPeriod.values());
        return "admin/registrations";
    }

    private static int parsePage(String page) {
        try {
            return Math.max(1, Integer.parseInt(page == null ? "1" : page.trim()));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }
}
