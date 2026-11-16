package dev.compactmods.compacttunnels.modules.compactmachines;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

final class Tell {
  private Tell() {}

  static void placed(ServerLevel level, ServerPlayer player, Direction side, String roomCode) {
    player.displayClientMessage(
        Component.translatableWithFallback(
            "compacttunnels.tunnel.placed",
            "Placed on %1$s (%2$s/%3$s used)",
            CapabilityTunnelItem.sideName(side),
            TunnelClaims.used(level.getServer(), roomCode),
            TunnelClaims.limit()),
        true);
  }

  static void freed(ServerLevel level, ServerPlayer player, Direction side, String roomCode) {
    player.displayClientMessage(
        Component.translatableWithFallback(
            "compacttunnels.tunnel.freed",
            "%1$s freed (%2$s/%3$s used)",
            CapabilityTunnelItem.sideName(side),
            TunnelClaims.used(level.getServer(), roomCode),
            TunnelClaims.limit()),
        true);
  }
}
