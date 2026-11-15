package lab;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Поддерживаемые типы и строгое преобразование SQL-литералов. */
public enum DataType {
    INT, DOUBLE, STRING, DATE, BOOLEAN;

    /** Преобразует литерал с учётом кавычек; NULL допустим для любого типа. */
    public Object parse(Parser.Literal literal) {
        String value = literal.text();
        if (!literal.quoted() && value.equalsIgnoreCase("NULL")) {
            return null;
        }
        try {
            return switch (this) {
                case STRING -> {
                    if (!literal.quoted()) {
                        throw new IllegalArgumentException();
                    }
                    yield value;
                }
                case DATE -> {
                    if (!literal.quoted() || !value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                        throw new IllegalArgumentException();
                    }
                    yield LocalDate.parse(value);
                }
                case INT -> {
                    if (literal.quoted() || !value.matches("[+-]?\\d+")) {
                        throw new IllegalArgumentException();
                    }
                    yield Integer.valueOf(value);
                }
                case DOUBLE -> {
                    if (literal.quoted() || !value.matches("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?")) {
                        throw new IllegalArgumentException();
                    }
                    double number = Double.parseDouble(value);
                    if (!Double.isFinite(number)) {
                        throw new IllegalArgumentException();
                    }
                    yield number == 0.0 ? 0.0 : number;
                }
                case BOOLEAN -> {
                    if (literal.quoted() || !(value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false"))) {
                        throw new IllegalArgumentException();
                    }
                    yield Boolean.valueOf(value);
                }
            };
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new DbException("Значение «" + value + "» несовместимо с типом " + this);
        }
    }

    @SuppressWarnings("unchecked")
    static int compare(Object left, Object right) {
        return ((Comparable<Object>) left).compareTo(right);
    }
}
