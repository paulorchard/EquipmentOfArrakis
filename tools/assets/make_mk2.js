// Builds the data of the Cutter Ray Mk2 (Arrakis_Cutter_Ray_Mk2): the vanilla Weapon_Gun in every respect, with copies of the
// vanilla assets that needed changing. Nothing vanilla is edited; every copy has an Arrakis_Cutter_Mk2_ id.
//
//   node tools/assets/make_mk2.js <folder holding the vanilla Server/ and Common/ folders>
//   node tools/assets/make_molten.js mk2        (the white globs of the impact; see that file)
//
// The vanilla folder is the unpacked Assets.zip of the game. The seven changes, and where they are made:
//   1 no bullet casing         Arrakis_Cutter_Mk2_Muzzle        RifleShooting without the Rifle_Bullet_Case spawner
//   2 recoil at 20%            Arrakis_Cutter_Mk2_Shoot         camera effect: Handgun_Shoot at Intensity 0.01 (0.05 x 0.2)
//                              Arrakis_Cutter_Mk2_Shoot*.blockyanim   the three Shoot animations with their kick scaled to 0.2
// 3 no sound                  the copies below carry none of the sound ids of the vanilla ones (listed in the comments)
//   4 sparks at the crosshair  Arrakis_Cutter_Mk2_Sparks        debris, sparks, linger sparks, flash glow with no offsets
//                              (the code spawns it; Arrakis_Cutter_Mk2_Impact keeps only the smoke, where it was)
//   5 white globs              Arrakis_Cutter_Mk2_Impact        also holds the globs, at the smoke's PositionOffset
//   6 fire rate, block damage  Arrakis_Cutter_Mk2_Shoot (interaction) RunTime 0.05; the damage is done in code, by the gate
//   7 range 5                  Arrakis_Cutter_Mk2_Gate          the Arrakis_Cutter_Mk2_Range_Gate interaction, first in Primary
const fs = require('fs');
const path = require('path');

const vanilla = process.argv[2];
if (!vanilla) {
    console.error('usage: node make_mk2.js <folder holding the vanilla Server/ and Common/ folders>');
    process.exit(1);
}
const res = path.join(__dirname, '..', '..', 'src', 'main', 'resources');
const read = rel => JSON.parse(fs.readFileSync(path.join(vanilla, rel), 'utf8'));
function write(rel, obj) {
    const file = path.join(res, rel);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, JSON.stringify(obj, null, 2) + '\n');
}
const clone = o => JSON.parse(JSON.stringify(o));
const ID = 'Arrakis_Cutter_Mk2_';
const SERVER = 'Server/';
const VANILLA_ASSETS = new Set(); // vanilla ids the copies refer to, for the report

// ---------------------------------------------------------------- recoil: the three Shoot animations at 20%
const KICK = 0.2;
const ANIMS = ['Shoot', 'Shoot_Moving', 'Shoot_FPS'];
const ANIM_DIR = 'Common/Characters/Animations/Items/Dual_Handed/Rifle/Attacks/Shoot/';
const ANIM_OUT = 'Common/Characters/Animations/Items/Equipment_of_Arrakis/';

// Quaternion slerp from a to b by t, both {x,y,z,w}.
function slerp(a, b, t) {
    let dot = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
    const sign = dot < 0 ? -1 : 1;
    dot = Math.abs(dot);
    let k0, k1;
    if (dot > 0.9995) {
        k0 = 1 - t; k1 = t;
    } else {
        const theta = Math.acos(Math.min(1, dot));
        const sin = Math.sin(theta);
        k0 = Math.sin((1 - t) * theta) / sin;
        k1 = Math.sin(t * theta) / sin;
    }
    const q = {
        x: k0 * a.x + k1 * sign * b.x, y: k0 * a.y + k1 * sign * b.y,
        z: k0 * a.z + k1 * sign * b.z, w: k0 * a.w + k1 * sign * b.w,
    };
    const n = Math.hypot(q.x, q.y, q.z, q.w);
    const round = v => Math.round(v / n * 1e6) / 1e6;
    return { x: round(q.x), y: round(q.y), z: round(q.z), w: round(q.w) };
}

// The first keyframe of each channel is the pose the gun is held in (it equals the idle pose); the later keyframes are the kick and
// its recovery. Scaling the kick about the first keyframe leaves the held pose alone and shrinks the movement away from it.
function scaleKick(anim) {
    const out = clone(anim);
    for (const node of Object.values(out.nodeAnimations)) {
        for (const channel of ['position', 'orientation']) {
            const keys = node[channel];
            if (!keys || keys.length < 2) continue;
            const rest = keys[0].delta;
            for (let i = 1; i < keys.length; i++) {
                const d = keys[i].delta;
                keys[i].delta = channel === 'orientation'
                    ? slerp(rest, d, KICK)
                    : { x: rest.x + (d.x - rest.x) * KICK, y: rest.y + (d.y - rest.y) * KICK, z: rest.z + (d.z - rest.z) * KICK };
            }
        }
    }
    return out;
}

function checkAnim(name, before, after) {
    const fail = msg => { throw new Error(name + ': ' + msg); };
    if (before.duration !== after.duration) fail('duration changed');
    const a = Object.keys(before.nodeAnimations), b = Object.keys(after.nodeAnimations);
    if (a.join() !== b.join()) fail('node names changed');
    let kicked = 0, biggestBefore = 0, biggestAfter = 0;
    for (const n of a) {
        for (const ch of Object.keys(before.nodeAnimations[n])) {
            const x = before.nodeAnimations[n][ch], y = after.nodeAnimations[n][ch];
            if (x.length !== y.length) fail(n + '.' + ch + ' frame count changed');
            x.forEach((k, i) => { if (k.time !== y[i].time) fail(n + '.' + ch + ' frame time changed'); });
            if (ch === 'orientation' && x.length > 1) {
                const angle = (p, q) => 2 * Math.acos(Math.min(1, Math.abs(p.x * q.x + p.y * q.y + p.z * q.z + p.w * q.w)));
                for (let i = 1; i < x.length; i++) {
                    biggestBefore = Math.max(biggestBefore, angle(x[0].delta, x[i].delta));
                    biggestAfter = Math.max(biggestAfter, angle(y[0].delta, y[i].delta));
                    kicked++;
                }
            }
            if (x.length) {
                // the held pose is unchanged
                const k0 = JSON.stringify(x[0].delta), j0 = JSON.stringify(y[0].delta);
                if (k0 !== j0) fail(n + '.' + ch + ' first keyframe changed');
            }
        }
    }
    return { kicked, biggestBefore, biggestAfter };
}

for (const name of ANIMS) {
    const before = JSON.parse(fs.readFileSync(path.join(vanilla, ANIM_DIR + name + '.blockyanim'), 'utf8'));
    const after = scaleKick(before);
    const report = checkAnim(name, before, after);
    const file = path.join(res, ANIM_OUT + ID + name + '.blockyanim');
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, JSON.stringify(after, null, 2) + '\n');
    console.log(`animation ${name}: ${report.kicked} kick keyframes, largest swing ${(report.biggestBefore * 180 / Math.PI).toFixed(2)} deg -> ${(report.biggestAfter * 180 / Math.PI).toFixed(2)} deg`);
}

// ---------------------------------------------------------------- the player animation set (Rifle plus the quieter Shoot)
const rifleItem = read(SERVER + 'Item/Items/Weapon/Gun/Weapon_Gun.json');
const shootAnim = clone(rifleItem.PlayerAnimationsId.Animations.Shoot);
shootAnim.ThirdPerson = ANIM_OUT.replace('Common/', '') + ID + 'Shoot.blockyanim';
shootAnim.ThirdPersonMoving = ANIM_OUT.replace('Common/', '') + ID + 'Shoot_Moving.blockyanim';
shootAnim.FirstPerson = ANIM_OUT.replace('Common/', '') + ID + 'Shoot_FPS.blockyanim';
write(SERVER + 'Item/Animations/Equipment_of_Arrakis/Arrakis_Cutter_Ray_Mk2.json', {
    $Comment: 'The vanilla Weapon_Gun set (Parent Rifle) with its Shoot animation replaced by copies whose kick is scaled to 20% (tools/assets/make_mk2.js). Speed 3, no blending, the frown face: as vanilla.',
    Parent: rifleItem.PlayerAnimationsId.Parent,
    Animations: { Shoot: shootAnim },
});

// ---------------------------------------------------------------- the camera shake at 20%
const camera = read(SERVER + 'Camera/CameraEffect/Handgun/Handgun_Shoot.json');
camera.Intensity.Value = Math.round(camera.Intensity.Value * KICK * 1e6) / 1e6;
write(SERVER + 'Camera/CameraEffect/Equipment_of_Arrakis/' + ID + 'Shoot.json', camera);
VANILLA_ASSETS.add('CameraShake Handgun_Shoot (used as it is; only the intensity differs)');

// ---------------------------------------------------------------- the bullet's model without the whiz-by sound
const bulletModel = read(SERVER + 'Models/Projectiles/Weapons/Bullet/Bullet_Blunderbuss.json');
for (const set of Object.values(bulletModel.AnimationSets || {})) {
    for (const animation of set.Animations || []) {
        delete animation.SoundEventId;
    }
}
write(SERVER + 'Models/Equipment_of_Arrakis/' + ID + 'Bullet.json', bulletModel);

// ---------------------------------------------------------------- the projectile
const bullet = read(SERVER + 'Projectiles/Hypixel/Minigames/GunPvP/GunPvP_Assault_Rifle_Bullet.json');
bullet.Appearance = ID + 'Bullet';
bullet.Damage = 0;
// Not used by the 0.0.8 server for anything (only the config class mentions it), kept at about one range of flight.
bullet.TimeToLive = 0.05;
bullet.DeathParticles = { SystemId: ID + 'Impact' };
delete bullet.DeathSoundEventId;
bullet.$Comment = 'GunPvP_Assault_Rifle_Bullet with Damage 0 (it neither hurts nor flinches a creature), the bullet model without its whiz-by sound, no death sound, and the death effect of Arrakis_Cutter_Mk2_Impact (smoke and white globs only; the sparks are spawned by code). TimeToLive is not enforced by this server version.';
write(SERVER + 'Projectiles/Equipment_of_Arrakis/' + ID + 'Bullet.json', bullet);

// ---------------------------------------------------------------- the particle systems
const muzzle = read(SERVER + 'Particles/Weapon/Rifle/RifleShooting.particlesystem');
muzzle.Spawners = muzzle.Spawners.filter(s => s.SpawnerId !== 'Rifle_Bullet_Case');
write(SERVER + 'Particles/Equipment_of_Arrakis_Mk2/' + ID + 'Muzzle.particlesystem', muzzle);

// The spark spawners of the impact, copied with their random emit offsets removed so that they start at the point they are spawned at.
const SPARKS = [
    ['Gun_Debris', 'Debris'],
    ['Explosion_Fail_Sparks', 'Sparks'],
    ['Explosion_Fail_Sparks_Linger', 'Sparks_Linger'],
    ['Explosion_Fail_Flash_Glow', 'Flash_Glow'],
];
const zero = { X: { Min: 0, Max: 0 }, Y: { Min: 0, Max: 0 }, Z: { Min: 0, Max: 0 } };
const sparkSystem = { Spawners: [], BoundingRadius: 100.0, CullDistance: 100.0, IsImportant: false };
for (const [from, short] of SPARKS) {
    const spawner = read(SERVER + 'Particles/Explosion/Explosion_Fail/Spawners/' + from + '.particlespawner');
    spawner.EmitOffset = clone(zero);
    write(SERVER + 'Particles/Equipment_of_Arrakis_Mk2/Spawners/' + ID + short + '.particlespawner', spawner);
    sparkSystem.Spawners.push({ SpawnerId: ID + short });
}
sparkSystem.$Comment = 'The vanilla Gun_Impact sparks, debris and flash glow with no position and no emit offsets: they start exactly where the code spawns the system, the point under the crosshair. The smoke is in Arrakis_Cutter_Mk2_Impact.';
write(SERVER + 'Particles/Equipment_of_Arrakis_Mk2/' + ID + 'Sparks.particlesystem', sparkSystem);

// The impact (spawned where the bullet dies): the smoke where it was (PositionOffset Y 0.2) and the white globs, written by make_molten.js.
const impact = read(SERVER + 'Particles/Explosion/Explosion_Fail/Gun_Impact.particlesystem');
const smoke = impact.Spawners.find(s => s.SpawnerId === 'Explosion_Fail_Smoke_Linger');
const mk2Impact = {
    $Comment: 'Where the bullet dies: the vanilla smoke at its vanilla position (the hit point plus Y 0.2) and the white globs at that very same point (Arrakis_Cutter_Mk2_Globs_*, generated by make_molten.js mk2). Sparks, debris and flash glow are spawned by code (Arrakis_Cutter_Mk2_Sparks).',
    Spawners: [smoke],
    BoundingRadius: impact.BoundingRadius,
    CullDistance: impact.CullDistance,
    IsImportant: impact.IsImportant,
};
const globs = JSON.parse(fs.readFileSync(path.join(res, SERVER + 'Particles/Equipment_of_Arrakis_Mk2/Arrakis_Cutter_Mk2_Globs.particlesystem'), 'utf8'));
for (const entry of globs.Spawners) {
    mk2Impact.Spawners.push(Object.assign({}, entry, { PositionOffset: clone(smoke.PositionOffset) }));
}
write(SERVER + 'Particles/Equipment_of_Arrakis_Mk2/' + ID + 'Impact.particlesystem', mk2Impact);

// ---------------------------------------------------------------- interactions
const shoot = read(SERVER + 'Item/Interactions/Weapons/Gun/Attacks/Shoot/Gun_Shoot.json');
shoot.RunTime = 0.05; // the vanilla gun overrides the 0.25 of Gun_Shoot with 0.05 in its item: this is the fire rate
shoot.ProjectileId = ID + 'Bullet';
shoot.Effects.CameraEffect = ID + 'Shoot';
shoot.Effects.Particles[0].SystemId = ID + 'Muzzle';
shoot.$Comment = 'Gun_Shoot with the Mk2 projectile, camera effect and muzzle, at the fire rate of the vanilla gun (RunTime 0.05). It has no sound of its own, like the vanilla one.';
write(SERVER + 'Item/Interactions/Equipment_of_Arrakis/' + ID + 'Shoot.json', shoot);
write(SERVER + 'Item/Interactions/Equipment_of_Arrakis/' + ID + 'Gate.json', {
    $Comment: 'First in the Primary chain. Fires the shot only if a block is within Range under the crosshair and there is power; fails (which stops the chain, so that nothing else happens) if not. When it fires it also pays 0.05 power, damages the block by BlockDamage, spawns the sparks and plays the impact sound (at most every ImpactSoundMinInterval s) at the crosshair point. A refusal for range plays the fizzle (at most every FizzleMinInterval s).',
    Type: 'Arrakis_Cutter_Mk2_Range_Gate',
    Range: 5,
    PowerPerSecond: 1,
    FireInterval: 0.05,
    BlockDamage: 1,
    ToolItemId: 'Tool_Pickaxe_Crude',
    SparksId: ID + 'Sparks',
    ImpactSoundMinInterval: 0.1,
    FizzleMinInterval: 0.27,
});
write(SERVER + 'Item/Interactions/Equipment_of_Arrakis/' + ID + 'Firing.json', {
    $Comment: 'Applies (refreshes) the entity effect Arrakis_Cutter_Mk2_Firing on the shooter at every shot that got through the gate; its sounds are the held-fire start, loop and end.',
    Type: 'ApplyEffect',
    EffectId: ID + 'Firing',
});
write(SERVER + 'Entity/Effects/Equipment_of_Arrakis/' + ID + 'Firing.json', {
    $Comment: 'Held-fire sounds, modelled on the vanilla FlamethrowerSource: the start+loop sound plays when the effect is first applied, the effect is refreshed at every shot (OVERWRITE: remaining time back to Duration, no new effect, so no new start), and when the shots stop it lapses after Duration and the removal sound plays.',
    ApplicationEffects: {
        WorldSoundEventId: 'SFX_Arrakis_Cutter_Mk2_Firing',
        LocalSoundEventId: 'SFX_Arrakis_Cutter_Mk2_Firing_Local',
    },
    WorldRemovalSoundEventId: 'SFX_Arrakis_Cutter_Mk2_Firing_End',
    LocalRemovalSoundEventId: 'SFX_Arrakis_Cutter_Mk2_Firing_End_Local',
    OverlapBehavior: 'Overwrite',
    Infinite: false,
    Duration: 0.25,
});
write(SERVER + 'Item/RootInteractions/Equipment_of_Arrakis/' + ID + 'Primary.json', {
    $Comment: 'Primary of the Cutter Ray Mk2: the range gate, then the held-fire effect (start, loop and end sound), then the vanilla shot.',
    Interactions: [ID + 'Gate', ID + 'Firing', ID + 'Shoot'],
    Tags: { Attack: ['Ranged'] },
});

// The swing of the right click: Gun_Attack, Gun_Swing_Left and its effect and damage, with the swing sounds removed.
const attack = read(SERVER + 'Item/Interactions/Weapons/Gun/Variants/Gun_Attack.json');
attack.Next = [ID + 'Swing_Left'];
write(SERVER + 'Item/Interactions/Equipment_of_Arrakis/' + ID + 'Attack.json', attack);
write(SERVER + 'Item/RootInteractions/Equipment_of_Arrakis/' + ID + 'Attack.json', { Interactions: [ID + 'Attack'] });

const swing = read(SERVER + 'Item/Interactions/Weapons/Gun/Attacks/Swing_Left/Gun_Swing_Left.json');
let swingText = JSON.stringify(swing)
    .split('"Gun_Swing_Left_Damage"').join('"' + ID + 'Swing_Left_Damage"')
    .split('"Gun_Swing_Left_Effect"').join('"' + ID + 'Swing_Left_Effect"');
write(SERVER + 'Item/Interactions/Equipment_of_Arrakis/' + ID + 'Swing_Left.json', JSON.parse(swingText));
write(SERVER + 'Item/RootInteractions/Equipment_of_Arrakis/' + ID + 'Swing_Left.json', {
    Interactions: [ID + 'Swing_Left'],
    Tags: { Attack: ['Melee'] },
});

const swingDamage = read(SERVER + 'Item/Interactions/Weapons/Gun/Attacks/Swing_Left/Gun_Swing_Left_Damage.json');
delete swingDamage.DamageEffects.WorldSoundEventId; // SFX_Unarmed_Impact
write(SERVER + 'Item/Interactions/Equipment_of_Arrakis/' + ID + 'Swing_Left_Damage.json', swingDamage);
const swingEffect = read(SERVER + 'Item/Interactions/Weapons/Gun/Attacks/Swing_Left/Gun_Swing_Left_Effect.json');
delete swingEffect.Effects.WorldSoundEventId; // SFX_Unarmed_Swing
write(SERVER + 'Item/Interactions/Equipment_of_Arrakis/' + ID + 'Swing_Left_Effect.json', swingEffect);
write(SERVER + 'Item/RootInteractions/Equipment_of_Arrakis/' + ID + 'Swing_Left_Effect.json', { Interactions: [ID + 'Swing_Left_Effect'] });

// ---------------------------------------------------------------- the item
const mk1 = JSON.parse(fs.readFileSync(path.join(res, SERVER + 'Item/Items/Equipment_of_Arrakis/Arrakis_Cutter_Ray.json'), 'utf8'));
const item = clone(rifleItem);
item.$Comment = 'The vanilla Weapon_Gun (same model, texture, icon, quality, tags, animations, Primary at RunTime 0.05 and the Secondary swing), with the Mk2 changes: no casing, recoil at 20%, no sound, sparks at the crosshair, white globs, block damage, range 5 (tools/assets/make_mk2.js). Two-handed as the vanilla gun is: no Utility.Compatible and a Secondary, so both clicks stay with it; the priorities are written out as on the Cutter Ray.';
item.TranslationProperties = {
    Name: 'server.items.Arrakis_Cutter_Ray_Mk2.name',
    Description: 'server.items.Arrakis_Cutter_Ray_Mk2.description',
};
item.Categories = ['Items.Tools'];
item.PlayerAnimationsId = 'Arrakis_Cutter_Ray_Mk2';
item.Interactions = { Primary: ID + 'Primary', Secondary: ID + 'Attack' };
const vars = clone(rifleItem.InteractionVars);
const effectVar = vars.Gun_Swing_Left_Effect;
effectVar.Interactions[0].Parent = ID + 'Swing_Left_Effect';
const damageVar = vars.Gun_Swing_Left_Damage;
damageVar.Interactions[0].Parent = ID + 'Swing_Left_Damage';
delete damageVar.Interactions[0].DamageEffects; // its only content was WorldSoundEventId SFX_Unarmed_Impact
item.InteractionVars = {
    [ID + 'Swing_Left_Effect']: effectVar,
    [ID + 'Swing_Left_Damage']: damageVar,
};
item.InteractionConfig = mk1.InteractionConfig;
write(SERVER + 'Item/Items/Equipment_of_Arrakis/Arrakis_Cutter_Ray_Mk2.json', item);

console.log('wrote the Mk2 assets');
