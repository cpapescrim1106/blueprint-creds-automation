package com.blueprint.relay;

public final class RelayExitHooks {
  private RelayExitHooks() {
  }

  public static void exit(int status) {
    Agent.log("Blocked System.exit(%d) from OMSClient", status);
  }
}
