package org.codingburgas.demetra.controllers;

import org.codingburgas.demetra.controllers.forms.NewUserForm;
import org.codingburgas.demetra.controllers.forms.ProfileForm;
import org.codingburgas.demetra.controllers.forms.UserSearchForm;
import org.codingburgas.demetra.models.Role;
import org.codingburgas.demetra.models.User;
import org.codingburgas.demetra.services.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;

/** User administration; the whole /admin area is restricted to ADMIN in SecurityConfig. */
@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    /** Search, filter, sort and page; malformed numbers are ignored via BindingResult and normalized to defaults. */
    @GetMapping
    public String list(@ModelAttribute("search") UserSearchForm search, BindingResult ignoredBindingErrors,
                       Authentication authentication, Model model) {
        search.normalize();
        Role roleFilter = parseRole(search.getRole());
        search.setRole(roleFilter == null ? "" : roleFilter.name());

        Page<User> users = search(search, roleFilter);
        if (users.getTotalPages() > 0 && search.getPage() > users.getTotalPages()) {
            // A page past the end (e.g. after a narrower search) shows the last page instead of an empty table.
            search.setPage(users.getTotalPages());
            users = search(search, roleFilter);
        }

        model.addAttribute("users", users);
        model.addAttribute("pageItems", Pagination.pageItems(users.getNumber() + 1, users.getTotalPages()));
        model.addAttribute("pageSizes", UserSearchForm.PAGE_SIZES);
        model.addAttribute("roles", Role.values());
        model.addAttribute("currentEmail", authentication.getName());
        return "admin/users/list";
    }

    private Page<User> search(UserSearchForm search, Role role) {
        return userService.searchForAdministration(search.getQ(), search.getStatus(), role,
                PageRequest.of(search.getPage() - 1, search.getSize(), search.toSort()));
    }

    @GetMapping("/new")
    public String newUser(Model model) {
        model.addAttribute("userForm", new NewUserForm());
        model.addAttribute("roles", Role.values());
        return "admin/users/new";
    }

    @PostMapping
    public String create(@ModelAttribute("userForm") NewUserForm form, Model model, RedirectAttributes redirect) {
        try {
            User user = userService.createUser(form.getName(), form.getEmail(), form.getPhone(), form.getPassword(),
                    parseRole(form.getRole()));
            redirect.addFlashAttribute("message", "User \"" + user.getName() + "\" was created.");
            return "redirect:/admin/users/" + user.getId();
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("roles", Role.values());
            return "admin/users/new";
        }
    }

    @GetMapping("/{id}")
    public String edit(@PathVariable Long id, Authentication authentication, Model model) {
        User user = load(id);
        if (isSelf(user, authentication)) {
            // Own name/email/phone go through the profile page; own role and status cannot be changed.
            return "redirect:/profile";
        }
        if (!model.containsAttribute("profileForm")) {
            model.addAttribute("profileForm", ProfileForm.of(user));
        }
        model.addAttribute("user", user);
        model.addAttribute("roles", Role.values());
        return "admin/users/edit";
    }

    @PostMapping("/{id}/profile")
    public String updateProfile(@PathVariable Long id, @ModelAttribute("profileForm") ProfileForm form,
                                Authentication authentication, Model model, RedirectAttributes redirect) {
        User user = load(id);
        try {
            userService.updateProfile(id, form.getName(), form.getEmail(), form.getPhone(), authentication.getName());
        } catch (IllegalArgumentException ex) {
            model.addAttribute("user", user);
            model.addAttribute("roles", Role.values());
            model.addAttribute("error", ex.getMessage());
            return "admin/users/edit";
        }
        redirect.addFlashAttribute("message", "The profile was updated.");
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/{id}/role")
    public String changeRole(@PathVariable Long id, @RequestParam String role, Authentication authentication,
                             RedirectAttributes redirect) {
        return act(id, redirect, "The role was changed.",
                () -> userService.changeRole(id, parseRole(role), authentication.getName()));
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
        return act(id, redirect, "The account was deactivated. The user can no longer sign in.",
                () -> userService.deactivate(id, authentication.getName()));
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes redirect) {
        return act(id, redirect, "The account was reactivated.", () -> userService.activate(id));
    }

    private String act(Long id, RedirectAttributes redirect, String success, Runnable action) {
        load(id);
        try {
            action.run();
            redirect.addFlashAttribute("message", success);
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/users/" + id;
    }

    private User load(Long id) {
        return userService.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static boolean isSelf(User user, Authentication authentication) {
        return user.getEmail().equals(authentication.getName());
    }

    /** Unknown or blank values become null, which the service rejects with a readable message. */
    private static Role parseRole(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Role.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
