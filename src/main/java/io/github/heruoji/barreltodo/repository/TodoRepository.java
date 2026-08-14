package io.github.heruoji.barreltodo.repository;

import io.github.heruoji.barrelkv.core.BarrelKv;
import io.github.heruoji.barreltodo.json.TodoJsonSerializer;
import io.github.heruoji.barreltodo.model.Priority;
import io.github.heruoji.barreltodo.model.Todo;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class TodoRepository {

    private static final String KEY_PREFIX = "todo:";

    private final BarrelKv db;

    public TodoRepository(BarrelKv db) {
        this.db = db;
    }

    public Todo add(String title, String description, LocalDate dueDate, Priority priority) {
        long id = nextId();
        long now = System.currentTimeMillis();
        Todo todo = new Todo(id, title, description, false, dueDate, priority, now, now);
        db.put(key(id), TodoJsonSerializer.encode(todo));
        return todo;
    }

    public Optional<Todo> find(long id) {
        return db.get(key(id)).map(TodoJsonSerializer::decode);
    }

    public List<Todo> listAll() {
        return db.keys().stream()
                .filter(k -> k.startsWith(KEY_PREFIX))
                .map(k -> TodoJsonSerializer.decode(db.get(k).orElseThrow()))
                .sorted(Comparator.comparing(Todo::priority).reversed().thenComparing(Todo::id))
                .toList();
    }

    public Optional<Todo> setDone(long id, boolean done) {
        return find(id).map(todo -> {
            Todo updated = new Todo(todo.id(), todo.title(), todo.description(), done, todo.dueDate(),
                    todo.priority(), todo.createdAt(), System.currentTimeMillis());
            db.put(key(id), TodoJsonSerializer.encode(updated));
            return updated;
        });
    }

    public boolean delete(long id) {
        if (find(id).isEmpty()) {
            return false;
        }
        db.delete(key(id));
        return true;
    }

    // 永続カウンタを持たず、"todo:"接頭辞のキーを毎回スキャンしてmax+1を採番する。
    // このため全件削除後はIDが1から再利用される。単一プロセスの対話CLIが前提であり、
    // 並行してadd()が呼ばれる場合の競合制御は範囲外とする。
    private long nextId() {
        return db.keys().stream()
                .filter(k -> k.startsWith(KEY_PREFIX))
                .map(k -> k.substring(KEY_PREFIX.length()))
                .mapToLong(Long::parseLong)
                .max()
                .orElse(0) + 1;
    }

    private static String key(long id) {
        return KEY_PREFIX + id;
    }
}
