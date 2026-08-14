package com.google.common.io;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MoreFiles {
    private MoreFiles() {
    }

    public static void deleteRecursively(Path path, RecursiveDeleteOption... options) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> children = Files.newDirectoryStream(path)) {
                for (Path child : children) {
                    deleteRecursively(child, options);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
