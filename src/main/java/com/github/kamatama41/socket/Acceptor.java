package com.github.kamatama41.socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ServerSocketChannel;

class Acceptor {
    private static final Logger log = LoggerFactory.getLogger(Acceptor.class);
    private boolean isRunning;
    private ServerSocketChannel serverChannel;
    private AcceptorLoop acceptor;
    private IOProcessor processor;
    private CommandWorker worker;
    private Context context;

    Acceptor(
            ServerSocketChannel serverChannel,
            IOProcessor processor,
            CommandWorker worker,
            Context context) {
        this.serverChannel = serverChannel;
        this.processor = processor;
        this.worker = worker;
        this.context = context;
        this.isRunning = false;
    }

    synchronized void start() {
        if (isRunning) {
            return;
        }
        isRunning = true;
        acceptor = new AcceptorLoop();
        acceptor.setName("acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    synchronized void stop() {
        if (!isRunning) {
            return;
        }
        isRunning = false;

        log.debug("Shutting down acceptor thread.");
        try {
            acceptor.interrupt();
            acceptor.join();
        } catch (InterruptedException e) {
            log.warn("Shutting down a acceptor thread interrupted.", e);
        }
    }

    private class AcceptorLoop extends Thread {
        @Override
        public void run() {
            while (isRunning || !isInterrupted()) {
                try {
                    IOProcessor.ProcessorLoop selected = processor.selectProcessor();
                    ServerConnection connection = new ServerConnection(
                            serverChannel.accept(),
                            selected,
                            worker,
                            context
                    );
                    connection.register();
                } catch (ClosedByInterruptException ignored) {
                } catch (Exception e) {
                    log.warn("An error occurred on acceptor.", e);
                }
            }
        }
    }
}
