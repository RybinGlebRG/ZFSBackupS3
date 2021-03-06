package ru.rerumu.backups.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import ru.rerumu.backups.controllers.BackupController;
import ru.rerumu.backups.controllers.RestoreController;
import ru.rerumu.backups.exceptions.IncorrectHashException;
import ru.rerumu.backups.exceptions.S3MissesFileException;
import ru.rerumu.backups.factories.SnapshotSenderFactory;
import ru.rerumu.backups.factories.ZFSFileReaderFactory;
import ru.rerumu.backups.factories.ZFSFileWriterFactory;
import ru.rerumu.backups.factories.ZFSProcessFactory;
import ru.rerumu.backups.factories.impl.SnapshotSenderFactoryImpl;
import ru.rerumu.backups.factories.impl.ZFSFileReaderFactoryImpl;
import ru.rerumu.backups.factories.impl.ZFSFileWriterFactoryImpl;
import ru.rerumu.backups.models.Snapshot;
import ru.rerumu.backups.models.ZFSPool;
import ru.rerumu.backups.repositories.FilePartRepository;
import ru.rerumu.backups.repositories.RemoteBackupRepository;
import ru.rerumu.backups.repositories.ZFSFileSystemRepository;
import ru.rerumu.backups.repositories.ZFSSnapshotRepository;
import ru.rerumu.backups.repositories.impl.FilePartRepositoryImpl;
import ru.rerumu.backups.repositories.impl.ZFSFileSystemRepositoryImpl;
import ru.rerumu.backups.repositories.impl.ZFSSnapshotRepositoryImpl;
import ru.rerumu.backups.services.SnapshotReceiver;
import ru.rerumu.backups.services.ZFSBackupService;
import ru.rerumu.backups.services.ZFSRestoreService;
import ru.rerumu.backups.services.impl.SnapshotReceiverImpl;
import ru.rerumu.backups.zfs_api.ProcessWrapper;
import ru.rerumu.backups.zfs_api.ZFSReceive;
import ru.rerumu.backups.zfs_api.ZFSSend;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TestBackupRestoreByDataset {

    private byte[] randomBytes(int n) {
        byte[] bytes = new byte[n];
        new Random().nextBytes(bytes);
        return bytes;
    }

    private List<byte[]> srcBytesList;
    private List<byte[]> resBytes;

    private BackupController setupBackup(Path backupDir, Path restoreDir, boolean isMultiIncremental)
            throws IOException, NoSuchAlgorithmException, InterruptedException, IncorrectHashException, S3MissesFileException {
        FilePartRepository filePartRepository = new FilePartRepositoryImpl(backupDir);

        // s3Loader
        RemoteBackupRepository remoteBackupRepository = Mockito.mock(RemoteBackupRepository.class);

        Mockito.doAnswer(invocationOnMock -> {
            Object[] args = invocationOnMock.getArguments();
            String filename = ((Path) args[1]).getFileName().toString();
            Files.copy((Path) args[1], restoreDir.resolve(filename + ".ready"));
            while (Files.exists(restoreDir.resolve(filename + ".ready"))) {
                Thread.sleep(1000);
            }
            return null;
        }).when(remoteBackupRepository).add(Mockito.any(), Mockito.any());

        // zfsListFilesystems
        ProcessWrapper zfsListFilesystems = Mockito.mock(ProcessWrapper.class);
        String filesystems = "ExternalPool\n" +
                "ExternalPool/Applications\n";
        Mockito.when(zfsListFilesystems.getBufferedInputStream()).thenReturn(
                new BufferedInputStream(new ByteArrayInputStream(filesystems.getBytes(StandardCharsets.UTF_8)))
        );

        // zfsListSnapshots
        List<ProcessWrapper> processWrappers = new ArrayList<>();
        processWrappers.add(Mockito.mock(ProcessWrapper.class));
        processWrappers.add(Mockito.mock(ProcessWrapper.class));

        List<String> stringSnapshots = new ArrayList<>();
        stringSnapshots.add("ExternalPool@auto-20220326-150000\n" +
                "ExternalPool@auto-2022.03.27-06.00.00\n" +
                "ExternalPool@auto-20220327-150000\n" +
                "ExternalPool@auto-20220328-150000\n");
        stringSnapshots.add("ExternalPool/Applications@auto-20220326-150000\n" +
                "ExternalPool/Applications@auto-2022.03.27-06.00.00\n" +
                "ExternalPool/Applications@auto-20220327-150000\n" +
                "ExternalPool/Applications@auto-20220328-150000\n");

        for (int i = 0; i < processWrappers.size(); i++) {
            Mockito.when(processWrappers.get(i).getBufferedInputStream())
                    .thenReturn(
                            new BufferedInputStream(new ByteArrayInputStream(stringSnapshots.get(i).getBytes(StandardCharsets.UTF_8)))
                    );
        }

        // ZFSSend
        List<ZFSSend> zfsSendList = new ArrayList<>();
        // ExternalPool
        zfsSendList.add(Mockito.mock(ZFSSend.class));
        zfsSendList.add(Mockito.mock(ZFSSend.class));
        // ExternalPool/Applications
        zfsSendList.add(Mockito.mock(ZFSSend.class));
        zfsSendList.add(Mockito.mock(ZFSSend.class));

        srcBytesList = new ArrayList<>();
        // ExternalPool
        srcBytesList.add(randomBytes(250));
        srcBytesList.add(randomBytes(250));
        // ExternalPool/Applications
        srcBytesList.add(randomBytes(2000));
        srcBytesList.add(randomBytes(250));

        for (int i = 0; i < zfsSendList.size(); i++) {
            Mockito.when(zfsSendList.get(i).getBufferedInputStream())
                    .thenReturn(
                            new BufferedInputStream(new ByteArrayInputStream(srcBytesList.get(i)))
                    );
        }

        // zfsProcessFactory
        ZFSProcessFactory zfsProcessFactory = Mockito.mock(ZFSProcessFactory.class);

        Mockito.when(zfsProcessFactory.getZFSListFilesystems("ExternalPool"))
                .thenReturn(zfsListFilesystems);

        Mockito.when(zfsProcessFactory.getZFSListSnapshots("ExternalPool"))
                .thenReturn(processWrappers.get(0));
        Mockito.when(zfsProcessFactory.getZFSListSnapshots("ExternalPool/Applications"))
                .thenReturn(processWrappers.get(1));

        Mockito.when(zfsProcessFactory.getZFSSendFull(
                new Snapshot("ExternalPool@auto-20220326-150000")
        )).thenReturn(zfsSendList.get(0));
        Mockito.when(zfsProcessFactory.getZFSSendIncremental(
                new Snapshot("ExternalPool@auto-20220326-150000"),
                new Snapshot("ExternalPool@auto-20220327-150000")
        )).thenReturn(zfsSendList.get(1));


        Mockito.when(zfsProcessFactory.getZFSSendFull(
                new Snapshot("ExternalPool/Applications@auto-20220326-150000")
        )).thenReturn(zfsSendList.get(2));
        Mockito.when(zfsProcessFactory.getZFSSendIncremental(
                new Snapshot("ExternalPool/Applications@auto-20220326-150000"),
                new Snapshot("ExternalPool/Applications@auto-20220327-150000")
        )).thenReturn(zfsSendList.get(3));


        ZFSSnapshotRepository zfsSnapshotRepository = new ZFSSnapshotRepositoryImpl(zfsProcessFactory);
        ZFSFileSystemRepository zfsFileSystemRepository = new ZFSFileSystemRepositoryImpl(zfsProcessFactory, zfsSnapshotRepository);
        ZFSFileWriterFactory zfsFileWriterFactory = new ZFSFileWriterFactoryImpl(
                "84fBS1KsChnuaV0",
                1070,
                1024);
        SnapshotSenderFactory snapshotSenderFactory = new SnapshotSenderFactoryImpl(
                isMultiIncremental,
                filePartRepository, remoteBackupRepository, zfsProcessFactory, zfsFileWriterFactory,
                true
        );
        ZFSBackupService zfsBackupService = new ZFSBackupService(
                true,
                zfsFileSystemRepository,
                snapshotSenderFactory.getSnapshotSender()
        );
        BackupController backupController = new BackupController(zfsBackupService);
        return backupController;
    }

    private RestoreController setupRestore(Path restoreDir) throws IOException, ExecutionException, InterruptedException {
        FilePartRepository filePartRepository = new FilePartRepositoryImpl(restoreDir);

        ZFSReceive zfsReceive = Mockito.mock(ZFSReceive.class);
        List<ByteArrayOutputStream> byteArrayOutputStreamList = new ArrayList<>();
        byteArrayOutputStreamList.add(new ByteArrayOutputStream());

        resBytes = new ArrayList<>();

        List<BufferedOutputStream> bufferedOutputStreamList = new ArrayList<>();
        ByteArrayOutputStream tmpArray1 = new ByteArrayOutputStream();
        byteArrayOutputStreamList.add(tmpArray1);
        BufferedOutputStream tmpBuf1 = new BufferedOutputStream(tmpArray1);
        bufferedOutputStreamList.add(tmpBuf1);

        Mockito.doAnswer(invocationOnMock -> {
            bufferedOutputStreamList.get(bufferedOutputStreamList.size()-1).flush();
            byte[] tmp = byteArrayOutputStreamList.get(byteArrayOutputStreamList.size()-1).toByteArray();
            resBytes.add(tmp);

            ByteArrayOutputStream tmpArray = new ByteArrayOutputStream();
            byteArrayOutputStreamList.add(tmpArray);
            BufferedOutputStream tmpBuf = new BufferedOutputStream(tmpArray);
            bufferedOutputStreamList.add(tmpBuf);


            return null;
        }).when(zfsReceive).close();

        Mockito.when(zfsReceive.getBufferedOutputStream())
                .thenAnswer(invocationOnMock -> {
                    BufferedOutputStream tmpBuf = bufferedOutputStreamList.get(bufferedOutputStreamList.size()-1);
                    return tmpBuf;
                });

        ZFSProcessFactory zfsProcessFactory = Mockito.mock(ZFSProcessFactory.class);
        Mockito.when(zfsProcessFactory.getZFSReceive(Mockito.any()))
                .thenReturn(zfsReceive);


        ZFSFileReaderFactory zfsFileReaderFactory = new ZFSFileReaderFactoryImpl("84fBS1KsChnuaV0");
        SnapshotReceiver snapshotReceiver = new SnapshotReceiverImpl(
                zfsProcessFactory,
                new ZFSPool("ReceivePool"),
                filePartRepository,
                zfsFileReaderFactory,
                true
        );

        ZFSRestoreService zfsRestoreService = new ZFSRestoreService(
                "84fBS1KsChnuaV0",
                zfsProcessFactory,
                true,
                filePartRepository,
                snapshotReceiver);

        RestoreController restoreController = new RestoreController(zfsRestoreService);
        return restoreController;
    }



    @Test
    void shouldBackupRestoreByDataset(@TempDir Path backupDir, @TempDir Path restoreDir) throws IOException, NoSuchAlgorithmException, InterruptedException, IncorrectHashException, ExecutionException, S3MissesFileException {

        BackupController backupController = setupBackup(backupDir,restoreDir,true);
        RestoreController restoreController = setupRestore(restoreDir);

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        Future<?> backupFuture = executorService.submit(() -> {
            backupController.backupFull("ExternalPool@auto-20220327-150000");
        });

        Future<?> restoreFuture = executorService.submit(restoreController::restore);

        backupFuture.get();

        while (true) {
            boolean isFoundFile = true;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(restoreDir)) {
                isFoundFile = false;
                for (Path item : stream) {
                    isFoundFile = true;
                }
            }
            if (!isFoundFile) {
                break;
            }
            Thread.sleep(10000);
        }
        Files.createFile(restoreDir.resolve("finished"));

        restoreFuture.get();

        Assertions.assertEquals(srcBytesList.size(), resBytes.size());

        for (int i = 0; i < srcBytesList.size(); i++) {
            Assertions.assertArrayEquals(srcBytesList.get(i), resBytes.get(i));
        }

    }
}
