package io.github.heruoji.barreltodo.cli;

import io.github.heruoji.barrelkv.core.BarrelKv;
import io.github.heruoji.barreltodo.model.Priority;
import io.github.heruoji.barreltodo.model.Todo;
import io.github.heruoji.barreltodo.repository.TodoRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Set;

public final class Main {

    private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> EXIT_COMMANDS = Set.of("exit", "quit");
    private static final String OMIT = "-";

    public static void main(String[] args) throws IOException {
        Path directory = args.length >= 1 ? Path.of(args[0]) : Path.of("barrel-todo-data");
        long maxFileSizeBytes = args.length >= 2 ? Long.parseLong(args[1]) : DEFAULT_MAX_FILE_SIZE_BYTES;

        BarrelKv db = new BarrelKv(directory, maxFileSizeBytes);
        Runtime.getRuntime().addShutdownHook(new Thread(db::close));
        TodoRepository repository = new TodoRepository(db);

        System.out.println("barrel-todo CLI - data dir: " + directory.toAbsolutePath());
        printHelp();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (EXIT_COMMANDS.contains(trimmed)) {
                    break;
                }
                handle(repository, trimmed);
            }
        }
    }

    static void handle(TodoRepository repository, String line) {
        if (line.isEmpty()) {
            return;
        }
        String[] parts = line.split("\\s+", 5);
        switch (parts[0]) {
            case "add" -> handleAdd(repository, parts);
            case "list" -> handleList(repository);
            case "done" -> handleSetDone(repository, parts, true);
            case "undone" -> handleSetDone(repository, parts, false);
            case "delete" -> handleDelete(repository, parts);
            case "show" -> handleShow(repository, parts);
            case "help" -> printHelp();
            default -> System.out.println("unknown command: " + parts[0] + " (type 'help')");
        }
    }

    private static void handleAdd(TodoRepository repository, String[] parts) {
        if (parts.length < 2) {
            System.out.println("usage: add <title> [description|-] [dueDate|-] [priority|-]");
            return;
        }
        String title = parts[1];
        String description = optionalArg(parts, 2);
        String dueDateText = optionalArg(parts, 3);
        String priorityText = optionalArg(parts, 4);

        LocalDate dueDate = null;
        if (dueDateText != null) {
            try {
                dueDate = LocalDate.parse(dueDateText);
            } catch (DateTimeParseException e) {
                System.out.println("invalid dueDate (expected yyyy-MM-dd): " + dueDateText);
                return;
            }
        }

        Priority priority = Priority.MEDIUM;
        if (priorityText != null) {
            try {
                priority = Priority.valueOf(priorityText.toUpperCase());
            } catch (IllegalArgumentException e) {
                System.out.println("invalid priority (expected LOW|MEDIUM|HIGH): " + priorityText);
                return;
            }
        }

        Todo todo = repository.add(title, description, dueDate, priority);
        System.out.println("OK (id=" + todo.id() + ")");
    }

    private static void handleList(TodoRepository repository) {
        for (Todo todo : repository.listAll()) {
            System.out.println(formatSummary(todo));
        }
    }

    private static void handleSetDone(TodoRepository repository, String[] parts, boolean done) {
        Optional<Long> id = parseId(parts);
        if (id.isEmpty()) {
            System.out.println("usage: " + (done ? "done" : "undone") + " <id>");
            return;
        }
        if (repository.setDone(id.get(), done).isPresent()) {
            System.out.println("OK");
        } else {
            System.out.println("(not found)");
        }
    }

    private static void handleDelete(TodoRepository repository, String[] parts) {
        Optional<Long> id = parseId(parts);
        if (id.isEmpty()) {
            System.out.println("usage: delete <id>");
            return;
        }
        System.out.println(repository.delete(id.get()) ? "OK" : "(not found)");
    }

    private static void handleShow(TodoRepository repository, String[] parts) {
        Optional<Long> id = parseId(parts);
        if (id.isEmpty()) {
            System.out.println("usage: show <id>");
            return;
        }
        Optional<Todo> todo = repository.find(id.get());
        if (todo.isEmpty()) {
            System.out.println("(not found)");
            return;
        }
        Todo t = todo.get();
        System.out.println("id: " + t.id());
        System.out.println("title: " + t.title());
        System.out.println("description: " + (t.description() == null ? "(none)" : t.description()));
        System.out.println("done: " + t.done());
        System.out.println("dueDate: " + (t.dueDate() == null ? "(none)" : t.dueDate()));
        System.out.println("priority: " + t.priority());
        System.out.println("createdAt: " + t.createdAt());
        System.out.println("updatedAt: " + t.updatedAt());
    }

    private static String formatSummary(Todo todo) {
        String marker = todo.done() ? "x" : " ";
        String due = todo.dueDate() == null ? "" : " (due " + todo.dueDate() + ")";
        return "[" + marker + "] " + todo.id() + " [" + todo.priority() + "] " + todo.title() + due;
    }

    private static Optional<Long> parseId(String[] parts) {
        if (parts.length < 2) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(parts[1]));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static String optionalArg(String[] parts, int index) {
        if (parts.length <= index || parts[index].equals(OMIT)) {
            return null;
        }
        return parts[index];
    }

    private static void printHelp() {
        System.out.println("commands: add <title> [description|-] [dueDate|-] [priority|-] | list | done <id> "
                + "| undone <id> | delete <id> | show <id> | help | exit");
    }
}
