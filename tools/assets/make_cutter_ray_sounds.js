// Builds the block impact sound pieces of the Cutter Ray: the first --length seconds (default 0.1) of two vanilla clips, cut
// without re-encoding (the cutter is trim_ogg.js). The two are played together by SFX_Arrakis_Cutter_Ray_Impact.
//
//   node tools/assets/make_cutter_ray_sounds.js [--length 0.1] [path of the game's Assets.zip]
//
// Stone_Hit_03 (mono, 48 kHz) -> Arrakis_Cutter_Ray_Impact_Stone.ogg     Entity_Effect_Burn_05 (mono, 44.1 kHz) -> Arrakis_Cutter_Ray_Impact_Burn.ogg
// The sample count is worked out from each clip's own rate (0.1 s is 4,800 samples at 48 kHz, 4,410 at 44.1 kHz). The cuts start at the
// start of the clip. The vanilla clips are read from the zip and never stored in the repo.
const fs = require('fs');
const path = require('path');
const { trimOgg, readZipEntry, assetsZip } = require('./trim_ogg');

let length = 0.1;
const rest = [];
const args = process.argv.slice(2);
for (let i = 0; i < args.length; i++) {
    if (args[i] === '--length') length = parseFloat(args[++i]);
    else rest.push(args[i]);
}
if (!(length > 0)) {
    console.error('usage: node make_cutter_ray_sounds.js [--length seconds] [Assets.zip]');
    process.exit(1);
}
const OUT = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'Common', 'Sounds', 'Equipment_of_Arrakis');
const zip = assetsZip(rest[0]);
const clips = [
    ['Common/Sounds/Blocks/Stone/Stone_Hit_03.ogg', 'Arrakis_Cutter_Ray_Impact_Stone.ogg'],
    ['Common/Sounds/Effects/Entity_Effect_Burn_05.ogg', 'Arrakis_Cutter_Ray_Impact_Burn.ogg'],
];
fs.mkdirSync(OUT, { recursive: true });
for (const [entry, name] of clips) {
    console.log(`${entry} -> ${name}, ${length} s`);
    const cut = trimOgg(readZipEntry(zip, entry), length);
    fs.writeFileSync(path.join(OUT, name), cut.buffer);
}
