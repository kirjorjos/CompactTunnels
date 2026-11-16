package dev.compactmods.compacttunnels.client;

import dev.compactmods.compacttunnels.common.TunnelConstants;
import net.minecraft.FileUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Add a handy DevWorld button to title screen
 * Fresh world every boot
 * superflat with ideal conditions
 */
@EventBusSubscriber(modid = TunnelConstants.MOD_ID, value = Dist.CLIENT)
public final class DevWorldButton {
    private static final String WORLD_NAME = "dev";

    private DevWorldButton() {
    }

    @SubscribeEvent
    public static void onTitleScreenInit(ScreenEvent.Init.Post event) {
        if (FMLEnvironment.production) {
            return;
        }

        if (!(event.getScreen() instanceof TitleScreen screen)) {
            return;
        }

        Button button = Button.builder(
                        Component.translatable("screen." + TunnelConstants.MOD_ID + ".dev_world"),
                        pressed -> createDevWorld(screen))
                .bounds(screen.width / 2 + 102, screen.height / 4 + 48, 80, 20)
                .build();

        event.addListener(button);
    }

    private static void createDevWorld(TitleScreen screen) {
        Minecraft minecraft = Minecraft.getInstance();

        String saveName;
        try {
            saveName = FileUtil.findAvailableName(minecraft.getLevelSource().getBaseDir(), WORLD_NAME, "");
        } catch (IOException exception) {
            saveName = WORLD_NAME;
        }

        GameRules gameRules = new GameRules();
        gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        gameRules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        gameRules.getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(true, null);
        gameRules.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(false, null);
        gameRules.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, null);

        List<String> enabledPacks = new ArrayList<>(DataPackConfig.DEFAULT.getEnabled());
        enabledPacks.add("mod/compactmachines:data/compactmachines/datapacks/basic_templates");
        enabledPacks.add("mod/compactmachines:data/compactmachines/datapacks/room_upgrades");

        WorldDataConfiguration dataConfiguration = new WorldDataConfiguration(
                new DataPackConfig(enabledPacks, List.of()),
                FeatureFlags.REGISTRY.allFlags());

        LevelSettings settings = new LevelSettings(
                saveName,
                GameType.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                gameRules,
                dataConfiguration);

        WorldOptions worldOptions = new WorldOptions(new Random().nextLong(), false, false);

        minecraft.createWorldOpenFlows().createFreshLevel(
                saveName,
                settings,
                worldOptions,
                registryAccess -> registryAccess.lookupOrThrow(Registries.WORLD_PRESET)
                        .getOrThrow(WorldPresets.FLAT)
                        .value()
                        .createWorldDimensions(),
                screen);
    }
}
