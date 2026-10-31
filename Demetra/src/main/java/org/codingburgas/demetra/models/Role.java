package org.codingburgas.demetra.models;

public enum Role {
    STUDENT("Student"),
    ORGANIZER("Organizer"),
    MANAGER("Manager"),
    /** Manages user accounts; can only be assigned by another administrator. */
    ADMIN("Administrator");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
