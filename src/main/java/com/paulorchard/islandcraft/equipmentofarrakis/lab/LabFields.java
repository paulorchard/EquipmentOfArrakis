// EXPERIMENT: delete with the lab module
package com.paulorchard.islandcraft.equipmentofarrakis.lab;

import com.hypixel.hytale.protocol.FlyMode;
import com.hypixel.hytale.protocol.MovementSettings;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Sets any float, boolean or fly-mode field of the protocol {@code MovementSettings} by its name (case does not matter). */
final class LabFields {

    private LabFields() {
    }

    /** The settable field names, in declaration order. */
    static List<String> names() {
        List<String> names = new ArrayList<>();
        for (Field field : MovementSettings.class.getFields()) {
            if (settable(field)) {
                names.add(field.getName());
            }
        }
        return names;
    }

    private static boolean settable(Field field) {
        int modifiers = field.getModifiers();
        Class<?> type = field.getType();
        return Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers)
                && (type == float.class || type == boolean.class || type == FlyMode.class);
    }

    private static Field find(String name) {
        for (Field field : MovementSettings.class.getFields()) {
            if (settable(field) && field.getName().equalsIgnoreCase(name)) {
                return field;
            }
        }
        return null;
    }

    /** The current value of a field as text, or null for an unknown field. */
    static String get(MovementSettings settings, String name) {
        Field field = find(name);
        try {
            return field == null ? null : String.valueOf(field.get(settings));
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    /**
     * Sets {@code name} to {@code value} and returns a line saying what changed ("name: old -> new"), or a line starting with "no" or "cannot" when
     * the field is unknown or the value does not fit its type.
     */
    static String set(MovementSettings settings, String name, String value) {
        Field field = find(name);
        if (field == null) {
            return "no settable field called " + name + " (float, boolean and fly fields only)";
        }
        try {
            Object old = field.get(settings);
            Class<?> type = field.getType();
            if (type == float.class) {
                field.setFloat(settings, Float.parseFloat(value));
            } else if (type == boolean.class) {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    return "cannot set " + field.getName() + ": " + value + " is not true or false";
                }
                field.setBoolean(settings, Boolean.parseBoolean(value));
            } else {
                String word = LabRules.flyWord(value);
                if (word == null) {
                    return "cannot set " + field.getName() + ": " + value + " is not off, allowed or forced";
                }
                field.set(settings, FlyMode.valueOf(word));
            }
            return field.getName() + ": " + old + " -> " + field.get(settings);
        } catch (NumberFormatException e) {
            return "cannot set " + field.getName() + ": " + value + " is not a number";
        } catch (IllegalAccessException e) {
            return "cannot set " + field.getName() + ": " + e.getMessage();
        }
    }
}
