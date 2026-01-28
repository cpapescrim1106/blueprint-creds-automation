package com.blueprint.relay;

import net.bytebuddy.asm.Advice;

public final class AwsProviderAdvice {
  @Advice.OnMethodExit(onThrowable = Throwable.class)
  public static void onExit(@Advice.This Object provider,
                            @Advice.Return Object returned,
                            @Advice.Thrown Throwable thrown) {
    if (thrown != null) {
      RelayLogger.log("aws.provider.%s threw %s: %s",
          provider != null ? provider.getClass().getName() : "<null>",
          thrown.getClass().getName(),
          thrown.getMessage());
      return;
    }

    if (returned == null) {
      RelayLogger.log("aws.provider.%s -> null", provider != null ? provider.getClass().getName() : "<null>");
      return;
    }

    String accessKey = readString(returned, "accessKeyId");
    String sessionToken = readString(returned, "sessionToken");
    boolean hasSession = sessionToken != null && !sessionToken.isEmpty();
    
    boolean reveal = "true".equalsIgnoreCase(System.getProperty("relay.dump.secrets"));

    if (reveal) {
      String secretKey = readString(returned, "secretAccessKey");
      RelayLogger.log("aws.provider.%s -> key=%s secret=%s session=%s",
          provider != null ? provider.getClass().getName() : "<null>",
          accessKey,
          secretKey,
          hasSession);
    } else {
      String maskedKey = mask(accessKey);
      RelayLogger.log("aws.provider.%s -> key=%s session=%s",
          provider != null ? provider.getClass().getName() : "<null>",
          maskedKey,
          hasSession);
    }
  }

  public static String readString(Object target, String methodName) {
    try {
      java.lang.reflect.Method method = target.getClass().getMethod(methodName);
      Object result = method.invoke(target);
      return result == null ? null : String.valueOf(result);
    } catch (Exception ignored) {
      return null;
    }
  }

  public static String mask(String key) {
    if (key == null || key.length() < 4) {
      return "<none>";
    }
    return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
  }
}
