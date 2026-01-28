package com.blueprint.relay;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.MemberSubstitution;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

final class SystemExitBlockerInstaller {
  private SystemExitBlockerInstaller() {
  }

  static void install(Instrumentation instrumentation) {
    Method replacement;
    try {
      replacement = RelayExitHooks.class.getMethod("exit", int.class);
    } catch (NoSuchMethodException ex) {
      Agent.log("System exit hook missing: %s", ex.getMessage());
      return;
    }

    new AgentBuilder.Default()
        .ignore(ElementMatchers.none())
        .type(ElementMatchers.named("com.blueprint.oms.gui.OMSClient"))
        .transform(new AgentBuilder.Transformer() {
          @Override
          public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder,
                                                  TypeDescription typeDescription,
                                                  ClassLoader classLoader,
                                                  JavaModule module,
                                                  ProtectionDomain protectionDomain) {
            return builder.visit(
                MemberSubstitution.strict()
                    .method(ElementMatchers.named("exit")
                        .and(ElementMatchers.isDeclaredBy(System.class))
                        .and(ElementMatchers.takesArguments(int.class)))
                    .replaceWith(replacement)
                    .on(ElementMatchers.any())
            );
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
            Agent.log("Installed System.exit blocker on %s", typeDescription.getName());
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
            Agent.log("System.exit blocker error for %s: %s", typeName, throwable.toString());
          }

          @Override
          public void onComplete(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
          }
        })
        .installOn(instrumentation);
  }
}
