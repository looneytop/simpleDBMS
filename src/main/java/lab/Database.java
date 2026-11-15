package lab;

import java.io.Serializable;
import java.util.*;

/** Снимок всей базы: схемы, строки и содержимое индексов. */
public final class Database implements Serializable {
    private static final long serialVersionUID = 1L;
    final Map<String, Table> tables = new LinkedHashMap<>();

    /** Независимая копия нужна для отклонения всего запроса при любой ошибке. */
    Database copy() {
        Database copy = new Database();
        for (Table table : tables.values()) {
            copy.tables.put(table.name, new Table(table));
        }
        return copy;
    }

    Table table(String name) {
        Table table = tables.get(name);
        if (table == null) {
            throw new DbException("Таблица не найдена: " + name);
        }
        return table;
    }

    /** Описание столбца и необязательной ссылки на первичный ключ. */
    public record Column(String name, DataType type, boolean primary, String parentTable,
                         String parentColumn) implements Serializable { }

    /** Строки доступны по первичному ключу без полного просмотра таблицы. */
    public static final class Table implements Serializable {
        private static final long serialVersionUID = 1L;
        final String name;
        final List<Column> columns;
        final int primary;
        final LinkedHashMap<Object, List<Object>> rows = new LinkedHashMap<>();
        final Map<String, Index> indexes = new LinkedHashMap<>();

        Table(String name, List<Column> columns) {
            this.name = name;
            this.columns = List.copyOf(columns);
            int position = -1;
            for (int i = 0; i < columns.size(); i++) {
                if (columns.get(i).primary()) {
                    if (position >= 0) {
                        throw new DbException("Допустим один первичный ключ");
                    }
                    position = i;
                }
                for (int j = 0; j < i; j++) {
                    if (columns.get(j).name().equals(columns.get(i).name())) {
                        throw new DbException("Повтор столбца");
                    }
                }
            }
            if (position < 0) {
                throw new DbException("Нужен первичный ключ");
            }
            primary = position;
        }

        Table(Table source) {
            name = source.name;
            columns = source.columns;
            primary = source.primary;
            
            rows.putAll(source.rows);
            for (Index index : source.indexes.values()) {
                indexes.put(index.name, new Index(index));
            }
        }

        int column(String name) {
            for (int i = 0; i < columns.size(); i++) {
                if (columns.get(i).name().equals(name)) {
                    return i;
                }
            }
            throw new DbException("Столбец не найден: " + name);
        }
    }
}
