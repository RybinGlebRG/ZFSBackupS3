package ru.rerumu.backups.io;

import java.io.BufferedOutputStream;
import java.nio.file.Path;

public interface ZFSFileReaderFactory {
    ZFSFileReader getZFSFileReader(BufferedOutputStream bufferedOutputStream, Path path);
}