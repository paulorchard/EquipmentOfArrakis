// EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments").
//
// Writes the particle system of the Flame Cutter's focusing beam, Arrakis_Exp_Flame_Focus, and its two spawners:
//
//   Arrakis_Exp_Flame_Focus_Beam   particles leave the muzzle on a RING of START_RADIUS around the beam axis, fly forward at SPEED
//                                  and are pulled towards the axis at a constant speed, so that they reach END_RADIUS exactly when
//                                  they die (after LIFETIME), RANGE blocks from the player. Their size shrinks from START_SIZE to
//                                  END_SIZE over the same life. Seen from the side that is a lens: wide and large at the muzzle,
//                                  narrow and fine at the focus.
//   Arrakis_Exp_Flame_Focus_Point  a tiny steady glow at the focus, so that the end of the beam stays visible (FOCUS_GLOW 0 = none).
//
// How the convergence is made (all of it data, no code):
//   * EmitOffset with Shape Sphere and only X and Y given, both with Min = Max = START_RADIUS: with two ranges the engine spawns on
//     a 2D form of the shape, and Min = Max puts it on the rim, so every particle starts on the same ring (RING_JITTER widens it).
//   * InitialVelocity Speed = SPEED, yaw and pitch 0: straight along the beam (the same direction the vanilla flame is thrown).
//   * One Attractor with RadialAxis (0, 0, 1) (the beam axis in the system's own frame) and RadialImpulse = -(START_RADIUS -
//     END_RADIUS) / LIFETIME: a constant speed TOWARDS the axis. Radial distance is therefore linear in time, and every particle of
//     the ring meets the others at the same moment and place. DampingMultiplier 1 on all axes leaves the forward speed alone.
//   * Scale animates from START_SIZE (the particle's initial scale) to END_SIZE (frame 100 is END_SIZE / START_SIZE times it).
//   * The colours are keyframes over the particle's life (COLORS): pale hot yellow-white, then a bright white-yellow point, no red.
// The system sits at the muzzle (MUZZLE blocks in front of the player) with the same yaw 180 as the vanilla flamethrower, which is
// no longer set: the system is spawned in world space (Arrakis_Exp_Flame_Spawn.json), pointing along the line to the look target.
//
//   node tools/assets/experiments/make_flame_focus.js
const fs = require('fs');
const path = require('path');

// ---- the look: change these and run the script again
const START_RADIUS = 0.45;   // blocks: radius of the ring the particles leave the muzzle on
const END_RADIUS = 0.02;     // blocks: radius at the focus (the fine point); keep it above 0 (a particle exactly on the axis has no direction to be pulled in)
const START_SIZE = 0.3;      // size of a particle at the muzzle (the vanilla flame starts at 0.2 to 0.3)
const END_SIZE = 0.03;       // size at the focus
const RANGE = 5;             // blocks from the player to the focus; the same number as Range in Arrakis_Exp_Cutter_Tick.json
const MUZZLE = 1.2;          // blocks in front of the player where the ring is (the vanilla flame starts 3.7 - 2.5 = 1.2 in front)
const LIFETIME = 0.25;       // seconds from ring to focus
const SPEED = null;          // blocks per second along the beam; null = (RANGE - MUZZLE) / LIFETIME, so the focus is at RANGE
const SPAWN_RATE = 160;      // particles per second
const COLORS = ['#fff3b0', '#fff6c8', '#ffffee']; // frame 0, frame 55, frame 100: pale hot yellow-white to bright white-yellow, no red
const RENDER_MODE = 'BlendAdd';
const TEXTURE = 'Particles/Textures/Basic/Glow.png'; // vanilla, by path: a soft round dot (32x32, one frame)
const SIZE_JITTER = 0.15;    // +- fraction of random size at the muzzle
const RING_JITTER = 0;       // blocks: how far inside START_RADIUS a particle may start (0 = exactly on the ring)
// Aim, found by measuring where the focus lands relative to the crosshair dot in two screenshots (Screenshots/true center.png and
// centerOff.png) and interpolating to zero. The field of view is not needed: the two tilts bracket the dot.
//   tilt yaw +3.76 / pitch +4.51 -> focus 19.9 px right of and 56.2 px above the dot
//   tilt yaw +5.30 / pitch +0.16 -> focus 22 px left of and 8.5 px below the dot
// so the focus is on the dot at yaw 3.76 + 1.54 * 19.9 / 41.9 = 4.49 and pitch 4.51 - 4.35 * 56.2 / 64.7 = 0.73.
// (More yaw moves the focus left, more pitch moves it up.) To re-aim, take a screenshot, measure, and interpolate again.
const AIM_YAW = 4.49;
const AIM_PITCH = 0.73;
const FOCUS_GLOW = 0.1;      // size of the steady glow at the focus; 0 = no glow

// ---- derived
const travel = RANGE - MUZZLE;
const speed = SPEED === null ? travel / LIFETIME : SPEED;
const focusAt = MUZZLE + speed * LIFETIME;
const inward = (START_RADIUS - END_RADIUS) / LIFETIME;
if (Math.abs(focusAt - RANGE) > 0.05) {
    console.warn('WARNING: the focus is at ' + focusAt.toFixed(2) + ' blocks, not RANGE ' + RANGE + ' (SPEED * LIFETIME + MUZZLE)');
}
if (END_RADIUS <= 0 || END_RADIUS >= START_RADIUS) {
    throw new Error('END_RADIUS must be above 0 and below START_RADIUS');
}

const marker = 'EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments"). ';
const root = path.join(__dirname, '..', '..', '..', 'src', 'main', 'resources', 'Server', 'Particles', 'Equipment_of_Arrakis', 'Experiments');
const ID = 'Arrakis_Exp_Flame_Focus';

function write(file, json) {
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, JSON.stringify(json, null, 2) + '\n');
}

const range = (min, max) => ({ Min: min, Max: max });
const scale = v => ({ X: range(v, v), Y: range(v, v) });

const beam = {
    $Comment: marker + 'The focusing beam of the Flame Cutter: a ring of particles at the muzzle pulled in to a point ' + RANGE + ' blocks out. Generated by tools/assets/experiments/make_flame_focus.js.',
    RenderMode: RENDER_MODE,
    ParticleRotationInfluence: 'Billboard',
    LinearFiltering: true,
    LightInfluence: 0.0,
    ParticleRotateWithSpawner: false,
    MaxConcurrentParticles: Math.ceil(SPAWN_RATE * LIFETIME * 1.5) + 4,
    ParticleLifeSpan: range(LIFETIME, LIFETIME),
    SpawnRate: range(SPAWN_RATE, SPAWN_RATE),
    SpawnBurst: false,
    Shape: 'Sphere',
    EmitOffset: {
        X: range(START_RADIUS - RING_JITTER, START_RADIUS),
        Y: range(START_RADIUS - RING_JITTER, START_RADIUS),
    },
    UseEmitDirection: false,
    InitialVelocity: { Speed: range(speed, speed), Yaw: range(0, 0), Pitch: range(0, 0) },
    TrailSpawnerPositionMultiplier: 1,
    TrailSpawnerRotationMultiplier: 1,
    Attractors: [{
        RadialAxis: { X: 0, Y: 0, Z: 1 },
        RadialImpulse: -inward,
        DampingMultiplier: { X: 1, Y: 1, Z: 1 },
    }],
    Particle: {
        Texture: TEXTURE,
        FrameSize: { Width: 32, Height: 32 },
        ScaleRatioConstraint: 'OneToOne',
        Animation: {
            0: { FrameIndex: range(0, 0), Opacity: 0.0, Color: COLORS[0], Scale: scale(1) },
            12: { Opacity: 0.9 },
            55: { Color: COLORS[1], Opacity: 1 },
            100: { Color: COLORS[2], Opacity: 1, Scale: scale(END_SIZE / START_SIZE) },
        },
        InitialAnimationFrame: {
            FrameIndex: range(0, 0),
            Opacity: 1,
            Scale: { X: range(START_SIZE * (1 - SIZE_JITTER), START_SIZE * (1 + SIZE_JITTER)), Y: range(START_SIZE * (1 - SIZE_JITTER), START_SIZE * (1 + SIZE_JITTER)) },
        },
        SoftParticles: 'Disable',
    },
};
write(path.join(root, 'Spawners', ID + '_Beam.particlespawner'), beam);

const spawners = [{ SpawnerId: ID + '_Beam' }];
if (FOCUS_GLOW > 0) {
    write(path.join(root, 'Spawners', ID + '_Point.particlespawner'), {
        $Comment: marker + 'A small steady glow at the focus of the Flame Cutter beam. Generated by tools/assets/experiments/make_flame_focus.js.',
        RenderMode: RENDER_MODE,
        ParticleRotationInfluence: 'Billboard',
        LinearFiltering: true,
        LightInfluence: 0.0,
        ParticleRotateWithSpawner: false,
        MaxConcurrentParticles: 8,
        ParticleLifeSpan: range(0.12, 0.12),
        SpawnRate: range(30, 30),
        SpawnBurst: false,
        InitialVelocity: {},
        Particle: {
            Texture: TEXTURE,
            FrameSize: { Width: 32, Height: 32 },
            ScaleRatioConstraint: 'OneToOne',
            Animation: {
                0: { FrameIndex: range(0, 0), Opacity: 0.0, Color: COLORS[2], Scale: scale(1) },
                40: { Opacity: 0.9 },
                100: { Opacity: 0.0, Scale: scale(0.5) },
            },
            InitialAnimationFrame: { FrameIndex: range(0, 0), Opacity: 1, Scale: { X: range(FOCUS_GLOW, FOCUS_GLOW), Y: range(FOCUS_GLOW, FOCUS_GLOW) } },
            SoftParticles: 'Disable',
        },
    });
    // Local Z of the system points back at the player (the vanilla flame sits at +Z 2.5 of a system placed 3.7 in front and turned
    // by 180 degrees), so the focus, RANGE - MUZZLE in front of the muzzle, is at negative Z.
    spawners.push({ SpawnerId: ID + '_Point', PositionOffset: { Z: -(RANGE - MUZZLE) } });
}
write(path.join(root, ID + '.particlesystem'), {
    $Comment: marker + 'The focusing beam of the Flame Cutter. Spawned in world space by Arrakis_Exp_Flame_Spawn.json with its origin ' + (RANGE - MUZZLE) + ' blocks before the look target. Generated by tools/assets/experiments/make_flame_focus.js.',
    Spawners: spawners,
    CullDistance: 100.0,
    IsImportant: false,
});

// ---- what the numbers come to
console.log('ring ' + START_RADIUS + ' -> ' + END_RADIUS + ' blocks, size ' + START_SIZE + ' -> ' + END_SIZE + ', ' + SPAWN_RATE + ' particles/s, life ' + LIFETIME + ' s');
console.log('forward speed ' + speed.toFixed(2) + ' blocks/s, inward speed ' + inward.toFixed(3) + ' blocks/s, focus ' + focusAt.toFixed(2) + ' blocks from the player');
console.log('about ' + Math.round(SPAWN_RATE * LIFETIME) + ' particles alive at once');
for (const f of [0, 0.25, 0.5, 0.75, 1]) {
    console.log('  at ' + Math.round(f * 100) + '% of the life: ' + (MUZZLE + speed * LIFETIME * f).toFixed(2) + ' blocks out, ring radius '
        + (START_RADIUS - (START_RADIUS - END_RADIUS) * f).toFixed(3) + ', size ' + (START_SIZE + (END_SIZE - START_SIZE) * f).toFixed(3));
}

// ---- aim: none here any more. The ring is spawned in world space by Arrakis_Exp_Beam_Spawn (see make_flame_beam.js): the server puts its
// origin EndOffset (RANGE - MUZZLE) before the look target and turns it along the line, so the focus is the target by construction.
// The earlier node-attached version (Arrakis_Exp_Flame_Burn.json with yaw 184.49 and pitch 0.73, measured from two screenshots) is in the git history.
