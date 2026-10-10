// Builds the molten glob particle effect of the Cutter Ray: Server/Particles/Equipment_of_Arrakis/.
//
// A glob hangs still where it is thrown off for 0.5 to 0.75 s and then falls. The engine has no per-particle delay
// before gravity, so each glob is a pair of spawners that start at the same spot with the same size:
//   Hold_n  a stationary glob (no gravity, no velocity) that lives for the delay;
//   Fall_n  started after that delay (StartDelay in the system), the same glob, now falling: random direction
//           (straight down to almost sideways, never upward), then gravity, shrinking to almost nothing, vanishing
//           early on touching something solid.
// There are three pairs with different delays, sizes and shades of white (a falling glob darkens as it cools), and a fixed offset each so that their globs do not sit on
// one another; the position and size of a Hold and its Fall are identical, so the hand-over is not visible.
// Each glob is a white soft glow (Basic/Glow.png, added to the scene, so it shines), not the yellow lava bubble.
// The code tints every burst with the colour of the block it hits (BlockType.getParticleColor), which is why the
// base colours are white-ish. There are three systems, Arrakis_Cutter_Molten_1 to _3, holding the first 1, 2 or 3
// pairs; the code picks one at random for each burst, so a burst throws 1 to 3 globs.
//
//   node tools/assets/make_molten.js         the Cutter Ray globs (Arrakis_Cutter_Molten_*, tinted by the code)
//   node tools/assets/make_molten.js mk2     the Cutter Ray Mk2 globs: Server/Particles/Equipment_of_Arrakis_Mk2/Arrakis_Cutter_Mk2_Globs*
//
// The Mk2 palette is pure white (#ffffff) falling to light grey (#bfbfbf), not tinted by anything, and every glob starts at
// the very point the system is spawned at (no offset), because the Mk2 impact system puts them at the position of its smoke.
// make_mk2.js then builds the impact system from the one system written here (Arrakis_Cutter_Mk2_Globs, all three pairs).
const fs = require('fs');
const path = require('path');

const MK2 = process.argv[2] === 'mk2';
const PREFIX = MK2 ? 'Arrakis_Cutter_Mk2_Globs' : 'Arrakis_Cutter_Molten';
const out = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'Server', 'Particles',
    MK2 ? 'Equipment_of_Arrakis_Mk2' : 'Equipment_of_Arrakis');
if (!MK2) {
    // The Mk2 folder is shared with make_mk2.js, so it is not emptied.
    fs.rmSync(out, { recursive: true, force: true });
}
fs.mkdirSync(path.join(out, 'Spawners'), { recursive: true });

const PAIRS = MK2 ? [
    { delay: 0.5, size: 0.025, color: '#ffffff', offset: { x: 0, y: 0, z: 0 } },
    { delay: 0.58, size: 0.036, color: '#ffffff', offset: { x: 0, y: 0, z: 0 } },
    { delay: 0.67, size: 0.03, color: '#ffffff', offset: { x: 0, y: 0, z: 0 } },
] : [
    { delay: 0.5, size: 0.025, color: '#ffffff', offset: { x: -0.03, y: 0.0, z: 0.02 } },
    { delay: 0.58, size: 0.036, color: '#f0f0f0', offset: { x: 0.025, y: 0.015, z: -0.03 } },
    { delay: 0.67, size: 0.03, color: '#e0e0e0', offset: { x: 0.0, y: -0.02, z: 0.035 } },
];
const OVERLAP = 0.04;      // the Hold outlives its delay a little, hidden under the Fall, so there is no gap
const GLOW = 1.6;          // the soft glow texture is mostly faint edge, so it is drawn larger than the size of the glob
const COOLED = MK2 ? 0.75 : 0.4;   // a falling glob ends at this share of its colour (light grey for Mk2, a darker shade of the block's colour for the Cutter Ray)
function cooled(hex) {
    const channel = i => Math.round(parseInt(hex.slice(1 + 2 * i, 3 + 2 * i), 16) * COOLED).toString(16).padStart(2, '0');
    return '#' + channel(0) + channel(1) + channel(2);
}

const range = (min, max) => ({ Min: min, Max: max });
const zero = v => ({ X: range(v.x, v.x), Y: range(v.y, v.y), Z: range(v.z, v.z) });
const rotation0 = { X: range(0, 0), Y: range(0, 0), Z: range(0, 0) };

function spawner(comment, pair, life, falling) {
    const body = {
        $Comment: comment,
        RenderMode: 'BlendAdd',
        EmitOffset: zero(pair.offset),
        ParticleRotationInfluence: 'Billboard',
        LinearFiltering: false,
        LightInfluence: 0,
        ParticleRotateWithSpawner: false,
        MaxConcurrentParticles: 4,
        ParticleLifeSpan: life,
        SpawnRate: range(500, 500),
        TotalParticles: range(1, 1),
        InitialVelocity: falling
            ? { Speed: range(0, 0.5), Yaw: range(0, 360), Pitch: range(-90, -10) }
            : {},
    };
    if (falling) {
        body.ParticleCollision = { BlockType: 'Solid', Action: 'Expire' };
        body.Attractors = [{
            Position: { X: 0, Y: 0, Z: 0 },
            Radius: 0,
            RadialImpulse: 0,
            RadialTangentAcceleration: 0,
            LinearAcceleration: { X: 0, Y: -9, Z: 0 },
        }];
    }
    body.Particle = {
        Texture: 'Particles/Textures/Basic/Glow.png',
        FrameSize: { Width: 32, Height: 32 },
        ScaleRatioConstraint: 'OneToOne',
        Animation: {
            0: { FrameIndex: range(0, 0), Opacity: 1, Color: pair.color, Scale: { X: range(1, 1) }, Rotation: rotation0 },
            100: { Opacity: 1, Color: falling ? cooled(pair.color) : pair.color, Scale: { X: falling ? range(0.05, 0.05) : range(1, 1) } },
        },
        InitialAnimationFrame: {
            Rotation: rotation0,
            Scale: { X: range(pair.size * GLOW, pair.size * GLOW) },
            Color: pair.color,
            Opacity: 1,
            FrameIndex: range(0, 0),
        },
        SoftParticles: 'Disable',
    };
    return body;
}

const write = (file, json) => fs.writeFileSync(path.join(out, file), JSON.stringify(json, null, 2) + '\n');
const spawners = [];
PAIRS.forEach((pair, i) => {
    const n = i + 1;
    write(`Spawners/${PREFIX}_Hold_${n}.particlespawner`, spawner(
        `Hold ${n}: a molten glob of size ${pair.size} hanging still for ${pair.delay} s, with no gravity. Fall ${n} takes over at the same spot.`,
        pair, range(pair.delay + OVERLAP, pair.delay + OVERLAP), false));
    write(`Spawners/${PREFIX}_Fall_${n}.particlespawner`, spawner(
        `Fall ${n}: the glob of Hold ${n} starts to fall, in a random direction (straight down to almost sideways, never upward), shrinking to almost nothing and darkening to ${cooled(pair.color)}, vanishing on touching something solid.`,
        pair, range(0.5, 0.9), true));
    spawners.push({ SpawnerId: `${PREFIX}_Hold_${n}` });
    spawners.push({ SpawnerId: `${PREFIX}_Fall_${n}`, StartDelay: pair.delay });
});
if (MK2) {
    write(`${PREFIX}.particlesystem`, {
        $Comment: 'The white globs of the Mk2 impact: three globs that hang still and then fall, white fading to light grey. Generated by tools/assets/make_molten.js mk2; make_mk2.js copies these entries into Arrakis_Cutter_Mk2_Impact at the position of its smoke.',
        Spawners: spawners,
    });
} else {
    for (let count = 1; count <= PAIRS.length; count++) {
        write(`Arrakis_Cutter_Molten_${count}.particlesystem`, {
            $Comment: `One burst of ${count} molten glob${count > 1 ? 's' : ''}, tinted by the code with the colour of the block hit. Generated by tools/assets/make_molten.js.`,
            Spawners: spawners.slice(0, count * 2),
        });
    }
}
console.log('wrote', PAIRS.length, 'pairs');
