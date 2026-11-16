package dev.compactmods.compacttunnels.modules.compactmachines;

import dev.compactmods.compacttunnels.common.TunnelConstants;
import dev.compactmods.machines.machine.Machines;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registries and adds capability logic for the tunnels.
 */
public final class CompactMachinesTunnels {
  private static final ResourceLocation COMPACT_MACHINES_TAB =
      ResourceLocation.fromNamespaceAndPath(CompactMachinesModule.MOD_ID, "main");

  private static final DeferredRegister.Blocks BLOCKS =
      DeferredRegister.createBlocks(TunnelConstants.MOD_ID);
  private static final DeferredRegister.Items ITEMS =
      DeferredRegister.createItems(TunnelConstants.MOD_ID);
  private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
      DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, TunnelConstants.MOD_ID);
  private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
      DeferredRegister.create(BuiltInRegistries.DATA_COMPONENT_TYPE, TunnelConstants.MOD_ID);

  public static final Supplier<DataComponentType<Direction>> TUNNEL_SIDE =
      DATA_COMPONENTS.register(
          "side",
          () ->
              DataComponentType.<Direction>builder()
                  .persistent(Direction.CODEC)
                  .networkSynchronized(Direction.STREAM_CODEC)
                  .build());

  public static final DeferredBlock<CapabilityTunnelBlock> CAPABILITY_TUNNEL =
      BLOCKS.register(
          "capability_tunnel",
          () -> new CapabilityTunnelBlock(CapabilityTunnelBlock.tunnelProperties()));

  public static final DeferredItem<CapabilityTunnelItem> CAPABILITY_TUNNEL_ITEM =
      ITEMS.register(
          "capability_tunnel",
          () -> new CapabilityTunnelItem(CAPABILITY_TUNNEL.get(), new Item.Properties()));

  public static final DeferredHolder<
          BlockEntityType<?>, BlockEntityType<CapabilityTunnelBlockEntity>>
      CAPABILITY_TUNNEL_ENTITY =
          BLOCK_ENTITIES.register(
              "capability_tunnel",
              () ->
                  BlockEntityType.Builder.of(
                          CapabilityTunnelBlockEntity::new, CAPABILITY_TUNNEL.get())
                      .build(null));

  private CompactMachinesTunnels() {}

  public static void register(IEventBus modBus) {
    BLOCKS.register(modBus);
    ITEMS.register(modBus);
    BLOCK_ENTITIES.register(modBus);
    DATA_COMPONENTS.register(modBus);
    modBus.addListener(CompactMachinesTunnels::registerCapabilities);
    modBus.addListener(CompactMachinesTunnels::addToCreativeTab);
  }

  private static void registerCapabilities(RegisterCapabilitiesEvent event) {
    event.registerBlockEntity(
        Capabilities.ItemHandler.BLOCK,
        CAPABILITY_TUNNEL_ENTITY.get(),
        (blockEntity, side) -> side == null ? null : blockEntity.farSideItemHandler());

    event.registerBlockEntity(
        Capabilities.ItemHandler.BLOCK,
        Machines.BlockEntities.MACHINE.get(),
        MachineOutsideEnd::itemHandler);
  }

  private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
    if (event.getTabKey().location().equals(COMPACT_MACHINES_TAB)) {
      event.accept(CAPABILITY_TUNNEL_ITEM.get());
    }
  }
}
