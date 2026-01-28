package com.blueprint.relay;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Arrays;

public final class ArgumentFormatter {
  private static final int MAX_LENGTH = 200;
  private static final int JMS_TEXT_MAX = 20000;

  private ArgumentFormatter() {
  }

  interface DetailedValue {
    String describe();
  }

  public static String summarizeArgs(Object[] args) {
    if (args == null || args.length == 0) {
      return "[]";
    }
    String[] pieces = new String[args.length];
    for (int i = 0; i < args.length; i++) {
      pieces[i] = summarizeValue(args[i]);
    }
    return Arrays.toString(pieces);
  }

  public static String summarizeValue(Object value) {
    if (value == null) {
      return "null";
    }

    if (value instanceof DetailedValue) {
      return ((DetailedValue) value).describe();
    }

    String reportDetails = describeReportLike(value);
    if (reportDetails != null) {
      return reportDetails;
    }

    String jmsDetails = describeJmsMessage(value);
    if (jmsDetails != null) {
      return jmsDetails;
    }

    if (value.getClass().isArray()) {
      return summarizeArray(value);
    }

    String type = value.getClass().getSimpleName();
    String repr = safeToString(value);
    return type + "(" + abbreviate(repr) + ")";
  }

  private static String describeReportLike(Object value) {
    String className = value.getClass().getName();
    if (!className.toLowerCase().contains("report")) {
      return null;
    }
    String reportName = readString(value, "getReportName");
    if (reportName == null) {
      reportName = readString(value, "getName");
    }
    String startDate = readString(value, "getStartDate");
    if (startDate == null) {
      startDate = readString(value, "getDateStart");
    }
    String endDate = readString(value, "getEndDate");
    if (endDate == null) {
      endDate = readString(value, "getDateEnd");
    }
    String locationIds = readString(value, "getLocationIds");
    if (locationIds == null) {
      locationIds = readString(value, "getLocations");
    }
    String userId = readString(value, "getUserId");
    String type = value.getClass().getSimpleName();
    StringBuilder builder = new StringBuilder();
    builder.append(type).append("{");
    if (reportName != null) {
      builder.append("name=").append(reportName).append(", ");
    }
    if (startDate != null || endDate != null) {
      builder.append("range=").append(startDate).append("..").append(endDate).append(", ");
    }
    if (locationIds != null) {
      builder.append("locations=").append(locationIds).append(", ");
    }
    if (userId != null) {
      builder.append("userId=").append(userId).append(", ");
    }
    builder.append("raw=").append(abbreviate(safeToString(value))).append("}");
    return builder.toString();
  }

  private static String describeJmsMessage(Object value) {
    if (!implementsInterface(value, "javax.jms.Message")) {
      return null;
    }
    StringBuilder builder = new StringBuilder();
    builder.append("JMSMessage{");
    builder.append("type=").append(value.getClass().getSimpleName());
    String correlation = readString(value, "getJMSCorrelationID");
    String messageId = readString(value, "getJMSMessageID");
    String jmsType = readString(value, "getJMSType");
    if (correlation != null) {
      builder.append(", correlation=").append(correlation);
    }
    if (messageId != null) {
      builder.append(", id=").append(messageId);
    }
    if (jmsType != null) {
      builder.append(", jmsType=").append(jmsType);
    }
    String text = readString(value, "getText");
    if (text != null) {
      builder.append(", text=").append(truncateJms(text));
    }
    Object obj = readObject(value, "getObject");
    if (obj != null) {
      builder.append(", object=").append(abbreviate(safeToString(obj)));
    }
    builder.append("}");
    return builder.toString();
  }

  private static boolean implementsInterface(Object value, String ifaceName) {
    Class<?> current = value.getClass();
    while (current != null) {
      for (Class<?> iface : current.getInterfaces()) {
        if (iface.getName().equals(ifaceName)) {
          return true;
        }
      }
      current = current.getSuperclass();
    }
    return false;
  }

  private static String readString(Object target, String methodName) {
    try {
      Method method = target.getClass().getMethod(methodName);
      Object result = method.invoke(target);
      return result == null ? null : String.valueOf(result);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static Object readObject(Object target, String methodName) {
    try {
      Method method = target.getClass().getMethod(methodName);
      return method.invoke(target);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String summarizeArray(Object array) {
    int length = Array.getLength(array);
    StringBuilder builder = new StringBuilder();
    builder.append(array.getClass().getComponentType() != null
        ? array.getClass().getComponentType().getSimpleName()
        : "Object");
    builder.append("[").append(length).append("]=");
    if (array instanceof Object[]) {
      builder.append(Arrays.toString((Object[]) array));
    } else if (array instanceof int[]) {
      builder.append(Arrays.toString((int[]) array));
    } else if (array instanceof long[]) {
      builder.append(Arrays.toString((long[]) array));
    } else if (array instanceof double[]) {
      builder.append(Arrays.toString((double[]) array));
    } else if (array instanceof float[]) {
      builder.append(Arrays.toString((float[]) array));
    } else if (array instanceof boolean[]) {
      builder.append(Arrays.toString((boolean[]) array));
    } else if (array instanceof byte[]) {
      builder.append(Arrays.toString((byte[]) array));
    } else if (array instanceof short[]) {
      builder.append(Arrays.toString((short[]) array));
    } else if (array instanceof char[]) {
      builder.append(Arrays.toString((char[]) array));
    } else {
      builder.append("?");
    }
    return abbreviate(builder.toString());
  }

  private static String abbreviate(String input) {
    if (input == null) {
      return "";
    }
    if (input.length() <= MAX_LENGTH) {
      return input;
    }
    return input.substring(0, MAX_LENGTH) + "...";
  }

  private static String truncateJms(String input) {
    if (input == null) {
      return "";
    }
    if (input.length() <= JMS_TEXT_MAX) {
      return input;
    }
    return input.substring(0, JMS_TEXT_MAX) + "...";
  }

  private static String safeToString(Object value) {
    try {
      return String.valueOf(value);
    } catch (Throwable throwable) {
      return "<" + value.getClass().getName() + ">";
    }
  }
}
