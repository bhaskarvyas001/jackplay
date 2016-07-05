package jackplay.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import jackplay.JackLogger;
import jackplay.play.Composer;

import java.io.IOException;
import java.lang.instrument.Instrumentation;

public class RootHandler implements HttpHandler {
    private final Instrumentation inst;
    private final Composer composer;

    public RootHandler(Instrumentation inst, Composer composer) {
        this.inst = inst;
        this.composer = composer;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String uri = exchange.getRequestURI().toString().substring("/jackplay".length());
        if (uri.isEmpty()) uri = "/index.html";
        JackLogger.debug("URI:" + uri);

        if (uri.startsWith("/play")) {
            new PlayHandler(inst, composer).handle(exchange);
        } else {
            if ("get".equalsIgnoreCase(exchange.getRequestMethod())) {
                CommonHandling.serveStaticResource(exchange, 200, uri);
            } else {
                CommonHandling.error_404(exchange);
            }
        }
    }
}