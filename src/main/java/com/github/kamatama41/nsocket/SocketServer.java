package com.github.kamatama41.nsocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;

public class SocketServer {
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
        this.context = new Context();
    }

    synchronized void start() throws IOException {
        if (isRunning) {
            return;
        }
        worker = CommandWorker.server(numOfWorkers, context.getCommandContext());
        processor = IOProcessor.server(numOfProcessors, context);
        acceptor = new Acceptor(serverChannel, processor, worker, context);
        registerCommand(new HeartbeatCommand());
        registerCommand(new SyncResultCommand(context.getCommandContext()));
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

    synchronized void stop() throws IOException {
        if (!isRunning) {
            return;
        }
        isRunning = false;

        processor.stop();
        acceptor.stop();
        worker.stop();
        serverChannel.close();
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

    public void registerCommand(Command command) {
        this.context.getCommandContext().registerCommand(command);
    }

    public void registerSyncCommand(SyncCommand syncCommand) {
        this.context.getCommandContext().registerSyncCommand(syncCommand);
    }
}
