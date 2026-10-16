package org.codingburgas.demetra.models;

public enum Role {
    STUDENT("Студент"),
    ORGANIZER("Организатор"),
    /** Manages user accounts; can only be assigned by another administrator. */
    ADMIN("Администратор");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
