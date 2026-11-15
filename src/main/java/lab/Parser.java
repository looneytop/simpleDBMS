package lab;

import java.util.*;

public final class Parser {
    public interface Command { }
    public record Literal(String text, boolean quoted) { }
    public record Where(String column, String operator, Literal value) { }
    public record CreateTable(String table, List<Database.Column> columns) implements Command { }
    public record DropTable(String table) implements Command { }
    public record CreateIndex(String name, String table, String column) implements Command { }
    public record Insert(String table, List<String> columns, List<List<Literal>> rows) implements Command { }
    public record Select(String table, List<String> columns, Where where) implements Command { }
    public record Update(String table, Map<String, Literal> values, Where where) implements Command { }
    public record Delete(String table, Where where) implements Command { }

    private final List<Tokenizer.Token> tokens;
    private int position;
    private Parser(String sql) {
        tokens = Tokenizer.tokenize(sql);
    }

    public static Command parse(String sql) {
        Parser parser = new Parser(sql);
        Command result = parser.command();
        parser.accept(";");
        if (parser.position != parser.tokens.size()) {
            throw parser.error("Лишний текст после команды");
        }
        return result;
    }

    private Command command() {
        if (accept("CREATE")) {
            if (accept("INDEX")) {
                String name = identifier();
                expect("ON");
                String table = identifier();
                expect("(");
                String column = identifier();
                expect(")");
                return new CreateIndex(name, table, column);
            }
            expect("TABLE");
            String table = identifier();
            expect("(");
            List<Database.Column> columns = new ArrayList<>();
            do {
                String name = identifier();
                String typeName = identifier();
                DataType type;
                try { type = DataType.valueOf(typeName.toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException e) { throw error("Неизвестный тип: " + typeName); }
                boolean primary = false;
                String parent = null;
                String parentColumn = null;
                boolean more = true;
                while (more) {
                    if (accept("PRIMARY")) {
                        if (primary) {
                            throw error("Повтор PRIMARY KEY");
                        }
                        expect("KEY");
                        primary = true;
                    } else if (accept("REFERENCES")) {
                        if (parent != null) {
                            throw error("Повтор REFERENCES");
                        }
                        parent = identifier();
                        expect("(");
                        parentColumn = identifier();
                        expect(")");
                    } else more = false;
                }
                columns.add(new Database.Column(name, type, primary, parent, parentColumn));
            } while (accept(","));
            expect(")");
            return new CreateTable(table, columns);
        }
        if (accept("DROP")) { expect("TABLE"); return new DropTable(identifier()); }
        if (accept("INSERT")) {
            expect("INTO");
            String table = identifier();
            List<String> columns = new ArrayList<>();
            if (accept("(")) { do { columns.add(identifier()); } while (accept(",")); expect(")"); }
            expect("VALUES");
            List<List<Literal>> rows = new ArrayList<>();
            do {
                expect("(");
                List<Literal> row = new ArrayList<>();
                do { row.add(literal()); } while (accept(","));
                expect(")");
                rows.add(row);
            } while (accept(","));
            return new Insert(table, columns, rows);
        }
        if (accept("SELECT")) {
            List<String> columns = new ArrayList<>();
            if (!accept("*")) { do { columns.add(identifier()); } while (accept(",")); }
            expect("FROM");
            String table = identifier();
            return new Select(table, columns, where());
        }
        if (accept("UPDATE")) {
            String table = identifier();
            expect("SET");
            Map<String, Literal> values = new LinkedHashMap<>();
            do {
                String name = identifier();
                expect("=");
                if (values.put(name, literal()) != null) {
                    throw error("Повтор столбца: " + name);
                }
            } while (accept(","));
            return new Update(table, values, where());
        }
        if (accept("DELETE")) { expect("FROM"); String table = identifier(); return new Delete(table, where()); }
        throw error("Ожидалась команда SQL");
    }

    private Where where() {
        if (!accept("WHERE")) {
            return null;
        }
        String column = identifier();
        Tokenizer.Token token = next();
        String operator = token.text();
        if (token.quoted() || !Set.of("=", "!=", "<>", ">", ">=", "<", "<=").contains(operator)) {
            throw error("Неизвестный оператор");
        }
        return new Where(column, operator, literal());
    }
    private Literal literal() {
        Tokenizer.Token token = next();
        if (!token.quoted() && ("(),;*=<>!".contains(token.text()))) {
            throw error("Ожидалось значение");
        }
        return new Literal(token.text(), token.quoted());
    }
    private String identifier() {
        Tokenizer.Token token = next();
        if (token.quoted() || !token.text().matches("[a-zA-Z_][a-zA-Z_0-9]*")) {
            throw error("Ожидалось имя");
        }
        return token.text().toLowerCase(Locale.ROOT);
    }
    private Tokenizer.Token next() {
        if (position == tokens.size()) {
            throw error("Неожиданный конец запроса");
        }
        return tokens.get(position++);
    }
    private boolean accept(String text) {
        if (position < tokens.size() && !tokens.get(position).quoted() && tokens.get(position).text().equalsIgnoreCase(text)) { position++; return true; }
        return false;
    }
    private void expect(String text) { if (!accept(text)) throw error("Ожидалось " + text); }
    private DbException error(String message) { return new DbException(message + " (позиция токена " + (position + 1) + ")"); }
}
