// EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments").
//
// Which pickaxe is the strongest stand-in for the Flame Cutter? Reads every Server/Item/Items/**/Tool_Pickaxe_*.json from the
// installed game's Assets.zip (unzipped to the folder given as the argument), resolves Parent, and compares each gather type:
// Power (higher is better) and Quality (higher is better; a missing Quality counts as 0). A pickaxe "dominates" another when it is
// at least as good in every gather type of the other. Prints the table and the winner(s).
//
//   unzip -o -q "$APPDATA/Hytale/install/release/package/game/latest/Assets.zip" "Server/Item/Items/*" -d /tmp/vz
//   node tools/assets/experiments/compare_pickaxes.js /tmp/vz
const fs = require('fs');
const path = require('path');

const root = path.join(process.argv[2] || '.', 'Server/Item/Items');
const items = {};
(function walk(dir) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            walk(full);
        } else if (entry.name.endsWith('.json')) {
            try {
                items[entry.name.replace(/\.json$/, '')] = JSON.parse(fs.readFileSync(full, 'utf8'));
            } catch (e) {
                // a file that is not plain JSON is not a pickaxe
            }
        }
    }
})(root);

function specsOf(id, seen = new Set()) {
    const item = items[id];
    if (!item || seen.has(id)) {
        return null;
    }
    seen.add(id);
    if (item.Tool && item.Tool.Specs) {
        return item.Tool.Specs;
    }
    return item.Parent ? specsOf(item.Parent, seen) : null;
}

const pickaxes = Object.keys(items).filter(id => id.startsWith('Tool_Pickaxe_')).sort();
const table = {};
const gatherTypes = new Set();
for (const id of pickaxes) {
    const specs = specsOf(id);
    if (!specs) {
        continue;
    }
    table[id] = {};
    for (const spec of specs) {
        gatherTypes.add(spec.GatherType);
        table[id][spec.GatherType] = { power: spec.Power, quality: spec.Quality === undefined ? 0 : spec.Quality };
    }
}
const ids = Object.keys(table);
console.log('pickaxes:', ids.join(', '));
for (const type of [...gatherTypes].sort()) {
    console.log(type.padEnd(16), ids.map(id => {
        const cell = table[id][type];
        return (id.replace('Tool_Pickaxe_', '') + '=' + (cell ? cell.power + '/q' + cell.quality : '-')).padEnd(18);
    }).join(''));
}

function dominates(a, b) {
    for (const type of Object.keys(table[b])) {
        const x = table[a][type];
        const y = table[b][type];
        if (!x || x.power < y.power || x.quality < y.quality) {
            return false;
        }
    }
    return true;
}
const winners = ids.filter(a => ids.every(b => a === b || dominates(a, b)));
console.log('dominates every other pickaxe in every gather type of that pickaxe:', winners.length ? winners.join(', ') : 'none');
for (const id of ids) {
    const beaten = ids.filter(b => b !== id && !dominates(id, b));
    console.log(id.padEnd(24), beaten.length ? 'does not cover: ' + beaten.map(b => b.replace('Tool_Pickaxe_', '')).join(', ') : 'covers all');
}
