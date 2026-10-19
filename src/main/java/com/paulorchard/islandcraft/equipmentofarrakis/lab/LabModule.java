// EXPERIMENT: delete with the lab module
package com.paulorchard.islandcraft.equipmentofarrakis.lab;

import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.paulorchard.islandcraft.equipmentofarrakis.core.EquipmentModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleAssets;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleContext;

import java.util.ArrayList;
import java.util.List;

/**
 * EXPERIMENT: the movement lab for the Suspensor Belt (three tiers: slow fall, flat flight, rising flight). Operator-only commands under
 * {@code /arrakis lab} watch the movement states the server sees, change fly and the movement settings, try the glider and the velocity
 * routes, run the Mk II / Mk III prototype and measure fall damage. Every change to a player's movement is undone by {@code lab reset}, on
 * death and on a world change, and the state is dropped on disconnect.
 */
public final class LabModule implements EquipmentModule {

    public static final String ID = "lab";

    private final ModuleAssets.Guard guard;

    public LabModule(JavaPlugin plugin) {
        guard = ModuleAssets.guard(plugin, ID, id -> id.startsWith("Arrakis_Lab_"));
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void setup(ModuleContext ctx) {
        guard.activate();
        Labs.configure(ctx.settings(ID, LabConfig.class), ctx.logger());
        ctx.system(new LabSystem());
        ctx.events().registerGlobal(PlayerDisconnectEvent.class, event -> LabState.forget(event.getPlayerRef().getUuid()));
        LabCommands.register(ctx.group("lab"));
    }

    @Override
    public List<String> selfCheck() {
        List<String> lines = new ArrayList<>();
        for (String id : new String[] {LabItems.BELT, LabItems.BELT_GLIDER}) {
            Item item = Item.getAssetMap().getAsset(id);
            lines.add(item == null ? "PROBLEM lab item " + id + " is not loaded" : "lab item " + id + " loaded, utility " + item.getUtility() + ", glider "
                    + (item.getGlider() == null ? "none" : "terminal velocity " + item.getGlider().getTerminalVelocity()));
        }
        lines.add(Item.getAssetMap().getAsset(LabItems.GLIDER) == null ? "PROBLEM the vanilla " + LabItems.GLIDER + " is not loaded" : "vanilla " + LabItems.GLIDER + " found");
        lines.add(RootInteraction.getAssetMap().getAsset("Arrakis_Lab_Glider_Toggle") == null ? "PROBLEM root interaction Arrakis_Lab_Glider_Toggle is not loaded"
                : "root interaction Arrakis_Lab_Glider_Toggle loaded");
        lines.add(EntityEffect.getAssetMap().getAsset(LabItems.HUM_EFFECT) == null ? "PROBLEM effect " + LabItems.HUM_EFFECT + " is not loaded" : "effect " + LabItems.HUM_EFFECT + " loaded");
        lines.add(LabFields.names().size() + " movement setting fields can be set by name");
        return lines;
    }
}
