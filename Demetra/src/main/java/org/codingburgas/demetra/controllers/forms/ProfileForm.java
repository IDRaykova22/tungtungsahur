package org.codingburgas.demetra.controllers.forms;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.codingburgas.demetra.models.User;

@Getter
@Setter
@NoArgsConstructor
public class ProfileForm {
    private String name = "";
    private String email = "";
    private String phone = "";

    public static ProfileForm of(User user) {
        ProfileForm form = new ProfileForm();
        form.setName(user.getName());
        form.setEmail(user.getEmail());
        form.setPhone(user.getPhone() == null ? "" : user.getPhone());
        return form;
    }
}
