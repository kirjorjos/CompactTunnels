package dev.compactmods.compacttunnels;

import com.mojang.logging.LogUtils;
import dev.compactmods.compacttunnels.common.TunnelConstants;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * NeoForge entry point.
 */
@Mod(TunnelConstants.MOD_ID)
public final class CompactTunnels {
    private static final Logger LOGGER = LogUtils.getLogger();

    public CompactTunnels(IEventBus modBus, ModContainer container) { }
}
