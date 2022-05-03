package ru.rerumu.backups.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rerumu.backups.exceptions.*;
import ru.rerumu.backups.models.CryptoMessage;
import ru.rerumu.backups.models.ZFSPool;
import ru.rerumu.backups.repositories.FilePartRepository;
import ru.rerumu.backups.services.impl.AESCryptor;
import ru.rerumu.backups.services.impl.GZIPCompressor;
import ru.rerumu.backups.services.impl.ZFSProcessFactoryImpl;
import ru.rerumu.backups.zfs_api.ZFSReceive;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class ZFSRestoreService {

    private final String password;
    private final Logger logger = LoggerFactory.getLogger(ZFSRestoreService.class);
    private final ZFSProcessFactory zfsProcessFactory;
    private final boolean isDelete;
    private final FilePartRepository filePartRepository;

    public ZFSRestoreService(String password,
                             ZFSProcessFactory zfsProcessFactory,
                             boolean isDelete,
                             FilePartRepository filePartRepository) {
        this.password = password;
        this.zfsProcessFactory = zfsProcessFactory;
        this.isDelete = isDelete;
        this.filePartRepository = filePartRepository;
    }

    private void processReceivedFile(Path path) throws IOException {
        if (isDelete) {
            filePartRepository.delete(path);
        } else {
            filePartRepository.markReceived(path);
        }
    }

    private void readFromFile(ZFSReceive zfsReceive, Path nextInputPath)
            throws
            IOException,
            ClassNotFoundException,
            NoMorePartsException,
            FinishedFlagException,
            TooManyPartsException,
            EncryptException,
            CompressorException,
            EOFException{
        Cryptor cryptor = new AESCryptor(password);
        Compressor compressor = new GZIPCompressor();


        try (InputStream inputStream = Files.newInputStream(nextInputPath);
             ObjectInputStream objectInputStream = new ObjectInputStream(inputStream)) {
            logger.info(String.format("Reading file '%s'", nextInputPath.toString()));
            while (true) {
                logger.trace("Reading object from stream");
                Object object = objectInputStream.readUnshared();
                if (object instanceof CryptoMessage) {
                    logger.trace("Trying to cast to CryptoMessage");
                    CryptoMessage cryptoMessage = (CryptoMessage) object;

                    logger.trace("Trying to decrypt chunk");
                    byte[] tmp = cryptor.decryptChunk(cryptoMessage);

                    logger.trace("Trying to decompress chunk");
                    tmp = compressor.decompressChunk(tmp);

                    logger.trace("Writing chunk to stream");
                    zfsReceive.getBufferedOutputStream().write(tmp);
                    logger.trace("End writing chunk to stream");
                } else {
                    logger.error("Object is not instance of CryptoMessage");
                    throw new IOException();
                }

            }

        }
    }

    public void zfsReceive(ZFSPool zfsPool) throws
            IOException,
            TooManyPartsException,
            EncryptException,
            CompressorException,
            InterruptedException,
            ClassNotFoundException,
            FinishedFlagException,
            NoMorePartsException {
        ZFSReceive zfsReceive = zfsProcessFactory.getZFSReceive(zfsPool);
        try{
            while (true) {
                Path nextInputPath = filePartRepository.getNextInputPath();
                try {
                    readFromFile(zfsReceive, nextInputPath);
                } catch (NoMorePartsException e) {
                    logger.debug("No files found. Waiting 10 seconds before retry");
                    Thread.sleep(10000);
                    continue;
                } catch (FinishedFlagException e) {
                    logger.info("Finish flag found. Exiting loop");
                    break;
                } catch (EOFException e) {
                    logger.info(String.format("End of file '%s'", nextInputPath.toString()));
                    processReceivedFile(nextInputPath);
                    break;
                }


            }

            zfsReceive.close();

        } catch (Exception e){
            zfsReceive.close();
            throw  e;
        }


    }
}
