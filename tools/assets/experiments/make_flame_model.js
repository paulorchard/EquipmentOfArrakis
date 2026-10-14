// EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments").
//
// The Flame Cutter with its laser as part of the WEAPON MODEL. Writes, from the vanilla Scrap flamethrower model and the vanilla Rifle animations:
//
//   1. Common/Items/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Cutter.blockymodel
//        a copy of Scrap.blockymodel with a tapered solid line added under its node Origin_Projectile (the muzzle: the end of the barrel). The line is a
//        stack of SEGMENTS boxes along the muzzle's forward axis, LENGTH_BLOCKS long, whose width goes from START_PX to END_PX pixels at 1920x1080
//        (relative numbers for a test, see below), unlit (shadingMode fullbright) and white, tilted by AIM_YAW / AIM_PITCH from the gun's own axis so
//        that it points at the centre of the screen. The boxes use a white 16x16 patch that make_flame_texture.js paints into the texture copy.
//   2. Common/Characters/Animations/Items/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Static.blockyanim
//        a copy of the vanilla Rifle idle animation (the player's arms holding a rifle) reduced to its first frame: a fixed pose.
//   3. Server/Item/Animations/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Static.json
//        the player animation set of the Flame Cutter: the vanilla "Rifle" set (parent) with EVERY third-person animation (idle, walk, run, jump, swim,
//        climb, ...) replaced by that fixed pose, so that in third person the gun never moves relative to the body. First person is the vanilla Rifle's.
//
// Units of a blockymodel are 1/32 of a block (assumed: the player is 1.8 blocks tall). Pixel sizes: the perspective already shrinks a line with distance, so the
// width in world units is px * 2 * d * tan(VFOV/2) / 1080 at the distance d (blocks) from the eye, d going from START_DIST (the muzzle) to START_DIST + LENGTH_BLOCKS.
// VFOV 70 degrees and the 1 block from the eye to the muzzle are guesses (the first person camera is not known to the server).
//
//   unzip -o -q "$APPDATA/Hytale/install/release/package/game/latest/Assets.zip" "Common/NPC/Intelligent/Goblin/Models/Weapons/Flamethrower/*" \
//         "Common/Characters/Animations/Items/Dual_Handed/Rifle/*" "Server/Item/Animations/Rifle.json" -d <dir>
//   node tools/assets/experiments/make_flame_model.js <dir>
const fs = require('fs');
const path = require('path');

// ---- the beam: change these and run the script again
const LENGTH_BLOCKS = 3;      // blocks the line reaches out from the muzzle
const UNITS_PER_BLOCK = 32;   // model units in a block
const SEGMENTS = 24;          // boxes in the stack (the taper is stepped; more = smoother)
const START_PX = 50;          // pixels wide at the muzzle (1920x1080)
const END_PX = 2;             // pixels wide at the far end
const VFOV = 70;              // degrees, vertical field of view (a guess)
const VERTICAL_PIXELS = 1080;
const START_DIST = 1.0;       // blocks from the eye to the muzzle (a guess)
const AIM_YAW = 4.49;         // degrees the line is turned about the gun's up axis, positive towards +X of the model (measured for the old particle: Arrakis_Exp_Flame_Burn yaw 184.49)
const AIM_PITCH = 0.73;       // degrees the line is raised (measured, same source)
const COLOR_PATCH = { x: 48, y: 112 }; // the white 16x16 patch of the texture (make_flame_texture.js)
const SHADING = 'fullbright';

const dir = process.argv[2] || '.';
const root = path.join(__dirname, '..', '..', '..', 'src', 'main', 'resources');
const marker = 'EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments"). ';
const rad = d => d * Math.PI / 180;

// ---- 1. the model
const modelIn = path.join(dir, 'Common/NPC/Intelligent/Goblin/Models/Weapons/Flamethrower/Scrap.blockymodel');
const model = JSON.parse(fs.readFileSync(modelIn, 'utf8'));
let maxId = 0;
(function walk(n) { maxId = Math.max(maxId, Number(n.id) || 0); (n.children || []).forEach(walk); })({ children: model.nodes });
const nextId = () => String(++maxId);

// The beam's direction in the model's frame (+Z is the gun's forward, +Y up, +X to the side the yaw turns to), then in the muzzle's own frame:
// Origin_Projectile is turned 90 degrees about X, so its local axes are x -> x, y -> z, z -> -y of the model (checked: the muzzle's y axis is the model's +Z).
const dx = Math.sin(rad(AIM_YAW)) * Math.cos(rad(AIM_PITCH));
const dy = Math.sin(rad(AIM_PITCH));
const dz = Math.cos(rad(AIM_YAW)) * Math.cos(rad(AIM_PITCH));
const local = { x: dx, y: dz, z: -dy };
// rotation taking the local +Y (along which the stack is built) to that direction: axis = Y x local, angle = acos(local.y)
const angle = Math.acos(Math.max(-1, Math.min(1, local.y)));
const axis = { x: local.z, y: 0, z: -local.x };
const axisLength = Math.hypot(axis.x, axis.z) || 1;
const orientation = { x: axis.x / axisLength * Math.sin(angle / 2), y: 0, z: axis.z / axisLength * Math.sin(angle / 2), w: Math.cos(angle / 2) };

const perPx = d => 2 * d * Math.tan(rad(VFOV / 2)) / VERTICAL_PIXELS;
const length = LENGTH_BLOCKS * UNITS_PER_BLOCK;
const segment = length / SEGMENTS;
const face = { offset: { x: COLOR_PATCH.x, y: COLOR_PATCH.y }, mirror: { x: false, y: false }, angle: 0 };
const noneShape = { type: 'none', offset: { x: 0, y: 0, z: 0 }, stretch: { x: 1, y: 1, z: 1 }, settings: { isPiece: false }, textureLayout: {}, unwrapMode: 'custom', visible: true, doubleSided: false, shadingMode: 'flat' };

const segments = [];
const widths = [];
for (let k = 0; k < SEGMENTS; k++) {
    const t = k / (SEGMENTS - 1); // the first box is START_PX wide, the last END_PX
    const px = START_PX + (END_PX - START_PX) * t;
    const distance = START_DIST + LENGTH_BLOCKS * t;
    const width = Math.max(0.05, +(px * perPx(distance) * UNITS_PER_BLOCK).toFixed(3));
    widths.push(width);
    segments.push({
        id: nextId(),
        name: 'Arrakis_Beam_' + (k + 1),
        position: { x: 0, y: +((k + 0.5) * segment).toFixed(3), z: 0 },
        orientation: { x: 0, y: 0, z: 0, w: 1 },
        shape: {
            type: 'box',
            offset: { x: 0, y: 0, z: 0 },
            stretch: { x: 1, y: 1, z: 1 },
            settings: { isPiece: false, size: { x: width, y: +(segment + 0.15).toFixed(3), z: width } },
            textureLayout: { back: face, right: face, front: face, left: face, top: face, bottom: face },
            unwrapMode: 'custom',
            visible: true,
            doubleSided: false,
            shadingMode: SHADING,
        },
        children: [],
    });
}
const beam = {
    id: nextId(),
    name: 'Arrakis_Beam',
    position: { x: 0, y: 0, z: 0 },
    orientation,
    shape: noneShape,
    children: segments,
};
let attached = false;
(function find(n) {
    if (n.name === 'Origin_Projectile') {
        n.children = (n.children || []).concat([beam]);
        attached = true;
    }
    (n.children || []).forEach(find);
})({ children: model.nodes });
if (!attached) {
    throw new Error('Origin_Projectile not found in the model');
}
function out(relative, json, compact) {
    const file = path.join(root, relative);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, compact ? JSON.stringify(json) : JSON.stringify(json, null, 2) + '\n');
}
out('Common/Items/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Cutter.blockymodel', model, true);

// ---- 2. the static pose: the first key of every channel of the vanilla Rifle idle, at time 0
const idle = JSON.parse(fs.readFileSync(path.join(dir, 'Common/Characters/Animations/Items/Dual_Handed/Rifle/Idle.blockyanim'), 'utf8'));
const still = { formatVersion: idle.formatVersion, duration: idle.duration, holdLastKeyframe: true, nodeAnimations: {} };
for (const [node, channels] of Object.entries(idle.nodeAnimations)) {
    still.nodeAnimations[node] = {};
    for (const [channel, keys] of Object.entries(channels)) {
        still.nodeAnimations[node][channel] = keys.length ? [Object.assign({}, keys[0], { time: 0 })] : [];
    }
}
out('Common/Characters/Animations/Items/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Static.blockyanim', still, true);

// ---- 3. the animation set: Rifle, every third-person animation replaced by the still one
const STILL = 'Characters/Animations/Items/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Static.blockyanim'; // animation files must be under Characters/ or NPC/ (the server checks)
const rifle = JSON.parse(fs.readFileSync(path.join(dir, 'Server/Item/Animations/Rifle.json'), 'utf8'));
const animations = {};
for (const [name, entry] of Object.entries(rifle.Animations)) {
    const copy = Object.assign({}, entry);
    for (const key of Object.keys(copy)) {
        if (key.startsWith('ThirdPerson') && key !== 'ThirdPersonFace') {
            copy[key] = STILL;
        }
    }
    if (copy.ThirdPersonFace) {
        copy.ThirdPersonFace = STILL;
    }
    animations[name] = copy;
}
out('Server/Item/Animations/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Static.json', {
    $Comment: marker + 'The animation set of the Flame Cutter: the vanilla Rifle set with every third-person animation replaced by one fixed pose (the first frame of the rifle idle), so the gun never moves relative to the body in third person. First person animations are the vanilla Rifle ones. Generated by tools/assets/experiments/make_flame_model.js.',
    Parent: 'Rifle',
    Animations: animations,
});
console.log('beam: ' + SEGMENTS + ' boxes, ' + length + ' units (' + LENGTH_BLOCKS + ' blocks), width ' + widths[0] + ' -> ' + widths[widths.length - 1] + ' units ('
    + START_PX + ' px -> ' + END_PX + ' px), aim yaw ' + AIM_YAW + ' pitch ' + AIM_PITCH + ' (muzzle-local direction ' + JSON.stringify(local) + ')');
console.log('animation set: ' + Object.keys(animations).length + ' animations, all third person -> one still pose');
