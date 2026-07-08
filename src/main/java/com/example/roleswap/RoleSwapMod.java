package com.example.roleswap;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class RoleSwapMod implements ModInitializer {

    @Override
    public void onInitialize() {
        RoleManager.INSTANCE.registerTickHandler();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("roleswap")
                .then(CommandManager.literal("start")
                    .then(CommandManager.argument("p1", EntityArgumentType.player())
                    .then(CommandManager.argument("p2", EntityArgumentType.player())
                    .then(CommandManager.argument("p3", EntityArgumentType.player())
                        .executes(ctx -> {
                            ServerPlayerEntity p1 = EntityArgumentType.getPlayer(ctx, "p1");
                            ServerPlayerEntity p2 = EntityArgumentType.getPlayer(ctx, "p2");
                            ServerPlayerEntity p3 = EntityArgumentType.getPlayer(ctx, "p3");
                            RoleManager.INSTANCE.start(ctx.getSource().getServer(), p1, p2, p3);
                            ctx.getSource().sendFeedback(() -> Text.literal("§aRoleSwap başladı!"), true);
                            return 1;
                        })))))
                .then(CommandManager.literal("stop")
                    .executes(ctx -> {
                        RoleManager.INSTANCE.stop(ctx.getSource().getServer());
                        ctx.getSource().sendFeedback(() -> Text.literal("§cRoleSwap durduruldu."), true);
                        return 1;
                    }))
                .then(CommandManager.literal("interval")
                    .then(CommandManager.argument("minutes", IntegerArgumentType.integer(1, 180))
                        .executes(ctx -> {
                            int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
                            RoleManager.INSTANCE.setIntervalMinutes(minutes);
                            ctx.getSource().sendFeedback(() ->
                                Text.literal("§eRotasyon aralığı: " + minutes + " dk."), true);
                            return 1;
                        })))
            );
        });
    }
}
