package com.blueprint.relay;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.*;

/**
 * Local HTTP server for triggering reports via the OMS controller bean.
 *
 * Accepts requests like:
 *   GET /report?name=Daily%20Cash%20Report
 *   GET /health
 */
public class LocalReportServer {
    private static final int PORT = 7777;
    private static final String BIND_ADDRESS = "127.0.0.1";
    private HttpServer server;
    private Object omsController;

    public LocalReportServer(Object omsController) {
        this.omsController = omsController;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(BIND_ADDRESS, PORT), 0);

        server.createContext("/report", new ReportHandler());
        server.createContext("/health", exchange -> {
            String response = "OK";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            exchange.getResponseBody().write(response.getBytes());
            exchange.close();
        });

        server.setExecutor(null); // Default executor (creates new thread per request)
        server.start();
        System.out.println("[RELAY] LocalReportServer started on http://" + BIND_ADDRESS + ":" + PORT);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("[RELAY] LocalReportServer stopped");
        }
    }

    /**
     * Parse query string into a map
     */
    private Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;

        for (String pair : query.split("&")) {
            try {
                int idx = pair.indexOf("=");
                String key = URLDecoder.decode(
                    idx > 0 ? pair.substring(0, idx) : pair,
                    "UTF-8"
                );
                String value = idx > 0 && pair.length() > idx + 1
                    ? URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                    : "";
                params.put(key, value);
            } catch (UnsupportedEncodingException e) {
                // UTF-8 should always be supported, but fallback to raw value
                int idx = pair.indexOf("=");
                String key = idx > 0 ? pair.substring(0, idx) : pair;
                String value = idx > 0 && pair.length() > idx + 1 ? pair.substring(idx + 1) : "";
                params.put(key, value);
            }
        }
        return params;
    }

    private class ReportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                if (!method.equals("GET")) {
                    sendError(exchange, 405, "Method not allowed");
                    return;
                }

                String query = exchange.getRequestURI().getQuery();
                Map<String, String> params = parseQueryString(query);

                String reportName = params.get("name");
                if (reportName == null || reportName.isEmpty()) {
                    sendError(exchange, 400, "Missing 'name' parameter");
                    return;
                }

                System.out.println("[RELAY] Report request received: " + reportName);

                // Try to invoke the report method
                Object result = invokeReport(reportName, params);

                String response = "Report triggered: " + reportName + "\nResult: " + (result != null ? result.toString() : "null");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                exchange.getResponseBody().write(response.getBytes());

            } catch (Exception e) {
                System.err.println("[RELAY] Error in report handler: " + e.getMessage());
                e.printStackTrace();
                try {
                    sendError(exchange, 500, e.getMessage());
                } catch (Exception ignore) {
                }
            } finally {
                try {
                    exchange.close();
                } catch (Exception ignore) {
                }
            }
        }

        private void sendError(HttpExchange exchange, int code, String message) throws IOException {
            exchange.sendResponseHeaders(code, message.getBytes().length);
            exchange.getResponseBody().write(message.getBytes());
            exchange.close();
        }
    }

    /**
     * Dynamically invoke report method on omsController
     */
    private Object invokeReport(String reportName, Map<String, String> params) throws Exception {
        Class<?> controllerClass = omsController.getClass();

        // Try common method signatures
        String[] methodNames = {
            "handleReportRequest",
            "submitReport",
            "generateReport",
            "requestReport",
            "runReport",
            "triggerReport",
            "exportReport",
            "createReport"
        };

        for (String methodName : methodNames) {
            try {
                // Try with String parameter
                java.lang.reflect.Method m = controllerClass.getMethod(methodName, String.class);
                System.out.println("[RELAY] Invoking: " + methodName + "(\"" + reportName + "\")");
                Object result = m.invoke(omsController, reportName);
                System.out.println("[RELAY]   -> Success: " + (result != null ? result.toString() : "null"));
                return result;
            } catch (NoSuchMethodException e) {
                // Try next
            }

            try {
                // Try with Map parameter (for parameters)
                java.lang.reflect.Method m = controllerClass.getMethod(methodName, Map.class);
                Map<String, Object> reportParams = new HashMap<>();
                reportParams.put("reportName", reportName);
                reportParams.putAll(params);
                System.out.println("[RELAY] Invoking: " + methodName + "(Map)");
                Object result = m.invoke(omsController, reportParams);
                System.out.println("[RELAY]   -> Success: " + (result != null ? result.toString() : "null"));
                return result;
            } catch (NoSuchMethodException e) {
                // Try next
            }
        }

        throw new NoSuchMethodException(
            "Could not find report method on " + controllerClass.getName() +
            ". Tried: " + Arrays.toString(methodNames)
        );
    }
}
