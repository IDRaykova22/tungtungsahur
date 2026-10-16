package org.codingburgas.demetra.controllers.forms;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NewUserForm {
    private String name = "";
    private String email = "";
    private String phone = "";
    private String password = "";
    private String role = "STUDENT";
}
