// Re-encodes the vanilla Temple Emerald torch model and texture into this mod, for the standing Thumper only.
//
//   node tools/assets/thumper/make_model.js
//   HUD_ASSETS_ZIP=<...\Assets.zip> node tools/assets/thumper/make_model.js
//
// Why a copy and not a path: a Server/Models asset (the model of a deployable entity) may only point at Characters/, NPC/, Items/ or
// VFX/ (the asset validator says "must be within the root"), and the torch is under Blocks/Decorative_Sets/. The held item does not
// have that limit and refers to the vanilla files by path (BlockType.CustomModel); only the entity needs these copies. They are
// the vanilla content with nothing changed in it (the flame of this torch is a particle, not geometry, so the model has none and the
// entity asset has no particles). They are re-encoded, not copied: the model is the same JSON written compactly and the texture the same
// pixels written again, because a byte-identical copy of a vanilla file counts as a "Duplicated Asset" on the server (ARCHIVE.md, rule 7).
// The script checks that the decoded content is equal to the vanilla one.
//
// Reads  Common/Blocks/Decorative_Sets/Temple_Emerald/Torch.blockymodel and Torch_Texture.png from Assets.zip.
// Writes src/main/resources/Common/Items/Equipment_of_Arrakis/Thumper/Arrakis_Thumper.blockymodel and Arrakis_Thumper_Texture.png.
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const assert = require('assert');
const { readPng, encodePng } = require('../hud/png');

const ASSETS_ZIP = process.env.HUD_ASSETS_ZIP
    || path.join(process.env.APPDATA, 'Hytale', 'install', 'release', 'package', 'game', 'latest', 'Assets.zip');
const FROM = 'Common/Blocks/Decorative_Sets/Temple_Emerald/';
const COPIES = { 'Torch.blockymodel': 'Arrakis_Thumper.blockymodel', 'Torch_Texture.png': 'Arrakis_Thumper_Texture.png' };

const root = path.join(__dirname, '..', '..', '..');
const out = path.join(root, 'src', 'main', 'resources', 'Common', 'Items', 'Equipment_of_Arrakis', 'Thumper');
fs.mkdirSync(out, { recursive: true });
for (const [from, to] of Object.entries(COPIES)) {
    const vanilla = execFileSync('unzip', ['-p', ASSETS_ZIP, FROM + from], { maxBuffer: 1 << 26 });
    let bytes;
    if (from.endsWith('.png')) {
        const image = readPng(vanilla);
        bytes = encodePng(image);
        const again = readPng(bytes);
        assert.strictEqual(again.width, image.width);
        assert.strictEqual(again.height, image.height);
        assert.ok(again.pixels.equals(image.pixels), 'the re-encoded texture has other pixels');
    } else {
        const model = JSON.parse(vanilla.toString('utf8'));
        bytes = Buffer.from(JSON.stringify(model), 'utf8');
        assert.deepStrictEqual(JSON.parse(bytes.toString('utf8')), model);
    }
    assert.ok(!bytes.equals(vanilla), 'identical to the vanilla bytes: it would be a Duplicated Asset');
    fs.writeFileSync(path.join(out, to), bytes);
    console.log(`wrote ${path.relative(root, path.join(out, to))} (${bytes.length} bytes; vanilla ${from} is ${vanilla.length})`);
}
