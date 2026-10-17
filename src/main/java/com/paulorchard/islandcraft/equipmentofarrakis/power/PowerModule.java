package com.paulorchard.islandcraft.equipmentofarrakis.power;

import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.asset.modifier.RegeneratingModifier;
import com.paulorchard.islandcraft.equipmentofarrakis.core.EquipmentModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleContext;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Power is the vanilla Mana stat. This module is the service ({@link Power}), the max-Mana modifiers
 * ({@link PowerModifiers}), the operator commands and the logging of the stat definition. It changes nothing in the stat.
 */
public final class PowerModule implements EquipmentModule {

    public static final String ID = "power";

    private PowerConfig config = new PowerConfig();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void setup(ModuleContext ctx) {
        config = ctx.settings(ID, PowerConfig.class);
        Power power = new Power();
        Power.install(power);
        ManaRefusal.show = config.showMessages();
        ctx.events().registerGlobal(com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent.class,
                event -> ManaRefusal.forget(event.getPlayerRef().getUuid()));
        ctx.command(PowerCommands.build(power));
    }

    @Override
    public void start(ModuleContext ctx) {
        // PLACE FOR THE REGENERATION CHANGE (a later prompt): vanilla Mana refills in about 30 s whether or not it is being
        // spent. Changing that means overriding the "Regenerating" entry of Mana.json, or replacing the loaded asset at
        // runtime through AssetStore.loadAssets / LoadedAssetsEvent. Not done now; the definition logged here is the baseline.
        if (config.logStatDefinition()) {
            for (String line : definition()) {
                ctx.logger().at(Level.INFO).log("%s", line);
            }
        }
    }

    @Override
    public List<String> selfCheck() {
        List<String> lines = new ArrayList<>();
        EntityStatType mana = stat();
        if (mana == null) {
            lines.add("PROBLEM the Mana stat is not loaded");
            return lines;
        }
        lines.add("Mana stat found at index " + Power.statIndex() + ", max " + mana.getMax() + ", " + regenCount(mana)
                + " regenerating entries (vanilla, unchanged)");
        if (Power.get() == null) {
            lines.add("PROBLEM the power service is not installed");
        }
        return lines;
    }

    private static EntityStatType stat() {
        int index = Power.statIndex();
        return index == Integer.MIN_VALUE ? null : EntityStatType.getAssetMap().getAsset(index);
    }

    private static int regenCount(EntityStatType stat) {
        return stat.getRegenerating() == null ? 0 : stat.getRegenerating().length;
    }

    /** The Mana definition as log lines. */
    static List<String> definition() {
        List<String> lines = new ArrayList<>();
        EntityStatType mana = stat();
        if (mana == null) {
            lines.add("Power: the Mana stat is not loaded");
            return lines;
        }
        lines.add("Power: Mana definition: initial " + mana.getInitialValue() + ", min " + mana.getMin() + ", max " + mana.getMax()
                + ", shared " + mana.isShared() + ", reset " + mana.getResetBehavior() + ", " + regenCount(mana) + " regenerating");
        EntityStatType.Regenerating[] regen = mana.getRegenerating();
        for (int i = 0; regen != null && i < regen.length; i++) {
            EntityStatType.Regenerating r = regen[i];
            StringBuilder conditions = new StringBuilder();
            if (r.getConditions() != null) {
                for (var condition : r.getConditions()) {
                    conditions.append(conditions.length() == 0 ? "" : ", ").append(condition.getClass().getSimpleName());
                }
            }
            RegeneratingModifier[] modifiers = r.getModifiers();
            lines.add("Power:   regen " + i + ": every " + r.getInterval() + " s, " + r.getRegenType() + " " + r.getAmount()
                    + ", conditions [" + conditions + "], " + (modifiers == null ? 0 : modifiers.length) + " modifiers");
        }
        return lines;
    }
}
