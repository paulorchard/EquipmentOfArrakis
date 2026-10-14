// EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments").
//
// THE ONE EXPERIMENT COPY OF A VANILLA ASSET. The Scrap flamethrower's texture paints its flask (Flamethrower_Eye, its Pupil and
// Flamethrower_Bottle_Top) in saturated red-orange (#ff1b03 and neighbours); no item property can change that, and the Flame Cutter
// must not look like a lit flamethrower. This script writes a copy of Scrap_Texture.png in which only the warm pixels inside the
// faces of those three nodes are turned into a cool steel blue of the same brightness. Every other pixel is untouched.
// To go back to the vanilla look, point "Texture" in Arrakis_Exp_Flame_Cutter.json at the vanilla path again and delete the copy.
//
//   unzip -o -q "$APPDATA/Hytale/install/release/package/game/latest/Assets.zip" "Common/NPC/Intelligent/Goblin/Models/Weapons/Flamethrower/*" -d <dir>
//   node tools/assets/experiments/make_flame_texture.js <dir>
const fs = require('fs');
const path = require('path');
const { readPng, writePng } = require('./png');

// ---- settings
const NODES = ['Flamethrower_Eye', 'Flamethrower_Pupil', 'Flamethrower_Bottle_Top']; // the nodes whose faces are recoloured
const WARM = 30;                       // a pixel is warm when red exceeds blue by at least this much
const TINT = [0.62, 0.8, 1.0];         // the cool colour at full brightness (steel blue); brightness is kept
const OUT = 'src/main/resources/Common/Items/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Cutter_Texture.png';

const dir = path.join(process.argv[2] || '.', 'Common/NPC/Intelligent/Goblin/Models/Weapons/Flamethrower');
const png = readPng(path.join(dir, 'Scrap_Texture.png'));
const model = JSON.parse(fs.readFileSync(path.join(dir, 'Scrap.blockymodel'), 'utf8'));

function faceRects(node) {
    const rects = [];
    const size = node.shape.settings.size || {};
    for (const [face, l] of Object.entries(node.shape.textureLayout || {})) {
        let w, h;
        if (node.shape.type === 'quad' || face === 'front' || face === 'back') {
            w = size.x; h = size.y;
        } else if (face === 'left' || face === 'right') {
            w = size.z; h = size.y;
        } else {
            w = size.x; h = size.z;
        }
        if (l.angle === 90 || l.angle === 270) {
            [w, h] = [h, w];
        }
        rects.push({ x: l.offset.x, y: l.offset.y, w, h });
    }
    return rects;
}

const rects = [];
(function walk(n) {
    if (NODES.includes(n.name)) {
        rects.push(...faceRects(n));
    }
    (n.children || []).forEach(walk);
})({ name: 'root', shape: { settings: {} }, children: model.nodes });

let changed = 0;
const done = new Set();
for (const r of rects) {
    for (let y = r.y; y < r.y + r.h; y++) {
        for (let x = r.x; x < r.x + r.w; x++) {
            const key = x + ',' + y;
            const p = png.at(x, y);
            if (done.has(key) || !p || p[3] === 0 || p[0] - p[2] < WARM) {
                continue;
            }
            done.add(key);
            const brightness = (0.3 * p[0] + 0.59 * p[1] + 0.11 * p[2]) / 255;
            png.set(x, y, [Math.round(brightness * TINT[0] * 255), Math.round(brightness * TINT[1] * 255), Math.round(brightness * TINT[2] * 255), p[3]]);
            changed++;
        }
    }
}
fs.mkdirSync(path.dirname(OUT), { recursive: true });
writePng(OUT, png, 'EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments"). '
    + 'Copy of Scrap_Texture.png with the warm pixels of ' + NODES.join(', ') + ' recoloured steel blue.');
console.log('recoloured ' + changed + ' pixels in ' + rects.length + ' face rectangles of ' + NODES.join(', ') + ' -> ' + OUT);
