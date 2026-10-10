// Builds the fizzle sound of the Cutter Ray Mk2: the first 0.27 s of the vanilla clip Portal_Zap_02.ogg, cut without re-encoding
// (the cutter is trim_ogg.js).
//
//   node tools/assets/make_fizzle.js [path of the game's Assets.zip]
//
// The default path is %APPDATA%/Hytale/install/release/package/game/latest/Assets.zip. The vanilla clip is read from the zip and never
// stored in the repo; the result is src/main/resources/Common/Sounds/Equipment_of_Arrakis/Arrakis_Cutter_Mk2_Fizzle.ogg.
const fs = require('fs');
const path = require('path');
const { trimOgg, readZipEntry, assetsZip } = require('./trim_ogg');

const ENTRY = 'Common/Sounds/Items/Portal/Textures/Portal_Zap_02.ogg';
const SECONDS = 0.27; // 11,907 samples at 44.1 kHz
const OUT = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'Common', 'Sounds', 'Equipment_of_Arrakis',
    'Arrakis_Cutter_Mk2_Fizzle.ogg');

const cut = trimOgg(readZipEntry(assetsZip(process.argv[2]), ENTRY), SECONDS);
fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, cut.buffer);
console.log(`wrote ${OUT}`);
