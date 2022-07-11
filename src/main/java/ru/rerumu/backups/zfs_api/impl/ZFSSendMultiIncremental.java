package ru.rerumu.backups.zfs_api.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rerumu.backups.factories.ProcessWrapperFactory;
import ru.rerumu.backups.models.Snapshot;
import ru.rerumu.backups.zfs_api.ProcessWrapper;
import ru.rerumu.backups.zfs_api.ZFSSend;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public class ZFSSendMultiIncremental  implements ZFSSend {
    protected final Logger logger = LoggerFactory.getLogger(ZFSSendMultiIncremental.class);

    private final ProcessWrapper processWrapper;

    public ZFSSendMultiIncremental(
            Snapshot baseSnapshot,
            Snapshot incrementalSnapshot,
            ProcessWrapperFactory processWrapperFactory)
            throws IOException {
//        super(Arrays.asList("zfs", "send", "-vpPI", baseSnapshot.getFullName(), incrementalSnapshot.getFullName()));

//        setStderrProcessor(logger::debug);

        processWrapper = processWrapperFactory.getProcessWrapper(
                Arrays.asList("zfs", "send", "-vpPI", baseSnapshot.getFullName(), incrementalSnapshot.getFullName())
        );

        processWrapper.setStderrProcessor(logger::error);

        processWrapper.run();
    }

    @Override
    public BufferedInputStream getBufferedInputStream() {
        return processWrapper.getBufferedInputStream();
    }

    @Override
    public void close() throws InterruptedException, IOException, ExecutionException {
        processWrapper.close();
    }

    @Override
    public void kill() throws InterruptedException, IOException, ExecutionException {
        processWrapper.kill();
    }
}
