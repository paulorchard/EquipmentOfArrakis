// EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments").
//
// Writes the particle system of the Flame Cutter, Arrakis_Exp_Flame_Focus, and its spawner Arrakis_Exp_Flame_Focus_Beam.
//
// Test version: DOTS white dots one after the other on the same path, so that they make an almost solid line. Each dot leaves the grip, flies
// straight along the path to the vanishing point (the look target) and shrinks on the way from START_PX pixels wide to END_PX pixel at
// 1920x1080. (The ring of dots pulled in to a focus is in git: da9b732; the single dot: the commit "Flame Cutter casts a single dot".)
//
// How many dots make a solid line? They are released at a constant rate and fly at one speed, so they are evenly spaced in the world; on the
// screen the spacing is the rate of change of the dot's screen position along the path divided by DOTS, and the line is solid where that is no more
// than the dot's width. The path runs from SCREEN_OFFSET blocks off the view axis at START_DIST to the axis at END_DIST (the vanishing point), so a point
// at fraction t of the way is at sx(t) = offset * (1 - t) / d(t) * (pixels per radian) pixels from the vanishing point, d(t) the distance from the eye.
// The worst place is the far end, where the dot is 1 px wide and the path still moves; the maths below finds the least number that keeps the
// spacing within SOLIDITY (0.8) of the width everywhere, and prints it. DOTS = 100 is that with a margin.
//
// How it is made (all data):
//   * SpawnBurst with one particle: one dot per cast. The server casts the system every pass (Arrakis_Exp_Flame_Spawn, pass = LIFETIME), so a new
//     dot is born exactly when the last one dies and there is always one on its way.
//   * InitialVelocity Speed = (RANGE - MUZZLE) / LIFETIME along the system's forward axis, so that it arrives at the target when it dies.
//   * Scale animates from START_SIZE to END_SIZE (frame 100 is END_SIZE / START_SIZE times the initial scale).
//   * Pixel sizes are RELATIVE, for the test. The perspective already shrinks a dot by distance, so what is set here is the world size that
//     makes the dot look START_PX wide at START_DIST blocks from the eye and END_PX at END_DIST, at a vertical field of view VFOV on a
//     screen VERTICAL_PIXELS high: size = px * 2 * distance * tan(VFOV / 2) / VERTICAL_PIXELS, times SPRITE_GAIN because the soft
//     Glow texture only fills part of its square. If the field of view is not 70 degrees the numbers are off by that ratio: change VFOV.
//
//   node tools/assets/experiments/make_flame_focus.js
const fs = require('fs');
const path = require('path');

// ---- the look: change these and run the script again
const START_PX = 50;         // pixels wide near the grip (1920x1080)
const END_PX = 1;            // pixels wide at the vanishing point
const VFOV = 70;             // degrees, vertical field of view of the client (a guess: not read from the client)
const VERTICAL_PIXELS = 1080;
const START_DIST = 1.2;      // blocks from the eye where the dot is born (the grip is about 0.6 from the eye and the system is placed RANGE - MUZZLE before the target)
const END_DIST = 5;          // blocks from the eye at the vanishing point (Apex)
const SPRITE_GAIN = 2;       // the Glow sprite's visible part is about half its square
const RANGE = 5;            // blocks from the eye to the target; the same number as Apex in Arrakis_Exp_Flame_Spawn
const MUZZLE = 1.2;         // blocks: where the dot is born, so it travels RANGE - MUZZLE
const LIFETIME = 0.3;       // seconds from the grip to the vanishing point; also the pass of the chain (make_flame_beam.js: FLAME_RUN_TIME)
const COLOR = '#ffffff';    // white
const DOTS = 100;          // dots on the path at any moment (also released per LIFETIME)
const SCREEN_OFFSET = 0.35; // blocks the dot is born off the view axis (the grip's 0.43 scaled to where the system starts)
const SOLIDITY = 0.8;       // spacing as a fraction of the dot's width at which the line is called solid
const RENDER_MODE = 'BlendAdd';
const TEXTURE = 'Particles/Textures/Basic/Glow.png'; // vanilla, by path: a soft round dot (32x32, one frame)

// ---- derived
const perPx = d => 2 * d * Math.tan(VFOV * Math.PI / 360) / VERTICAL_PIXELS;
const START_SIZE = +(START_PX * perPx(START_DIST) * SPRITE_GAIN).toFixed(4);
const END_SIZE = +(END_PX * perPx(END_DIST) * SPRITE_GAIN).toFixed(4);
const travel = RANGE - MUZZLE;
const speed = travel / LIFETIME;

const marker = 'EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments"). ';
const root = path.join(__dirname, '..', '..', '..', 'src', 'main', 'resources', 'Server', 'Particles', 'Equipment_of_Arrakis', 'Experiments');
const ID = 'Arrakis_Exp_Flame_Focus';

function write(file, json) {
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, JSON.stringify(json, null, 2) + '\n');
}
const range = (min, max) => ({ Min: min, Max: max });
const scale = v => ({ X: range(v, v), Y: range(v, v) });

write(path.join(root, 'Spawners', ID + '_Beam.particlespawner'), {
    $Comment: marker + 'DOTS dots a cast, one after the other, from the grip to the vanishing point, ' + START_PX + ' px to ' + END_PX + ' px (world size ' + START_SIZE + ' to ' + END_SIZE + ' blocks). Generated by tools/assets/experiments/make_flame_focus.js.',
    RenderMode: RENDER_MODE,
    ParticleRotationInfluence: 'Billboard',
    LinearFiltering: true,
    LightInfluence: 0.0,
    ParticleRotateWithSpawner: true,
    MaxConcurrentParticles: DOTS + 10,
    ParticleLifeSpan: range(LIFETIME, LIFETIME),
    SpawnRate: range(DOTS / LIFETIME, DOTS / LIFETIME),
    SpawnBurst: false,
    UseEmitDirection: false,
    InitialVelocity: { Speed: range(speed, speed), Yaw: range(0, 0), Pitch: range(0, 0) },
    Particle: {
        Texture: TEXTURE,
        FrameSize: { Width: 32, Height: 32 },
        ScaleRatioConstraint: 'OneToOne',
        Animation: {
            0: { FrameIndex: range(0, 0), Opacity: 1, Color: COLOR, Scale: scale(1) },
            100: { Opacity: 1, Scale: scale(END_SIZE / START_SIZE) },
        },
        InitialAnimationFrame: { FrameIndex: range(0, 0), Opacity: 1, Scale: scale(START_SIZE) },
        SoftParticles: 'Disable',
    },
});
write(path.join(root, ID + '.particlesystem'), {
    $Comment: marker + 'The Flame Cutter: a line of dots. Spawned in world space by Arrakis_Exp_Flame_Spawn.json with its origin ' + travel + ' blocks before the look target. Generated by tools/assets/experiments/make_flame_focus.js.',
    Spawners: [{ SpawnerId: ID + '_Beam' }],
    LifeSpan: 0.5,
    CullDistance: 100.0,
    IsImportant: false,
});
// the old second spawner (a steady glow at the focus) is not part of this test
for (const old of ['_Point.particlespawner']) {
    const f = path.join(root, 'Spawners', ID + old);
    if (fs.existsSync(f)) fs.unlinkSync(f);
}

// ---- the maths of the line
const pxPerRadian = VERTICAL_PIXELS / (2 * Math.tan(VFOV * Math.PI / 360));
const dist = t => START_DIST + (END_DIST - START_DIST) * t;
const screenX = t => SCREEN_OFFSET * (1 - t) / dist(t) * pxPerRadian;
const widthPx = t => (START_SIZE + (END_SIZE - START_SIZE) * t) / (SPRITE_GAIN * perPx(dist(t)));
let need = 0;
for (let i = 0; i <= 1000; i++) {
    const t = i / 1000;
    need = Math.max(need, Math.abs(screenX(t + 1e-4) - screenX(t)) / 1e-4 / (SOLIDITY * widthPx(t)));
}
console.log('the path is ' + screenX(0).toFixed(0) + ' px long on screen; a solid line needs at least ' + Math.ceil(need) + ' dots, using ' + DOTS
    + ' (spacing ' + (Math.abs(screenX(1e-4) - screenX(0)) / 1e-4 / DOTS).toFixed(1) + ' px next to the grip where the dot is ' + widthPx(0).toFixed(0) + ' px wide, '
    + (Math.abs(screenX(1) - screenX(0.9999)) / 1e-4 / DOTS).toFixed(2) + ' px at the far end where it is ' + widthPx(1).toFixed(1) + ' px)');
console.log('one dot, ' + START_PX + ' px -> ' + END_PX + ' px = size ' + START_SIZE + ' -> ' + END_SIZE + ' blocks, ' + travel.toFixed(1) + ' blocks in ' + LIFETIME + ' s ('
    + speed.toFixed(2) + ' blocks/s)');
