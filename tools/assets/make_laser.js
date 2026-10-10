// Builds the laser pieces of the Cutter Ray: two thin boxes, each a model, a texture and a model asset.
//   Arrakis_Cutter_Laser        the white-hot core: opaque, solid white
//   Arrakis_Cutter_Laser_Glow   the glow around it: the same box, white and translucent
// Files: Common/Items/Equipment_of_Arrakis/<name>.blockymodel and <name>_Texture.png, Server/Models/Equipment_of_Arrakis/<name>.json
//
// An entity can only be scaled evenly (EntityScaleComponent is one float), so the line is a row of pieces of different
// scales: CutterLaser picks each piece's scale from the width it should have at that point, which tapers the line, and
// lays the pieces end to end so that they overlap. Both boxes are 1 model unit thick and 96 long at scale 1, so a piece
// of width w blocks is 96 * w blocks long (CutterLaser.LENGTH_PER_WIDTH). The box lies along the model's z axis and is
// centred on its own origin, so the entity position is the middle of the piece.
//
//   node tools/assets/make_laser.js
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const root = path.join(__dirname, '..', '..', 'src', 'main', 'resources');
const modelDir = path.join(root, 'Common', 'Items', 'Equipment_of_Arrakis');
const assetDir = path.join(root, 'Server', 'Models', 'Equipment_of_Arrakis');
fs.mkdirSync(modelDir, { recursive: true });
fs.mkdirSync(assetDir, { recursive: true });

const WIDTH = 1;     // model units: the scale of each piece sets the real width
const LENGTH = 96;   // model units: CutterLaser.LENGTH_PER_WIDTH
const TEXTURE = 128; // pixels; the whole image is the one colour, so any face mapping reads that colour
const VARIANTS = [
    { name: 'Arrakis_Cutter_Laser', color: [0xff, 0xff, 0xff, 255] },
    // Translucent: if the client only does alpha-test (cut-off at 0.5) this half-transparent layer is not drawn at all
    // and the core is seen alone, which is the safe way to fail.
    { name: 'Arrakis_Cutter_Laser_Glow', color: [0xff, 0xff, 0xff, 110] },
];

// ---- model
const face = { offset: { x: 0, y: 0 }, mirror: { x: false, y: false }, angle: 0 };
const model = {
    nodes: [{
        id: '1',
        name: 'Laser',
        position: { x: 0, y: 0, z: 0 },
        orientation: { x: 0, y: 0, z: 0, w: 1 },
        children: [],
        shape: {
            type: 'box',
            offset: { x: 0, y: 0, z: 0 },
            stretch: { x: 1, y: 1, z: 1 },
            settings: { size: { x: WIDTH, y: WIDTH, z: LENGTH } },
            visible: true,
            doubleSided: false,
            // Fullbright: drawn at full brightness whatever the light, so it stays bright in a cave and in daylight.
            shadingMode: 'fullbright',
            unwrapMode: 'custom',
            textureLayout: { front: face, back: face, left: face, right: face, top: face, bottom: face },
        },
    }],
    lod: 'auto',
};

// ---- textures: solid PNGs, written by hand so that no image library is needed
function crc32(buf) {
    let c, crc = 0xffffffff;
    for (let n = 0; n < buf.length; n++) {
        c = (crc ^ buf[n]) & 0xff;
        for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
        crc = (crc >>> 8) ^ c;
    }
    return (crc ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
    const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
    const body = Buffer.concat([Buffer.from(type), data]);
    const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body));
    return Buffer.concat([len, body, crc]);
}
function texture(color) {
    const header = Buffer.alloc(13);
    header.writeUInt32BE(TEXTURE, 0); header.writeUInt32BE(TEXTURE, 4);
    header[8] = 8; header[9] = 6; // 8 bits, RGBA
    const row = Buffer.alloc(1 + TEXTURE * 4);
    for (let x = 0; x < TEXTURE; x++) { for (let c = 0; c < 4; c++) row[1 + x * 4 + c] = color[c]; }
    const raw = Buffer.concat(Array.from({ length: TEXTURE }, () => row));
    return Buffer.concat([
        Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
        chunk('IHDR', header), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0)),
    ]);
}

for (const v of VARIANTS) {
    fs.writeFileSync(path.join(modelDir, v.name + '.blockymodel'), JSON.stringify(model, null, 2) + '\n');
    fs.writeFileSync(path.join(modelDir, v.name + '_Texture.png'), texture(v.color));
    const asset = {
        Model: 'Items/Equipment_of_Arrakis/' + v.name + '.blockymodel',
        Texture: 'Items/Equipment_of_Arrakis/' + v.name + '_Texture.png',
        HitBox: { Min: { X: -0.05, Y: -0.05, Z: -0.05 }, Max: { X: 0.05, Y: 0.05, Z: 0.05 } },
    };
    fs.writeFileSync(path.join(assetDir, v.name + '.json'), JSON.stringify(asset, null, 2) + '\n');
}
console.log('wrote', VARIANTS.length, 'laser models');
