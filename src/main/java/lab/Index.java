package lab;

import java.io.Serializable;
import java.util.*;

public final class Index implements Serializable {
    private static final long serialVersionUID = 1L;
    final String name;
    final int column;
    private final TreeMap<Object, LinkedHashSet<Object>> entries = new TreeMap<>(new ValueComparator());

    private static final class ValueComparator implements Comparator<Object>, Serializable {
        private static final long serialVersionUID = 1L;
        public int compare(Object a, Object b) {
            return DataType.compare(a, b);
        }
    }

    Index(String name, int column) {
        this.name = name;
        this.column = column;
    }

    Index(Index source) {
        this(source.name, source.column);
        for (Map.Entry<Object, LinkedHashSet<Object>> entry : source.entries.entrySet()) {
            entries.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
    }

    void add(List<Object> row, int primary) {
        Object value = row.get(column);
        if (value == null) {
            return;
        }
        LinkedHashSet<Object> keys = entries.get(value);
        if (keys == null) {
            keys = new LinkedHashSet<>();
            entries.put(value, keys);
        }
        keys.add(row.get(primary));
    }

    void remove(List<Object> row, int primary) {
        Object value = row.get(column);
        if (value == null) {
            return;
        }
        Set<Object> keys = entries.get(value);
        keys.remove(row.get(primary));
        if (keys.isEmpty()) {
            entries.remove(value);
        }
    }

    Set<Object> find(String operator, Object value) {
        Set<Object> result = new LinkedHashSet<>();
        if (value == null) {
            return result;
        }
        if (operator.equals("=")) {
            Set<Object> keys = entries.get(value);
            if (keys != null) {
                result.addAll(keys);
            }
            return result;
        }
        Map<Object, LinkedHashSet<Object>> range = switch (operator) {
            case ">" -> entries.tailMap(value, false);
            case ">=" -> entries.tailMap(value, true);
            case "<" -> entries.headMap(value, false);
            case "<=" -> entries.headMap(value, true);
            default -> throw new IllegalArgumentException(operator);
        };
        for (Set<Object> keys : range.values()) {
            result.addAll(keys);
        }
        return result;
    }
}
