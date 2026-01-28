package com.blueprint.relay;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public final class HttpRequestLogUtil {
  private HttpRequestLogUtil() {
  }

  public static void logHttpRequest(Object request) {
    if (request == null) {
      return;
    }

    String url = extractUrl(request);
    List<String> headers = extractHeaderNames(request);

    if (url != null || !headers.isEmpty()) {
      Agent.log("HTTP request: %s headers=%s", url == null ? "(unknown)" : url, headers);
    }
  }

  private static String extractUrl(Object request) {
    try {
      Method getUri = request.getClass().getMethod("getURI");
      Object uriObj = getUri.invoke(request);
      if (uriObj instanceof URI) {
        URI uri = (URI) uriObj;
        StringBuilder sb = new StringBuilder();
        if (uri.getScheme() != null) {
          sb.append(uri.getScheme()).append("://");
        }
        if (uri.getHost() != null) {
          sb.append(uri.getHost());
        }
        if (uri.getPort() != -1) {
          sb.append(":").append(uri.getPort());
        }
        if (uri.getPath() != null) {
          sb.append(uri.getPath());
        }
        return sb.toString();
      }
    } catch (Exception ignore) {
    }
    return null;
  }

  private static List<String> extractHeaderNames(Object request) {
    List<String> names = new ArrayList<>();
    try {
      Method getAllHeaders = request.getClass().getMethod("getAllHeaders");
      Object headersObj = getAllHeaders.invoke(request);
      if (headersObj != null && headersObj.getClass().isArray()) {
        Object[] headers = (Object[]) headersObj;
        for (Object header : headers) {
          if (header == null) {
            continue;
          }
          try {
            Method getName = header.getClass().getMethod("getName");
            Object nameObj = getName.invoke(header);
            if (nameObj instanceof String) {
              names.add((String) nameObj);
            }
          } catch (Exception ignore) {
          }
        }
      }
    } catch (Exception ignore) {
    }
    return names;
  }
}
