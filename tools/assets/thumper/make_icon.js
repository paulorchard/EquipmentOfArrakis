// Makes the Thumper icon. The game has no icon of the Temple Emerald torch without its flame, so this takes the lit one and removes
// the flame.
//
//   node tools/assets/thumper/make_icon.js
//   HUD_ASSETS_ZIP=<...\Assets.zip> node tools/assets/thumper/make_icon.js
//
// Reads  Common/Icons/ItemsGenerated/Furniture_Temple_Emerald_Torch.png from Assets.zip (64 x 64, the torch with the green flame).
// Writes src/main/resources/Common/Icons/Items/Equipment_of_Arrakis/Arrakis_Thumper.png (the one derived image of the thumper module; the
// reason is in MODULES.md: no unlit icon of this torch exists in the game). Byte identical on every run. The path is under Icons/Items/
// because an item icon may only live under Icons/ItemsGenerated/ or Icons/Items/ (the asset validator checks it).
//
// The rule, found by looking at the picture and its pixels:
//  * the flame is the saturated green / yellow-green / yellow pixels (hue 53..170 degrees, saturation >= 0.45) plus its white-hot core,
//    which is almost white (value >= 0.9, saturation < 0.45) and touches those pixels: it is taken in two steps outward from them,
//    but only above the cup floor (FLAME_FLOOR_ROW), so the cream stone of the cup is never taken. The stone is hue 38..52, saturation
//    <= 0.35, so no hue rule touches it.
//  * above the rim of the cup (rows < RIM_ROW) what the flame covered is the background: transparent.
//  * from the rim down (the flame stood in the bowl) the hole is filled with the colour of the first stone pixel below it in the same
//    column (the darkest of the few below it), so the bowl reads as an empty dark bowl and the cap looks finished.
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { readPng, encodePng } = require('../hud/png');

// ------------------------------------------------------------------ where the vanilla file is, and the tunables
const ASSETS_ZIP = process.env.HUD_ASSETS_ZIP
    || path.join(process.env.APPDATA, 'Hytale', 'install', 'release', 'package', 'game', 'latest', 'Assets.zip');
const VANILLA_ICON = 'Common/Icons/ItemsGenerated/Furniture_Temple_Emerald_Torch.png';
const HUE_MIN = 53, HUE_MAX = 170;   // degrees: yellow (the flame's lower tongue) through green
const SAT_MIN = 0.45;                // the cream stone tops out near 0.35
const WHITE_VALUE = 0.9;             // the white-hot core
const CORE_STEPS = 2;                // how far the white core is followed outward from the coloured flame
const RIM_ROW = 21;                  // first row of the cup (the flame above it stands against the background)
const FLAME_FLOOR_ROW = 25;          // no flame below this row
const FILL_DEPTH = 8;                // how far down to look for a stone pixel to fill the hole with
// ------------------------------------------------------------------

const root = path.join(__dirname, '..', '..', '..');
const OUT = path.join(root, 'src', 'main', 'resources', 'Common', 'Icons', 'Items', 'Equipment_of_Arrakis', 'Arrakis_Thumper.png');

function hsv(r, g, b) {
    const max = Math.max(r, g, b), min = Math.min(r, g, b), d = max - min;
    let h = 0;
    if (d > 0) {
        if (max === r) h = ((g - b) / d) % 6;
        else if (max === g) h = (b - r) / d + 2;
        else h = (r - g) / d + 4;
        h *= 60;
        if (h < 0) h += 360;
    }
    return { h, s: max === 0 ? 0 : d / max, v: max / 255 };
}

const image = readPng(execFileSync('unzip', ['-p', ASSETS_ZIP, VANILLA_ICON], { maxBuffer: 1 << 26 }));
const { width, height, pixels } = image;
const at = (x, y) => (y * width + x) * 4;
const opaque = (x, y) => pixels[at(x, y) + 3] >= 20;

// step 1: the coloured flame
const flame = new Uint8Array(width * height);
for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
        if (!opaque(x, y) || y >= FLAME_FLOOR_ROW) continue;
        const i = at(x, y);
        const c = hsv(pixels[i], pixels[i + 1], pixels[i + 2]);
        if (c.h >= HUE_MIN && c.h <= HUE_MAX && c.s >= SAT_MIN) flame[y * width + x] = 1;
    }
}
// step 2: the white-hot core, followed outward from the coloured flame
for (let step = 0; step < CORE_STEPS; step++) {
    const grow = [];
    for (let y = 0; y < FLAME_FLOOR_ROW; y++) {
        for (let x = 0; x < width; x++) {
            if (flame[y * width + x] || !opaque(x, y)) continue;
            const i = at(x, y);
            const c = hsv(pixels[i], pixels[i + 1], pixels[i + 2]);
            if (c.v < WHITE_VALUE || c.s >= SAT_MIN) continue;
            let touches = false;
            for (let dy = -1; dy <= 1 && !touches; dy++) {
                for (let dx = -1; dx <= 1; dx++) {
                    const nx = x + dx, ny = y + dy;
                    if (nx >= 0 && ny >= 0 && nx < width && ny < height && flame[ny * width + nx]) {
                        touches = true;
                        break;
                    }
                }
            }
            if (touches) grow.push(y * width + x);
        }
    }
    grow.forEach(k => { flame[k] = 1; });
}

// step 3: remove it
const out = Buffer.from(pixels);
let removed = 0, filled = 0;
for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
        if (!flame[y * width + x]) continue;
        removed++;
        const i = at(x, y);
        out[i] = out[i + 1] = out[i + 2] = out[i + 3] = 0;
        if (y < RIM_ROW) continue;
        let best = -1, bestLuma = 1e9;
        for (let k = 1; k <= FILL_DEPTH && y + k < height; k++) {
            if (flame[(y + k) * width + x] || !opaque(x, y + k)) continue;
            const j = at(x, y + k);
            const luma = 0.299 * pixels[j] + 0.587 * pixels[j + 1] + 0.114 * pixels[j + 2];
            if (luma < bestLuma) {
                bestLuma = luma;
                best = j;
            }
        }
        if (best >= 0) {
            out[i] = pixels[best]; out[i + 1] = pixels[best + 1]; out[i + 2] = pixels[best + 2]; out[i + 3] = 255;
            filled++;
        }
    }
}

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, encodePng({ width, height, pixels: out }));
console.log(`removed ${removed} flame pixels, ${filled} of them filled from the stone below; wrote ${path.relative(root, OUT)}`);
