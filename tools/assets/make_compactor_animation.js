// Builds the Compactor's aiming animation from the vanilla off-hand torch Idle pose: the same stance, frozen at its
// first keyframe, plus one move, the left wrist turning EXTRA_PITCH_DEGREES about its local X axis over 10 frames
// (1/6 s). The torch's head, the flashlight end, then points out in front of the player. No arm swing. The last
// keyframe is held. Made in third person and first person; the set's Camera.Pitch block keeps the arm following the
// look direction, so the head points at the ground when the player looks down.
//
//   node tools/assets/make_compactor_animation.js <folder holding the vanilla Common/> [degrees]
// If the head turns the wrong way in game, pass -90 instead of 90.
const fs = require('fs');
const path = require('path');

const vanilla = process.argv[2];
const EXTRA_PITCH_DEGREES = process.argv[3] !== undefined ? Number(process.argv[3]) : 90;
if (!vanilla) {
    console.error('usage: node make_compactor_animation.js <folder holding the vanilla Common/> [degrees]');
    process.exit(1);
}
const src = path.join(vanilla, 'Common/Characters/Animations/Items/Off_Handed/Torch');
const out = path.join(__dirname, '../../src/main/resources/Common/Characters/Animations/Items/Equipment_of_Arrakis');
fs.mkdirSync(out, { recursive: true });
for (const old of fs.readdirSync(out)) {
    if (old.startsWith('Compactor_Aim')) fs.unlinkSync(path.join(out, old));
}

const h = EXTRA_PITCH_DEGREES * Math.PI / 360;
const r = { x: Math.sin(h), y: 0, z: 0, w: Math.cos(h) };
function mul(a, b) {
    return {
        w: a.w * b.w - a.x * b.x - a.y * b.y - a.z * b.z,
        x: a.w * b.x + a.x * b.w + a.y * b.z - a.z * b.y,
        y: a.w * b.y - a.x * b.z + a.y * b.w + a.z * b.x,
        z: a.w * b.z + a.x * b.y - a.y * b.x + a.z * b.w,
    };
}

const TURN_FRAMES = 10;
for (const [from, to] of [['Idle', 'Compactor_Aim'], ['Idle_FPS', 'Compactor_Aim_FPS']]) {
    const anim = JSON.parse(fs.readFileSync(path.join(src, from + '.blockyanim'), 'utf8'));
    for (const node of Object.values(anim.nodeAnimations)) {
        for (const channel of Object.keys(node)) {
            if (Array.isArray(node[channel])) node[channel] = node[channel].slice(0, 1);
        }
    }
    const hand = anim.nodeAnimations['L-Hand'] || (anim.nodeAnimations['L-Hand'] = {});
    const first = (hand.orientation && hand.orientation[0]) || { time: 0, delta: { x: 0, y: 0, z: 0, w: 1 }, interpolationType: 'smooth' };
    hand.orientation = [first, { time: TURN_FRAMES, delta: mul(first.delta, r), interpolationType: 'smooth' }];
    anim.duration = TURN_FRAMES;
    anim.holdLastKeyframe = true;
    fs.writeFileSync(path.join(out, to + '.blockyanim'), JSON.stringify(anim, null, 2) + '\n');
    console.log(to, 'nodes:', Object.keys(anim.nodeAnimations).length);
}
