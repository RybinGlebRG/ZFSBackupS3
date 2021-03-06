package ru.rerumu.backups.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.rerumu.backups.exceptions.CompressorException;
import ru.rerumu.backups.exceptions.EncryptException;
import ru.rerumu.backups.exceptions.FileHitSizeLimitException;
import ru.rerumu.backups.exceptions.ZFSStreamEndedException;
import ru.rerumu.backups.services.ZFSFileReader;
import ru.rerumu.backups.services.ZFSFileWriter;
import ru.rerumu.backups.services.impl.ZFSFileReaderFull;
import ru.rerumu.backups.services.impl.ZFSFileWriterFull;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TestWriteRead {

    @Test
    void shouldWriteReadSame(@TempDir Path tempDir) throws IOException, CompressorException, ClassNotFoundException, EncryptException, FileHitSizeLimitException, ZFSStreamEndedException {
        String password = "jNfdCfxcWUqg5xa";
        int chunkSize = 1024;
        long filePartSize = 1000;
        ZFSFileWriter zfsFileWriter = new ZFSFileWriterFull(password, chunkSize, filePartSize);
        byte[] srcBuf = new byte[700];
        new Random().nextBytes(srcBuf);
        Path path = tempDir.resolve("test");

        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(srcBuf);
             BufferedInputStream bufferedInputStream = new BufferedInputStream(byteArrayInputStream)) {
            zfsFileWriter.write(bufferedInputStream, path);
        } catch (ZFSStreamEndedException ignored) {

        }

        byte[] resBuf;
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(byteArrayOutputStream)) {
            ZFSFileReader zfsFileReader = new ZFSFileReaderFull(bufferedOutputStream, path, password);
            try {
                zfsFileReader.read();
            } catch (EOFException ignored) {
            }
            bufferedOutputStream.flush();
            resBuf = byteArrayOutputStream.toByteArray();
        }

        Assertions.assertArrayEquals(srcBuf, resBuf);

    }


    @Test
    void shouldWriteReadSameTwoFiles(@TempDir Path tempDir) throws IOException, CompressorException, ClassNotFoundException, EncryptException, FileHitSizeLimitException, ZFSStreamEndedException {
        String password = "jNfdCfxcWUqg5xa";
        int chunkSize = 1024;
        long filePartSize = 1000;
        ZFSFileWriter zfsFileWriter = new ZFSFileWriterFull(password, chunkSize, filePartSize);
        byte[] srcBuf = new byte[1100];
        new Random().nextBytes(srcBuf);
//        Path path = tempDir.resolve("test");

        List<Path> pathList = new ArrayList<>();
        pathList.add(tempDir.resolve("test1"));
        pathList.add(tempDir.resolve("test2"));

        int n = 0;

        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(srcBuf);
             BufferedInputStream bufferedInputStream = new BufferedInputStream(byteArrayInputStream)) {
            while (true) {
                try {
                    zfsFileWriter.write(bufferedInputStream, pathList.get(n));
                } catch (ZFSStreamEndedException ignored) {
                    break;
                } catch (FileHitSizeLimitException e) {
                    n++;
                }
            }
        }


        byte[] resBuf;

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(byteArrayOutputStream)) {

            for (Path path : pathList) {
                ZFSFileReader zfsFileReader = new ZFSFileReaderFull(bufferedOutputStream, path, password);
                try {
                    zfsFileReader.read();
                } catch (EOFException ignored) {
                }
            }
            bufferedOutputStream.flush();
            resBuf = byteArrayOutputStream.toByteArray();
        }

        Assertions.assertArrayEquals(srcBuf, resBuf);

    }
}
