package lab;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Точка входа: необязательные аргументы --data каталог и --diagnostics. */
public final class Main {
    public static void main(String[] args) {
        Path directory = Path.of("data");
        boolean diagnostics = false;
        try {
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("--data") && i + 1 < args.length) {
                    directory = Path.of(args[++i]);
                } else if (args[i].equals("--diagnostics")) {
                    diagnostics = true;
                } else {
                    throw new DbException("Аргументы: --data каталог --diagnostics");
                }
            }
            Engine engine = new Engine(new SnapshotRepository(directory, System.err::println));
            PrintWriter output = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
            Console console = new Console(engine, output, diagnostics);
            InputStreamReader input = new InputStreamReader(System.in, StandardCharsets.UTF_8);
            console.run(input, System.console() != null);
        } catch (DbException | IOException e) {
            System.err.println("Ошибка: " + e.getMessage());
            System.exit(1);
        }
    }
}
