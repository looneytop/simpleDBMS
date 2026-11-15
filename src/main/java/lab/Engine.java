package lab;

import java.util.*;

/** Выполняет команды, проверяет ключи и публикует изменения только после сохранения. */
public final class Engine {
    public enum Search { SCAN, INDEX }
    public record Result(List<String> columns, List<List<Object>> rows, int affected, String message,
                         Search search, int examined) { }
    private Database database;
    private final SnapshotRepository repository;

    public Engine(SnapshotRepository repository) {
        this.repository = repository;
        database = repository.load();
    }

    /** Выполняет одну SQL-команду. Ошибка не меняет опубликованное состояние. */
    public Result execute(String sql) {
        return execute(Parser.parse(sql), true);
    }

    /** Отключение индекса служит для проверки результатов и честного бенчмарка. */
    public Result execute(Parser.Command command, boolean useIndex) {
        if (command instanceof Parser.Select select) {
            return select(select, useIndex);
        }
        Database draft = database.copy();
        int affected = mutate(draft, command);
        validate(draft);
        repository.save(draft);
        database = draft;
        return new Result(List.of(), List.of(), affected, "Команда выполнена", Search.SCAN, 0);
    }

    private int mutate(Database draft, Parser.Command command) {
        if (command instanceof Parser.CreateTable create) {
            if (draft.tables.containsKey(create.table())) {
                throw new DbException("Таблица уже существует");
            }
            draft.tables.put(create.table(), new Database.Table(create.table(), create.columns()));
            return 0;
        }
        if (command instanceof Parser.DropTable drop) {
            draft.table(drop.table());
            for (Database.Table table : draft.tables.values()) {
                for (Database.Column column : table.columns) {
                    if (!table.name.equals(drop.table()) && drop.table().equals(column.parentTable())) {
                        throw new DbException("На таблицу ссылается " + table.name);
                    }
                }
            }
            draft.tables.remove(drop.table());
            return 0;
        }
        if (command instanceof Parser.CreateIndex create) {
            for (Database.Table table : draft.tables.values()) {
                if (table.indexes.containsKey(create.name())) {
                    throw new DbException("Индекс уже существует");
                }
            }
            Database.Table table = draft.table(create.table());
            Index index = new Index(create.name(), table.column(create.column()));
            for (List<Object> row : table.rows.values()) {
                index.add(row, table.primary);
            }
            table.indexes.put(index.name, index);
            return 0;
        }
        if (command instanceof Parser.Insert insert) {
            Database.Table table = draft.table(insert.table());
            List<Integer> positions = new ArrayList<>();
            if (insert.columns().isEmpty()) {
                for (int i = 0; i < table.columns.size(); i++) {
                    positions.add(i);
                }
            } else {
                for (String name : insert.columns()) {
                    int index = table.column(name);
                    if (positions.contains(index)) {
                        throw new DbException("Повтор столбца: " + name);
                    }
                    positions.add(index);
                }
            }
            for (List<Parser.Literal> literals : insert.rows()) {
                if (literals.size() != positions.size()) {
                    throw new DbException("Количество значений не совпадает с количеством столбцов");
                }
                List<Object> row = new ArrayList<>(Collections.nCopies(table.columns.size(), null));
                for (int i = 0; i < positions.size(); i++) {
                    int position = positions.get(i);
                    row.set(position, table.columns.get(position).type().parse(literals.get(i)));
                }
                add(table, row);
            }
            return insert.rows().size();
        }
        String tableName;
        Parser.Where where;
        Map<String, Parser.Literal> assignments = null;
        if (command instanceof Parser.Update update) {
            tableName = update.table();
            where = update.where();
            assignments = update.values();
        } else if (command instanceof Parser.Delete delete) {
            tableName = delete.table();
            where = delete.where();
        } else {
            throw new DbException("Неизвестная команда");
        }
        Database.Table table = draft.table(tableName);
        Condition condition = condition(table, where);
        Map<Integer, Object> values = new LinkedHashMap<>();
        if (assignments != null) {
            for (Map.Entry<String, Parser.Literal> entry : assignments.entrySet()) {
                int position = table.column(entry.getKey());
                values.put(position, table.columns.get(position).type().parse(entry.getValue()));
            }
        }
        List<List<Object>> oldRows = new ArrayList<>();
        List<List<Object>> newRows = new ArrayList<>();
        for (List<Object> row : table.rows.values()) {
            if (!condition.matches(row)) {
                continue;
            }
            oldRows.add(row);
            if (assignments != null) {
                List<Object> changed = new ArrayList<>(row);
                for (Map.Entry<Integer, Object> entry : values.entrySet()) {
                    changed.set(entry.getKey(), entry.getValue());
                }
                if (!Objects.equals(row.get(table.primary), changed.get(table.primary))) {
                    checkReferenced(table, row.get(table.primary));
                }
                newRows.add(changed);
            } else {
                checkReferenced(table, row.get(table.primary));
            }
        }
        for (List<Object> row : oldRows) {
            table.rows.remove(row.get(table.primary));
            for (Index index : table.indexes.values()) {
                index.remove(row, table.primary);
            }
        }
        for (List<Object> row : newRows) {
            add(table, row);
        }
        return oldRows.size();
    }

    private static void add(Database.Table table, List<Object> row) {
        Object key = row.get(table.primary);
        if (key == null) {
            throw new DbException("Первичный ключ не может быть NULL");
        }
        if (table.rows.containsKey(key)) {
            throw new DbException("Повтор первичного ключа: " + key);
        }
        table.rows.put(key, Collections.unmodifiableList(new ArrayList<>(row)));
        for (Index index : table.indexes.values()) {
            index.add(row, table.primary);
        }
    }

    private void checkReferenced(Database.Table parent, Object key) {
        for (Database.Table table : database.tables.values()) {
            for (int i = 0; i < table.columns.size(); i++) {
                if (!parent.name.equals(table.columns.get(i).parentTable())) {
                    continue;
                }
                for (List<Object> row : table.rows.values()) {
                    if (Objects.equals(row.get(i), key)) {
                        throw new DbException("На ключ «" + key + "» ссылается таблица " + table.name);
                    }
                }
            }
        }
    }

    private static void validate(Database database) {
        for (Database.Table table : database.tables.values()) {
            for (int i = 0; i < table.columns.size(); i++) {
                Database.Column column = table.columns.get(i);
                if (column.parentTable() == null) {
                    continue;
                }
                Database.Table parent = database.table(column.parentTable());
                int target = parent.column(column.parentColumn());
                if (target != parent.primary || column.type() != parent.columns.get(target).type()) {
                    throw new DbException("REFERENCES должен указывать на первичный ключ того же типа");
                }
                for (List<Object> row : table.rows.values()) {
                    Object value = row.get(i);
                    if (value != null && !parent.rows.containsKey(value)) {
                        throw new DbException("Внешний ключ " + table.name + "." + column.name() + ": запись «" + value + "» отсутствует");
                    }
                }
            }
        }
    }

    private record Condition(int column, String operator, Object value) {
        boolean matches(List<Object> row) {
            if (column < 0) {
                return true;
            }
            Object left = row.get(column);
            if (left == null || value == null) {
                return false;
            }
            int comparison = DataType.compare(left, value);
            return switch (operator) {
                case "=" -> comparison == 0;
                case "!=", "<>" -> comparison != 0;
                case ">" -> comparison > 0;
                case ">=" -> comparison >= 0;
                case "<" -> comparison < 0;
                case "<=" -> comparison <= 0;
                default -> false;
            };
        }
    }
    private static Condition condition(Database.Table table, Parser.Where where) {
        if (where == null) {
            return new Condition(-1, "", null);
        }
        int position = table.column(where.column());
        DataType type = table.columns.get(position).type();
        if (type == DataType.BOOLEAN && !Set.of("=", "!=", "<>").contains(where.operator())) {
            throw new DbException("BOOLEAN поддерживает только равенство и неравенство");
        }
        return new Condition(position, where.operator(), type.parse(where.value()));
    }
    private Result select(Parser.Select select, boolean useIndex) {
        Database.Table table = database.table(select.table());
        Condition condition = condition(table, select.where());
        List<Integer> positions = new ArrayList<>();
        List<String> headers = new ArrayList<>();
        if (select.columns().isEmpty()) {
            for (int i = 0; i < table.columns.size(); i++) {
                positions.add(i);
            }
        } else {
            for (String name : select.columns()) {
                positions.add(table.column(name));
            }
        }
        for (int position : positions) {
            headers.add(table.columns.get(position).name());
        }
        Index chosen = null;
        if (useIndex && condition.column >= 0 && !Set.of("!=", "<>").contains(condition.operator)) {
            for (Index index : table.indexes.values()) {
                if (index.column == condition.column) {
                    chosen = index;
                    break;
                }
            }
        }
        List<List<Object>> result = new ArrayList<>();
        int examined = 0;
        if (chosen != null) {
            for (Object key : chosen.find(condition.operator, condition.value)) {
                List<Object> row = table.rows.get(key);
                examined++;
                if (condition.matches(row)) {
                    result.add(project(row, positions));
                }
            }
        } else {
            for (List<Object> row : table.rows.values()) {
                examined++;
                if (condition.matches(row)) {
                    result.add(project(row, positions));
                }
            }
        }
        return new Result(headers, result, 0, "Найдено строк: " + result.size(), chosen == null ? Search.SCAN : Search.INDEX, examined);
    }
    private static List<Object> project(List<Object> row, List<Integer> positions) {
        List<Object> result = new ArrayList<>();
        for (int position : positions) {
            result.add(row.get(position));
        }
        return Collections.unmodifiableList(result);
    }
}
