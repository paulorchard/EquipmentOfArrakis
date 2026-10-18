package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.core.EquipmentModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleContext;
import com.paulorchard.islandcraft.equipmentofarrakis.core.water.WaterApi;
import com.paulorchard.islandcraft.equipmentofarrakis.core.water.WaterLink;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * One combined HUD of four bars in two rows above the hotbar: Power (the vanilla Mana stat) and Health on the left, Heat Exposure and
 * Water (from the Water mod) on the right, in our own art. It hides the vanilla Health and Mana bars it replaces and nothing else,
 * and asks the Water mod not to draw its own edge bars. Display only: no stat, regeneration or rule is touched.
 *
 * <p>Without the Water mod the two right-hand bars are not drawn (said once in the log). The module puts everything back when it stops
 * (the vanilla bars shown, our HUD removed, the Water bars allowed again).
 */
public final class HudModule implements EquipmentModule {

    public static final String ID = "hud";

    private static final HytaleLogger LOG = HytaleLogger.getLogger();
    private static volatile boolean waterHidden;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void setup(ModuleContext ctx) {
        HudRuntime.configure(ctx.settings(ID, HudConfig.class));
        HudRuntime.setEnabled(true);
        ctx.system(new HudSystem());
        ctx.events().registerGlobal(PlayerDisconnectEvent.class, event -> HudRuntime.forget(event.getPlayerRef().getUuid()));
        ctx.command(HudCommands.build());
    }

    @Override
    public void start(ModuleContext ctx) {
        HudRuntime.resolveExposureRules();
        if (WaterLink.api() == null) {
            ctx.logger().at(Level.INFO).log("Hud: the Water mod is not there, so the Heat Exposure and Water bars are not drawn (Power and Health are)");
        }
        ctx.logger().at(Level.INFO).log("Hud: %s", hideWaterBars(true));
    }

    @Override
    public void stop(ModuleContext ctx) {
        HudRuntime.setEnabled(false);
        hideWaterBars(false);
        for (PlayerRef player : Universe.get().getPlayers()) {
            try {
                Ref<EntityStore> ref = player.getReference();
                if (ref == null || !ref.isValid()) {
                    continue;
                }
                Store<EntityStore> store = ref.getStore();
                store.getExternalData().getWorld().execute(() -> {
                    Player p = store.getComponent(ref, Player.getComponentType());
                    if (p != null) {
                        p.getHudManager().showHudComponents(player, HudRuntime.REPLACED);
                        p.getHudManager().removeCustomHud(player, HudView.KEY);
                    }
                });
            } catch (RuntimeException e) {
                LOG.at(Level.WARNING).log("Hud: could not give %s the vanilla bars back: %s", player.getUsername(), e);
            }
        }
    }

    /**
     * Switches the Water mod own bars off (true) or on (false) and says what happened. An older Water jar has no switch: its bars stay on
     * and this logs a warning, because the Water mod adds its HUD again every tick and cannot be overridden from here.
     */
    static String hideWaterBars(boolean hide) {
        WaterApi water = WaterLink.api();
        if (water == null) {
            waterHidden = false;
            return "Water mod not loaded: nothing to switch.";
        }
        if (water.setHudHidden(hide)) {
            waterHidden = hide;
            return hide ? "Water bars switched off (setHudHidden)." : "Water bars allowed again.";
        }
        waterHidden = false;
        if (hide) {
            LOG.at(Level.WARNING).log("Hud: this Water jar has no setHudHidden (older than its prompt 06 API): the Water bars are still on");
        }
        return "This Water jar has no setHudHidden: its bars are still on.";
    }

    static boolean waterHidden() {
        return waterHidden;
    }

    @Override
    public List<String> selfCheck() {
        List<String> lines = new ArrayList<>();
        String base = "UI/Custom/Hud/Equipment_of_Arrakis/Hud/";
        lines.add(common(base + "Arrakis_Hud.ui"));
        for (String side : new String[] {"Left", "Right"}) {
            for (String kind : new String[] {"Background", "Fill", "Sheen"}) {
                lines.add(common(base + "Arrakis_Hud_Bar_" + kind + "_" + side + "@2x.png"));
            }
        }
        for (HudBar bar : HudBar.values()) {
            lines.add(common(base + "Arrakis_Hud_Icon_" + bar.id + "@2x.png"));
        }
        lines.add("exposure colour rules: " + (HudRuntime.exposureRulesFromWater() ? "from the Water mod" : "Hud config fallback (the Water defaults)"));
        lines.add("water link: " + WaterLink.status());
        return lines;
    }

    private static String common(String path) {
        boolean found = com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry.hasCommonAsset(path)
                || com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry.hasCommonAsset("Common/" + path);
        return (found ? "" : "PROBLEM not found: ") + path;
    }
}
