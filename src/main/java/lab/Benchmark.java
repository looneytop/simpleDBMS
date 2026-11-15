package lab;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** Отдельный воспроизводимый замер SELECT с получением всех строк результата. */
public final class Benchmark {
    private static volatile int consumed;
    public static void main(String[] args) throws Exception {
        int[] sizes = args.length == 0 ? new int[]{10_000, 100_000} : new int[]{Integer.parseInt(args[0])};
        System.out.println("Java: " + System.getProperty("java.runtime.version") + "; " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        System.out.println("seed=42, прогрев=30 пар, измерения=21 пара, время в мс (медиана)");
        for (int size : sizes) {
            run(size);
        }
    }
    private static void run(int size) throws Exception {
        Path directory = Files.createTempDirectory("lab2-benchmark-");
        try {
            Engine engine = new Engine(new SnapshotRepository(directory, System.err::println));
            engine.execute("CREATE TABLE samples (id INT PRIMARY KEY, value INT)");
            Random random = new Random(42);
            StringBuilder insert = new StringBuilder("INSERT INTO samples VALUES ");
            for (int i = 0; i < size; i++) {
                if (i > 0) insert.append(',');
                insert.append('(').append(i).append(',').append(random.nextInt(1000)).append(')');
            }
            engine.execute(insert.toString());
            engine.execute("CREATE INDEX idx_value ON samples (value)");
            for (String condition : List.of("value = 500", "value >= 990", "value >= 100")) {
                Parser.Command query = Parser.parse("SELECT * FROM samples WHERE " + condition);
                Engine.Result scan = engine.execute(query, false);
                Engine.Result index = engine.execute(query, true);
                if (!new HashSet<>(scan.rows()).equals(new HashSet<>(index.rows())) || scan.rows().size() != index.rows().size()) {
                    throw new AssertionError("Результаты не совпали");
                }
                if (index.search() != Engine.Search.INDEX) {
                    throw new AssertionError("Индекс не выбран");
                }
                for (int i = 0; i < 30; i++) {
                    measure(engine, query, false);
                    measure(engine, query, true);
                }
                long[] scanTimes = new long[21];
                long[] indexTimes = new long[21];
                for (int i = 0; i < 21; i++) {
                    if (i % 2 == 0) {
                        scanTimes[i] = measure(engine, query, false);
                        indexTimes[i] = measure(engine, query, true);
                    } else {
                        indexTimes[i] = measure(engine, query, true);
                        scanTimes[i] = measure(engine, query, false);
                    }
                }
                Arrays.sort(scanTimes);
                Arrays.sort(indexTimes);
                System.out.printf(Locale.ROOT, "%d | %s | %d | %.3f | %.3f | %.2f%n", size, condition, scan.rows().size(), scanTimes[10] / 1e6, indexTimes[10] / 1e6, (double) scanTimes[10] / indexTimes[10]);
            }
        } finally {
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
    private static long measure(Engine engine, Parser.Command query, boolean indexed) {
        long start = System.nanoTime();
        Engine.Result result = engine.execute(query, indexed);
        long elapsed = System.nanoTime() - start;
        consumed = result.rows().size();
        return elapsed;
    }
}
