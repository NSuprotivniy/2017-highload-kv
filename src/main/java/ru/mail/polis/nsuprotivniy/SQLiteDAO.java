package ru.mail.polis.nsuprotivniy;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.NoSuchElementException;

public class SQLiteDAO implements DAO {

    private Connection connection;

    private PreparedStatement getDataStmt;
    private PreparedStatement upsertDataStmt;
    private PreparedStatement deleteDataStmt;

    final private String createTableQuery = "CREATE TABLE storage (k TEXT PRIMARY KEY, v BLOB, deleted INTEGER DEFAULT 0)";
    final private String getDataQuery = "SELECT v, deleted FROM storage where k = ?";
    final private String upsertDataQuery = "INSERT OR REPLACE INTO storage(k, v, deleted) VALUES (?, ?, 0)";
    final private String deleteDataQuery = "UPDATE storage SET deleted = 1 where k = ?";

    public SQLiteDAO(String directory) throws IOException {
        try {
            Class.forName("org.sqlite.JDBC");
            System.out.println(directory + "/storage.s3db");
            boolean exists = Files.exists(Paths.get(directory + "/storage.s3db"));
            connection = DriverManager.getConnection("jdbc:sqlite:" + directory + "/storage.s3db?journal_mode=OFF&synchronous=OFF&locking_mode=exclusive");
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
            getDataStmt.setString(1, key);
            ResultSet rs = getDataStmt.executeQuery();
            if (rs.getInt("deleted") == 1) return DELETED;
            else return rs.getBytes("v");
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IOException();
        }
    }

    @NotNull
    @Override
    public void upsertData(@NotNull String key, @NotNull byte[] data) throws IllegalArgumentException, IOException {
        try {
            upsertDataStmt.setString(1, key);
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
            deleteDataStmt.setString(1, key);
            deleteDataStmt.execute();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IOException();
        }
    }
}
