package com.example.roleswap;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;

import java.util.UUID;

/**
 * Точка входа для Simple Voice Chat (регистрируется через entrypoint "voicechat" в fabric.mod.json).
 *
 * Логика:
 *  - MicrophonePacketEvent прилетает, когда сервер получает пакет с микрофона игрока.
 *    Если игрок в роли MUTE — отменяем пакет, тогда никто его не услышит.
 *  - *SoundPacketEvent прилетает перед отправкой звука конкретному игроку-получателю.
 *    Если получатель в роли DEAF — отменяем, тогда этот игрок не услышит остальных.
 *
 * ВАЖНО: названия методов получения UUID игрока из VoicechatConnection могут отличаться
 * между версиями API (getPlayer().getUuid() / getPlayerUuid() и т.п.) — сверься с
 * javadoc твоей версии voicechat-api (https://voicechat.modrepo.de/), если не скомпилируется.
 */
public class VoicechatIntegration implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return "roleswap";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
        registration.registerEvent(EntitySoundPacketEvent.class, this::onEntitySound);
        registration.registerEvent(LocationalSoundPacketEvent.class, this::onLocationalSound);
        registration.registerEvent(StaticSoundPacketEvent.class, this::onStaticSound);
    }

    // Немой: блокируем то, что игрок пытается сказать
    private void onMicrophone(MicrophonePacketEvent event) {
        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null) return;
        UUID uuid = sender.getPlayer().getUuid();

        if (RoleManager.INSTANCE.isTracked(uuid) && RoleManager.INSTANCE.isMuted(uuid)) {
            event.cancel();
        }
    }

    // Глухой: блокируем то, что игрок должен был бы услышать
    private void onEntitySound(EntitySoundPacketEvent event) {
        blockIfReceiverIsDeaf(event.getReceiverConnection(), event::cancel);
    }

    private void onLocationalSound(LocationalSoundPacketEvent event) {
        blockIfReceiverIsDeaf(event.getReceiverConnection(), event::cancel);
    }

    private void onStaticSound(StaticSoundPacketEvent event) {
        blockIfReceiverIsDeaf(event.getReceiverConnection(), event::cancel);
    }

    private void blockIfReceiverIsDeaf(VoicechatConnection receiver, Runnable cancel) {
        if (receiver == null) return;
        UUID uuid = receiver.getPlayer().getUuid();
        if (RoleManager.INSTANCE.isTracked(uuid) && RoleManager.INSTANCE.isDeaf(uuid)) {
            cancel.run();
        }
    }

    @Override
    public void initialize(VoicechatApi api) {
        // Ничего дополнительно инициализировать не нужно — вся логика в RoleManager
    }
}
