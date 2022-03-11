package ru.rerumu.backups.services;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;

public interface ZFSReceive {

    BufferedOutputStream getBufferedOutputStream();
    void close() throws InterruptedException, IOException;
}
