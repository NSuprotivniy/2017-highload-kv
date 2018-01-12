package ru.mail.polis.nsuprotivniy;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.NoSuchElementException;

public interface DAO {

    public static byte[] DELETED = new byte[0];

    @NotNull
    byte[] getData(@NotNull String key) throws NoSuchElementException, IllegalArgumentException, IOException;

    @NotNull
    void upsertData(@NotNull String key, @NotNull byte[] data) throws IllegalArgumentException, IOException;

    @NotNull
    void deleteData(@NotNull String key) throws IllegalArgumentException, IOException;

    @NotNull
    void close();
}