// Draws the art of the hud module: the bar background, fill and sheen textures (left and right) and the four round icons (bolt,
// heart, sun, drop). Everything is our own: nothing here is read from the game or from the Water mod.
//
//   node tools/assets/hud/make_hud.js
//
// Writes, under src/main/resources/Common/UI/Custom/Hud/Equipment_of_Arrakis/Hud/ (the @2x files are referenced from the .ui without
// the @2x, the client picks them up as it does for the Water mod's bars):
//   Arrakis_Hud_Bar_Background_{Left,Right}@2x.png   dark frame with a lighter rim and a dark outline
//   Arrakis_Hud_Bar_Fill_{Left,Right}@2x.png         white body, tinted by Background.Color in the .ui
//   Arrakis_Hud_Bar_Sheen_{Left,Right}@2x.png        gloss and shade over the fill, not tinted
//   Arrakis_Hud_Icon_{Power,Health,Exposure,Water}@2x.png
//
// "Left" is the bar of the left half: its OUTER end (the left end) is the slanted one, as in the reference screenshot (the bar is
// wider at the bottom than at the top there). "Right" is the mirror image. In the .ui the textures are 9-sliced horizontally with
// HorizontalBorder = CAP (virtual pixels), so only the straight middle stretches and the slant keeps its angle at every size.
//
// Sizes are in the HUD's virtual pixels (what the hotbar is measured in); a texture is drawn at 2x. The reference screenshot was
// measured against a hotbar of 9 slots 360 virtual pixels either side of the centre, so 1 virtual pixel = 0.847 screenshot pixels:
// the 9.5 px thick bars of the screenshot are 11 to 12 virtual pixels.
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

// ------------------------------------------------------------------ tunables
const THICK = 12;          // bar thickness, virtual pixels (the .ui and the Hud config section must use the same number)
const CAP = 12;            // horizontal size of the slanted end, virtual pixels; equal to THICK gives 45 degrees
const MIDDLE = 16;         // virtual pixels of straight middle in the texture (it stretches, so any value works)
const OUTLINE = 1;         // dark outline, virtual pixels
const RIM = 1;             // lighter rim along the top and bottom of the background, virtual pixels
const BACKGROUND = [0x1c, 0x22, 0x2c, 215];    // frame body (r, g, b, alpha 0..255)
const RIM_COLOR = [0x46, 0x4d, 0x5a, 235];
const OUTLINE_COLOR = [0x07, 0x09, 0x0d, 235];
const SHEEN_TOP = 120;     // alpha (0..255) of the white highlight on the first texture row of the fill
const SHEEN_GLOSS = 38;    // alpha of the white gloss over the upper half of the fill
const SHEEN_SHADE = 44;    // alpha of the black shade over the lower half of the fill
const FILL_SHADE = 28;     // how much darker the white fill is at the bottom than at the top (0..255), so a tint reads as a gradient
const ICON = 22;           // icon size, virtual pixels
const ICON_RING = 1.5;     // dark ring round the disc, virtual pixels
const ICONS = {            // disc colours sampled from the screenshot bars, and the glyph colour
    Power: { disc: [0x2f, 0x9d, 0x90], glyph: [255, 255, 255] },
    Health: { disc: [0xc8, 0x3a, 0x4c], glyph: [255, 255, 255] },
    Exposure: { disc: [0xd9, 0x8e, 0x12], glyph: [255, 255, 255] },
    Water: { disc: [0x3f, 0x82, 0xd6], glyph: [255, 255, 255] },
};
// ------------------------------------------------------------------

const out = path.join(__dirname, '..', '..', '..', 'src', 'main', 'resources', 'Common', 'UI', 'Custom', 'Hud', 'Equipment_of_Arrakis', 'Hud');
const S = 2;                                   // texture pixels per virtual pixel
const H = THICK * S;
const CAPPX = CAP * S;
const W = (CAP * 2 + MIDDLE) * S;
const SS = 4;                                  // supersampling per axis

// ---- a minimal PNG writer (8 bit RGBA)
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
    const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
    const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body));
    return Buffer.concat([len, body, crc]);
}
function writePng(file, width, height, rgba) {
    const raw = Buffer.alloc(height * (width * 4 + 1));
    for (let y = 0; y < height; y++) {
        raw[y * (width * 4 + 1)] = 0;
        Buffer.from(rgba.buffer, y * width * 4, width * 4).copy(raw, y * (width * 4 + 1) + 1);
    }
    const header = Buffer.alloc(13);
    header.writeUInt32BE(width, 0); header.writeUInt32BE(height, 4); header[8] = 8; header[9] = 6;
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', header),
        chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]));
}

// ---- shapes. Coordinates are texture pixels, y down; (x, y) is a point, not a pixel index.
// The Left bar: inside when the point is right of the slanted left edge (the edge runs from the top at x = CAPPX down to the bottom at x = 0).
function insideLeft(x, y, inset) {
    return y >= inset && y <= H - inset && x >= (CAPPX * (1 - y / H)) + inset * 1.2 && x <= W;
}
function insideShape(left, x, y, inset) {
    return left ? insideLeft(x, y, inset) : insideLeft(W - x, y, inset);
}

/** Renders `fn(x, y) -> [r, g, b, a] or null` with supersampling into an RGBA Uint8ClampedArray of w x h. */
function render(w, h, fn) {
    const px = new Uint8ClampedArray(w * h * 4);
    for (let y = 0; y < h; y++) {
        for (let x = 0; x < w; x++) {
            let r = 0, g = 0, b = 0, a = 0;
            for (let sy = 0; sy < SS; sy++) {
                for (let sx = 0; sx < SS; sx++) {
                    const c = fn(x + (sx + 0.5) / SS, y + (sy + 0.5) / SS);
                    if (c) {
                        const al = c[3] / 255;
                        r += c[0] * al; g += c[1] * al; b += c[2] * al; a += al;
                    }
                }
            }
            const n = SS * SS;
            const o = (y * w + x) * 4;
            if (a > 0) {
                px[o] = r / a; px[o + 1] = g / a; px[o + 2] = b / a;
            }
            px[o + 3] = (a / n) * 255;
        }
    }
    return px;
}

for (const left of [true, false]) {
    const side = left ? 'Left' : 'Right';
    // Background: outline outside, lighter rim on the long edges, dark body.
    writePng(path.join(out, `Arrakis_Hud_Bar_Background_${side}@2x.png`), W, H, render(W, H, (x, y) => {
        if (!insideShape(left, x, y, 0)) return null;
        if (!insideShape(left, x, y, OUTLINE * S)) return OUTLINE_COLOR;
        if (y < (OUTLINE + RIM) * S || y > H - (OUTLINE + RIM) * S) return RIM_COLOR;
        return BACKGROUND;
    }));
    // Fill: white, a little darker toward the bottom, inside the outline. The square end runs to the edge of the texture.
    writePng(path.join(out, `Arrakis_Hud_Bar_Fill_${side}@2x.png`), W, H, render(W, H, (x, y) => {
        if (!insideShape(left, x, y, OUTLINE * S)) return null;
        const v = 255 - FILL_SHADE * (y / H);
        return [v, v, v, 255];
    }));
    // Sheen: a bright first row, a gloss over the upper half, a shade over the lower half.
    writePng(path.join(out, `Arrakis_Hud_Bar_Sheen_${side}@2x.png`), W, H, render(W, H, (x, y) => {
        if (!insideShape(left, x, y, OUTLINE * S)) return null;
        const first = OUTLINE * S;
        if (y < first + S) return [255, 255, 255, SHEEN_TOP];
        if (y < H / 2) return [255, 255, 255, SHEEN_GLOSS];
        return [0, 0, 0, SHEEN_SHADE];
    }));
}

// ---- icons: a round disc with a dark ring and a white glyph. Icon pixels: the box is ICON * S wide, glyph coordinates are 0..48.
const IW = ICON * S;
const u = IW / 48;                              // glyph unit (the glyphs below are drawn on a 48 x 48 grid)
function polygon(points) {
    return (x, y) => {
        let inside = false;
        for (let i = 0, j = points.length - 1; i < points.length; j = i++) {
            const [xi, yi] = points[i], [xj, yj] = points[j];
            if ((yi > y) !== (yj > y) && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside;
        }
        return inside;
    };
}
const circle = (cx, cy, r) => (x, y) => (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r;
const union = (...fns) => (x, y) => fns.some(f => f(x, y));
const glyphs = {
    Power: polygon([[28, 8], [14, 27], [22, 27], [19, 41], [34, 21], [26, 21], [30, 8]]),
    Health: (() => {
        const pts = [];
        for (let i = 0; i < 80; i++) {
            const t = (i / 80) * Math.PI * 2;
            pts.push([24 + 1.0 * 16 * Math.pow(Math.sin(t), 3) * 0.95,
                25.5 - 0.95 * (13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t))]);
        }
        return polygon(pts);
    })(),
    Exposure: (() => {
        const rays = [];
        for (let i = 0; i < 8; i++) {
            const a = (i * Math.PI) / 4, c = Math.cos(a), s = Math.sin(a), n = [-s, c];
            const r0 = 11.5, r1 = 17.5, hw = 1.7;
            rays.push(polygon([[24 + c * r0 + n[0] * hw, 24 + s * r0 + n[1] * hw], [24 + c * r1 + n[0] * hw, 24 + s * r1 + n[1] * hw],
                [24 + c * r1 - n[0] * hw, 24 + s * r1 - n[1] * hw], [24 + c * r0 - n[0] * hw, 24 + s * r0 - n[1] * hw]]));
        }
        return union(circle(24, 24, 8), ...rays);
    })(),
    Water: union(circle(24, 30, 9.5), polygon([[24, 7], [14.3, 27], [33.7, 27]])),
};
for (const [name, spec] of Object.entries(ICONS)) {
    const glyph = glyphs[name];
    const cx = IW / 2, cy = IW / 2, R = IW / 2 - 0.5;
    writePng(path.join(out, `Arrakis_Hud_Icon_${name}@2x.png`), IW, IW, render(IW, IW, (x, y) => {
        const d = Math.hypot(x - cx, y - cy);
        if (d > R) return null;
        if (d > R - ICON_RING * S) return [0x07, 0x09, 0x0d, 240];
        if (glyph(x / u, y / u)) return [...spec.glyph, 255];
        return [...spec.disc, 255];
    }));
}
console.log(`Hud art written to ${out}: textures ${W}x${H}, icons ${IW}x${IW} (virtual ${ICON}x${ICON}), bar ${THICK} thick, cap ${CAP}`);

// ---- the .ui: a skeleton with the default numbers. HudView sets every anchor and colour again from the Hud config section when the
// document is built, so these are only starting points; they are the same numbers as the defaults of HudConfig.
const LAYOUT = {
    bottomRowBottom: 111,   // from the bottom of the screen to the bottom of the Health / Water row (hotbar baseline 24 + slot 78 + gap 9)
    rowPitch: 26,           // from the bottom of one row to the bottom of the row above it
    topOuter: 314,          // from the centre of the screen to the outer end of the Power / Heat Exposure bars
    bottomOuter: 335,       // the same for the Health / Water bars
    innerGap: 7,            // from the centre of the screen to the inner end of every bar
    iconSize: 22,
    topIconCentre: 334,     // from the centre of the screen to the centre of the Power / Heat Exposure icons
    bottomIconCentre: 350,
    colors: { Power: '#57bbaf', Health: '#d64d58', Exposure: '#d49c1b', Water: '#599de3' },
};
const BARS = [
    { name: 'Power', left: true, top: true },
    { name: 'Health', left: true, top: false },
    { name: 'Exposure', left: false, top: true },
    { name: 'Water', left: false, top: false },
];
const SCALE = 1000;
let ui = '// The four-bar HUD of Equipment of Arrakis, generated by tools/assets/hud/make_hud.js (the numbers are only defaults: HudView sets them again\n'
    + '// from the Hud section of Equipment_of_Arrakis.json). Two rows above the hotbar, each split in half at the middle of the screen, which is the\n'
    + '// middle of the hotbar. Power over Health on the left, Heat Exposure over Water on the right. A bar is a track of two parts that share its\n'
    + '// length by FlexWeight (1000 - v and v), so the fill is exactly v of the bar at any screen size; left bars fill from their outer (left) end,\n'
    + '// right bars from their outer (right) end. Fill colour is Background.Color on a white texture; the sheen is not tinted.\n\n';
for (const bar of BARS) {
    const n = bar.name;
    const side = bar.left ? 'Left' : 'Right';
    const bottom = LAYOUT.bottomRowBottom + (bar.top ? LAYOUT.rowPitch : 0);
    const outer = bar.top ? LAYOUT.topOuter : LAYOUT.bottomOuter;
    const width = outer - LAYOUT.innerGap;
    const tex = (kind) => `TexturePath: "Arrakis_Hud_Bar_${kind}_${side}.png", HorizontalBorder: ${CAP}`;
    const track = `    Group #${n}Track {\n      Anchor: (Width: ${width});\n      LayoutMode: Left;\n      Background: (${tex('Background')});\n\n`
        + (bar.left ? '' : `      Group #${n}Empty {\n        FlexWeight: ${SCALE};\n      }\n\n`)
        + `      Group #${n}Fill {\n        FlexWeight: 0;\n        Background: (${tex('Fill')}, Color: ${LAYOUT.colors[n]});\n\n`
        + `        Group #${n}Sheen {\n          Background: (${tex('Sheen')});\n        }\n      }\n`
        + (bar.left ? `\n      Group #${n}Empty {\n        FlexWeight: ${SCALE};\n      }\n` : '')
        + '    }\n';
    const inner = bar.left
        ? `    Group #${n}Spacer {\n      FlexWeight: 1;\n    }\n\n${track}\n    Group #${n}Pad {\n      Anchor: (Width: ${LAYOUT.innerGap});\n    }\n`
        : `    Group #${n}Pad {\n      Anchor: (Width: ${LAYOUT.innerGap});\n    }\n\n${track}\n    Group #${n}Spacer {\n      FlexWeight: 1;\n    }\n`;
    const half = `  Group #${n}Half {\n    FlexWeight: 1;\n    LayoutMode: Left;\n\n${inner}  }\n`;
    const other = `  Group #${n}OtherHalf {\n    FlexWeight: 1;\n  }\n`;
    ui += `Group #${n}Row {\n  Anchor: (Left: 0, Right: 0, Bottom: ${bottom}, Height: ${THICK});\n  LayoutMode: Left;\n\n`
        + (bar.left ? half + '\n' + other : other + '\n' + half) + '}\n\n';
    const iconBottom = bottom + Math.round((THICK - LAYOUT.iconSize) / 2);
    const centre = bar.top ? LAYOUT.topIconCentre : LAYOUT.bottomIconCentre;
    const padWidth = centre - LAYOUT.iconSize / 2;
    const icon = `    Group #${n}Icon {\n      Anchor: (Width: ${LAYOUT.iconSize}, Height: ${LAYOUT.iconSize});\n      Background: "Arrakis_Hud_Icon_${n}.png";\n    }\n`;
    const iconPad = `    Group #${n}IconPad {\n      Anchor: (Width: ${padWidth});\n    }\n`;
    const iconSpacer = `    Group #${n}IconSpacer {\n      FlexWeight: 1;\n    }\n`;
    const iconHalf = `  Group #${n}IconHalf {\n    FlexWeight: 1;\n    LayoutMode: Left;\n\n`
        + (bar.left ? iconSpacer + '\n' + icon + '\n' + iconPad : iconPad + '\n' + icon + '\n' + iconSpacer) + '  }\n';
    const iconOther = `  Group #${n}IconOtherHalf {\n    FlexWeight: 1;\n  }\n`;
    ui += `Group #${n}IconRow {\n  Anchor: (Left: 0, Right: 0, Bottom: ${iconBottom}, Height: ${LAYOUT.iconSize});\n  LayoutMode: Left;\n\n`
        + (bar.left ? iconHalf + '\n' + iconOther : iconOther + '\n' + iconHalf) + '}\n\n';
}
fs.writeFileSync(path.join(out, 'Arrakis_Hud.ui'), ui.trimEnd() + '\n');
console.log('Arrakis_Hud.ui written');
