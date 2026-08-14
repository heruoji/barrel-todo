package io.github.heruoji.barreltodo.exception;

public class BarrelTodoException extends RuntimeException {

    public BarrelTodoException(String message) {
        super(message);
    }

    public BarrelTodoException(String message, Throwable cause) {
        super(message, cause);
    }
}
