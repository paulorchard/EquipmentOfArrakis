// Builds the Blood Extractor's hold-out animation from two vanilla off-hand torch animations: it goes straight from the
// Idle stance (first keyframe) to the final pose of Interact (arm reaching out in front), over 8 frames, and holds
// there. Interact itself first swings the arm out and across; only its last keyframe is used here.
//
//   node tools/assets/make_blood_extractor_animation.js <folder holding the vanilla Common/>
const fs = require('fs');
const path = require('path');

const vanilla = process.argv[2];
if (!vanilla) {
    console.error('usage: node make_blood_extractor_animation.js <folder holding the vanilla Common/>');
    process.exit(1);
}
const src = path.join(vanilla, 'Common/Characters/Animations/Items/Off_Handed/Torch');
const out = path.join(__dirname, '../../src/main/resources/Common/Characters/Animations/Items/Equipment_of_Arrakis');
fs.mkdirSync(out, { recursive: true });
const FRAMES = 8;
const load = f => JSON.parse(fs.readFileSync(path.join(src, f + '.blockyanim'), 'utf8'));
const at = (frame, time) => Object.assign(JSON.parse(JSON.stringify(frame)), { time });

for (const [suffix, name] of [['', 'Blood_Extractor_Hold'], ['_FPS', 'Blood_Extractor_Hold_FPS']]) {
    const idle = load('Idle' + suffix);
    const reach = load('Interact' + suffix);
    for (const [node, channels] of Object.entries(reach.nodeAnimations)) {
        for (const channel of Object.keys(channels)) {
            const keys = channels[channel];
            if (!Array.isArray(keys) || keys.length === 0) continue;
            const idleKeys = idle.nodeAnimations[node] && idle.nodeAnimations[node][channel];
            const start = idleKeys && idleKeys.length ? idleKeys[0] : keys[0];
            channels[channel] = [at(start, 0), at(keys[keys.length - 1], FRAMES)];
        }
    }
    reach.duration = FRAMES;
    reach.holdLastKeyframe = true;
    fs.writeFileSync(path.join(out, name + '.blockyanim'), JSON.stringify(reach, null, 2) + '\n');
    console.log(name, 'nodes:', Object.keys(reach.nodeAnimations).length);
}
