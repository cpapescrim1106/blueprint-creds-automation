package com.blueprint.relay;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Scans all Spring beans for fields that hold a JMS MessageListener and wraps them.
 */
final class MessageListenerFieldInstaller {
  private static final String MESSAGE_LISTENER = "javax.jms.MessageListener";

  private MessageListenerFieldInstaller() {
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
          Agent.log("Skipping bean '%s' during JMS field scan: %s (%s)",
              name,
              cause.getMessage(),
              cause.getClass().getName());
          continue;
        }
        if (bean == null) {
          continue;
        }

        Field[] fields = ContextUtils.getAllFields(bean.getClass());
        for (Field field : fields) {
          if (!ContextUtils.isAssignableTo(field.getType(), MESSAGE_LISTENER)) {
            continue;
          }
          try {
            field.setAccessible(true);
            Object current = field.get(bean);
            if (current == null) {
              continue;
            }
            Object proxy = MessageListenerProxy.wrap(current);
            if (proxy == null) {
              continue;
            }
            field.set(bean, proxy);
            Agent.log("Wrapped JMS listener field %s on bean '%s' (%s)",
                field.getName(),
                name,
                bean.getClass().getName());
          } catch (IllegalAccessException ex) {
            Agent.log("Unable to access JMS listener field %s on %s: %s",
                field.getName(),
                bean.getClass().getName(),
                ex.getMessage());
          }
        }
      }
    } catch (ReflectiveOperationException ex) {
      Agent.log("JMS field scan failed: %s", ex.getMessage());
    }
  }
}
