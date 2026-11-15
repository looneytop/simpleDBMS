package lab;

import java.io.*;
import java.util.*;

/** Читает команды до разделителя вне кавычек, выводит результаты и ошибки. */
public final class Console {
    private final Engine engine;
    private final PrintWriter output;
    private final boolean diagnostics;
    public Console(Engine engine, PrintWriter output, boolean diagnostics) {
        this.engine = engine;
        this.output = output;
        this.diagnostics = diagnostics;
    }

    /** EOF завершает последний запрос без точки с запятой, если кавычки закрыты. */
    public void run(Reader reader, boolean interactive) throws IOException {
        PushbackReader input = new PushbackReader(reader, 1);
        StringBuilder buffer = new StringBuilder();
        boolean quoted = false;
        if (interactive) {
            prompt();
        }
        int next;
        while ((next = input.read()) != -1) {
            char c = (char) next;
            if (c == '\'') {
                buffer.append(c);
                if (quoted) {
                    int after = input.read();
                    if (after == '\'') {
                        buffer.append('\'');
                        continue;
                    }
                    quoted = false;
                    if (after != -1) {
                        input.unread(after);
                    }
                } else {
                    quoted = true;
                }
            } else if (c == ';' && !quoted) {
                if (!process(buffer.toString())) {
                    return;
                }
                buffer.setLength(0);
                if (interactive) {
                    prompt();
                }
            } else if ((c == '\n' || c == '\r') && !quoted && buffer.toString().trim().equalsIgnoreCase("exit")) {
                return;
            } else {
                buffer.append(c);
            }
        }
        if (!buffer.toString().isBlank()) {
            process(buffer.toString());
        }
    }

    private void prompt() {
        output.print("sql> ");
        output.flush();
    }

    private boolean process(String sql) {
        sql = sql.trim();
        if (sql.isEmpty()) {
            return true;
        }
        if (sql.equalsIgnoreCase("exit")) {
            return false;
        }
        try {
            Parser.Command command = Parser.parse(sql);
            Engine.Result result = engine.execute(command, true);
            if (command instanceof Parser.Select) {
                printTable(result);
                output.println(result.message());
                if (diagnostics) {
                    output.println("Поиск: " + result.search() + ", проверено строк: " + result.examined());
                }
            } else if (command instanceof Parser.Insert || command instanceof Parser.Update || command instanceof Parser.Delete) {
                output.println("Затронуто строк: " + result.affected());
            } else {
                output.println(result.message());
            }
        } catch (DbException e) {
            output.println("Ошибка: " + e.getMessage());
        }
        output.flush();
        return true;
    }
    private void printTable(Engine.Result result) {
        int[] widths = new int[result.columns().size()];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = result.columns().get(i).length();
        }
        for (List<Object> row : result.rows()) {
            for (int i = 0; i < widths.length; i++) {
                widths[i] = Math.max(widths[i], display(row.get(i)).length());
            }
        }
        printRow(new ArrayList<>(result.columns()), widths);
        List<Object> separator = new ArrayList<>();
        for (int width : widths) {
            separator.add("-".repeat(width));
        }
        printRow(separator, widths);
        for (List<Object> row : result.rows()) {
            printRow(row, widths);
        }
    }
    private void printRow(List<?> row, int[] widths) {
        output.print("|");
        for (int i = 0; i < widths.length; i++) {
            String value = display(row.get(i));
            output.print(" " + value + " ".repeat(widths[i] - value.length()) + " |");
        }
        output.println();
    }
    private static String display(Object value) {
        return value == null ? "NULL" : value.toString().replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
