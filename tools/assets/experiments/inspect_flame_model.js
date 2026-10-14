// EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments").
//
// Is there pilot-flame glow baked into the Scrap flamethrower's model or texture? Prints the nodes of Scrap.blockymodel named
// Flame or Wick (geometry or not), the texture pixels each maps to, and every hot (red/orange) region of the texture with the
// faces that cover it and its commonest colours.
//
//   node tools/assets/experiments/inspect_flame_model.js <Scrap.blockymodel> <Scrap_Texture.png>
const fs = require('fs');
const { readPng } = require('./png');

const [modelPath, texturePath] = process.argv.slice(2);
const png = readPng(texturePath);
const model = JSON.parse(fs.readFileSync(modelPath, 'utf8'));
const hex = p => !p ? '-' : '#' + p.slice(0, 3).map(v => v.toString(16).padStart(2, '0')).join('') + (p[3] < 255 ? '/a' + p[3] : '');
const hot = p => p && p[3] > 0 && p[0] >= 200 && p[1] <= 170 && p[0] - p[2] >= 80; // red, orange, hot yellow-orange

// The rectangle of texture each face of a node covers.
function faceRects(node) {
    const rects = [];
    const size = node.shape.settings.size || {};
    const layout = node.shape.textureLayout || {};
    for (const [face, l] of Object.entries(layout)) {
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
        rects.push({ face, x: l.offset.x, y: l.offset.y, w, h });
    }
    return rects;
}

const nodes = [];
(function walk(n, parent) {
    nodes.push({ node: n, parent });
    (n.children || []).forEach(c => walk(c, n.name));
})({ name: 'root', shape: { settings: {} }, children: model.nodes }, null);

console.log('texture', png.width + 'x' + png.height);
console.log('--- nodes named Flame or Wick');
for (const { node, parent } of nodes) {
    if (/^(flame|wick)$/i.test(node.name)) {
        console.log(node.name, '(under ' + parent + ') type', node.shape.type, 'visible', node.shape.visible, 'size', JSON.stringify(node.shape.settings.size));
        for (const r of faceRects(node)) {
            const pixels = [];
            for (let y = r.y; y < r.y + r.h; y++) {
                for (let x = r.x; x < r.x + r.w; x++) {
                    pixels.push(hex(png.at(x, y)));
                }
            }
            console.log('   ', r.face, 'rect', r.x + ',' + r.y, r.w + 'x' + r.h, '->', pixels.join(' '));
        }
    }
}

console.log('--- hot (red/orange) regions of the texture');
const seen = new Set();
const regions = [];
for (let y = 0; y < png.height; y++) {
    for (let x = 0; x < png.width; x++) {
        if (!seen.has(x + ',' + y) && hot(png.at(x, y))) {
            const stack = [[x, y]];
            const region = { minX: x, maxX: x, minY: y, maxY: y, count: 0 };
            seen.add(x + ',' + y);
            while (stack.length) {
                const [cx, cy] = stack.pop();
                region.count++;
                region.minX = Math.min(region.minX, cx); region.maxX = Math.max(region.maxX, cx);
                region.minY = Math.min(region.minY, cy); region.maxY = Math.max(region.maxY, cy);
                for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
                    const nx = cx + dx, ny = cy + dy;
                    if (!seen.has(nx + ',' + ny) && hot(png.at(nx, ny))) {
                        seen.add(nx + ',' + ny);
                        stack.push([nx, ny]);
                    }
                }
            }
            regions.push(region);
        }
    }
}
for (const g of regions) {
    const users = [];
    for (const { node } of nodes) {
        for (const r of faceRects(node)) {
            if (r.x < g.maxX + 1 && r.x + r.w > g.minX && r.y < g.maxY + 1 && r.y + r.h > g.minY) {
                users.push(node.name + '.' + r.face);
            }
        }
    }
    const counts = {};
    for (let y = g.minY; y <= g.maxY; y++) {
        for (let x = g.minX; x <= g.maxX; x++) {
            if (hot(png.at(x, y))) {
                counts[hex(png.at(x, y))] = (counts[hex(png.at(x, y))] || 0) + 1;
            }
        }
    }
    const top = Object.entries(counts).sort((a, b) => b[1] - a[1]).slice(0, 4).map(([c, n]) => c + 'x' + n).join(' ');
    console.log('x ' + g.minX + '..' + g.maxX + ', y ' + g.minY + '..' + g.maxY + ', ' + g.count + ' px [' + top + '] faces of: '
        + (users.length ? users.join(', ') : '(no face)'));
}
