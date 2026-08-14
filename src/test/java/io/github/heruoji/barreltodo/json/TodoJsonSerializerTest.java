package io.github.heruoji.barreltodo.json;

import io.github.heruoji.barreltodo.exception.BarrelTodoException;
import io.github.heruoji.barreltodo.model.Priority;
import io.github.heruoji.barreltodo.model.Todo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TodoJsonSerializerTest {

    @Test
    void encode_thenDecode_allFieldsPresent_roundTrips() {
        Todo todo = new Todo(1, "Buy milk", "2% milk, eggs", false,
                LocalDate.of(2026, 8, 20), Priority.HIGH, 1_000L, 2_000L);

        String json = TodoJsonSerializer.encode(todo);
        Todo decoded = TodoJsonSerializer.decode(json);

        assertEquals(todo, decoded);
    }

    @Test
    void encode_thenDecode_nullDescriptionAndDueDate_roundTrips() {
        Todo todo = new Todo(2, "Clean desk", null, false, null, Priority.LOW, 1_000L, 1_000L);

        String json = TodoJsonSerializer.encode(todo);
        Todo decoded = TodoJsonSerializer.decode(json);

        assertEquals(todo, decoded);
    }

    @Test
    void encode_escapesQuotesAndBackslashesInTitle_decodeRestoresOriginal() {
        Todo todo = new Todo(3, "Say \"hi\" \\ done", null, false, null, Priority.MEDIUM, 1_000L, 1_000L);

        String json = TodoJsonSerializer.encode(todo);
        Todo decoded = TodoJsonSerializer.decode(json);

        assertEquals("Say \"hi\" \\ done", decoded.title());
    }

    @Test
    void encode_escapesNewlineAndTabInDescription_decodeRestoresOriginal() {
        Todo todo = new Todo(4, "Multi-line", "line1\n\tline2\r\n", false, null, Priority.MEDIUM, 1_000L, 1_000L);

        String json = TodoJsonSerializer.encode(todo);
        Todo decoded = TodoJsonSerializer.decode(json);

        assertEquals("line1\n\tline2\r\n", decoded.description());
    }

    @Test
    void encode_producesExpectedFixedShape() {
        Todo todo = new Todo(1, "Buy milk", null, false, LocalDate.of(2026, 8, 20), Priority.HIGH, 1_000L, 2_000L);

        String json = TodoJsonSerializer.encode(todo);

        assertEquals("{\"id\":1,\"title\":\"Buy milk\",\"description\":null,\"done\":false,"
                + "\"dueDate\":\"2026-08-20\",\"priority\":\"HIGH\",\"createdAt\":1000,\"updatedAt\":2000}", json);
    }

    @Test
    void decode_malformedJson_throwsBarrelTodoException() {
        assertThrows(BarrelTodoException.class, () -> TodoJsonSerializer.decode("{\"id\":1,"));
    }

    @Test
    void decode_missingRequiredField_throwsBarrelTodoException() {
        assertThrows(BarrelTodoException.class, () -> TodoJsonSerializer.decode(
                "{\"id\":1,\"description\":null,\"done\":false,\"dueDate\":null,"
                        + "\"priority\":\"LOW\",\"createdAt\":1,\"updatedAt\":1}"));
    }

    @Test
    void decode_invalidPriority_throwsBarrelTodoException() {
        assertThrows(BarrelTodoException.class, () -> TodoJsonSerializer.decode(
                "{\"id\":1,\"title\":\"x\",\"description\":null,\"done\":false,\"dueDate\":null,"
                        + "\"priority\":\"URGENT\",\"createdAt\":1,\"updatedAt\":1}"));
    }
}
