package com.paulorchard.islandcraft.equipmentofarrakis.weapons;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Development: lists every field of two loaded items whose values differ (arrays and maps compared by content, other objects by their
 * text), to find what an item lacks that a vanilla one has. {@code /arrakis weapons diff <itemA> <itemB>}.
 */
final class ItemDiff {

    private ItemDiff() {
    }

    static void run(String idA, String idB, Consumer<String> out) {
        Item a = Item.getAssetMap().getAsset(idA);
        Item b = Item.getAssetMap().getAsset(idB);
        if (a == null || b == null) {
            out.accept("Not loaded: " + (a == null ? idA : idB));
            return;
        }
        int differences = 0;
        for (Class<?> type = Item.class; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    String left = text(field.get(a));
                    String right = text(field.get(b));
                    if (!Objects.equals(left, right)) {
                        differences++;
                        out.accept(field.getName() + ": " + clip(left) + "  |  " + clip(right));
                    }
                } catch (ReflectiveOperationException | RuntimeException e) {
                    out.accept(field.getName() + ": cannot read (" + e.getClass().getSimpleName() + ")");
                }
            }
        }
        out.accept(differences + " differing field(s) between " + idA + " and " + idB);
    }

    private static String text(Object value) {
        if (value instanceof Object[] array) {
            return Arrays.deepToString(array);
        }
        if (value instanceof int[] array) {
            return Arrays.toString(array);
        }
        return String.valueOf(value);
    }

    private static String clip(String text) {
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }
}
