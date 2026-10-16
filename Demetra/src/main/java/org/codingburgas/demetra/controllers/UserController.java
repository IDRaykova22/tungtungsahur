package org.codingburgas.demetra.controllers;

import org.codingburgas.demetra.services.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public String list(@RequestParam(defaultValue = "name") String sort, Model model) {
        model.addAttribute("users", userService.listRegisteredUsers(sort));
        model.addAttribute("sort", sort);
        return "users/list";
    }
}
