package org.codingburgas.demetra.controllers;

import org.codingburgas.demetra.controllers.forms.RegisterForm;
import org.codingburgas.demetra.models.Role;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.codingburgas.demetra.services.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.regex.Pattern;

@Controller
public class AuthController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String loginForm() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        if (!model.containsAttribute("registerForm")) {
            model.addAttribute("registerForm", new RegisterForm());
        }
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute("registerForm") RegisterForm form, Model model) {
        String error = validate(form);
        if (error != null) {
            model.addAttribute("error", error);
            return "auth/register";
        }

        Role role = "ORGANIZER".equalsIgnoreCase(form.getRole()) ? Role.ORGANIZER : Role.STUDENT;

        try {
            userService.register(form.getName(), form.getEmail(), form.getPassword(), role);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            return "auth/register";
        }

        return "redirect:/login?registered";
    }

    private String validate(RegisterForm form) {
        String name = form.getName() == null ? "" : form.getName().trim();
        String email = form.getEmail() == null ? "" : form.getEmail().trim().toLowerCase();
        String password = form.getPassword() == null ? "" : form.getPassword();

        if (name.length() < 2 || name.length() > 80) {
            return "Name must be between 2 and 80 characters.";
        }
        if (!EMAIL_PATTERN.matcher(email).matches() || email.length() > 254) {
            return "Enter a valid email address.";
        }
        if (password.length() < 8 || password.length() > 128) {
            return "Password must be between 8 and 128 characters.";
        }
        if (!password.chars().anyMatch(Character::isLowerCase)) {
            return "Password must include at least one lowercase letter.";
        }
        if (!password.chars().anyMatch(Character::isUpperCase)) {
            return "Password must include at least one uppercase letter.";
        }
        return null;
    }
}
