package io.github.heruoji.barreltodo.model;

import java.time.LocalDate;
import java.util.Objects;

public record Todo(
        long id,
        String title,
        String description, // nullable
        boolean done,
        LocalDate dueDate, // nullable
        Priority priority,
        long createdAt,
        long updatedAt) {

    public Todo {
        Objects.requireNonNull(title, "title must not be null");
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        Objects.requireNonNull(priority, "priority must not be null");
    }
}
