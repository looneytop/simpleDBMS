package lab;

import java.io.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.function.Consumer;

public class SnapshotRepository {
    private final Path file;
    private final Path backup;
    private final Consumer<String> warning;

    public SnapshotRepository(Path directory, Consumer<String> warning) {
        file = directory.resolve("database.bin");
        backup = directory.resolve("database.bak");
        this.warning = warning;
    }

    public Database load() {
        if (!Files.exists(file) && !Files.exists(backup)) {
            return new Database();
        }
        try { return read(file); }
        catch (IOException | ClassNotFoundException | RuntimeException mainError) {
            try {
                Database recovered = read(backup);
                replaceWithCopy(backup, file);
                warning.accept("Основной снимок повреждён или отсутствует. Восстановлена предыдущая резервная копия.");
                return recovered;
            } catch (IOException | ClassNotFoundException | RuntimeException backupError) {
                throw new DbException("Не удалось загрузить базу и резервную копию. Файлы сохранены для восстановления.", mainError);
            }
        }
    }

    public void save(Database database) {
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "snapshot-", ".tmp");
            try (FileOutputStream stream = new FileOutputStream(temporary.toFile());
                 ObjectOutputStream output = new ObjectOutputStream(stream)) {
                output.writeObject(database);
                output.flush();
                stream.getFD().sync();
            }
            if (Files.exists(file)) replaceWithCopy(file, backup);
            move(temporary, file);
            
            try (FileChannel directory = FileChannel.open(file.getParent(), StandardOpenOption.READ)) {
                directory.force(true);
            } catch (IOException | UnsupportedOperationException ignored) { }
        } catch (IOException e) {
            throw new DbException("Не удалось сохранить базу. Запрос отклонён: " + e.getMessage(), e);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    private static Database read(Path path) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(path))) {
            input.setObjectInputFilter(info -> {
                if (info.depth() > 100 || info.references() > 20_000_000 || info.arrayLength() > 10_000_000) {
                    return ObjectInputFilter.Status.REJECTED;
                }
                Class<?> type = info.serialClass();
                if (type == null) {
                    return ObjectInputFilter.Status.UNDECIDED;
                }
                while (type.isArray()) type = type.getComponentType();
                String name = type.getName();
                return type.isPrimitive() || name.startsWith("lab.") || name.startsWith("java.lang.") || name.startsWith("java.util.") || name.startsWith("java.time.")
                        ? ObjectInputFilter.Status.ALLOWED : ObjectInputFilter.Status.REJECTED;
            });
            Object value = input.readObject();
            if (!(value instanceof Database database)) {
                throw new IOException("Неверный формат снимка");
            }
            return database;
        }
    }
    private static void replaceWithCopy(Path source, Path destination) throws IOException {
        Path temporary = Files.createTempFile(destination.getParent(), "backup-", ".tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            move(temporary, destination);
        } finally { Files.deleteIfExists(temporary); }
    }
    private static void move(Path source, Path destination) throws IOException {
        try { Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING); }
    }
}
