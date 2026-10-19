package com.paulorchard.islandcraft.equipmentofarrakis.core.worms;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.paulorchard.islandcraft.wormsofarrakis.WormAggro;
import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakisPlugin;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The one class of this mod that names classes of Worms of Arrakis (compiled against its jar, which is not packed into this one). The
 * JVM loads it only when something first uses it, and {@link WormsLink} does that only after finding the Worms plugin, inside a handler
 * for linkage errors; so when Worms is not installed this class is never loaded and nothing here can fail.
 */
final class WormsBridge implements WormsApi {

    private final Method sandBlockIds = method();

    @Override
    public boolean registerTotem(String deployableId) {
        return WormAggro.registerTotem(deployableId);
    }

    @Override
    public boolean unregisterTotem(String deployableId) {
        return WormAggro.unregisterTotem(deployableId);
    }

    @Override
    public List<String> totemIds() {
        return WormAggro.totemIds();
    }

    @Override
    public Set<String> sandBlockNames() {
        if (sandBlockIds == null) {
            return null;
        }
        try {
            Object result = sandBlockIds.invoke(WormsOfArrakisPlugin.get());
            if (!(result instanceof Set<?> ids)) {
                return null;
            }
            Set<String> names = new LinkedHashSet<>();
            for (Object id : ids) {
                if (id instanceof Integer index) {
                    BlockType type = BlockType.getAssetMap().getAsset(index);
                    if (type != null && type.getId() != null) {
                        names.add(String.valueOf(type.getId()));
                    }
                }
            }
            return names;
        } catch (ReflectiveOperationException | RuntimeException e) {
            HytaleLogger.getLogger().atWarning().log("Worms of Arrakis sand block list could not be read: %s", e);
            return null;
        }
    }

    @Override
    public Double sandTolerance() {
        try {
            java.lang.reflect.Field field = WormsOfArrakisPlugin.class.getDeclaredField("config");
            field.setAccessible(true);
            Object holder = field.get(WormsOfArrakisPlugin.get());
            Object settings = holder.getClass().getMethod("get").invoke(holder);
            Object value = settings.getClass().getMethod("getSandTolerance").invoke(settings);
            return value instanceof Number number ? number.doubleValue() : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Method method() {
        try {
            Method m = WormsOfArrakisPlugin.class.getDeclaredMethod("sandBlockIds");
            m.setAccessible(true);
            return m;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
