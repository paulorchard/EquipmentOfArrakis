// EXPERIMENT: delete with the lab module
// Removes the movement lab from a checkout, the exact steps listed in MODULES.md ("Removing the lab"):
//   node tools/lab/remove_lab.js [repo root]       (default: the repository this script is in)
// 1. deletes the lab package and its tests, 2. deletes every asset folder called Lab, 3. deletes the lines marked
// "EXPERIMENT: remove with the lab module" in the plugin and the config, 4. deletes the language block between "# LAB BEGIN" and
// "# LAB END", 5. deletes tools/lab (this script). What is left to do by hand: the "Removing the lab" and lab notes in MODULES.md,
// and the movement facts in ARCHIVE.md stay (they are project knowledge).
const fs = require('fs');
const path = require('path');

const root = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
const J = 'src/main/java/com/paulorchard/islandcraft/equipmentofarrakis';
const T = 'src/test/java/com/paulorchard/islandcraft/equipmentofarrakis';
const MARK = 'EXPERIMENT: remove with the lab module';

function rm(rel) {
    fs.rmSync(path.join(root, rel), { recursive: true, force: true });
    console.log('deleted ' + rel);
}

rm(J + '/lab');
rm(T + '/lab');

function labFolders(dir, found) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        if (!entry.isDirectory()) continue;
        const full = path.join(dir, entry.name);
        if (entry.name === 'Lab') found.push(full);
        else labFolders(full, found);
    }
    return found;
}
for (const dir of labFolders(path.join(root, 'src/main/resources'), [])) {
    fs.rmSync(dir, { recursive: true, force: true });
    console.log('deleted ' + path.relative(root, dir));
}

for (const file of [J + '/EquipmentOfArrakisPlugin.java', J + '/EquipmentOfArrakisConfig.java']) {
    const full = path.join(root, file);
    let s = fs.readFileSync(full, 'utf8');
    const crlf = s.includes('\r\n');
    const lines = s.replace(/\r\n/g, '\n').split('\n');
    const kept = lines.filter(line => !line.includes(MARK));
    console.log(file + ': removed ' + (lines.length - kept.length) + ' marked lines');
    fs.writeFileSync(full, crlf ? kept.join('\r\n') : kept.join('\n'));
}

const lang = path.join(root, 'src/main/resources/Server/Languages/en-US/server.lang');
let text = fs.readFileSync(lang, 'utf8');
const crlf = text.includes('\r\n');
text = text.replace(/\r\n/g, '\n').replace(/\n*# LAB BEGIN[\s\S]*?# LAB END\n?/, '\n');
fs.writeFileSync(lang, crlf ? text.replace(/\n/g, '\r\n') : text);
console.log('language block removed');

rm('tools/lab');
