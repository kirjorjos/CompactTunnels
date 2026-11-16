package dev.compactmods.compacttunnels.modules.compactmachines;

import dev.compactmods.compacttunnels.common.TunnelConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = TunnelConstants.MOD_ID, value = Dist.CLIENT)
public final class TunnelScrollCycle {
  private TunnelScrollCycle() {}

  @SubscribeEvent
  public static void onScroll(InputEvent.MouseScrollingEvent event) {
    final Minecraft minecraft = Minecraft.getInstance();
    if (!Screen.hasControlDown() || minecraft.screen != null || minecraft.level == null) {
      return;
    }

    if (!(minecraft.hitResult instanceof BlockHitResult hit)) {
      return;
    }

    if (!(minecraft.level.getBlockState(hit.getBlockPos()).getBlock()
        instanceof CapabilityTunnelBlock)) {
      return;
    }

    event.setCanceled(true);
    PacketDistributor.sendToServer(
        new CycleTunnelSidePayload(hit.getBlockPos(), event.getScrollDeltaY() > 0));
  }
}
