package org.codingburgas.demetra.controllers.forms;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterForm {
    private String name = "";
    private String email = "";
    private String password = "";
    private String role = "STUDENT";
}
