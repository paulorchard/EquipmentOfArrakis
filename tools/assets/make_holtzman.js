// Builds the look of the Holtzman Shield: the dot model in the hand, the ModelVFX, the entity effect, and the particle systems.
//
//   node tools/assets/make_holtzman.js
//
// Writes (all under src/main/resources):
//   Common/Items/Equipment_of_Arrakis/Arrakis_Holtzman_Shield.blockymodel + _Texture.png   a 1-unit amber dot (nothing noticeable in the hand)
//   Server/Entity/ModelVFX/Equipment_of_Arrakis/Arrakis_Holtzman_Field.json              the amber outline and shimmer on the body
//   Server/Entity/Effects/Equipment_of_Arrakis/Arrakis_Holtzman_Field.json               the effect: tint, ModelVFX, bubble, sounds
//   Server/Particles/Equipment_of_Arrakis_Holtzman/*.particlesystem and Spawners/*       bubble (+ first-person bubble), hit flash, shatter burst
//
// Every number you are likely to tune is in the block below. The ones to try first: BUBBLE_DIAMETER, BUBBLE_OPACITY, BUBBLE_FP_OPACITY
// (how strong the owner sees it), OUTLINE_THICKNESS, POST_COLOR_OPACITY, BUBBLE_NODE and BUBBLE_OFFSET_Y (where the bubble is centred).

// ---- the numbers -------------------------------------------------------------------------------------------------------------------
const AMBER = '#ffb02e';             // the main colour: outline, bubble, motes
const AMBER_LIGHT = '#ffd37a';       // the lighter amber of the hit flash and shards
const WHITE = '#fff1d0';             // the white-ish of the shards

// A. glow on the body
const TINT_BOTTOM = '#7a4a00';       // EntityBottomTint (the tint at the feet end of the model)
const TINT_TOP = '#ffc24a';          // EntityTopTint (at the head end): keep both faint so the player stays recognisable
const OUTLINE_COLOR = '#ffb02e';     // ModelVFX HighlightColor
const OUTLINE_THICKNESS = 3.0;       // ModelVFX HighlightThickness
const POST_COLOR = '#ffb02e';        // ModelVFX PostColor
const POST_COLOR_OPACITY = 0.12;     // how much of the body is washed with it
const SHIMMER_SECONDS = 2.5;         // ModelVFX AnimationDuration: one sweep of the shimmer (it mirrors back, so a full cycle is twice this)

// B. the bubble (third person: what other players see)
const BUBBLE_NODE = 'Belly';         // the player model node it is attached to: Belly is the middle of the torso (Pelvis is just under it)
const BUBBLE_OFFSET_Y = 0.0;         // blocks up from that node
const BUBBLE_DIAMETER = 2.2;         // blocks across
const BUBBLE_PULSE = 0.08;           // the size pulses by this fraction either way (0.08 = +-8%)
const BUBBLE_PULSE_SECONDS = 3.0;    // one fade in and out; two overlap, so the glow never drops to nothing
const BUBBLE_OPACITY = 0.26;         // peak opacity of the soft glow (additive, so it brightens what is behind)
const RIM_OPACITY = 0.30;            // peak opacity of the faint ring at the edge
const MOTE_RATE = 8;                 // fine amber specks drifting inside it, per second
const MOTE_MAX = 18;
const MOTE_SIZE = [0.04, 0.07];      // blocks
const MOTE_LIFE = [1.2, 2.0];        // seconds
const BUBBLE_CULL_DISTANCE = 64;     // blocks: beyond this the bubble is not drawn
const BUBBLE_BOUNDING_RADIUS = 3;    // must be well above the bubble radius, or a large sprite is culled close to the camera / at the screen edge

// B2. what the owner sees (first person): a gentle version, so the view stays readable
const BUBBLE_FP_OPACITY = 0.05;
const MOTE_FP_RATE = 3;

// hit flash, where a projectile met the field
const HIT_SIZE = [0.5, 0.9];         // blocks: the flash grows from the first to the second size
const HIT_SECONDS = 0.35;

// shatter burst, when the shield breaks
const SHATTER_SHARDS = 16;           // of each of the two colours
const SHATTER_SPEED = [3, 6];        // blocks per second, outwards
const SHATTER_SECONDS = [0.5, 0.8];  // how long a shard lives
const SHATTER_SHARD_SIZE = [0.07, 0.14];
const SHATTER_RING_SIZE = 2.6;       // the expanding flash ring
// ------------------------------------------------------------------------------------------------------------------------------------

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const root = path.join(__dirname, '..', '..', 'src', 'main', 'resources');
const ensure = p => fs.mkdirSync(p, { recursive: true });
const writeJson = (rel, obj) => {
    const file = path.join(root, rel);
    ensure(path.dirname(file));
    fs.writeFileSync(file, JSON.stringify(obj, null, 2) + '\n');
};

const range = (min, max) => ({ Min: min, Max: max });
const rzero = v => ({ X: range(v, v), Y: range(v, v), Z: range(v, v) });
const rbox = v => ({ X: range(-v, v), Y: range(-v, v), Z: range(-v, v) });
const scale = (a, b) => ({ X: range(a, b), Y: range(a, b) });

// ---- the dot in the hand: a 1-unit amber box under an L-Attachment node (the grip point of off-hand items)
{
    const face = { offset: { x: 0, y: 0 }, mirror: { x: false, y: false }, angle: 0 };
    const model = {
        nodes: [{
            id: '1',
            name: 'L-Attachment',
            position: { x: 0, y: 0, z: 0 },
            orientation: { x: 0, y: 0, z: 0, w: 1 },
            children: [{
                id: '2',
                name: 'Dot',
                position: { x: 0, y: 0, z: 0 },
                orientation: { x: 0, y: 0, z: 0, w: 1 },
                children: [],
                shape: {
                    type: 'box',
                    offset: { x: 0, y: 0, z: 0 },
                    stretch: { x: 1, y: 1, z: 1 },
                    settings: { size: { x: 1, y: 1, z: 1 } },
                    visible: true,
                    doubleSided: false,
                    shadingMode: 'fullbright',
                    unwrapMode: 'custom',
                    textureLayout: { front: face, back: face, left: face, right: face, top: face, bottom: face },
                },
            }],
        }],
        lod: 'auto',
    };
    const dir = path.join(root, 'Common', 'Items', 'Equipment_of_Arrakis');
    ensure(dir);
    fs.writeFileSync(path.join(dir, 'Arrakis_Holtzman_Shield.blockymodel'), JSON.stringify(model, null, 2) + '\n');

    // a solid amber PNG written by hand (no image library)
    const crcTable = [];
    for (let n = 0; n < 256; n++) {
        let c = n;
        for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
        crcTable[n] = c >>> 0;
    }
    const crc32 = buf => {
        let crc = 0xffffffff;
        for (const b of buf) crc = crcTable[(crc ^ b) & 0xff] ^ (crc >>> 8);
        return (crc ^ 0xffffffff) >>> 0;
    };
    const chunk = (type, data) => {
        const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
        const body = Buffer.concat([Buffer.from(type), data]);
        const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body));
        return Buffer.concat([len, body, crc]);
    };
    const SIZE = 16;
    const color = [0xff, 0xb0, 0x2e, 0xff];
    const header = Buffer.alloc(13);
    header.writeUInt32BE(SIZE, 0); header.writeUInt32BE(SIZE, 4); header[8] = 8; header[9] = 6;
    const row = Buffer.alloc(1 + SIZE * 4);
    for (let x = 0; x < SIZE; x++) for (let c = 0; c < 4; c++) row[1 + x * 4 + c] = color[c];
    const raw = Buffer.concat(Array.from({ length: SIZE }, () => row));
    fs.writeFileSync(path.join(dir, 'Arrakis_Holtzman_Shield_Texture.png'), Buffer.concat([
        Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
        chunk('IHDR', header), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]));
}

// ---- the ModelVFX: amber outline with bloom, a faint amber wash, mirrored smoothly so it shimmers
writeJson('Server/Entity/ModelVFX/Equipment_of_Arrakis/Arrakis_Holtzman_Field.json', {
    $Comment: 'The Holtzman field on the body (tools/assets/make_holtzman.js, modelled on Bow_Signature_Status): an amber outline with bloom and a faint amber wash that sweeps up and down the model and back, smoothly.',
    SwitchTo: 'PostColor',
    EffectDirection: 'FromCenter',
    AnimationDuration: SHIMMER_SECONDS,
    CurveType: 'QuartInOut',
    LoopOption: 'LoopMirror',
    HighlightColor: OUTLINE_COLOR,
    HighlightThickness: OUTLINE_THICKNESS,
    UseBloomOnHighlight: true,
    NoiseScale: { X: 1.0, Y: 5.0 },
    PostColor: POST_COLOR,
    PostColorOpacity: POST_COLOR_OPACITY,
    UseProgessiveHighlight: false,
    AnimationRange: { Y: 0.6 },
});

// ---- particle spawners
const SPAWNERS = 'Server/Particles/Equipment_of_Arrakis_Holtzman/Spawners/';
const SYSTEMS = 'Server/Particles/Equipment_of_Arrakis_Holtzman/';

function glowSpawner(comment, o) {
    const body = {
        $Comment: comment,
        RenderMode: 'BlendAdd',
        EmitOffset: o.emit || rzero(0),
        ParticleRotationInfluence: o.rotation || 'Billboard',
        LinearFiltering: true,
        LightInfluence: 0,
        ParticleRotateWithSpawner: false,
        MaxConcurrentParticles: o.max,
        ParticleLifeSpan: o.life,
    };
    if (o.rate) body.SpawnRate = o.rate;
    if (o.total) { body.SpawnRate = range(o.burstRate || 500, o.burstRate || 500); body.TotalParticles = o.total; }
    body.InitialVelocity = o.velocity || {};
    if (o.gravity !== undefined) {
        body.Attractors = [{
            Position: { X: 0, Y: 0, Z: 0 },
            Radius: 0,
            RadialImpulse: 0,
            RadialTangentAcceleration: 0,
            LinearAcceleration: { X: 0, Y: o.gravity, Z: 0 },
            DampingMultiplier: { X: o.damping || 1, Y: o.damping || 1, Z: o.damping || 1 },
        }];
    }
    body.Particle = {
        Texture: o.texture,
        FrameSize: { Width: o.frame || 32, Height: o.frame || 32 },
        ScaleRatioConstraint: 'OneToOne',
        Animation: o.animation,
        InitialAnimationFrame: o.initial || { Rotation: rzero(0), Scale: o.initialScale, Color: o.color, Opacity: 0, FrameIndex: range(0, 0) },
        SoftParticles: 'Disable',
    };
    return body;
}

const keys = (list) => {
    const out = {};
    for (const k of list) {
        const frame = { Opacity: k.opacity };
        if (k.color) frame.Color = k.color;
        if (k.scale) frame.Scale = scale(k.scale[0], k.scale[1]);
        if (k.frame !== undefined) frame.FrameIndex = range(k.frame, k.frame);
        out[String(k.at)] = frame;
    }
    return out;
};

// The bubble: a soft glow that fades in and out; two overlap (life = twice the spawn interval) so the sum is a steady, slowly pulsing glow.
const low = BUBBLE_DIAMETER * (1 - BUBBLE_PULSE);
const high = BUBBLE_DIAMETER * (1 + BUBBLE_PULSE);
function bubbleGlow(id, opacity, comment) {
    writeJson(SPAWNERS + id + '.particlespawner', glowSpawner(comment, {
        max: 3,
        life: range(BUBBLE_PULSE_SECONDS, BUBBLE_PULSE_SECONDS),
        rate: range(2 / BUBBLE_PULSE_SECONDS, 2 / BUBBLE_PULSE_SECONDS),
        texture: 'Particles/Textures/Basic/Glow.png',
        color: AMBER,
        initialScale: scale(low, low),
        animation: keys([
            { at: 0, opacity: 0, color: AMBER, scale: [low, low] },
            { at: 50, opacity: opacity, color: AMBER, scale: [high, high] },
            { at: 100, opacity: 0, color: AMBER, scale: [low, low] },
        ]),
    }));
}
bubbleGlow('Arrakis_Holtzman_Bubble_Glow', BUBBLE_OPACITY, 'The soft amber glow of the Holtzman bubble: one large additive sprite that fades in and out and breathes in size.');
bubbleGlow('Arrakis_Holtzman_Bubble_Glow_FP', BUBBLE_FP_OPACITY, 'The same glow at a fraction of the opacity, for the owner\'s own view.');

writeJson(SPAWNERS + 'Arrakis_Holtzman_Bubble_Rim.particlespawner', glowSpawner('A faint ring at the edge of the bubble (Ring.png, billboard, additive).', {
    max: 3,
    life: range(BUBBLE_PULSE_SECONDS, BUBBLE_PULSE_SECONDS),
    rate: range(2 / BUBBLE_PULSE_SECONDS, 2 / BUBBLE_PULSE_SECONDS),
    texture: 'Particles/Textures/Basic/Ring.png',
    color: AMBER,
    initialScale: scale(low, low),
    animation: keys([
        { at: 0, opacity: 0, color: AMBER, scale: [low, low] },
        { at: 50, opacity: RIM_OPACITY, color: AMBER_LIGHT, scale: [high, high] },
        { at: 100, opacity: 0, color: AMBER, scale: [low, low] },
    ]),
}));

function motes(id, rate, comment) {
    const r = BUBBLE_DIAMETER / 2 * 0.8;
    writeJson(SPAWNERS + id + '.particlespawner', glowSpawner(comment, {
        max: MOTE_MAX,
        life: range(MOTE_LIFE[0], MOTE_LIFE[1]),
        rate: range(rate, rate),
        emit: rbox(r),
        texture: 'Particles/Textures/Basic/Glow.png',
        color: AMBER_LIGHT,
        initialScale: scale(MOTE_SIZE[0], MOTE_SIZE[1]),
        velocity: { Speed: range(0.05, 0.25), Yaw: range(0, 360), Pitch: range(-90, 90) },
        animation: keys([
            { at: 0, opacity: 0, color: AMBER_LIGHT, scale: [MOTE_SIZE[0], MOTE_SIZE[1]] },
            { at: 30, opacity: 0.9, color: AMBER_LIGHT, scale: [MOTE_SIZE[0], MOTE_SIZE[1]] },
            { at: 100, opacity: 0, color: AMBER, scale: [MOTE_SIZE[0] * 0.5, MOTE_SIZE[1] * 0.5] },
        ]),
    }));
}
motes('Arrakis_Holtzman_Bubble_Motes', MOTE_RATE, 'Fine amber specks drifting about inside the bubble.');
motes('Arrakis_Holtzman_Bubble_Motes_FP', MOTE_FP_RATE, 'A few specks for the owner\'s own view.');

// hit flash: a quick amber glow and a ring that grows
writeJson(SPAWNERS + 'Arrakis_Holtzman_Hit_Flash.particlespawner', glowSpawner('A small amber flash where a projectile met the field.', {
    max: 2, life: range(HIT_SECONDS, HIT_SECONDS), total: range(1, 1),
    texture: 'Particles/Textures/Basic/Glow.png', color: AMBER_LIGHT,
    initialScale: scale(HIT_SIZE[0], HIT_SIZE[0]),
    animation: keys([
        { at: 0, opacity: 0.9, color: WHITE, scale: [HIT_SIZE[0], HIT_SIZE[0]] },
        { at: 100, opacity: 0, color: AMBER, scale: [HIT_SIZE[1], HIT_SIZE[1]] },
    ]),
}));
writeJson(SPAWNERS + 'Arrakis_Holtzman_Hit_Ripple.particlespawner', glowSpawner('A ripple that spreads from the hit point.', {
    max: 2, life: range(HIT_SECONDS, HIT_SECONDS), total: range(1, 1),
    texture: 'Particles/Textures/Basic/Ring.png', color: AMBER,
    initialScale: scale(HIT_SIZE[0] * 0.5, HIT_SIZE[0] * 0.5),
    animation: keys([
        { at: 0, opacity: 0.8, color: AMBER_LIGHT, scale: [HIT_SIZE[0] * 0.5, HIT_SIZE[0] * 0.5] },
        { at: 100, opacity: 0, color: AMBER, scale: [HIT_SIZE[1] * 1.6, HIT_SIZE[1] * 1.6] },
    ]),
}));

// shatter: shards flying outwards, amber and white, a flash and an expanding ring
function shards(id, color, comment) {
    writeJson(SPAWNERS + id + '.particlespawner', glowSpawner(comment, {
        max: SHATTER_SHARDS + 4, life: range(SHATTER_SECONDS[0], SHATTER_SECONDS[1]), total: range(SHATTER_SHARDS, SHATTER_SHARDS),
        texture: 'Particles/Textures/Basic/Spark2.png', color,
        rotation: 'BillboardVelocity',
        initialScale: scale(SHATTER_SHARD_SIZE[0], SHATTER_SHARD_SIZE[1]),
        velocity: { Speed: range(SHATTER_SPEED[0], SHATTER_SPEED[1]), Yaw: range(0, 360), Pitch: range(-90, 90) },
        gravity: -4, damping: 0.97,
        animation: keys([
            { at: 0, opacity: 1, color, scale: [SHATTER_SHARD_SIZE[0], SHATTER_SHARD_SIZE[1]] },
            { at: 100, opacity: 0, color: AMBER, scale: [SHATTER_SHARD_SIZE[0] * 0.3, SHATTER_SHARD_SIZE[1] * 0.3] },
        ]),
    }));
}
shards('Arrakis_Holtzman_Shatter_Amber', AMBER_LIGHT, 'Amber shards flying outwards and falling a little as they fade.');
shards('Arrakis_Holtzman_Shatter_White', WHITE, 'White shards, the glass in the glass-like break.');
writeJson(SPAWNERS + 'Arrakis_Holtzman_Shatter_Flash.particlespawner', glowSpawner('The flash of the break: a big amber glow that fades at once.', {
    max: 2, life: range(0.3, 0.3), total: range(1, 1),
    texture: 'Particles/Textures/Basic/Glow.png', color: AMBER,
    initialScale: scale(SHATTER_RING_SIZE * 0.6, SHATTER_RING_SIZE * 0.6),
    animation: keys([
        { at: 0, opacity: 0.8, color: WHITE, scale: [SHATTER_RING_SIZE * 0.6, SHATTER_RING_SIZE * 0.6] },
        { at: 100, opacity: 0, color: AMBER, scale: [SHATTER_RING_SIZE, SHATTER_RING_SIZE] },
    ]),
}));
writeJson(SPAWNERS + 'Arrakis_Holtzman_Shatter_Ring.particlespawner', glowSpawner('A ring that expands from the player as the field goes.', {
    max: 2, life: range(0.5, 0.5), total: range(1, 1),
    texture: 'Particles/Textures/Basic/Ring.png', color: AMBER,
    initialScale: scale(BUBBLE_DIAMETER, BUBBLE_DIAMETER),
    animation: keys([
        { at: 0, opacity: 0.7, color: AMBER_LIGHT, scale: [BUBBLE_DIAMETER, BUBBLE_DIAMETER] },
        { at: 100, opacity: 0, color: AMBER, scale: [SHATTER_RING_SIZE * 1.4, SHATTER_RING_SIZE * 1.4] },
    ]),
}));

// ---- particle systems
const system = (comment, list, extra) => Object.assign({
    $Comment: comment,
    Spawners: list.map(id => ({ SpawnerId: id })),
    BoundingRadius: BUBBLE_BOUNDING_RADIUS,
    CullDistance: BUBBLE_CULL_DISTANCE,
    IsImportant: false,
}, extra || {});

writeJson(SYSTEMS + 'Arrakis_Holtzman_Bubble.particlesystem', system(
    'The Holtzman bubble others see: the soft glow, the faint ring, and drifting motes. Attached to the player by the entity effect Arrakis_Holtzman_Field.',
    ['Arrakis_Holtzman_Bubble_Glow', 'Arrakis_Holtzman_Bubble_Rim', 'Arrakis_Holtzman_Bubble_Motes']));
writeJson(SYSTEMS + 'Arrakis_Holtzman_Bubble_FP.particlesystem', system(
    'The Holtzman bubble the owner sees from the inside: the glow at a fraction of the opacity and a few motes, so the view stays readable.',
    ['Arrakis_Holtzman_Bubble_Glow_FP', 'Arrakis_Holtzman_Bubble_Motes_FP']));
writeJson(SYSTEMS + 'Arrakis_Holtzman_Hit.particlesystem', system(
    'A small amber flash and ripple where a projectile hit the field. Spawned by the code, no sound.',
    ['Arrakis_Holtzman_Hit_Flash', 'Arrakis_Holtzman_Hit_Ripple'], { BoundingRadius: 2, CullDistance: 64 }));
writeJson(SYSTEMS + 'Arrakis_Holtzman_Shatter.particlesystem', system(
    'The burst when the Holtzman shield breaks: amber and white shards, a flash and a ring. Spawned by the code at the player.',
    ['Arrakis_Holtzman_Shatter_Amber', 'Arrakis_Holtzman_Shatter_White', 'Arrakis_Holtzman_Shatter_Flash', 'Arrakis_Holtzman_Shatter_Ring'],
    { BoundingRadius: 8, CullDistance: 64 }));

// ---- the entity effect
writeJson('Server/Entity/Effects/Equipment_of_Arrakis/Arrakis_Holtzman_Field.json', {
    $Comment: 'The Holtzman field (tools/assets/make_holtzman.js). Infinite: the Holtzman system adds it while the shield is raised and takes it off when it is not (and every tick it takes off a field that should not be there, so one left in a save is removed). A faint amber tint and outline (ModelVFX), the bubble attached to the Belly node (a gentler one for the owner), and the static hum.',
    ApplicationEffects: {
        EntityBottomTint: TINT_BOTTOM,
        EntityTopTint: TINT_TOP,
        ModelVFXId: 'Arrakis_Holtzman_Field',
        Particles: [{
            SystemId: 'Arrakis_Holtzman_Bubble',
            TargetEntityPart: 'Entity',
            TargetNodeName: BUBBLE_NODE,
            PositionOffset: { X: 0, Y: BUBBLE_OFFSET_Y, Z: 0 },
            Scale: 1,
            DetachedFromModel: false,
            ClearParticlesOnRemove: true,
        }],
        FirstPersonParticles: [{
            SystemId: 'Arrakis_Holtzman_Bubble_FP',
            TargetEntityPart: 'Entity',
            TargetNodeName: BUBBLE_NODE,
            PositionOffset: { X: 0, Y: BUBBLE_OFFSET_Y, Z: 0 },
            Scale: 1,
            DetachedFromModel: false,
            ClearParticlesOnRemove: true,
        }],
        WorldSoundEventId: 'SFX_Arrakis_Holtzman_Field',
        LocalSoundEventId: 'SFX_Arrakis_Holtzman_Field_Local',
    },
    OverlapBehavior: 'Overwrite',
    Infinite: true,
    Duration: 1,
    Debuff: false,
});
console.log('Holtzman assets written');
