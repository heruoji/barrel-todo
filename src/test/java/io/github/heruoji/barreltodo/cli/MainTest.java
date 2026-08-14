package io.github.heruoji.barreltodo.cli;

import io.github.heruoji.barrelkv.core.BarrelKv;
import io.github.heruoji.barreltodo.repository.TodoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @Test
    void handle_addWithTitleOnly_printsOkAndTodoIsFindable(@TempDir Path tempDir) {
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            TodoRepository repository = new TodoRepository(db);
            String output = captureStdout(() -> Main.handle(repository, "add Buy_milk"));
            assertTrue(output.contains("OK (id=1)"));
            assertTrue(repository.find(1).isPresent());
        }
    }

    @Test
    void handle_addMissingTitle_printsUsage(@TempDir Path tempDir) {
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            TodoRepository repository = new TodoRepository(db);
            String output = captureStdout(() -> Main.handle(repository, "add"));
            assertTrue(output.contains("usage: add"));
        }
    }

    @Test
    void handle_addWithInvalidDueDate_printsErrorMessage(@TempDir Path tempDir) {
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            TodoRepository repository = new TodoRepository(db);
            String output = captureStdout(() -> Main.handle(repository, "add title - not-a-date"));
            assertTrue(output.contains("invalid dueDate"));
        }
    }

    @Test
    void handle_addWithInvalidPriority_printsErrorMessage(@TempDir Path tempDir) {
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            TodoRepository repository = new TodoRepository(db);
            String output = captureStdout(() -> Main.handle(repository, "add title - - URGENT"));
            assertTrue(output.contains("invalid priority"));
        }
    }

    @Test
    void handle_list_printsEachTodoWithDoneMarker(@TempDir Path tempDir) {
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            TodoRepository repository = new TodoRepository(db);
            captureStdout(() -> Main.handle(repository, "add first - - HIGH"));
            String output = captureStdout(() -> Main.handle(repository, "list"));
            assertTrue(output.contains("[ ] 1 [HIGH] first"));
        }
    }

    @Test
    void handle_doneOnExistingId_printsOkAndTodoBecomesDone(@TempDir Path tempDir) {
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            TodoRepository repository = new TodoRepository(db);
            captureStdout(() -> Main.handle(repository, "add first"));
            String output = captureStdout(() -> Main.handle(repository, "done 1"));
            assertTrue(output.contains("OK"));
            assertTrue(repository.find(1).orElseThrow().done());
        }
    }

    @Test
    void handle_doneOnMissingId_printsNotFound(@TempDir Path tempDir) {
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            TodoRepository repository = new TodoRepository(db);
            String output = captureStdout(() -> Main.handle(repository, "done 999"));
            assertTrue(output.contains("(not found)"));
        }
    }

    @Test
    void handle_deleteExistingId_printsOk_subsequentShowPrintsNotFound(@TempDir Path tempDir) {
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            TodoRepository repository = new TodoRepository(db);
            captureStdout(() -> Main.handle(repository, "add first"));
            String deleteOutput = captureStdout(() -> Main.handle(repository, "delete 1"));
            String showOutput = captureStdout(() -> Main.handle(repository, "show 1"));
            assertTrue(deleteOutput.contains("OK"));
            assertTrue(showOutput.contains("(not found)"));
        }
    }

    @Test
    void handle_addWithQuotedTitleAndDescription_allowsSpaces(@TempDir Path tempDir) {
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            TodoRepository repository = new TodoRepository(db);
            String output = captureStdout(() -> Main.handle(repository, "add \"Buy milk\" \"2% or whole\" - HIGH"));
            assertTrue(output.contains("OK (id=1)"));
            assertTrue(repository.find(1).orElseThrow().title().equals("Buy milk"));
            assertTrue(repository.find(1).orElseThrow().description().equals("2% or whole"));
        }
    }

    @Test
    void handle_addWithEscapedQuoteInsideQuotedArg_unescapesIt(@TempDir Path tempDir) {
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            TodoRepository repository = new TodoRepository(db);
            captureStdout(() -> Main.handle(repository, "add \"Say \\\"hi\\\"\""));
            assertTrue(repository.find(1).orElseThrow().title().equals("Say \"hi\""));
        }
    }

    @Test
    void handle_addWithUnterminatedQuote_printsError(@TempDir Path tempDir) {
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            TodoRepository repository = new TodoRepository(db);
            String output = captureStdout(() -> Main.handle(repository, "add \"Buy milk"));
            assertTrue(output.contains("unterminated quote"));
        }
    }

    @Test
    void handle_unknownCommand_printsUnknownCommandMessage(@TempDir Path tempDir) {
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            TodoRepository repository = new TodoRepository(db);
            String output = captureStdout(() -> Main.handle(repository, "frobnicate"));
            assertTrue(output.contains("unknown command"));
        }
    }

    private static String captureStdout(Runnable action) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            action.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
