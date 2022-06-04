package ru.rerumu.backups.services;

import ru.rerumu.backups.exceptions.CompressorException;
import ru.rerumu.backups.exceptions.EncryptException;
import ru.rerumu.backups.exceptions.IncorrectHashException;
import ru.rerumu.backups.io.S3Loader;
import ru.rerumu.backups.models.Snapshot;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public interface SnapshotSender {

    void sendBaseSnapshot(Snapshot baseSnapshot, S3Loader s3Loader, boolean isLoadS3)
            throws InterruptedException, CompressorException, IOException, EncryptException, NoSuchAlgorithmException, IncorrectHashException;

    void sendIncrementalSnapshot(Snapshot baseSnapshot, Snapshot incrementalSnapshot, S3Loader s3Loader, boolean isLoadS3)
            throws InterruptedException, CompressorException, IOException, EncryptException, NoSuchAlgorithmException, IncorrectHashException;

    void checkSent(List<Snapshot> snapshotList, S3Loader s3Loader);

    void sendStartingFromFull(List<Snapshot> snapshotList)
            throws InterruptedException, CompressorException, IOException, EncryptException, NoSuchAlgorithmException, IncorrectHashException;
    void sendStartingFromIncremental(List<Snapshot> snapshotList)
            throws InterruptedException, CompressorException, IOException, EncryptException, NoSuchAlgorithmException, IncorrectHashException;
}
