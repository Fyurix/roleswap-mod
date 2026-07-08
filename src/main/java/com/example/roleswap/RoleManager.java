package com.example.roleswap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Держит текущее распределение ролей между 3 игроками и крутит его по таймеру.
 * Только эффект слепоты применяется тут напрямую (ванильный StatusEffect).
 * Мут/глухота считываются через isMuted()/isDeaf() из VoicechatIntegration.
 */
public class RoleManager {

    public static final RoleManager INSTANCE = new RoleManager();

    // Игрок -> его текущая роль
    private final Map<UUID, Role> assignments = new LinkedHashMap<>();
    // Кэш имён для команд/логов
    private final Map<UUID, String> names = new ConcurrentHashMap<>();

    private boolean running = false;
    private int intervalTicks = 20 * 60 * 20; // 20 минут по умолчанию (20 тиков/сек)
    private int ticksLeft = 0;

    private RoleManager() {}

    public void registerTickHandler() {
        ServerTickEvents.END_SERVER_TICK.register(this::onTick);
    }

    /** Запускает челлендж на переданных 3 игроках, назначает роли по кругу. */
    public void start(MinecraftServer server, ServerPlayerEntity p1, ServerPlayerEntity p2, ServerPlayerEntity p3) {
        assignments.clear();
        names.clear();

        ServerPlayerEntity[] players = {p1, p2, p3};
        Role[] roles = Role.values();
        for (int i = 0; i < players.length; i++) {
            assignments.put(players[i].getUuid(), roles[i]);
            names.put(players[i].getUuid(), players[i].getGameProfile().getName());
        }

        running = true;
        ticksLeft = intervalTicks;
        applyAllEffects(server);
        broadcastState(server);
    }

    public void stop(MinecraftServer server) {
        running = false;
        for (UUID uuid : assignments.keySet()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
            if (p != null) {
                p.removeStatusEffect(StatusEffects.BLINDNESS);
            }
        }
        assignments.clear();
        names.clear();
    }

    public void setIntervalMinutes(int minutes) {
        this.intervalTicks = minutes * 60 * 20;
        this.ticksLeft = this.intervalTicks;
    }

    public boolean isRunning() {
        return running;
    }

    public Role getRole(UUID playerUuid) {
        return assignments.get(playerUuid);
    }

    public boolean isMuted(UUID playerUuid) {
        return getRole(playerUuid) == Role.MUTE;
    }

    public boolean isDeaf(UUID playerUuid) {
        return getRole(playerUuid) == Role.DEAF;
    }

    public boolean isTracked(UUID playerUuid) {
        return assignments.containsKey(playerUuid);
    }

    private void onTick(MinecraftServer server) {
        if (!running) return;

        // Поддерживаем эффект слепоты каждый тик, т.к. он ограничен по времени
        for (Map.Entry<UUID, Role> entry : assignments.entrySet()) {
            if (entry.getValue() == Role.BLIND) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(entry.getKey());
                if (p != null && !p.hasStatusEffect(StatusEffects.BLINDNESS)) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 25 * 20, 0, false, false));
                }
            }
        }

        // Раз в секунду (каждые 20 тиков) обновляем надпись над хотбаром у каждого игрока с его ролью
        if (server.getTicks() % 20 == 0) {
            for (Map.Entry<UUID, Role> entry : assignments.entrySet()) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(entry.getKey());
                if (p != null) {
                    p.sendMessage(actionBarTextFor(entry.getValue()), true);
                }
            }
        }

        ticksLeft--;
        if (ticksLeft <= 0) {
            rotate(server);
            ticksLeft = intervalTicks;
        }
    }

    private Text actionBarTextFor(Role role) {
        String label = switch (role) {
            case BLIND -> "§c§lKÖRSÜN";
            case MUTE -> "§e§lDİLSİZSİN (seni duyamazlar)";
            case DEAF -> "§b§lSAĞIRSIN (sen duyamazsın)";
        };
        return Text.literal(label);
    }

    private void rotate(MinecraftServer server) {
        // Снимаем слепоту с текущего "слепого" перед сменой
        for (Map.Entry<UUID, Role> entry : assignments.entrySet()) {
            if (entry.getValue() == Role.BLIND) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(entry.getKey());
                if (p != null) p.removeStatusEffect(StatusEffects.BLINDNESS);
            }
        }

        for (UUID uuid : assignments.keySet()) {
            assignments.put(uuid, assignments.get(uuid).next());
        }

        applyAllEffects(server);
        broadcastState(server);
    }

    private void applyAllEffects(MinecraftServer server) {
        for (Map.Entry<UUID, Role> entry : assignments.entrySet()) {
            if (entry.getValue() == Role.BLIND) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(entry.getKey());
                if (p != null) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 25 * 20, 0, false, false));
                }
            }
        }
        // MUTE и DEAF ничего не требуют тут — их читает VoicechatIntegration в реальном времени
    }

    private void broadcastState(MinecraftServer server) {
        StringBuilder sb = new StringBuilder("§6[RoleSwap] Yeni roller: ");
        for (Map.Entry<UUID, Role> entry : assignments.entrySet()) {
            sb.append(names.getOrDefault(entry.getKey(), "???"))
              .append(" -> ").append(entry.getValue()).append("  ");
        }
        Text msg = Text.literal(sb.toString());
        server.getPlayerManager().broadcast(msg, false);
    }
