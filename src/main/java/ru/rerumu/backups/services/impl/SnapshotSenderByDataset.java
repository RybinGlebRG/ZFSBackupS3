package ru.rerumu.backups.services.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rerumu.backups.exceptions.*;
import ru.rerumu.backups.factories.ZFSFileWriterFactory;
import ru.rerumu.backups.factories.ZFSProcessFactory;
import ru.rerumu.backups.models.Snapshot;
import ru.rerumu.backups.repositories.FilePartRepository;
import ru.rerumu.backups.repositories.RemoteBackupRepository;
import ru.rerumu.backups.services.SnapshotSender;
import ru.rerumu.backups.zfs_api.ZFSSend;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.ExecutionException;


public class SnapshotSenderByDataset extends AbstractSnapshotSender {
    private final Logger logger = LoggerFactory.getLogger(SnapshotSenderByDataset.class);

    public SnapshotSenderByDataset(
            FilePartRepository filePartRepository,
            RemoteBackupRepository remoteBackupRepository,
            ZFSProcessFactory zfsProcessFactory,
            ZFSFileWriterFactory zfsFileWriterFactory,
            boolean isLoadS3
    ) {
        super(
                filePartRepository,
                remoteBackupRepository,
                zfsProcessFactory,
                zfsFileWriterFactory,
                isLoadS3
        );
    }

    @Override
    public void sendStartingFromFull(String datasetName, List<Snapshot> snapshotList)
            throws InterruptedException,
            CompressorException,
            IOException,
            EncryptException,
            NoSuchAlgorithmException,
            IncorrectHashException,
            ExecutionException,
            S3MissesFileException {

        if (snapshotList.size() <1){
            throw new IllegalArgumentException();
        }

        Snapshot baseSnapshot = snapshotList.get(0);
        Snapshot lastIncrementalSnapshot = null;

        if (snapshotList.size()>1){
            lastIncrementalSnapshot = snapshotList.get(snapshotList.size()-1);
        }

        sendBaseSnapshot(baseSnapshot);
        if (lastIncrementalSnapshot!=null){
            sendIncrementalSnapshot(baseSnapshot, lastIncrementalSnapshot);
        }
    }

    @Override
    public void sendStartingFromIncremental(String datasetName,List<Snapshot> snapshotList)
            throws InterruptedException,
            CompressorException,
            IOException,
            EncryptException,
            NoSuchAlgorithmException,
            IncorrectHashException,
            ExecutionException,
            S3MissesFileException {

        if (snapshotList.size() <2){
            throw new IllegalArgumentException();
        }

        Snapshot baseSnapshot = snapshotList.get(0);
        Snapshot lastIncrementalSnapshot = snapshotList.get(snapshotList.size()-1);

        sendIncrementalSnapshot(baseSnapshot, lastIncrementalSnapshot);
    }

}