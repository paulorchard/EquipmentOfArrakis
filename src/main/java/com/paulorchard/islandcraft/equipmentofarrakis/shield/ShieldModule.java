package com.paulorchard.islandcraft.equipmentofarrakis.shield;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.paulorchard.islandcraft.equipmentofarrakis.core.EquipmentModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleAssets;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleContext;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ToggleRegistry;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ToolUse;

import java.util.ArrayList;
import java.util.List;

/**
 * The Holtzman Shield, ported from v1 (same look, same sounds, same absorb rule) as a passive toggle on the picker page: no item, no
 * off hand. ON, the field is up (subject to Mana); a projectile hit is absorbed whole for {@code ManaPerDamage} Mana per point; a hit
 * that uses up the Mana breaks the field (the toggle stays ON) and it re-arms by itself when the Mana is back to 100. A shot of the
 * player (any projectile, or a Cutter Ray shot through {@link ToolUse}) drops the field for the cooldown. Not saved across a relog.
 */
public final class ShieldModule implements EquipmentModule {

    public static final String ID = "shield";
    public static final String TOGGLE = "Holtzman";
    private static final int ORDER = 10;

    private static HytaleLogger logger;
    private static volatile ShieldConfig config = new ShieldConfig();
    private final ModuleAssets.Guard guard;

    public ShieldModule(JavaPlugin plugin) {
        logger = plugin.getLogger();
        guard = ModuleAssets.guard(plugin, ID, id -> id.startsWith("Arrakis_Holtzman_") || id.startsWith("SFX_Arrakis_Holtzman_"));
    }

    static HytaleLogger logger() {
        return logger;
    }

    static ShieldConfig config() {
        return config;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void setup(ModuleContext ctx) {
        guard.activate();
        config = ctx.settings(ID, ShieldConfig.class);
        ShieldCooldown.bind(ShieldModule::config);
        ToggleRegistry.get().register(new ToggleRegistry.Toggle(TOGGLE, ORDER, "server.customUI.arrakisPicker.shield",
                "Weapon_Shield_Mithril", (store, ref, player, on) -> HoltzmanState.of(player.getUuid()).on = on,
                HoltzmanState::isOn, HoltzmanState::forget));
        ctx.system(new HoltzmanSystem(ShieldModule::config));
        ctx.system(new HoltzmanDamageSystem(ShieldModule::config));
        ctx.system(new ShieldShotLegacySystem());
        ctx.system(new ShieldShotSystem());
        ToolUse.listen(player -> ShieldShots.shotBy(player.getUuid()));
        ctx.command(ShieldCommands.build(ShieldModule::config));
    }

    @Override
    public List<String> selfCheck() {
        List<String> lines = new ArrayList<>();
        for (String id : new String[] {config.getEffectId(), config.getImpactEffectId()}) {
            lines.add(EntityEffect.getAssetMap().getAsset(id) == null ? "PROBLEM entity effect " + id + " is not loaded" : "entity effect " + id + " loaded");
        }
        for (String id : new String[] {"SFX_Arrakis_Holtzman_Field", "SFX_Arrakis_Holtzman_Field_Local", "SFX_Arrakis_Holtzman_On",
                "SFX_Arrakis_Holtzman_On_Local", "SFX_Arrakis_Holtzman_Off", "SFX_Arrakis_Holtzman_Off_Local", "SFX_Arrakis_Holtzman_Break",
                "SFX_Arrakis_Holtzman_Break_Local"}) {
            lines.add(SoundEvent.getAssetMap().getAsset(id) == null ? "PROBLEM sound " + id + " is not loaded" : "sound " + id + " loaded");
        }
        for (String id : new String[] {"Arrakis_Holtzman_Bubble", "Arrakis_Holtzman_Bubble_FP", "Arrakis_Holtzman_Hit", "Arrakis_Holtzman_Shatter"}) {
            lines.add(ParticleSystem.getAssetMap().getAsset(id) == null ? "PROBLEM particle system " + id + " is not loaded" : "particle system " + id + " loaded");
        }
        lines.add(ToggleRegistry.get().find(TOGGLE) == null ? "PROBLEM the shield toggle is not registered" : "toggle shield registered");
        return lines;
    }
}
