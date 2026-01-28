package com.blueprint.relay;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

final class PackageMethodAdvice {
  @Advice.OnMethodEnter
  static void onEnter(@Advice.Origin("#t.#m") String signature,
                      @Advice.AllArguments Object[] args) {
    RelayLogger.log("pkg.%s args=%s", signature, ArgumentFormatter.summarizeArgs(args));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class)
  static void onExit(@Advice.Origin("#t.#m") String signature,
                     @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object returned,
                     @Advice.Thrown Throwable thrown) {
    if (thrown != null) {
      RelayLogger.log("pkg.%s threw %s: %s", signature, thrown.getClass().getName(), thrown.getMessage());
      return;
    }
    RelayLogger.log("pkg.%s -> %s", signature, ArgumentFormatter.summarizeValue(returned));
  }
}
