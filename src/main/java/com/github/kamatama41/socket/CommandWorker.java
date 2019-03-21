package com.github.kamatama41.socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class CommandWorker {
    private static final Logger log = LoggerFactory.getLogger(CommandWorker.class);
    private final WorkerLoop[] workers;
    private final BlockingQueue<CommandRequest> requestQueue;
    private final String namePrefix;
    private final CommandContext context;
    private final ExecutorService executorService;
    private boolean isRunning;

    static CommandWorker server(int numOfWorkers, CommandContext context) {
        return new CommandWorker("server", numOfWorkers, context);
    }

    static CommandWorker client(CommandContext context) {
        return new CommandWorker("client", 1, context);
    }

    private CommandWorker(String namePrefix, int numOfWorkers, CommandContext context) {
        this.requestQueue = new LinkedBlockingQueue<>();
        this.namePrefix = namePrefix;
        this.workers = new WorkerLoop[numOfWorkers];
        this.context = context;
        this.executorService = Executors.newCachedThreadPool();
        this.isRunning = false;
    }

    synchronized void start() {
        if (isRunning) {
            return;
        }
        isRunning = true;
        for (int i = 0; i < workers.length; i++) {
            WorkerLoop worker = new WorkerLoop();
            worker.setName(namePrefix + "-worker-" + i);
            worker.setDaemon(true);
            worker.start();
            workers[i] = worker;
        }
    }

    synchronized void stop() {
        if (!isRunning) {
            return;
        }
        isRunning = false;

        log.debug("Stopping worker threads");
        for (WorkerLoop worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                log.warn("Exception occurred with stopping worker thread.", e);
            }
        }
        executorService.shutdown();
    }

    void addRequest(CommandRequest request) {
        requestQueue.add(request);
    }

    private class WorkerLoop extends Thread {
        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            while (isRunning) {
                try {
                    CommandRequest request = requestQueue.poll(1, TimeUnit.SECONDS);
                    if (request == null) {
                        continue;
                    }
                    String dataJson = request.getDataJson();
                    CommandData data = context.decode(dataJson);
                    Connection connection = request.getConnection();

                    Object body = data.getBody();
                    String commandId = data.getCommandId();
                    Integer callId = data.getCallId();
                    Class<?> dataClass = context.getDataClass(commandId);
                    if (dataClass == null) {
                        log.warn("DataClass for '{}' not found.", commandId);
                        continue;
                    }
                    body = context.convert(body, dataClass);

                    Command command = context.getCommand(commandId);
                    if (command != null) {
                        command.execute(body, connection);
                        continue;
                    }
                    SyncCommand syncCommand = context.getSyncCommand(commandId);
                    if (syncCommand != null) {
                        runSyncCommand(syncCommand, commandId, callId, body, connection);
                        continue;
                    }

                    log.warn("CommandID '{}' not found.", commandId);

                } catch (Exception e) {
                    log.warn("Exception occurred on worker thread.", e);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void runSyncCommand(
            SyncCommand syncCommand, String commandId, Integer callId, Object data, Connection connection) {
        Future<Object> future = executorService.submit(() -> syncCommand.apply(data, connection));
        SyncResultData resultData = new SyncResultData(commandId, callId);
        try {
            Object result = future.get(syncCommand.getTimeoutMillis(), TimeUnit.MILLISECONDS);
            resultData.setResult(result);
            resultData.setStatus(SyncResultData.Status.SUCCEEDED);
        } catch (TimeoutException e) {
            resultData.setStatus(SyncResultData.Status.TIMEOUT);
            log.warn(String.format("Running a SyncCommand '%s:%d' timed out.", syncCommand, callId), e);
        } catch (ExecutionException e) {
            resultData.setStatus(SyncResultData.Status.FAILED);
            log.warn(String.format("Running a SyncCommand '%s:%d' failed.", syncCommand, callId), e.getCause());
        } catch (Exception e) {
            resultData.setStatus(SyncResultData.Status.FAILED);
            log.warn(String.format("Running a SyncCommand '%s:%d' failed.", syncCommand, callId), e);
        }
        connection.sendCommand(SyncResultCommand.COMMAND_ID, resultData);
    }
}
