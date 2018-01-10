package ru.mail.polis.nsuprotivniy;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.LongAccumulator;

public class SQLiteDAO implements DAO {

    private Connection connection;

    private PreparedStatement getDataStmt;
    private PreparedStatement upsertDataStmt;
    private PreparedStatement deleteDataStmt;

    final private String createTableQuery = "CREATE TABLE storage (k INTEGER PRIMARY KEY, v BLOB)";
    final private String getDataQuery = "SELECT v FROM storage where k = ?";
    final private String upsertDataQuery = "INSERT OR REPLACE INTO storage(k, v) VALUES (?, ?)";
    final private String deleteDataQuery = "DELETE FROM storage where k = ?";

    public SQLiteDAO(String directory) throws IOException {
        try {
            Class.forName("org.sqlite.JDBC");
            System.out.println(directory + "/storage.s3db");
            boolean exists = Files.exists(Paths.get(directory + "/storage.s3db"));
            connection = DriverManager.getConnection("jdbc:sqlite:" + directory + "/storage.s3db");
            if (!exists) {
                connection.createStatement().execute(createTableQuery);
            }

            getDataStmt = connection.prepareStatement(getDataQuery);
            upsertDataStmt = connection.prepareStatement(upsertDataQuery);
            deleteDataStmt = connection.prepareStatement(deleteDataQuery);

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new IOException();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IOException();
        }
    }

    @NotNull
    @Override
    public byte[] getData(@NotNull String key) throws NoSuchElementException, IllegalArgumentException, IOException {
        try {
            getDataStmt.setLong(1, Long.parseLong(key));
            ResultSet rs = getDataStmt.executeQuery();
            return rs.getBytes("v");
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IOException();
        }
    }

    @NotNull
    @Override
    public void upsertData(@NotNull String key, @NotNull byte[] data) throws IllegalArgumentException, IOException {
        try {
            upsertDataStmt.setLong(1, Long.parseLong(key));
            upsertDataStmt.setBytes(2, data);
            upsertDataStmt.execute();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IOException();
        }
    }

    @NotNull
    @Override
    public void deleteData(@NotNull String key) throws IllegalArgumentException, IOException {
        try {
            deleteDataStmt.setLong(1, Long.parseLong(key));
            deleteDataStmt.execute();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IOException();
        }
    }
}
