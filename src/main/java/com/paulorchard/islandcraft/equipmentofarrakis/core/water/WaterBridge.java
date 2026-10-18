package com.paulorchard.islandcraft.equipmentofarrakis.core.water;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.paulorchard.islandcraft.waterofarrakis.WaterOfArrakisPlugin;
import com.paulorchard.islandcraft.waterofarrakis.WaterService;

import java.lang.reflect.Method;

/**
 * The one class of this mod that names classes of Water of Arrakis (compiled against its jar, which is not packed into this one). The
 * JVM loads it only when something first uses it, and {@link WaterLink} does that only after checking that the Water plugin is
 * present, and inside a handler for linkage errors; so when Water is not installed this class is never loaded and nothing here can
 * fail. What the jar this was compiled against may not have is looked up by reflection, so an older or a newer Water jar both work.
 */
final class WaterBridge implements WaterApi {

    private final Method setHudHidden = method("setHudHidden", boolean.class);
    private final Method redStart = method("getExposureRedStartPercent");
    private final Method orange = method("getExposureColorOrange");
    private final Method red = method("getExposureColorRed");

    @Override
    public double getWater(PlayerRef player) {
        return WaterOfArrakisPlugin.service().getWater(player);
    }

    @Override
    public double addWater(PlayerRef player, double amount) {
        return WaterOfArrakisPlugin.service().addWater(player, amount);
    }

    @Override
    public double max() {
        return WaterService.MAX;
    }

    @Override
    public double getExposure(PlayerRef player) {
        return WaterOfArrakisPlugin.service().getExposure(player);
    }

    @Override
    public boolean setHudHidden(boolean hidden) {
        if (setHudHidden == null) {
            return false;
        }
        try {
            setHudHidden.invoke(WaterOfArrakisPlugin.service(), hidden);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            HytaleLogger.getLogger().atWarning().log("Water of Arrakis setHudHidden failed: %s", e);
            return false;
        }
    }

    @Override
    public ExposureColors exposureColors() {
        if (redStart == null || orange == null || red == null) {
            return null;
        }
        try {
            Object service = WaterOfArrakisPlugin.service();
            return new ExposureColors(((Number) redStart.invoke(service)).doubleValue(), (String) orange.invoke(service), (String) red.invoke(service));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Method method(String name, Class<?>... parameters) {
        try {
            return WaterService.class.getMethod(name, parameters);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
