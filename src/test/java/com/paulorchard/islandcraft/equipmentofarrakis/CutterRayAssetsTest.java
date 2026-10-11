package com.paulorchard.islandcraft.equipmentofarrakis;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks on the generated assets of the Cutter Ray (tools/assets/make_cutter_ray.js), read from the jar's resources. */
class CutterRayAssetsTest {

    private static final String ANIMATIONS = "/Common/Characters/Animations/Items/Equipment_of_Arrakis/";

    private static String resource(String path) throws IOException {
        try (InputStream in = CutterRayAssetsTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> nodeNames(String animation) {
        List<String> names = new ArrayList<>();
        Matcher m = Pattern.compile("\"([A-Za-z-]+)\": \\{\\s*\"position\"").matcher(animation);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    @Test
    void theShootAnimationsKeepTheirLengthAndNodes() throws IOException {
        String body = resource(ANIMATIONS + "Arrakis_Cutter_Ray_Shoot.blockyanim");
        String moving = resource(ANIMATIONS + "Arrakis_Cutter_Ray_Shoot_Moving.blockyanim");
        String fps = resource(ANIMATIONS + "Arrakis_Cutter_Ray_Shoot_FPS.blockyanim");
        for (String animation : new String[] {body, moving, fps}) {
            assertTrue(animation.contains("\"duration\": 20"));
        }
        // The same nodes the vanilla animations have, in the same order.
        assertEquals(List.of("Chest", "Belly", "L-Hand", "R-Hand", "L-Forearm", "R-Forearm", "L-Arm", "R-Arm", "Head",
                "Pelvis", "R-Thigh", "L-Thigh", "L-Calf", "R-Calf", "R-Foot", "L-Foot"), nodeNames(body));
        assertEquals(List.of("Chest", "Belly", "L-Hand", "R-Hand", "L-Forearm", "R-Forearm", "L-Arm", "R-Arm", "Head"),
                nodeNames(moving));
        assertEquals(List.of("R-Forearm", "R-Hand", "R-Arm", "L-Arm", "L-Forearm", "L-Hand"), nodeNames(fps));
    }

    @Test
    void theKickIsAFifthOfTheVanillaOne() throws IOException {
        // Vanilla Shoot_FPS: the right arm starts at z 46 and is pushed back to z 40 on the third frame: a kick of 6.
        // At 20% the frame holds z 44.8 (a kick of 1.2) and the held pose, z 46, is the same.
        String fps = resource(ANIMATIONS + "Arrakis_Cutter_Ray_Shoot_FPS.blockyanim");
        assertTrue(fps.contains("\"z\": 44.8"), "kick of the right arm");
        assertTrue(fps.contains("\"z\": 46"), "held pose of the right arm");
        assertFalse(fps.contains("\"z\": 40,") || fps.contains("\"z\": 40\n"), "the vanilla kick of 6 is gone");
    }

    @Test
    void theCameraShakeIsAFifthOfTheVanillaOne() throws IOException {
        String effect = resource("/Server/Camera/CameraEffect/Equipment_of_Arrakis/Arrakis_Cutter_Ray_Shoot.json");
        assertTrue(effect.contains("\"CameraShake\": \"Handgun_Shoot\""));
        assertTrue(effect.contains("\"Value\": 0.01"));
    }

    @Test
    void theCutterRayAssetsCarryNoSoundAndNoCasing() throws IOException {
        String[] files = {
                "/Server/Projectiles/Equipment_of_Arrakis/Arrakis_Cutter_Ray_Bullet.json",
                "/Server/Models/Equipment_of_Arrakis/Arrakis_Cutter_Ray_Bullet.json",
                "/Server/Item/Interactions/Equipment_of_Arrakis/Arrakis_Cutter_Ray_Shoot.json",
                "/Server/Item/Interactions/Equipment_of_Arrakis/Arrakis_Cutter_Ray_Swing_Left.json",
                "/Server/Item/Interactions/Equipment_of_Arrakis/Arrakis_Cutter_Ray_Swing_Left_Damage.json",
                "/Server/Item/Interactions/Equipment_of_Arrakis/Arrakis_Cutter_Ray_Swing_Left_Effect.json",
                "/Server/Item/Items/Equipment_of_Arrakis/Arrakis_Cutter_Ray.json",
                "/Server/Particles/Equipment_of_Arrakis/Arrakis_Cutter_Ray_Muzzle.particlesystem",
                "/Server/Particles/Equipment_of_Arrakis/Arrakis_Cutter_Ray_Impact.particlesystem",
                "/Server/Particles/Equipment_of_Arrakis/Arrakis_Cutter_Ray_Sparks.particlesystem",
        };
        for (String file : files) {
            String text = resource(file);
            // "$Comment" texts may name the removed sounds; the ids themselves must not be in a SoundEventId field.
            assertFalse(text.contains("SoundEventId\""), file + " has a sound event");
            assertFalse(text.contains("Rifle_Bullet_Case"), file + " has the casing");
        }
        assertTrue(resource(files[0]).contains("\"Damage\": 0,"));
    }

    @Test
    void theImpactIsSmokeAndGlobsAtTheSamePointAndTheSparksHaveNoOffset() throws IOException {
        String impact = resource("/Server/Particles/Equipment_of_Arrakis/Arrakis_Cutter_Ray_Impact.particlesystem");
        assertFalse(impact.contains("Explosion_Fail_Sparks"));
        assertFalse(impact.contains("Gun_Debris"));
        assertTrue(impact.contains("Explosion_Fail_Smoke_Linger"));
        // Every entry (the smoke and 3 holds and 3 falls) sits at Y 0.2.
        assertEquals(7, impact.split("\"Y\": 0.2", -1).length - 1);
        String sparks = resource("/Server/Particles/Equipment_of_Arrakis/Arrakis_Cutter_Ray_Sparks.particlesystem");
        assertFalse(sparks.contains("PositionOffset"));
        assertFalse(sparks.contains("Smoke"));
    }
}
