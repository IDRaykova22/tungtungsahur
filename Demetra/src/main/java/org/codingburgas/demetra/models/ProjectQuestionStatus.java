package org.codingburgas.demetra.models;

public enum ProjectQuestionStatus {
    PENDING("Waiting for reply"),
    ANSWERED("Answered");

    private final String label;

    ProjectQuestionStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
