package com.github.kamatama41.nsocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

public class SocketClient {
    private final IOProcessor processor;
    private final CommandWorker worker;
    private final Context context;
    private ClientConnection connection;
    private String host = "127.0.0.1";
    private int port = 30000;

    public SocketClient() {
        this.context = new Context();
        this.worker = CommandWorker.client(context);
        this.processor = IOProcessor.client(context);
    }

    public synchronized void open() throws IOException {
        if (connection != null && connection.isOpen()) {
            return;
        }

        SocketChannel channel = SocketChannel.open();
        registerCommand(new HeartbeatCommand());
        registerCommand(new SyncResultCommand(context));
        registerCommand(new ErrorCommand());

        worker.start();
        processor.start();
        connection = new ClientConnection(channel, processor.selectProcessor(), worker, context);
        connection.connect(new InetSocketAddress(host, port));
    }

    public synchronized void close() throws IOException {
        if (connection == null) {
            return;
        }
        if (!connection.isOpen()) {
            return;
        }
        processor.stop();
        worker.stop();
        connection.close();
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

    public void sendCommand(String id, Object body) {
        connection.sendCommand(id, body);
    }

    public <R> R sendSyncCommand(String id, Object body) {
        return connection.sendSyncCommand(id, body);
    }
}
