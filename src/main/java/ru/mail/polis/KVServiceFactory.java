package ru.mail.polis;

import org.jetbrains.annotations.NotNull;
import ru.mail.polis.nsuprotivniy.DAO;
import ru.mail.polis.nsuprotivniy.KVServiceImpl;
import ru.mail.polis.nsuprotivniy.SQLiteDAO;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;

/**
 * Constructs {@link KVService} instances.
 *
 * @author Vadim Tsesko <mail@incubos.org>
 */
final class KVServiceFactory {
    private static final long MAX_HEAP = 1024 * 1024 * 1024;

    private KVServiceFactory() {
        // Not supposed to be instantiated
    }

    /**
     * Construct a storage instance.
     *
     * @param port     port to bind HTTP server to
     * @param data     local disk folder to persist the data to
     * @param topology a list of all cluster endpoints {@code http://<host>:<port>} (including this one)
     * @return a storage instance
     */
    @NotNull
    static KVService create(
            final int port,
            @NotNull final File data,
            @NotNull final Set<String> topology) throws IOException {
        if (Runtime.getRuntime().maxMemory() > MAX_HEAP) {
            System.out.println(Runtime.getRuntime().maxMemory());
            throw new IllegalStateException("The heap is too big. Consider setting Xmx.");
        }

        if (port <= 0 || 65536 <= port) {
            throw new IllegalArgumentException("Port out of range");
        }

        if (!data.exists()) {
            throw new IllegalArgumentException("Path doesn't exist: " + data);
        }

        if (!data.isDirectory()) {
            throw new IllegalArgumentException("Path is not a directory: " + data);
        }

        DAO dao = new SQLiteDAO(data.getPath());
        return new KVServiceImpl(port, dao, topology);
    }
}
