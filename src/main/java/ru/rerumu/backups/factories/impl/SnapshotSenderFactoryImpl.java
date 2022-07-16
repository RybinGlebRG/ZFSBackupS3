package ru.rerumu.backups.factories.impl;

import ru.rerumu.backups.factories.SnapshotSenderFactory;
import ru.rerumu.backups.factories.ZFSFileWriterFactory;
import ru.rerumu.backups.factories.ZFSProcessFactory;
import ru.rerumu.backups.repositories.LocalBackupRepository;
import ru.rerumu.backups.repositories.RemoteBackupRepository;
import ru.rerumu.backups.services.SnapshotSender;
import ru.rerumu.backups.services.impl.SnapshotSenderByDataset;
import ru.rerumu.backups.services.impl.SnapshotSenderBySnapshot;

public class SnapshotSenderFactoryImpl implements SnapshotSenderFactory {

    private final boolean isMultiIncremental;
    private final LocalBackupRepository localBackupRepository;
    private final RemoteBackupRepository remoteBackupRepository;
    private final ZFSProcessFactory zfsProcessFactory;
    private final ZFSFileWriterFactory zfsFileWriterFactory;
    private final boolean isLoadS3;

    public SnapshotSenderFactoryImpl(
            boolean isMultiIncremental,
            LocalBackupRepository localBackupRepository,
            RemoteBackupRepository remoteBackupRepository,
            ZFSProcessFactory zfsProcessFactory,
            ZFSFileWriterFactory zfsFileWriterFactory,
            boolean isLoadS3
    ){
        this.isMultiIncremental = isMultiIncremental;
        this.localBackupRepository = localBackupRepository;
        this.remoteBackupRepository = remoteBackupRepository;
        this.zfsProcessFactory = zfsProcessFactory;
        this.zfsFileWriterFactory = zfsFileWriterFactory;
        this.isLoadS3 = isLoadS3;
    }

    @Override
    public SnapshotSender getSnapshotSender() {
        if (isMultiIncremental){
            return new SnapshotSenderByDataset(
                    localBackupRepository,
                    remoteBackupRepository,
                    zfsProcessFactory,
                    zfsFileWriterFactory,
                    isLoadS3
            );
        } else {
            return new SnapshotSenderBySnapshot(
                    localBackupRepository,
                    remoteBackupRepository,
                    zfsProcessFactory,
                    zfsFileWriterFactory,
                    isLoadS3
            );
        }
    }
}
