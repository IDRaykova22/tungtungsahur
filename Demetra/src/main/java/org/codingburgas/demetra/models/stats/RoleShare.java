package org.codingburgas.demetra.models.stats;

import org.codingburgas.demetra.models.Role;

public record RoleShare(Role role, long count, int percent) {

    public String getCssClass() {
        return role.name().toLowerCase();
    }
}
