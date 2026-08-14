package io.github.heruoji.barreltodo.repository;

import io.github.heruoji.barrelkv.core.BarrelKv;
import io.github.heruoji.barreltodo.model.Priority;
import io.github.heruoji.barreltodo.model.Todo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TodoRepositoryTest {

    @Test
    void add_firstItem_getsIdOne(@TempDir Path tempDir) {
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            TodoRepository repository = new TodoRepository(db);
            Todo todo = repository.add("first", null, null, Priority.MEDIUM);
            assertEquals(1, todo.id());
        }
    }

    @Test
    void add_secondItem_idIsMaxOfExistingPlusOne(@TempDir Path tempDir) {
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            TodoRepository repository = new TodoRepository(db);
            repository.add("first", null, null, Priority.MEDIUM);
            Todo second = repository.add("second", null, null, Priority.MEDIUM);
            assertEquals(2, second.id());
        }
    }

    @Test
    void add_afterAllItemsDeleted_reusesIdStartingAtOne(@TempDir Path tempDir) {
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            TodoRepository repository = new TodoRepository(db);
            Todo first = repository.add("first", null, null, Priority.MEDIUM);
            repository.delete(first.id());
            Todo again = repository.add("again", null, null, Priority.MEDIUM);
            assertEquals(1, again.id());
        }
    }

    @Test
    void find_existingId_returnsTodo(@TempDir Path tempDir) {
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            TodoRepository repository = new TodoRepository(db);
            Todo added = repository.add("title", "desc", LocalDate.of(2026, 1, 1), Priority.HIGH);
            assertEquals(Optional.of(added), repository.find(added.id()));
        }
    }

    @Test
    void find_missingId_returnsEmpty(@TempDir Path tempDir) {
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            TodoRepository repository = new TodoRepository(db);
            assertEquals(Optional.empty(), repository.find(999));
        }
    }

    @Test
    void listAll_returnsAllAddedTodos_sortedByPriorityThenId(@TempDir Path tempDir) {
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            TodoRepository repository = new TodoRepository(db);
            Todo low = repository.add("low", null, null, Priority.LOW);
            Todo highA = repository.add("highA", null, null, Priority.HIGH);
            Todo medium = repository.add("medium", null, null, Priority.MEDIUM);
            Todo highB = repository.add("highB", null, null, Priority.HIGH);

            List<Todo> all = repository.listAll();

            assertEquals(List.of(highA, highB, medium, low), all);
        }
    }

    @Test
    void listAll_ignoresNonTodoPrefixedKeysInSameStore(@TempDir Path tempDir) {
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            db.put("other:1", "x");
            TodoRepository repository = new TodoRepository(db);
            repository.add("title", null, null, Priority.MEDIUM);

            assertEquals(1, repository.listAll().size());
        }
    }

    @Test
    void setDone_true_updatesDoneAndBumpsUpdatedAt(@TempDir Path tempDir) {
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            TodoRepository repository = new TodoRepository(db);
            Todo added = repository.add("title", null, null, Priority.MEDIUM);

            Optional<Todo> updated = repository.setDone(added.id(), true);

            assertTrue(updated.isPresent());
            assertTrue(updated.get().done());
            assertTrue(updated.get().updatedAt() >= added.updatedAt());
        }
    }

    @Test
    void setDone_onMissingId_returnsEmpty(@TempDir Path tempDir) {
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            TodoRepository repository = new TodoRepository(db);
            assertEquals(Optional.empty(), repository.setDone(999, true));
        }
    }

    @Test
    void delete_existingId_removesIt_subsequentFindIsEmpty(@TempDir Path tempDir) {
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            TodoRepository repository = new TodoRepository(db);
            Todo added = repository.add("title", null, null, Priority.MEDIUM);

            assertTrue(repository.delete(added.id()));
            assertEquals(Optional.empty(), repository.find(added.id()));
        }
    }

    @Test
    void delete_missingId_returnsFalse(@TempDir Path tempDir) {
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            TodoRepository repository = new TodoRepository(db);
            assertFalse(repository.delete(999));
        }
    }

    @Test
    void persistsAcrossReopen_addThenReopenNewBarrelKvAndRepository_stillListable(@TempDir Path tempDir) {
        Todo added;
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            added = new TodoRepository(db).add("title", "desc", LocalDate.of(2026, 1, 1), Priority.HIGH);
        }
        try (BarrelKv db = new BarrelKv(tempDir, Long.MAX_VALUE)) {
            TodoRepository reopened = new TodoRepository(db);
            assertEquals(Optional.of(added), reopened.find(added.id()));
            assertEquals(1, reopened.listAll().size());
        }
    }
}
