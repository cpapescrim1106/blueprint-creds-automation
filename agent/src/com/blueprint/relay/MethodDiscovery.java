package com.blueprint.relay;

import java.lang.reflect.Method;

/**
 * Discovers and logs report-related methods on a bean.
 * Used for debugging to understand what methods are available.
 */
public class MethodDiscovery {

    /**
     * Scan a bean for report-related methods and log them
     */
    public static void discoverReportMethods(Object bean) {
        Class<?> clazz = bean.getClass();

        System.out.println("[RELAY] === Discovering methods on " + clazz.getName() + " ===");

        Method[] methods = clazz.getMethods();
        int found = 0;

        for (Method m : methods) {
            String name = m.getName();
            String lcName = name.toLowerCase();

            // Look for report-related keywords
            if (lcName.contains("report")
                || lcName.contains("request")
                || lcName.contains("export")
                || lcName.contains("generate")
                || lcName.contains("handle")
                || lcName.contains("submit")
                || lcName.contains("run")
                || lcName.contains("send")) {

                StringBuilder sig = new StringBuilder();
                sig.append(name).append("(");
                Class<?>[] params = m.getParameterTypes();
                for (int i = 0; i < params.length; i++) {
                    if (i > 0) sig.append(", ");
                    sig.append(params[i].getSimpleName());
                }
                sig.append(") -> ").append(m.getReturnType().getSimpleName());

                System.out.println("[RELAY]   FOUND: " + sig.toString());
                found++;
            }
        }

        if (found == 0) {
            System.out.println("[RELAY]   (no report-related methods found)");
        } else {
            System.out.println("[RELAY] Total methods found: " + found);
        }
    }
}
