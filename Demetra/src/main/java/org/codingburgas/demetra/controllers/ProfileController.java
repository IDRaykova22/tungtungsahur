package org.codingburgas.demetra.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.codingburgas.demetra.controllers.forms.ProfileForm;
import org.codingburgas.demetra.models.User;
import org.codingburgas.demetra.services.AppUserPrincipal;
import org.codingburgas.demetra.services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Every signed-in user edits their own name, email and phone here. */
@Controller
public class ProfileController {

    private final UserService userService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public String form(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        if (!model.containsAttribute("profileForm")) {
            model.addAttribute("profileForm", ProfileForm.of(user));
        }
        model.addAttribute("user", user);
        return "users/profile";
    }

    @PostMapping("/profile")
    public String update(@ModelAttribute("profileForm") ProfileForm form, Authentication authentication, Model model,
                         RedirectAttributes redirect, HttpServletRequest request, HttpServletResponse response) {
        User user = currentUser(authentication);
        User updated;
        try {
            updated = userService.updateProfile(user.getId(), form.getName(), form.getEmail(), form.getPhone(),
                    authentication.getName());
        } catch (IllegalArgumentException ex) {
            model.addAttribute("user", user);
            model.addAttribute("error", ex.getMessage());
            return "users/profile";
        }
        refreshAuthentication(updated, authentication, request, response);
        redirect.addFlashAttribute("message", "Профилът е обновен.");
        return "redirect:/profile";
    }

    private User currentUser(Authentication authentication) {
        return userService.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** The session still holds the old name and email; replace it so the change shows at once and sign-in keeps working. */
    private void refreshAuthentication(User user, Authentication current, HttpServletRequest request,
                                       HttpServletResponse response) {
        AppUserPrincipal principal = new AppUserPrincipal(user);
        UsernamePasswordAuthenticationToken token =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        token.setDetails(current.getDetails());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(token);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
