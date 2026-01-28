package com.blueprint.relay;

import net.bytebuddy.asm.Advice;

public final class HttpRequestAdvice {
  private HttpRequestAdvice() {
  }

  @Advice.OnMethodEnter
  public static void onEnter(@Advice.AllArguments Object[] args) {
    if (args == null || args.length == 0) {
      return;
    }
    for (Object arg : args) {
      if (arg != null) {
        HttpRequestLogUtil.logHttpRequest(arg);
      }
    }
  }
}
