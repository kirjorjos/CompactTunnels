package dev.compactmods.compacttunnels;

import com.mojang.logging.LogUtils;
import dev.compactmods.compacttunnels.client.ClientSetup;
import dev.compactmods.compacttunnels.common.TunnelConstants;
import dev.compactmods.compacttunnels.modules.compactmachines.CompactMachinesTunnels;
import dev.compactmods.compacttunnels.modules.compactmachines.TunnelClientConfig;
import dev.compactmods.compacttunnels.modules.compactmachines.TunnelNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * NeoForge entry point.
 */
@Mod(TunnelConstants.MOD_ID)
public final class CompactTunnels {
  private static final Logger LOGGER = LogUtils.getLogger();

  public CompactTunnels(IEventBus modBus, ModContainer container) {
    CompactMachinesTunnels.register(modBus);
    modBus.addListener(TunnelNetwork::register);

    container.registerConfig(ModConfig.Type.CLIENT, TunnelClientConfig.SPEC);

    if (FMLEnvironment.dist == Dist.CLIENT) {
      ClientSetup.registerConfigScreen(container);
    }
  }
}
