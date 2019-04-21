package com.github.kamatama41.nsocket;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

class TlsContext {
    private SSLContext sslContext;
    private boolean useClientMode;
    private boolean needClientAuth;

    TlsContext(boolean isServer) {
        this.sslContext = null;
        this.useClientMode = !isServer;
        this.needClientAuth = false;
    }

    boolean useTls() {
        return sslContext != null;
    }

    void setSslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    void enableSslClientAuth() {
        this.needClientAuth = true;
    }

    SSLEngine createSSLEngine() {
        SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setUseClientMode(useClientMode);
        sslEngine.setNeedClientAuth(needClientAuth);
        return sslEngine;
    }
}
