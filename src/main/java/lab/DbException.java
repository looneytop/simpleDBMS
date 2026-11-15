package lab;

/** Ошибка запроса или хранилища, которую можно показать без трассировки стека. */
public class DbException extends RuntimeException {
    public DbException(String message) {
        super(message);
    }

    public DbException(String message, Throwable cause) {
        super(message, cause);
    }
}
