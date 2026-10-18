package com.paulorchard.islandcraft.equipmentofarrakis.core;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;


/**
 * Interaction type {@code Arrakis_Toggle}: flips the toggle {@code ToggleId} of the player who runs it, through the same
 * {@link ToggleRegistry#press} the picker page uses, so the page, the field, the sounds and the Mana rules agree. It is what the
 * Ability4 slot (the H key in the game client) of every Arrakis weapon and hand tool runs. It costs nothing, needs no stat, and
 * does nothing when no module registered the toggle. Presses closer than {@code DebounceSeconds} are ignored.
 *
 * <pre>
 * {"Type": "Arrakis_Toggle", "ToggleId": "Holtzman", "DebounceSeconds": 0.2}
 * </pre>
 */
public class ToggleInteraction extends SimpleInstantInteraction {

    public static final String TYPE = "Arrakis_Toggle";

    public static final BuilderCodec<ToggleInteraction> CODEC = BuilderCodec
            .builder(ToggleInteraction.class, ToggleInteraction::new, SimpleInstantInteraction.CODEC)
            .append(new KeyedCodec<>("ToggleId", Codec.STRING, false), (i, v) -> i.toggleId = v, i -> i.toggleId)
            .add()
            .append(new KeyedCodec<>("DebounceSeconds", Codec.FLOAT, false), (i, v) -> i.debounceSeconds = v, i -> i.debounceSeconds)
            .add()
            .build();

    private String toggleId = "Holtzman";
    private float debounceSeconds = 0.2f;

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldowns) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> ref = context.getEntity();
        PlayerRef player = buffer == null || ref == null ? null : buffer.getComponent(ref, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        ToggleRegistry.Outcome outcome = ToggleRegistry.get().press(toggleId, buffer.getStore(), ref, player,
                System.nanoTime(), (long) (debounceSeconds * 1e9));
        if (outcome == ToggleRegistry.Outcome.ON || outcome == ToggleRegistry.Outcome.OFF) {
            ToggleRegistry.Toggle toggle = ToggleRegistry.get().find(toggleId);
            player.sendMessage(Message.translation(toggle.labelKey()).insert(": " + (outcome == ToggleRegistry.Outcome.ON ? "ON" : "OFF")));
        }
    }
}
