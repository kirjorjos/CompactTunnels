package dev.compactmods.compacttunnels.modules.compactmachines;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TunnelClientConfig {
  public static final ModConfigSpec SPEC;

  private static final ModConfigSpec.BooleanValue GHOST_BLOCK;

  static {
    final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

    builder.translation("config.compacttunnels.tooltip").push("tooltip");
    GHOST_BLOCK =
        builder
            .translation("config.compacttunnels.tooltip.ghost_block")
            .comment(
                "Show the block on the other side of a tunnel embeded inside the tunnel's tooltip utilizing a hidden ghost block.",
                "Disable if you have problems with the ghost block.",
                "Requires Jade.")
            .define("ghost_block", true);
    builder.pop();

    SPEC = builder.build();
  }

  private TunnelClientConfig() {}

  public static boolean ghostBlock() {
    return GHOST_BLOCK.get();
  }
}
