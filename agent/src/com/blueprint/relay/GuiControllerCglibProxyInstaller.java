package com.blueprint.relay;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

final class GuiControllerCglibProxyInstaller {
  private static final String TARGET_BEAN_NAME = "guiController";
  private static final String SETTER_NAME = "setGuiController";

  private GuiControllerCglibProxyInstaller() {
  }

  static void install(Object context) {
    if (context == null) {
      return;
    }
    Object original = null;
    try {
      original = ContextUtils.getBean(context, TARGET_BEAN_NAME);
      if (original == null) {
        Agent.log("guiController bean not found, skipping GUI proxy install");
        return;
      }
      Object proxy = wrapWithCglib(original);
      if (proxy == null) {
        return;
      }

      if (replaceSingleton(context, proxy)) {
        Agent.log("Registered guiController CGLIB proxy in bean factory");
      } else {
        Agent.log("Unable to replace guiController singleton cleanly");
      }

      updateConsumers(context, proxy);
    } catch (ReflectiveOperationException ex) {
      Agent.log("GUI proxy install failed: %s", ex.getMessage());
    }
  }

  private static Object wrapWithCglib(Object target) {
    try {
      Class<?> enhancerClass = Class.forName("org.springframework.cglib.proxy.Enhancer");
      Class<?> interceptorClass = Class.forName("org.springframework.cglib.proxy.MethodInterceptor");
      Class<?> methodProxyClass = Class.forName("org.springframework.cglib.proxy.MethodProxy");

      Object interceptor = java.lang.reflect.Proxy.newProxyInstance(
          interceptorClass.getClassLoader(),
          new Class<?>[] {interceptorClass},
          (proxy, method, args) -> {
            String name = method.getName();
            if ("intercept".equals(name) && args != null && args.length == 4) {
              Object obj = args[0];
              Method invoked = (Method) args[1];
              Object[] mArgs = (Object[]) args[2];
              Object methodProxy = args[3];

              if (invoked.getDeclaringClass() == Object.class) {
                return invoked.invoke(target, mArgs);
              }

              boolean reportRelated = isReportRelated(invoked, mArgs);
              if (reportRelated) {
                RelayLogger.log("guiController.%s args=%s",
                    invoked.getName(),
                    ArgumentFormatter.summarizeArgs(mArgs));
              }

              Method invokeMethod = methodProxyClass.getMethod("invoke", Object.class, Object[].class);
              Object result = invokeMethod.invoke(methodProxy, target, mArgs);

              if (reportRelated) {
                RelayLogger.log("guiController.%s -> %s",
                    invoked.getName(),
                    ArgumentFormatter.summarizeValue(result));
              }
              return result;
            }
            return null;
          });

      Object enhancer = enhancerClass.getDeclaredConstructor().newInstance();
      Method setSuperclass = enhancerClass.getMethod("setSuperclass", Class.class);
      Method setClassLoader = enhancerClass.getMethod("setClassLoader", ClassLoader.class);
      Method setCallback = enhancerClass.getMethod("setCallback", Class.forName("org.springframework.cglib.proxy.Callback"));
      Method create = enhancerClass.getMethod("create");

      setSuperclass.invoke(enhancer, target.getClass());
      setClassLoader.invoke(enhancer, target.getClass().getClassLoader());
      setCallback.invoke(enhancer, interceptor);

      Agent.log("Creating CGLIB proxy for guiController: %s", target.getClass().getName());
      return create.invoke(enhancer);
    } catch (ClassNotFoundException ex) {
      Agent.log("CGLIB not available on classpath: %s", ex.getMessage());
      return null;
    } catch (ReflectiveOperationException ex) {
      Agent.log("Failed to build CGLIB proxy for guiController: %s", ex.getMessage());
      return null;
    }
  }

  private static boolean isReportRelated(Method method, Object[] args) {
    String name = method.getName().toLowerCase();
    if (name.contains("report")) {
      return true;
    }
    if (args == null) {
      return false;
    }
    for (Object arg : args) {
      if (arg == null) {
        continue;
      }
      String typeName = arg.getClass().getName().toLowerCase();
      if (typeName.contains("report")) {
        return true;
      }
    }
    return false;
  }

  private static boolean replaceSingleton(Object context, Object proxy) {
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
        destroy.invoke(factory, TARGET_BEAN_NAME);
      }

      Method register = findMethod(factory.getClass(), "registerSingleton", String.class, Object.class);
      if (register == null) {
        return false;
      }
      register.invoke(factory, TARGET_BEAN_NAME, proxy);
      return true;
    } catch (ReflectiveOperationException ex) {
      Agent.log("GUI bean factory replace failed: %s", ex.getMessage());
      return false;
    }
  }

  private static void updateConsumers(Object context, Object proxy) {
    try {
      Method namesMethod = context.getClass().getMethod("getBeanDefinitionNames");
      String[] beanNames = (String[]) namesMethod.invoke(context);
      if (beanNames == null) {
        return;
      }

      Method getBean = context.getClass().getMethod("getBean", String.class);
      for (String name : beanNames) {
        if (TARGET_BEAN_NAME.equals(name)) {
          continue;
        }
        Object bean;
        try {
          bean = getBean.invoke(context, name);
        } catch (InvocationTargetException ex) {
          Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
          Agent.log("Skipping bean '%s': %s (%s)", name, cause.getMessage(), cause.getClass().getName());
          continue;
        }
        if (bean == null) {
          continue;
        }
        Method setter = findSetter(bean.getClass());
        if (setter == null) {
          continue;
        }
        try {
          setter.invoke(bean, proxy);
          Agent.log("Injected guiController proxy into bean '%s' (%s)", name, bean.getClass().getName());
        } catch (ReflectiveOperationException ex) {
          Agent.log("Failed to call %s on %s: %s", SETTER_NAME, bean.getClass().getName(), ex.getMessage());
        }
      }
    } catch (ReflectiveOperationException ex) {
      Agent.log("Unable to enumerate beans for GUI proxy injection: %s", ex.getMessage());
    }
  }

  private static Method findSetter(Class<?> type) {
    for (Method method : type.getMethods()) {
      if (!method.getName().equals(SETTER_NAME) || method.getParameterCount() != 1) {
        continue;
      }
      Class<?> parameterType = method.getParameterTypes()[0];
      if (!parameterType.isInterface() && !parameterType.getName().startsWith("com.blueprint")) {
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
