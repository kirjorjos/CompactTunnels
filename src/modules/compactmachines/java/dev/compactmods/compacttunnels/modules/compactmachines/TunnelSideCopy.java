package dev.compactmods.compacttunnels.modules.compactmachines;

import net.minecraft.client.gui.screens.Screen;

final class TunnelSideCopy {
  private TunnelSideCopy() {}

  static boolean held() {
    return Screen.hasControlDown();
  }
}
