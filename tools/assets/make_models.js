// Builds the three held models of the off-hand tools from vanilla models. Nothing is drawn from scratch: each model
// is a vanilla one with a small edit, so the textures stay the vanilla ones.
//
//   Arrakis_Blood_Extractor  Scarak Temple lantern, wrapped in an L-Attachment node (the off-hand grip point, like
//                            the shield and the torch have) and with the glowing core made ordinary (shadingMode
//                            "fullbright" -> "flat").
//   Arrakis_Dew_Reaper       Adamantite pickaxe with its root renamed R-Attachment -> L-Attachment, so it is gripped by
//                            the left hand when it sits in the Utility section.
//   Arrakis_Compactor        Human Ruins torch without the Flame node (the node is only the anchor the flame particle
//                            was attached to; removing it leaves the bare torch).
//
//   node tools/assets/make_models.js <folder holding the vanilla Common/ folder>
// Extract the needed vanilla files first, for example
//   tar -xf Assets.zip -C <folder> Common/Blocks/Decorative_Sets Common/Items/Tools/Pickaxe
const fs = require('fs');
const path = require('path');

const vanilla = process.argv[2];
if (!vanilla) {
    console.error('usage: node make_models.js <folder holding the vanilla Common/ folder>');
    process.exit(1);
}
const outDir = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'Common', 'Items', 'Equipment_of_Arrakis');
fs.mkdirSync(outDir, { recursive: true });

function load(rel) {
    return JSON.parse(fs.readFileSync(path.join(vanilla, 'Common', rel), 'utf8'));
}

function save(name, model) {
    fs.writeFileSync(path.join(outDir, name + '.blockymodel'), JSON.stringify(model, null, 2) + '\n');
    console.log('wrote', name);
}

function walk(node, visit) {
    visit(node);
    (node.children || []).forEach(c => walk(c, visit));
}

// ---- Blood Extractor
{
    const model = load('Blocks/Decorative_Sets/Scarak_Temple/Lantern.blockymodel');
    let glow = 0;
    model.nodes.forEach(n => walk(n, node => {
        if (node.shape && node.shape.shadingMode === 'fullbright') {
            node.shape.shadingMode = 'flat';
            glow++;
        }
    }));
    const torch = load('Blocks/Decorative_Sets/Human_Ruins/Torch.blockymodel');
    const grip = JSON.parse(JSON.stringify(torch.nodes[0]));
    grip.id = '900';
    grip.children = model.nodes;
    // The lantern stands on its base at the origin; this lowers it so the grip is about a third of the way up.
    grip.position = { x: 0, y: -4, z: 0 };
    model.nodes = [grip];
    save('Arrakis_Blood_Extractor', model);
    console.log('  glowing parts made ordinary:', glow);
}

// ---- Dew Reaper
{
    const model = load('Items/Tools/Pickaxe/Adamantite.blockymodel');
    if (model.nodes[0].name !== 'R-Attachment') {
        throw new Error('unexpected pickaxe root ' + model.nodes[0].name);
    }
    model.nodes[0].name = 'L-Attachment';
    save('Arrakis_Dew_Reaper', model);
}

// ---- Compactor
{
    const model = load('Blocks/Decorative_Sets/Human_Ruins/Torch.blockymodel');
    let removed = 0;
    model.nodes.forEach(n => walk(n, node => {
        const before = (node.children || []).length;
        node.children = (node.children || []).filter(c => c.name !== 'Flame');
        removed += before - node.children.length;
    }));
    save('Arrakis_Compactor', model);
    console.log('  flame nodes removed:', removed);
}
