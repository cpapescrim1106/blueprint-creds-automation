package com.blueprint.relay;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

final class HttpRequestLoggerInstaller {
  private HttpRequestLoggerInstaller() {
  }

  static void install(Instrumentation instrumentation) {
    new AgentBuilder.Default()
        .ignore(ElementMatchers.none())
        .type(ElementMatchers.nameStartsWith("org.apache.http.impl.client"))
        .transform(new AgentBuilder.Transformer() {
          @Override
          public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder,
                                                  TypeDescription typeDescription,
                                                  ClassLoader classLoader,
                                                  JavaModule module,
                                                  ProtectionDomain protectionDomain) {
            return builder.visit(Advice.to(HttpRequestAdvice.class).on(
                ElementMatchers.named("execute")
                    .and(ElementMatchers.isMethod())
            ));
          }
        })
        .with(new AgentBuilder.Listener() {
          @Override
          public void onDiscovery(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
          }

          @Override
          public void onTransformation(TypeDescription typeDescription,
                                       ClassLoader classLoader,
                                       JavaModule module,
                                       boolean loaded,
                                       net.bytebuddy.dynamic.DynamicType dynamicType) {
            Agent.log("Installed HTTP request logger on %s", typeDescription.getName());
          }

          @Override
          public void onIgnored(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, boolean loaded) {
          }

          @Override
          public void onError(String typeName,
                              ClassLoader classLoader,
                              JavaModule module,
                              boolean loaded,
                              Throwable throwable) {
            Agent.log("HTTP request logger error for %s: %s", typeName, throwable.toString());
          }

          @Override
          public void onComplete(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
          }
        })
        .installOn(instrumentation);
  }
}
