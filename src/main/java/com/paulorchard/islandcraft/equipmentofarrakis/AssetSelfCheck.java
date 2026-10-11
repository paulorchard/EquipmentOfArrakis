package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ItemAnimation;
import com.hypixel.hytale.protocol.PrioritySlot;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the loaded assets back and says what the game made of them, so a typo in a JSON file shows up in the log at
 * start instead of as a missing animation in the world. Pure reporting: nothing here changes any state.
 */
final class AssetSelfCheck {

    private AssetSelfCheck() {
    }

    /** One entry per tool: item id, the animation key its secondary uses, and the interaction that plays it. */
    private static final String[][] OFF_HAND = {
            {"Arrakis_Blood_Extractor", "Arrakis_Hold_Out", "Arrakis_Blood_Extractor_Hold"},
            {"Arrakis_Dew_Reaper", "Arrakis_Slow_Swing", "Arrakis_Dew_Reaper_Swing"},
            {"Arrakis_Compactor", "Arrakis_Aim", "Arrakis_Compactor_Aim"},
    };

    /** Returns the lines to log; lines starting with "PROBLEM" are errors. */
    static List<String> run() {
        List<String> lines = new ArrayList<>();
        for (String[] tool : OFF_HAND) {
            Item item = Item.getAssetMap().getAsset(tool[0]);
            if (item == null) {
                lines.add("PROBLEM no item " + tool[0]);
                continue;
            }
            lines.add(String.format("%s: usable=%s compatible=%s scale=%.4f model=%s animations=%s light=%s secondary=%s",
                    tool[0], item.getUtility() != null && item.getUtility().isUsable(),
                    item.getUtility() != null && item.getUtility().isCompatible(), item.getScale(), item.getModel(),
                    item.getPlayerAnimationsId(), item.getLight(), item.getInteractions().get(InteractionType.Secondary)));
            if (item.getLight() != null) {
                lines.add("PROBLEM " + tool[0] + " has a light");
            }
            ItemPlayerAnimations set = ItemPlayerAnimations.getAssetMap().getAsset(item.getPlayerAnimationsId());
            if (set == null) {
                lines.add("PROBLEM no animation set " + item.getPlayerAnimationsId());
            } else {
                ItemAnimation animation = set.getAnimations().get(tool[1]);
                lines.add(String.format("  animation set %s: %d animations, %s=%s, Idle=%s, camera pitch=%s",
                        set.getId(), set.getAnimations().size(), tool[1],
                        animation == null ? "MISSING" : "speed " + animation.speed + " " + animation.thirdPerson,
                        set.getAnimations().containsKey("Idle"), set.getCamera() == null ? "none"
                                : String.valueOf(set.getCamera().getPitch())));
                if (animation == null) {
                    lines.add("PROBLEM no animation " + tool[1]);
                }
            }
            Interaction interaction = Interaction.getAssetMap().getAsset(tool[2]);
            lines.add("  interaction " + tool[2] + ": " + (interaction == null ? "MISSING"
                    : interaction.getClass().getSimpleName() + " runTime=" + interaction.getRunTime()));
            if (interaction == null) {
                lines.add("PROBLEM no interaction " + tool[2]);
            }
            String root = item.getInteractions().get(InteractionType.Secondary);
            if (root == null || RootInteraction.getAssetMap().getAsset(root) == null) {
                lines.add("PROBLEM no root interaction " + root);
            }
            lines.add("  secondary priority main hand/off hand: "
                    + item.getInteractionConfig().getPriorityFor(InteractionType.Secondary, PrioritySlot.MainHand) + "/"
                    + item.getInteractionConfig().getPriorityFor(InteractionType.Secondary, PrioritySlot.OffHand));
        }

        lines.addAll(mk2());
        lines.addAll(compactor());
        lines.addAll(dewReaper());
        lines.addAll(holtzman());
        lines.addAll(darts());
        lines.addAll(hud());
        return lines;
    }

    /** The Cutter Ray: the item, its animations, the gate, the projectile and the particle systems it refers to. */
    private static List<String> mk2() {
        List<String> lines = new ArrayList<>();
        Item mk2 = Item.getAssetMap().getAsset("Arrakis_Cutter_Ray");
        if (mk2 == null) {
            lines.add("PROBLEM no item Arrakis_Cutter_Ray");
            return lines;
        }
        lines.add(String.format("Arrakis_Cutter_Ray: model=%s animations=%s primary=%s secondary=%s priority primary/secondary %d/%d",
                mk2.getModel(), mk2.getPlayerAnimationsId(), mk2.getInteractions().get(InteractionType.Primary),
                mk2.getInteractions().get(InteractionType.Secondary),
                mk2.getInteractionConfig().getPriorityFor(InteractionType.Primary, PrioritySlot.MainHand),
                mk2.getInteractionConfig().getPriorityFor(InteractionType.Secondary, PrioritySlot.MainHand)));
        ItemPlayerAnimations set = ItemPlayerAnimations.getAssetMap().getAsset(mk2.getPlayerAnimationsId());
        ItemAnimation shoot = set == null ? null : set.getAnimations().get("Shoot");
        lines.add("  Cutter Ray animation set: " + (shoot == null ? "PROBLEM no Shoot animation" : "Shoot speed " + shoot.speed + " "
                + shoot.thirdPerson + " / " + shoot.firstPerson));
        Interaction gate = Interaction.getAssetMap().getAsset("Arrakis_Cutter_Ray_Gate");
        lines.add("  gate: " + (gate instanceof CutterRayGateInteraction ? "ok" : "PROBLEM missing or of the wrong type"));
        Interaction shot = Interaction.getAssetMap().getAsset("Arrakis_Cutter_Ray_Shoot");
        lines.add("  shot interaction: " + (shot == null ? "PROBLEM MISSING" : shot.getClass().getSimpleName()
                + " runTime=" + shot.getRunTime()));
        RootInteraction primary = RootInteraction.getAssetMap().getAsset("Arrakis_Cutter_Ray_Primary");
        lines.add("  primary root: " + (primary == null ? "PROBLEM MISSING" : "ok"));
        com.hypixel.hytale.server.core.asset.type.projectile.config.Projectile bullet =
                com.hypixel.hytale.server.core.asset.type.projectile.config.Projectile.getAssetMap()
                        .getAsset("Arrakis_Cutter_Ray_Bullet");
        lines.add("  projectile: " + (bullet == null ? "PROBLEM MISSING" : "damage=" + bullet.getDamage()
                + " deathSound=" + bullet.getDeathSoundEventId() + " hitSound=" + bullet.getHitSoundEventId()
                + " missSound=" + bullet.getMissSoundEventId()));
        for (String id : new String[] {"Arrakis_Cutter_Ray_Muzzle", "Arrakis_Cutter_Ray_Impact", "Arrakis_Cutter_Ray_Sparks"}) {
            lines.add("  particle system " + id + ": "
                    + (ParticleSystem.getAssetMap().getAsset(id) == null ? "PROBLEM MISSING" : "ok"));
        }
        for (String id : new String[] {"SFX_Arrakis_Cutter_Ray_Firing", "SFX_Arrakis_Cutter_Ray_Firing_Local",
                "SFX_Arrakis_Cutter_Ray_Firing_End", "SFX_Arrakis_Cutter_Ray_Firing_End_Local",
                "SFX_Arrakis_Cutter_Ray_Impact", "SFX_Arrakis_Cutter_Ray_Impact_Local",
                "SFX_Arrakis_Cutter_Ray_Fizzle", "SFX_Arrakis_Cutter_Ray_Fizzle_Local"}) {
            com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent sound =
                    com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent.getAssetMap().getAsset(id);
            lines.add("  sound event " + id + ": " + (sound == null ? "PROBLEM MISSING" : "ok, layers=" + sound.getLayers().length
                    + " maxInstance=" + sound.getMaxInstance() + " files=" + sound.getLayers()[0].getFiles().length));
        }
        com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect firing =
                com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect.getAssetMap()
                        .getAsset("Arrakis_Cutter_Ray_Firing");
        lines.add("  effect Arrakis_Cutter_Ray_Firing: " + (firing == null ? "PROBLEM MISSING" : "ok, duration=" + firing.getDuration()
                + " overlap=" + firing.getOverlapBehavior() + " infinite=" + firing.isInfinite()));
        lines.add("  firing interaction: " + (Interaction.getAssetMap().getAsset("Arrakis_Cutter_Ray_Firing") == null
                ? "PROBLEM MISSING" : "ok"));
        for (String clip : new String[] {"Fizzle", "Impact_Stone", "Impact_Burn"}) {
            boolean there = AssetSelfCheck.class.getResource("/Common/Sounds/Equipment_of_Arrakis/Arrakis_Cutter_Ray_" + clip + ".ogg")
                    != null;
            lines.add("  clip " + clip + " in the jar: " + (there ? "ok" : "PROBLEM MISSING"));
        }
        return lines;
    }

    /** The Compactor: the compacting interaction type, the hum effect and its sound events and loop clip. */
    private static List<String> compactor() {
        List<String> lines = new ArrayList<>();
        Interaction aim = Interaction.getAssetMap().getAsset("Arrakis_Compactor_Aim");
        lines.add("Compactor aim/compact interaction: " + (aim instanceof CompactorInteraction ? "ok (CompactorInteraction)"
                : "PROBLEM missing or of the wrong type: " + aim));
        com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect hum =
                com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect.getAssetMap()
                        .getAsset("Arrakis_Compactor_Active");
        lines.add("  effect Arrakis_Compactor_Active: " + (hum == null ? "PROBLEM MISSING" : "ok, duration=" + hum.getDuration()
                + " overlap=" + hum.getOverlapBehavior() + " infinite=" + hum.isInfinite()));
        for (String id : new String[] {"SFX_Arrakis_Compactor_Active", "SFX_Arrakis_Compactor_Active_Local"}) {
            com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent sound =
                    com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent.getAssetMap().getAsset(id);
            lines.add("  sound event " + id + ": " + (sound == null ? "PROBLEM MISSING" : "ok, layers=" + sound.getLayers().length
                    + " maxInstance=" + sound.getMaxInstance()));
        }
        lines.add("  particle system Arrakis_Compactor_Dust: "
                + (ParticleSystem.getAssetMap().getAsset("Arrakis_Compactor_Dust") == null ? "PROBLEM MISSING" : "ok"));
        return lines;
    }

    /** The on-screen display: its document, and the tool panel items with the Secondary root the system watches for. */
    private static List<String> hud() {
        List<String> lines = new ArrayList<>();
        boolean ui = AssetSelfCheck.class.getResource("/Common/UI/Custom/Hud/Equipment_of_Arrakis/Arrakis_Equipment.ui") != null;
        lines.add("HUD document Arrakis_Equipment.ui in the jar: " + (ui ? "ok" : "PROBLEM MISSING"));
        for (String id : new HudSettings().getToolItems()) {
            Item item = Item.getAssetMap().getAsset(id);
            String root = item == null ? null : item.getInteractions().get(InteractionType.Secondary);
            lines.add("  tool panel item " + id + ": " + (item == null ? "PROBLEM no such item" : root == null
                    ? "PROBLEM no Secondary root" : "ok, Secondary root " + root));
        }
        return lines;
    }

    /** The Dew Reaper: the two interaction types, the hum effect, and whether Water of Arrakis can be used. */
    private static List<String> dewReaper() {
        List<String> lines = new ArrayList<>();
        Interaction gate = Interaction.getAssetMap().getAsset("Arrakis_Dew_Reaper_Gate");
        Interaction finish = Interaction.getAssetMap().getAsset("Arrakis_Dew_Reaper_Finish");
        lines.add("Dew Reaper gate: " + (gate instanceof DewReaperGateInteraction ? "ok" : "PROBLEM missing or of the wrong type")
                + ", finish: " + (finish instanceof DewReaperFinishInteraction ? "ok" : "PROBLEM missing or of the wrong type"));
        com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect hum =
                com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect.getAssetMap()
                        .getAsset("Arrakis_Dew_Reaper_Active");
        lines.add("  effect Arrakis_Dew_Reaper_Active: " + (hum == null ? "PROBLEM MISSING" : "ok, duration=" + hum.getDuration()
                + " overlap=" + hum.getOverlapBehavior()));
        lines.add("  " + WaterLink.status());
        return lines;
    }

    /**
     * The two spring guns: each item (did Parent bring the vanilla model, icon, animations, categories, Secondary and swing vars; is it kept
     * out of the off-hand rules), its Primary root (RequireNewClick, the Shoot cooldown), the gate and shot interactions, the dart, the
     * smoke, the four sound events with the volume of the Transient layer, and the ammo stat.
     */
    private static List<String> darts() {
        List<String> lines = new ArrayList<>();
        lines.add("Arrakis_Ammo stat: " + (ArrakisAmmo.statIndex() == Integer.MIN_VALUE ? "PROBLEM MISSING" : "ok, index " + ArrakisAmmo.statIndex()));
        lines.add("Arrakis_Dart_Shot particle system: " + (ParticleSystem.getAssetMap().getAsset("Arrakis_Dart_Shot") == null ? "PROBLEM MISSING" : "ok"));
        String[][] guns = {{"Arrakis_Maula_Pistol", "Arrakis_Maula", "Weapon_Handgun", "SFX_Handgun_Fire"},
                {"Arrakis_Dartgun", "Arrakis_Dartgun", "Weapon_Assault_Rifle", "SFX_Rifle_Fire"}};
        for (String[] gun : guns) {
            Item item = Item.getAssetMap().getAsset(gun[0]);
            Item vanilla = Item.getAssetMap().getAsset(gun[2]);
            if (item == null || vanilla == null) {
                lines.add("PROBLEM no item " + (item == null ? gun[0] : gun[2]));
                continue;
            }
            lines.add(String.format("%s: categories=%s model=%s texture=%s icon=%s animations=%s weapon=%s usable=%s compatible=%s (vanilla %s usable=%s compatible=%s)",
                    gun[0], String.join(",", item.getCategories()), item.getModel(), item.getTexture(), item.getIcon(), item.getPlayerAnimationsId(),
                    item.getWeapon() != null, item.getUtility() != null && item.getUtility().isUsable(),
                    item.getUtility() != null && item.getUtility().isCompatible(), gun[2], vanilla.getUtility() != null && vanilla.getUtility().isUsable(),
                    vanilla.getUtility() != null && vanilla.getUtility().isCompatible()));
            lines.add(String.format("  name %s; primary=%s secondary=%s (vanilla %s); %d interaction vars (vanilla %d); priority primary/secondary main hand %d/%d",
                    item.getTranslationProperties(), item.getInteractions().get(InteractionType.Primary),
                    item.getInteractions().get(InteractionType.Secondary), vanilla.getInteractions().get(InteractionType.Secondary),
                    item.getInteractionVars().size(), vanilla.getInteractionVars().size(),
                    item.getInteractionConfig().getPriorityFor(InteractionType.Primary, PrioritySlot.MainHand),
                    item.getInteractionConfig().getPriorityFor(InteractionType.Secondary, PrioritySlot.MainHand)));
            if (!java.util.Objects.equals(item.getModel(), vanilla.getModel()) || !java.util.Objects.equals(item.getIcon(), vanilla.getIcon())
                    || !java.util.Objects.equals(item.getPlayerAnimationsId(), vanilla.getPlayerAnimationsId())) {
                lines.add("PROBLEM " + gun[0] + " does not have the vanilla model, icon and animations");
            }
            if (!java.util.Objects.equals(item.getInteractions().get(InteractionType.Secondary), vanilla.getInteractions().get(InteractionType.Secondary))
                    || item.getInteractionVars().size() != vanilla.getInteractionVars().size()) {
                lines.add("PROBLEM " + gun[0] + " lost the vanilla Secondary or the swing interaction vars");
            }
            RootInteraction root = RootInteraction.getAssetMap().getAsset(item.getInteractions().get(InteractionType.Primary));
            if (root == null) {
                lines.add("PROBLEM no Primary root for " + gun[0]);
            } else {
                com.hypixel.hytale.protocol.RootInteraction packet = root.toPacket();
                lines.add(String.format("  primary root %s: requireNewClick=%s clickQueuingTimeout=%s cooldown=%s %s, interactions=%s", root.getId(), packet.requireNewClick,
                        packet.clickQueuingTimeout,
                        packet.cooldown == null ? null : packet.cooldown.cooldownId, packet.cooldown == null ? null : packet.cooldown.cooldown,
                        String.join(",", root.getInteractionIds())));
            }
            Interaction gate = Interaction.getAssetMap().getAsset(gun[1] + "_Gate");
            lines.add("  gate " + gun[1] + "_Gate: " + (gate instanceof DartAmmoGateInteraction ? "ok (" + gate + ")" : "PROBLEM missing or of the wrong type"));
            Interaction shot = Interaction.getAssetMap().getAsset(gun[1] + "_Shoot");
            lines.add("  shot " + gun[1] + "_Shoot: " + (shot == null ? "PROBLEM MISSING" : shot.getClass().getSimpleName() + " runTime=" + shot.getRunTime()));
            com.hypixel.hytale.server.core.asset.type.projectile.config.Projectile dart =
                    com.hypixel.hytale.server.core.asset.type.projectile.config.Projectile.getAssetMap().getAsset(gun[1] + "_Dart");
            lines.add("  dart " + gun[1] + "_Dart: " + (dart == null ? "PROBLEM MISSING" : String.format(
                    "damage=%d muzzle=%.0f terminal=%.0f gravity=%.1f sticksVertically=%s deadTime=%.2f deadTimeMiss=%.2f appearance=%s deathSound=%s radius=%.2f",
                    dart.getDamage(), dart.getMuzzleVelocity(), dart.getTerminalVelocity(), dart.getGravity(), dart.isSticksVertically(),
                    dart.getDeadTime(), dart.getDeadTimeMiss(), dart.getAppearance(), dart.getDeathSoundEventId(), dart.getRadius())));
            for (String suffix : new String[] {"", "_Local"}) {
                com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent sound =
                        com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent.getAssetMap().getAsset("SFX_" + gun[1] + "_Fire" + suffix);
                com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent original =
                        com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent.getAssetMap().getAsset(gun[3] + suffix);
                lines.add("  sound event SFX_" + gun[1] + "_Fire" + suffix + ": " + (sound == null || original == null ? "PROBLEM MISSING" : String.format(
                        "%d layers, Transient %.1f dB (vanilla %.1f), the others %s (vanilla %s), maxInstance=%d", sound.getLayers().length,
                        sound.getLayers()[0].getVolume(), original.getLayers()[0].getVolume(), volumes(sound), volumes(original), sound.getMaxInstance())));
            }
        }
        return lines;
    }

    private static String volumes(com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent sound) {
        StringBuilder out = new StringBuilder();
        for (int i = 1; i < sound.getLayers().length; i++) {
            out.append(i > 1 ? "/" : "").append(sound.getLayers()[i].getVolume());
        }
        return out.toString();
    }

    /** The Holtzman Shield: the item, the effect, the ModelVFX, the particle systems and the sound events. */
    private static List<String> holtzman() {
        List<String> lines = new ArrayList<>();
        Item item = Item.getAssetMap().getAsset("Arrakis_Holtzman_Shield");
        lines.add("Arrakis_Holtzman_Shield: " + (item == null ? "PROBLEM MISSING" : String.format(
                "usable=%s compatible=%s animations=%s primary=%s secondary=%s weapon=%s model=%s off-hand priority primary/secondary %d/%d",
                item.getUtility() != null && item.getUtility().isUsable(), item.getUtility() != null && item.getUtility().isCompatible(),
                item.getPlayerAnimationsId(), item.getInteractions().get(InteractionType.Primary),
                item.getInteractions().get(InteractionType.Secondary), item.getWeapon() != null, item.getModel(),
                item.getInteractionConfig().getPriorityFor(InteractionType.Primary, PrioritySlot.OffHand),
                item.getInteractionConfig().getPriorityFor(InteractionType.Secondary, PrioritySlot.OffHand))));
        com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect field =
                com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect.getAssetMap().getAsset("Arrakis_Holtzman_Field");
        lines.add("  effect Arrakis_Holtzman_Field: " + (field == null ? "PROBLEM MISSING" : "ok, infinite=" + field.isInfinite()
                + " overlap=" + field.getOverlapBehavior()));
        com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect impact =
                com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect.getAssetMap().getAsset("Arrakis_Holtzman_Impact");
        lines.add("  effect Arrakis_Holtzman_Impact: " + (impact == null ? "PROBLEM MISSING" : "ok, duration=" + impact.getDuration()));
        for (String id : new String[] {"Arrakis_Holtzman_Bubble", "Arrakis_Holtzman_Bubble_FP", "Arrakis_Holtzman_Hit", "Arrakis_Holtzman_Shatter"}) {
            lines.add("  particle system " + id + ": " + (ParticleSystem.getAssetMap().getAsset(id) == null ? "PROBLEM MISSING" : "ok"));
        }
        for (String id : new String[] {"SFX_Arrakis_Holtzman_Field", "SFX_Arrakis_Holtzman_Field_Local", "SFX_Arrakis_Holtzman_Break",
                "SFX_Arrakis_Holtzman_Break_Local"}) {
            lines.add("  sound event " + id + ": " + (com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent.getAssetMap()
                    .getAsset(id) == null ? "PROBLEM MISSING" : "ok"));
        }
        return lines;
    }
}
