package com.blueprint.relay;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

final class MessageListenerProxyInstaller {
  private static final String MESSAGE_LISTENER = "javax.jms.MessageListener";
  private static final String SETTER_NAME = "setMessageListener";

  private MessageListenerProxyInstaller() {
  }

  static void install(Object context) {
    if (context == null) {
      return;
    }
    try {
      Method namesMethod = context.getClass().getMethod("getBeanDefinitionNames");
      String[] beanNames = (String[]) namesMethod.invoke(context);
      if (beanNames == null) {
        return;
      }

      Method getBean = context.getClass().getMethod("getBean", String.class);
      for (String name : beanNames) {
        Object bean;
        try {
          bean = getBean.invoke(context, name);
        } catch (InvocationTargetException ex) {
          Throwable cause = ContextUtils.unwrapInvocationTarget(ex);
          Agent.log("Skipping bean '%s' during JMS install: %s (%s)",
              name,
              cause.getMessage(),
              cause.getClass().getName());
          continue;
        }
        if (bean == null) {
          continue;
        }

        // If this bean is a MessageListener, wrap it.
        if (ContextUtils.isAssignableTo(bean.getClass(), MESSAGE_LISTENER)) {
          Object proxy = MessageListenerProxy.wrap(bean);
          if (proxy != null && replaceSingleton(context, name, proxy)) {
            Agent.log("Registered JMS listener proxy for bean '%s'", name);
          }
        }

        // If this bean can accept a MessageListener, try to inject a proxy.
        Method setter = findSetter(bean.getClass());
        if (setter != null) {
          Object currentListener = null;
          try {
            Method getter = ContextUtils.findMethod(bean.getClass(), "getMessageListener");
            if (getter != null) {
              currentListener = getter.invoke(bean);
            }
          } catch (ReflectiveOperationException ignored) {
            // no-op
          }
          if (currentListener != null && ContextUtils.isAssignableTo(currentListener.getClass(), MESSAGE_LISTENER)) {
            Object proxy = MessageListenerProxy.wrap(currentListener);
            if (proxy != null) {
              try {
                setter.invoke(bean, proxy);
                Agent.log("Injected JMS listener proxy into bean '%s' (%s)", name, bean.getClass().getName());
              } catch (ReflectiveOperationException ex) {
                Agent.log("Failed to call %s on %s: %s", SETTER_NAME, bean.getClass().getName(), ex.getMessage());
              }
            }
          }
        }
      }
    } catch (ReflectiveOperationException ex) {
      Agent.log("JMS proxy install failed: %s", ex.getMessage());
    }
  }

  private static boolean replaceSingleton(Object context, String beanName, Object proxy) {
    try {
      Method getBeanFactory = findMethod(context.getClass(), "getBeanFactory");
      if (getBeanFactory == null) {
        return false;
      }
      Object factory = getBeanFactory.invoke(context);
      if (factory == null) {
        return false;
      }

      Method destroy = findMethod(factory.getClass(), "destroySingleton", String.class);
      if (destroy != null) {
        destroy.invoke(factory, beanName);
      }

      Method register = findMethod(factory.getClass(), "registerSingleton", String.class, Object.class);
      if (register == null) {
        return false;
      }
      register.invoke(factory, beanName, proxy);
      return true;
    } catch (ReflectiveOperationException ex) {
      Agent.log("JMS bean factory replace failed for %s: %s", beanName, ex.getMessage());
      return false;
    }
  }

  private static Method findSetter(Class<?> type) {
    for (Method method : type.getMethods()) {
      if (!method.getName().equals(SETTER_NAME) || method.getParameterCount() != 1) {
        continue;
      }
      Class<?> parameterType = method.getParameterTypes()[0];
      if (!ContextUtils.isAssignableTo(parameterType, MESSAGE_LISTENER)) {
        continue;
      }
      return method;
    }
    return null;
  }

  private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
    Class<?> current = type;
    while (current != null) {
      try {
        Method method = current.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
      } catch (NoSuchMethodException ignored) {
        current = current.getSuperclass();
      }
    }
    for (Class<?> iface : type.getInterfaces()) {
      try {
        Method method = iface.getMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
      } catch (NoSuchMethodException ignored) {
        // keep searching
      }
    }
    Agent.log("Method '%s' not found on %s (params %s)", name, type.getName(), Arrays.toString(parameterTypes));
    return null;
  }
}
