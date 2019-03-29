package com.github.kamatama41.nsocket;

import com.github.kamatama41.nsocket.codec.ObjectCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;

public class SocketServer {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final ServerSocketChannel serverChannel;
    private final Context context;
    private IOProcessor processor;
    private CommandWorker worker;
    private Acceptor acceptor;
    private boolean isRunning;
    private String host = "127.0.0.1";
    private int port = 30000;
    private int numOfWorkers = 1;
    private int numOfProcessors = Runtime.getRuntime().availableProcessors();

    public SocketServer() throws IOException {
        this.isRunning = false;
        this.serverChannel = ServerSocketChannel.open();
        this.context = new Context("server");
        Thread shutdownHook = new Thread(this::shutdownHook);
        shutdownHook.setName("shutdownHook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    public synchronized void start() throws IOException {
        if (isRunning) {
            return;
        }
        log.info("Starting server..");
        worker = CommandWorker.server(numOfWorkers, context);
        processor = IOProcessor.server(numOfProcessors, context);
        acceptor = new Acceptor(serverChannel, processor, worker, context);
        registerCommand(new HeartbeatCommand());
        registerCommand(new SyncResultCommand(context));
        registerCommand(new ErrorCommand());

        serverChannel.configureBlocking(true);
        serverChannel.socket().setSoTimeout(0);
        serverChannel.socket().setReuseAddress(true);
        serverChannel.socket().bind(new InetSocketAddress(host, port));

        worker.start();
        processor.start();
        acceptor.start();
        isRunning = true;
    }

    public synchronized void stop() throws IOException {
        if (!isRunning) {
            return;
        }
        isRunning = false;

        processor.stop();
        acceptor.stop();
        worker.stop();
        serverChannel.close();
    }

    public void setName(String name) {
        this.context.setName(name);
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setNumOfWorkers(int numOfWorkers) {
        this.numOfWorkers = numOfWorkers;
    }

    public void setNumOfProcessors(int numOfProcessors) {
        this.numOfProcessors = numOfProcessors;
    }

    public void setDefaultContentBufferSize(int defaultContentBufferSize) {
        this.context.setDefaultContentBufferSize(defaultContentBufferSize);
    }

    public void registerCommand(Command command) {
        this.context.getCommandRegistry().registerCommand(command);
    }

    public void registerSyncCommand(SyncCommand syncCommand) {
        this.context.getCommandRegistry().registerSyncCommand(syncCommand);
    }

    public void registerListener(CommandListener listener) {
        this.context.getListenerRegistry().registerListener(listener);
    }

    public void setCodec(ObjectCodec codec) {
        this.context.setCodec(codec);
    }

    private void shutdownHook() {
        try {
            log.info("Shutdown detected. Stopping server..");
            stop();
        } catch (IOException e) {
            log.warn("Failed to stop server.", e);
        }
    }
}
