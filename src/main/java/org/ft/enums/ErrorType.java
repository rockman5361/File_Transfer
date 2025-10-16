package org.ft.enums;

public enum ErrorType {
    DUPLICATE_FILE("Duplicate file"),
    WRONG_FILE_TYPE("Wrong file type"),
    EXTRACTION_ERROR("Extraction error");

    private final String description;

    ErrorType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
