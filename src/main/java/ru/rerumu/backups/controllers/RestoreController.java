package ru.rerumu.backups.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rerumu.backups.models.ZFSPool;
import ru.rerumu.backups.repositories.FilePartRepository;
import ru.rerumu.backups.repositories.impl.FilePartRepositoryImpl;
import ru.rerumu.backups.services.impl.ZFSProcessFactoryImpl;
import ru.rerumu.backups.services.ZFSRestoreService;

import java.nio.file.Paths;

public class RestoreController {
    private final Logger logger = LoggerFactory.getLogger(RestoreController.class);

    public void restore(String backupDirectory,
                        String password,
                        boolean isDeleteAfterReceive,
                        ZFSPool zfsPool){
        try {
            FilePartRepository filePartRepository = new FilePartRepositoryImpl(
                    Paths.get(backupDirectory)
            );

            ZFSRestoreService zfsRestoreService = new ZFSRestoreService(
                    password,
                    new ZFSProcessFactoryImpl(),
                    isDeleteAfterReceive,
                    filePartRepository);

            zfsRestoreService.zfsReceive(zfsPool);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }
}
