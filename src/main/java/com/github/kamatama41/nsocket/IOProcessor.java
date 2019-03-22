package com.github.kamatama41.nsocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

class IOProcessor {
    private static final Logger log = LoggerFactory.getLogger(IOProcessor.class);
    private final ProcessorLoop[] processors;
    private final String namePrefix;
    private final Context context;
    private boolean isRunning;
    private final AtomicInteger counter = new AtomicInteger(0);

    static IOProcessor server(int numOfProcessors, Context context) {
        return new IOProcessor("server", numOfProcessors, context);
    }

    static IOProcessor client(Context context) {
        return new IOProcessor("client", 1, context);
    }

    private IOProcessor(String namePrefix, int numOfProcessors, Context context) {
        this.isRunning = false;
        this.processors = new ProcessorLoop[numOfProcessors];
        this.namePrefix = namePrefix;
        this.context = context;
    }

    synchronized void start() throws IOException {
        if (isRunning) {
            return;
        }
        isRunning = true;
        for (int i = 0; i < processors.length; i++) {
            ProcessorLoop processor = new ProcessorLoop();
            processor.setName(namePrefix + "-processor-" + i);
            processor.setDaemon(true);
            processor.start();
            processors[i] = processor;
        }
    }

    synchronized void stop() {
        if (!isRunning) {
            return;
        }
        isRunning = false;

        log.debug("Stopping processor threads");
        for (ProcessorLoop processor : processors) {
            processor.shutdown();
            try {
                processor.join();
            } catch (InterruptedException e) {
                log.warn("Shutting down a processor thread interrupted.", e);
            }
        }
    }

    ProcessorLoop selectProcessor() {
        // TODO: More smart selection logic (e.g. most least connections)
        // Round robin
        return processors[counter.getAndAdd(1) % processors.length];
    }

    class ProcessorLoop extends Thread {
        private Queue<Event> eventQueue;
        Selector selector;

        ProcessorLoop() throws IOException {
            this.eventQueue = new ConcurrentLinkedQueue<>();
            this.selector = Selector.open();
        }

        void shutdown() {
            log.trace("shutdown");
            for (SelectionKey key : selector.keys()) {
                try {
                    key.channel().close();
                } catch (IOException e) {
                    log.warn("An error occurred with closing a key.", e);
                }
            }
            selector.wakeup();
        }

        @Override
        public void run() {
            long lastHeartbeatTime = System.currentTimeMillis();
            while (isRunning) {
                try {
                    Event event;
                    while ((event = eventQueue.poll()) != null) {
                        event.process();
                    }
                    final long now = System.currentTimeMillis();
                    if (now - lastHeartbeatTime >= context.getHeartbeatInterval()) {
                        lastHeartbeatTime = now;
                        sendHeartbeat();
                    }
                    select();
                } catch (Exception e) {
                    log.warn("An error occurred on processor.", e);
                }
            }
        }

        Selector getSelector() {
            return selector;
        }

        void addEvent(Event event) {
            log.trace("addEvent");
            eventQueue.add(event);
            selector.wakeup();
        }

        private void select() throws IOException {
            int select = selector.select(1000L);
            if (select == 0) {
                return;
            }

            final Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();
                Connection connection = (Connection) key.attachment();

                if (!key.isValid()) {
                    connection.close();
                    continue;
                }

                if (key.isConnectable()) {
                    log.trace("onConnectable");
                    connection.onConnectable();
                }
                if (key.isWritable()) {
                    log.trace("onWritable");
                    connection.onWritable();
                }
                if (key.isReadable()) {
                    log.trace("onReadable");
                    connection.onReadable();
                }
            }
        }

        private void sendHeartbeat() {
            for (SelectionKey key : selector.keys()) {
                if (key.attachment() == null) {
                    continue;
                }
                Connection connection = (Connection) key.attachment();
                if (!connection.isOpen()) {
                    continue;
                }
                try {
                    connection.sendHeartbeat();
                } catch (IOException e) {
                    log.warn("Failed to send heartbeat.");
                }
            }
        }
    }

    @FunctionalInterface
    interface Event {
        void process() throws Exception;
    }
}
