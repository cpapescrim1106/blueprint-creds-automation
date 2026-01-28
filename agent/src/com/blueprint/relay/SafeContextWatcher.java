package com.blueprint.relay;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Simple background thread that waits for Spring context to be ready.
 * No instrumentation hooks - just pure polling via reflection.
 */
public class SafeContextWatcher implements Runnable {
    private static final int MAX_WAIT_TIME = 180000; // 3 minutes
    private static final int POLL_INTERVAL = 1000; // Check every second

    @Override
    public void run() {
        System.out.println("[RELAY] SafeContextWatcher started");

        try {
            long startTime = System.currentTimeMillis();

            while (System.currentTimeMillis() - startTime < MAX_WAIT_TIME) {
                try {
                    // Try to find the context by looking through all loaded classes
                    Object context = findSpringContext();

                    if (context != null) {
                        System.out.println("[RELAY] Spring context found!");
                        // Wait a bit for beans to be fully instantiated
                        Thread.sleep(2000);
                        initializeServer(context);
                        return; // Done
                    }

                    Thread.sleep(POLL_INTERVAL);
                } catch (Exception e) {
                    Thread.sleep(POLL_INTERVAL);
                }
            }

            System.out.println("[RELAY] Timeout waiting for Spring context");

        } catch (Exception e) {
            System.err.println("[RELAY] Error in SafeContextWatcher: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Find Spring context by checking the thread context class loader
     */
    private Object findSpringContext() throws Exception {
        try {
            // Try to load GenericApplicationContext
            Class<?> contextClass = Class.forName("org.springframework.context.support.GenericApplicationContext");

            // Get all instances via reflection inspection
            // Look in commonly used static holder classes
            Class<?>[] commonHolders = {
                Class.forName("com.blueprint.oms.gui.OMSClient"),
                Class.forName("com.blueprint.oms.controller.OMSControllerImpl")
            };

            for (Class<?> holder : commonHolders) {
                try {
                    Field[] fields = holder.getDeclaredFields();
                    for (Field field : fields) {
                        try {
                            if (field.getType().getName().contains("ApplicationContext")) {
                                field.setAccessible(true);
                                Object value = field.get(null);
                                if (value != null && value.getClass().getName().contains("GenericApplicationContext")) {
                                    System.out.println("[RELAY] Found context in " + holder.getSimpleName() + "." + field.getName());
                                    return value;
                                }
                            }
                        } catch (IllegalArgumentException e) {
                            // Instance field, skip
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        return null;
    }

    /**
     * Initialize the report server once context is available
     */
    private void initializeServer(Object context) {
        try {
            System.out.println("[RELAY] Context type: " + context.getClass().getName());

            // Retry up to 5 times to get the bean
            for (int attempt = 1; attempt <= 5; attempt++) {
                try {
                    System.out.println("[RELAY] Getting omsController bean (attempt " + attempt + ")...");

                    Method getBean = context.getClass().getMethod("getBean", String.class);
                    Object omsController = getBean.invoke(context, "omsController");

                    if (omsController != null) {
                        System.out.println("[RELAY] omsController found: " + omsController.getClass().getSimpleName());

                        // Discover methods
                        MethodDiscovery.discoverReportMethods(omsController);

                        // Start server
                        LocalReportServer server = new LocalReportServer(omsController);
                        server.start();

                        System.out.println("[RELAY] Success! Report server running on 127.0.0.1:7777");
                        return;
                    } else {
                        System.out.println("[RELAY] omsController is null");
                    }
                    break; // Don't retry if getBean succeeded but returned null
                } catch (Exception e) {
                    System.out.println("[RELAY] Attempt " + attempt + " failed: " + e.getClass().getSimpleName());
                    if (attempt < 5) {
                        Thread.sleep(1000); // Wait before retry
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("[RELAY] Error initializing server: " + e.getClass().getSimpleName());

            // Print the root cause
            Throwable cause = e.getCause();
            if (cause != null) {
                System.err.println("[RELAY] Caused by: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            } else {
                System.err.println("[RELAY] Message: " + e.getMessage());
            }

            // Don't print full stack trace - just note that server init failed
            System.err.println("[RELAY] Could not initialize HTTP server. Beans may not be ready yet.");
        }
    }
}
