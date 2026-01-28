package com.blueprint.relay;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

final class MessageListenerProxy implements InvocationHandler {
  private final Object target;

  private MessageListenerProxy(Object target) {
    this.target = target;
  }

  static Object wrap(Object target) {
    if (target == null) {
      return null;
    }
    Class<?>[] interfaces = target.getClass().getInterfaces();
    if (interfaces == null || interfaces.length == 0) {
      Agent.log("Cannot proxy MessageListener -- no interfaces exposed on %s", target.getClass().getName());
      return null;
    }
    Agent.log("Creating proxy for MessageListener using interfaces: %s", Arrays.toString(interfaces));
    return Proxy.newProxyInstance(
        target.getClass().getClassLoader(),
        interfaces,
        new MessageListenerProxy(target));
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if (method.getDeclaringClass() == Object.class) {
      return method.invoke(target, args);
    }

    boolean isOnMessage = "onMessage".equals(method.getName());
    if (isOnMessage) {
      RelayLogger.log("jms.onMessage args=%s", ArgumentFormatter.summarizeArgs(args));
    }

    try {
      Object result = method.invoke(target, args);
      if (isOnMessage) {
        RelayLogger.log("jms.onMessage -> %s", ArgumentFormatter.summarizeValue(result));
      }
      return result;
    } catch (InvocationTargetException ex) {
      Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
      if (isOnMessage) {
        RelayLogger.log("jms.onMessage threw %s: %s",
            cause.getClass().getName(),
            cause.getMessage());
      }
      throw cause;
    }
  }
}
