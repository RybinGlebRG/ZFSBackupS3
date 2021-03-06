package ru.rerumu.backups.factories.impl;

import ru.rerumu.backups.models.Snapshot;
import ru.rerumu.backups.models.ZFSPool;
import ru.rerumu.backups.factories.ZFSProcessFactory;
import ru.rerumu.backups.zfs_api.*;
import ru.rerumu.backups.zfs_api.impl.ZFSReceiveImpl;
import ru.rerumu.backups.zfs_api.impl.ZFSSendFull;
import ru.rerumu.backups.zfs_api.impl.ZFSSendIncremental;
import ru.rerumu.backups.zfs_api.impl.ZFSSendMultiIncremental;

import java.io.IOException;

public class ZFSProcessFactoryImpl implements ZFSProcessFactory {
    private final boolean isMultiIncremental;

    public ZFSProcessFactoryImpl(boolean isMultiIncremental){
        this.isMultiIncremental = isMultiIncremental;
    }

    @Override
    public ZFSSend getZFSSendFull(Snapshot snapshot) throws IOException {
        return new ZFSSendFull(snapshot);
    }

    @Override
    public ZFSSend getZFSSendIncremental(Snapshot baseSnapshot, Snapshot incrementalSnapshot) throws IOException {
        if (isMultiIncremental){
            return new ZFSSendMultiIncremental(baseSnapshot, incrementalSnapshot);
        } else {
            return new ZFSSendIncremental(baseSnapshot, incrementalSnapshot);
        }
    }

    @Override
    public ZFSReceive getZFSReceive(ZFSPool zfsPool) throws IOException {
        return new ZFSReceiveImpl(zfsPool.getName());
    }

    @Override
    public ProcessWrapper getZFSListFilesystems(String parentFileSystem) throws IOException {
        return new ZFSListFilesystems(parentFileSystem);
    }

    @Override
    public ProcessWrapper getZFSListSnapshots(String fileSystemName) throws IOException {
        return new ZFSListSnapshots(fileSystemName);
    }


}
