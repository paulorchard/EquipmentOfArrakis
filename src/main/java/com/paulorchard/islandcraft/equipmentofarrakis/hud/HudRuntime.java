package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.core.water.WaterApi;
import com.paulorchard.islandcraft.equipmentofarrakis.core.water.WaterLink;
import com.paulorchard.islandcraft.equipmentofarrakis.power.Power;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * What the HUD does for each player on each tick, on the world thread: keeps the vanilla Health and Mana bars hidden (it checks the
 * visible set every tick, so a join, a respawn, a world change or a game mode change that resets it is undone within a tick), keeps
 * the player own {@link HudView} added, reads the four values and sends what changed. Display only: it never changes a stat, and the
 * visible set is read by nothing on the server but the packet that tells the client what to draw (checked in the server jar).
 */
final class HudRuntime {

    /** The vanilla parts the four bars replace. Nothing else is ever hidden. */
    static final HudComponent[] REPLACED = {HudComponent.Health, HudComponent.Mana};

    /** A bar value held by /arrakis hud demo. */
    record Pin(HudBar bar, double percent, long untilNanos) {
    }

    /** What is kept for one player. */
    static final class PlayerHud {
        HudView view;
        /** True while this module has hidden the vanilla bars for the player (so that off can give them back). */
        boolean hidden;
        /** The generation of the settings (nudges, art route) the view was built with. */
        int generation;
        final double[] percent = {Double.NaN, Double.NaN, Double.NaN, Double.NaN};
        final HudMath.Alert[] alert = {HudMath.Alert.NONE, HudMath.Alert.NONE, HudMath.Alert.NONE, HudMath.Alert.NONE};
        boolean bright;
        Pin pin;
    }

    private static final HytaleLogger LOG = HytaleLogger.getLogger();

    private static final Map<UUID, PlayerHud> PLAYERS = new ConcurrentHashMap<>();
    private static volatile boolean enabled = true;
    private static volatile HudConfig config = new HudConfig();
    private static volatile WaterApi.ExposureColors exposureRules;
    private static volatile boolean exposureRulesFromWater;
    private static volatile boolean warnedNoDocument;
    /** Bumped when the nudges or the art route change: every view built with an older one is built again. */
    private static final java.util.concurrent.atomic.AtomicInteger GENERATION = new java.util.concurrent.atomic.AtomicInteger();

    private HudRuntime() {
    }

    static void configure(HudConfig settings) {
        config = settings;
    }

    static HudConfig config() {
        return config;
    }

    /** The settings that shape the document changed (a nudge, the art route): every player gets a new view on their next tick. */
    static void rebuildAll() {
        GENERATION.incrementAndGet();
    }

    /** Whether the pack has a file (the same registry the self-check uses). */
    static boolean packHas(String path) {
        return com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry.hasCommonAsset(path)
                || com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry.hasCommonAsset("Common/" + path);
    }

    static boolean enabled() {
        return enabled;
    }

    static void setEnabled(boolean on) {
        enabled = on;
    }

    static PlayerHud of(UUID player) {
        return PLAYERS.computeIfAbsent(player, id -> new PlayerHud());
    }

    static PlayerHud peek(UUID player) {
        return PLAYERS.get(player);
    }

    static void forget(UUID player) {
        PLAYERS.remove(player);
    }

    static Map<UUID, PlayerHud> all() {
        return PLAYERS;
    }

    /** Works out the exposure colour rules once: the Water mod own when it gives them, else the config fallback (and says which). */
    static void resolveExposureRules() {
        WaterApi water = WaterLink.api();
        WaterApi.ExposureColors fromWater = water == null ? null : water.exposureColors();
        if (fromWater != null) {
            exposureRules = fromWater;
            exposureRulesFromWater = true;
            LOG.at(Level.INFO).log("Hud: the Heat Exposure colour rules come from the Water mod (orange %s, red %s, red from %s%%)", fromWater.orange(),
                    fromWater.red(), fromWater.redStartPercent());
            return;
        }
        exposureRules = new WaterApi.ExposureColors(config.getExposureRedStartPercent(), config.getExposureColorOrange(), config.getExposureColorRed());
        exposureRulesFromWater = false;
        if (water != null) {
            LOG.at(Level.WARNING).log("Hud: this Water jar gives no exposure colour rules (older than its prompt 06 API): using the Hud config fallback"
                    + " (orange %s, red %s, red from %s%%), which are the Water defaults", exposureRules.orange(), exposureRules.red(), exposureRules.redStartPercent());
        }
    }

    static WaterApi.ExposureColors exposureRules() {
        WaterApi.ExposureColors rules = exposureRules;
        if (rules == null) {
            resolveExposureRules();
            rules = exposureRules;
        }
        return rules;
    }

    static boolean exposureRulesFromWater() {
        return exposureRulesFromWater;
    }

    // ------------------------------------------------------------------ the tick

    static void tick(Player player, PlayerRef ref, Ref<EntityStore> entity, ComponentAccessor<EntityStore> accessor) {
        HudManager hud = player.getHudManager();
        PlayerHud state = of(ref.getUuid());
        HudConfig cfg = config;
        Set<HudComponent> visible = hud.getVisibleHudComponents();
        boolean creative = player.getGameMode() == GameMode.Creative;
        // Only a document the pack really has is ever appended (the client refuses to join otherwise); with none, the vanilla bars stay.
        String document = HudDocs.pick(creative, HudRuntime::packHas);
        if (document == null && !warnedNoDocument) {
            warnedNoDocument = true;
            LOG.at(Level.WARNING).log("Hud: the pack has no HUD document (not even the Copy one): the four bars are not shown and the vanilla bars stay");
        }
        // The bars sit above the hotbar: they follow it (a spectator has none, and the vanilla bars are not drawn there either).
        boolean show = enabled && document != null && visible.contains(HudComponent.Hotbar);

        if (!enabled || document == null) {
            if (state.hidden) {
                hud.showHudComponents(ref, REPLACED);
                state.hidden = false;
            }
        } else if (visible.contains(HudComponent.Health) || visible.contains(HudComponent.Mana)) {
            hud.hideHudComponents(ref, REPLACED);
            state.hidden = true;
        }

        if (!show) {
            if (state.view != null) {
                hud.removeCustomHud(ref, HudView.KEY);
                state.view = null;
            }
            return;
        }
        WaterApi water = WaterLink.api();
        int generation = GENERATION.get();
        if (state.view != null && (!state.view.document().equals(document) || state.generation != generation)) {
            // The game mode, the art route or a nudge changed: the document is built again (a texture path is not changed on a live one).
            hud.removeCustomHud(ref, HudView.KEY);
            state.view = null;
        }
        if (state.view == null || hud.getCustomHud(HudView.KEY) != state.view) {
            state.view = new HudView(ref, cfg, water != null, creative, document);
            state.generation = generation;
            hud.addCustomHud(ref, state.view);
        }
        long now = System.nanoTime();
        if (state.pin != null && now > state.pin.untilNanos()) {
            state.pin = null;
        }
        readValues(ref, entity, accessor, water, state.percent);
        if (state.pin != null) {
            state.percent[state.pin.bar().ordinal()] = state.pin.percent();
        }
        state.bright = HudMath.bright(now / 1e9, cfg.getAlertFlipSeconds());
        String[] fill = new String[4];
        String[] icon = new String[4];
        for (HudBar bar : HudBar.values()) {
            int i = bar.ordinal();
            double percent = state.percent[i];
            boolean alerting = HudMath.alerting(cfg.flashes(bar), percent, cfg.getFlashBelowPercent());
            state.alert[i] = HudMath.alert(alerting, state.bright);
            if (bar == HudBar.EXPOSURE) {
                WaterApi.ExposureColors rules = exposureRules();
                fill[i] = HudMath.safeColor(HudMath.exposureColor(Double.isNaN(percent) ? 0 : percent, rules.redStartPercent(), rules.orange(), rules.red()), "#ff8a1f");
                icon[i] = HudMath.safeColor(HudMath.exposureIconColor(percent, HudMath.safeColor(cfg.getExposureIconLow(), "#ffd23f"), HudMath.safeColor(cfg.getExposureIconHigh(), "#ff8a1f")), "#ffd23f");
            }
        }
        state.view.refresh(state.percent, fill, icon, state.alert);
    }

    /** The four values in percent (NaN for a bar that cannot be read, which is then not drawn). */
    static void readValues(PlayerRef ref, Ref<EntityStore> entity, ComponentAccessor<EntityStore> accessor, WaterApi water, double[] out) {
        Power power = Power.get();
        out[HudBar.POWER.ordinal()] = power == null ? Double.NaN : power.fraction(accessor, entity) * 100.0;
        out[HudBar.HEALTH.ordinal()] = health(accessor, entity);
        if (water == null) {
            out[HudBar.EXPOSURE.ordinal()] = Double.NaN;
            out[HudBar.WATER.ordinal()] = Double.NaN;
        } else {
            double max = water.max();
            out[HudBar.EXPOSURE.ordinal()] = clamp(water.getExposure(ref) / max * 100.0);
            out[HudBar.WATER.ordinal()] = clamp(water.getWater(ref) / max * 100.0);
        }
    }

    private static double health(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> entity) {
        EntityStatMap stats = accessor.getComponent(entity, EntityStatMap.getComponentType());
        EntityStatValue value = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
        return value == null || value.getMax() <= 0 ? Double.NaN : clamp(value.get() / value.getMax() * 100.0);
    }

    private static double clamp(double percent) {
        return Math.max(0.0, Math.min(100.0, percent));
    }
}
