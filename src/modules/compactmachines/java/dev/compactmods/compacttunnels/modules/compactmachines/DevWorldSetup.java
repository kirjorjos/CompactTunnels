package dev.compactmods.compacttunnels.modules.compactmachines;

import com.mojang.logging.LogUtils;
import dev.compactmods.compacttunnels.common.TunnelConstants;
import dev.compactmods.machines.api.component.CMDataComponents;
import dev.compactmods.machines.api.room.template.RoomTemplateHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.slf4j.Logger;

/**
 * Makes dev world
 * Sets time noon
 * Disables mob spawns
 * Gives player machine and shrinking device
 */
@EventBusSubscriber(modid = TunnelConstants.MOD_ID)
public final class DevWorldSetup {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String DEV_WORLD_PREFIX = "dev";
    private static final long NOON = 6000L;

    private static final ResourceLocation NEW_MACHINE_ID =
            ResourceLocation.fromNamespaceAndPath(CompactMachinesModule.MOD_ID, "new_machine");
    private static final ResourceLocation SHRINKING_DEVICE_ID =
            ResourceLocation.fromNamespaceAndPath(CompactMachinesModule.MOD_ID, "personal_shrinking_device");

    private DevWorldSetup() {
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isDevWorld(level)) {
            return;
        }

        GameRules rules = level.getGameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        rules.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(false, level.getServer());
        rules.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, level.getServer());
        rules.getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(true, level.getServer());

        if (level.dimension().equals(Level.OVERWORLD)) {
            level.setDayTime(NOON);
        }

        LOGGER.info("Dev world '{}' configured: time {}, cycles off, spawning off (mob={}, patrol={}, trader={}), respawn={}",
                level.dimension().location(), level.getDayTime(),
                rules.getBoolean(GameRules.RULE_DOMOBSPAWNING),
                rules.getBoolean(GameRules.RULE_DO_PATROL_SPAWNING),
                rules.getBoolean(GameRules.RULE_DO_TRADER_SPAWNING),
                rules.getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN));
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isDevWorld(player.serverLevel())) {
            return;
        }

        ItemStack machine = createMachine(player);
        if (machine != null) {
            giveOnce(player, machine);
        }

        Item device = BuiltInRegistries.ITEM.get(SHRINKING_DEVICE_ID);
        if (device == Items.AIR) {
            LOGGER.warn("Compact Machines item {} is missing.", SHRINKING_DEVICE_ID);
        } else {
            giveOnce(player, device.getDefaultInstance());
        }
    }

    /** A new machine to give to the player on world join. */
    private static ItemStack createMachine(ServerPlayer player) {
        Item machineItem = BuiltInRegistries.ITEM.get(NEW_MACHINE_ID);
        if (machineItem == Items.AIR) {
            LOGGER.warn("Compact Machines item {} is missing.", NEW_MACHINE_ID);
            return null;
        }

        ItemStack stack = machineItem.getDefaultInstance();
        RoomTemplateHelper.getTemplateHolders(player.server.registryAccess())
                .findFirst()
                .ifPresentOrElse(holder -> {
                    stack.set(CMDataComponents.ROOM_TEMPLATE_ID, holder.key().location());
                    stack.set(CMDataComponents.MACHINE_COLOR, holder.value().defaultMachineColor());
                }, () -> LOGGER.warn("No room template found"));

        return stack;
    }

    /** Limit machine and shrinking device spawn to initial world join. */
    private static void giveOnce(ServerPlayer player, ItemStack stack) {
        boolean alreadyCarried = player.getInventory().items.stream()
                .anyMatch(held -> held.is(stack.getItem()));
        if (alreadyCarried) {
            return;
        }

        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static boolean isDevWorld(ServerLevel level) {
        MinecraftServer server = level.getServer();
        return server.getWorldData().getLevelName().startsWith(DEV_WORLD_PREFIX);
    }
}
