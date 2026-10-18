package com.paulorchard.islandcraft.equipmentofarrakis.shield;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CommandRoot;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CoreModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldPositional;
import com.paulorchard.islandcraft.equipmentofarrakis.power.Power;
import org.joml.Vector3d;
import org.joml.Vector4d;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * <pre>
 * /arrakis shield hit &lt;damage&gt;    a projectile-sourced hit on yourself through the real damage pipeline
 * /arrakis shield state            toggle, field, Mana, broken, cooldown
 * /arrakis shield shot             counts as a shot of yours (the field drops, the cooldown starts)
 * /arrakis shield cooldown [s]     read or set the cooldown (not saved)
 * </pre>
 */
final class ShieldCommands {

    private static final String LANG = CommandRoot.LANG + "shield.";

    private ShieldCommands() {
    }

    static AbstractCommandCollection build(Supplier<ShieldConfig> settings) {
        AbstractCommandCollection shield = new AbstractCommandCollection("shield", LANG + "desc") {
        };
        shield.addSubCommand(WorldPositional.build("hit", LANG + "hit.desc", (c, w, s, a) -> hit(c, s, a), "damage"));
        shield.addSubCommand(WorldPositional.build("state", LANG + "state.desc", (c, w, s, a) -> state(c, s, settings.get())));
        shield.addSubCommand(WorldPositional.build("shot", LANG + "shot.desc", (c, w, s, a) -> shot(c, s)));
        shield.addSubCommand(WorldPositional.build("cooldown", LANG + "cooldown.desc", (c, w, s, a) -> cooldown(c, a, settings.get()), "seconds"));
        return shield;
    }

    private static Ref<EntityStore> self(CommandContext c) {
        if (!c.isPlayer() || c.senderAsPlayerRef() == null) {
            say(c, "Run this as a player.");
            return null;
        }
        return c.senderAsPlayerRef();
    }

    private static void hit(CommandContext c, Store<EntityStore> store, String[] args) {
        Ref<EntityStore> self = self(c);
        if (self == null) {
            return;
        }
        float amount;
        try {
            amount = Float.parseFloat(args[0]);
        } catch (NumberFormatException e) {
            say(c, "Not a number: " + args[0]);
            return;
        }
        Damage damage = new Damage(new Damage.ProjectileSource(self, self), DamageCause.PROJECTILE, amount);
        // A hit location one block in front of the eyes, so the flash has somewhere to go.
        Transform look = TargetUtil.getLook(self, store);
        Vector3d at = new Vector3d(look.getPosition()).fma(1.0, new Vector3d(look.getDirection()).normalize());
        damage.putMetaObject(Damage.HIT_LOCATION, new Vector4d(at.x, at.y, at.z, 1.0));
        Power power = Power.get();
        float before = power == null ? 0f : power.get(store, self);
        DamageSystems.executeDamage(self, store, damage);
        say(c, "Sent " + amount + " projectile damage (Mana was " + before + "). See arrakis shield state.");
    }

    private static void state(CommandContext c, Store<EntityStore> store, ShieldConfig cfg) {
        Ref<EntityStore> self = self(c);
        Power power = Power.get();
        if (self == null || power == null) {
            return;
        }
        PlayerRef player = store.getComponent(self, PlayerRef.getComponentType());
        UUID id = player.getUuid();
        HoltzmanState state = HoltzmanState.of(id);
        boolean alive = store.getComponent(self, DeathComponent.getComponentType()) == null;
        float mana = power.get(store, self);
        long now = System.nanoTime();
        boolean cooling = state.cooling(id, now);
        say(c, "toggle " + (state.on ? "ON" : "OFF") + ", field " + (HoltzmanField.hasField(store, self, cfg) ? "UP" : "down") + ", should be up "
                + HoltzmanRules.raised(state.on, alive, mana, cooling, state.broken) + ", broken (waiting to re-arm at "
                + HoltzmanRules.rearmLevel((float) cfg.getRearmMana(), power.max(store, self)) + ") " + state.broken + ", Mana " + mana
                + ", ManaPerDamage " + cfg.getManaPerDamage());
        say(c, String.format(Locale.ROOT, "cooldown %.2f s (base %.2f, %d modifiers), remaining %.2f s; last shot %s; shots seen %d",
                ShieldCooldown.secondsFor(id), ShieldCooldown.baseSeconds(), ShieldCooldown.modifierCount(), state.remaining(id, now),
                state.lastShot == ShieldCooldown.NONE ? "never (or cleared)" : String.format(Locale.ROOT, "%.2f s ago", (now - state.lastShot) / 1e9),
                state.projectiles));
    }

    private static void shot(CommandContext c, Store<EntityStore> store) {
        Ref<EntityStore> self = self(c);
        if (self == null) {
            return;
        }
        UUID id = store.getComponent(self, PlayerRef.getComponentType()).getUuid();
        ShieldShots.shotBy(id);
        say(c, "Counted as a shot: the field drops and the cooldown (" + ShieldCooldown.secondsFor(id) + " s) starts.");
    }

    private static void cooldown(CommandContext c, String[] args, ShieldConfig cfg) {
        if (args.length == 0) {
            say(c, "Cooldown base " + ShieldCooldown.baseSeconds() + " s (data " + cfg.getCooldownSeconds() + "), "
                    + ShieldCooldown.modifierCount() + " modifiers registered.");
            return;
        }
        try {
            double seconds = Double.parseDouble(args[0]);
            if (seconds < 0) {
                throw new NumberFormatException();
            }
            ShieldCooldown.setOverride(seconds);
            say(c, "Cooldown is now " + seconds + " s (not saved; a restart goes back to " + cfg.getCooldownSeconds() + ").");
        } catch (NumberFormatException e) {
            say(c, "Not a number of seconds: " + args[0]);
        }
    }

    private static void say(CommandContext c, String text) {
        CoreModule.say(c, text);
    }
}
