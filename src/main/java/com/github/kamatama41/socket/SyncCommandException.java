package com.github.kamatama41.socket;

class SyncCommandException extends RuntimeException {
    SyncCommandException(String message) {
        super(message);
    }

    SyncCommandException(Throwable cause) {
        super(cause);
    }
}
