// Builds the .ui documents and the few derived images of the hud module from the game's own bar art.
//
//   node tools/assets/hud/make_hud.js
//   HUD_CLIENT=<...\Client\Data\Game\Interface> HUD_ASSETS_ZIP=<...\Assets.zip> node tools/assets/hud/make_hud.js
//
// The four bars are the game's own Health bar (left) and Mana bar (right), laid out like the vanilla Health.ui and Mana.ui, with the
// vanilla ProgressBar element (the server sets Value 0..1; Bar: (Color) tints the fill). The rule of the whole mod family is to use the
// vanilla art BY REFERENCE to its asset path, so that a texture pack that redraws it redraws ours. Where that cannot give the result,
// this script writes a derived image into VanillaDerived/ and the module notes list each one with its reason.
//
// What it reads: the client Interface folder (HUD_CLIENT: InGame/Hud/Health, Mana, Stamina, Hotbar.ui, InGame/Common.ui, the two
// bar layouts) and Assets.zip (Common/Icons/ItemCategories/Misc.png and Natural-Fluid.png).
// What it writes (all under src/main/resources/Common/UI/Custom/Hud/Equipment_of_Arrakis/Hud/):
//   Arrakis_Hud_Reference.ui, Arrakis_Hud_Reference_Creative.ui   texture paths point at the vanilla files
//   Arrakis_Hud_Copy.ui, Arrakis_Hud_Copy_Creative.ui             texture paths point at VanillaDerived/ (works on its own)
//   VanillaDerived/*.png                                          derived images (both routes) and plain copies (Copy route only)
// and tools/assets/hud/vanilla-metrics.json (the numbers it read, which a unit test compares with the ones in HudMetrics.java).
// The output is byte identical on every run.
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { readPng, encodePng } = require('./png');

// ------------------------------------------------------------------ where the vanilla files are
const CLIENT = process.env.HUD_CLIENT
    || path.join(process.env.APPDATA, 'Hytale', 'install', 'release', 'package', 'game', 'latest', 'Client', 'Data', 'Game', 'Interface');
const ASSETS_ZIP = process.env.HUD_ASSETS_ZIP
    || path.join(process.env.APPDATA, 'Hytale', 'install', 'release', 'package', 'game', 'latest', 'Assets.zip');

// ------------------------------------------------------------------ tunables (the Hud config section holds the live ones)
const DEFAULT_ROW_GAP = 26;       // from the bottom of the lower row to the bottom of the upper row (measured from the reference screenshot)
const TINT = { Power: '#57bbaf', Exposure: '#d49c1b', Health: '#ffffff', Water: '#ffffff' };
const ICON_TINT = { Power: '#7fd0ff', Exposure: '#ffd23f', Water: '#4da3ff' };
const NEUTRAL_BODY = 0.88;        // brightness of the median pixel of a neutral fill (the brightest pixels clip to 1.0 so the rim reads lighter)
const ALERT_RED = '#e6004f';      // the vanilla error overlay rim (#bf0046) brightened a fifth so that a flashing fill reads as the same red
// ------------------------------------------------------------------

const root = path.join(__dirname, '..', '..', '..');
const out = path.join(root, 'src', 'main', 'resources', 'Common', 'UI', 'Custom', 'Hud', 'Equipment_of_Arrakis', 'Hud');
const derived = path.join(out, 'VanillaDerived');
const HUD = path.join(CLIENT, 'InGame', 'Hud');

function write(file, data) {
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, data);
}
const text = file => fs.readFileSync(file, 'utf8').replace(/\r/g, '');
const number = (source, pattern, what) => {
    const m = source.match(pattern);
    if (!m) throw new Error('vanilla layout changed: could not find ' + what);
    return Number(m[1]);
};

// ---------------------------------------------------------------- vanilla metrics, read from the .ui files
const common = text(path.join(CLIENT, 'InGame', 'Common.ui'));
const hotbar = text(path.join(HUD, 'Hotbar.ui'));
const healthUi = text(path.join(HUD, 'Health', 'Health.ui'));
const manaUi = text(path.join(HUD, 'Mana', 'Mana.ui'));
const staminaUi = text(path.join(HUD, 'Stamina', 'StaminaPanel.ui'));
const slotSize = number(common, /@DefaultItemSlotSize\s*=\s*(\d+)/, '@DefaultItemSlotSize');
const slotsPerRow = number(common, /@DefaultItemSlotsPerRow\s*=\s*(\d+)/, '@DefaultItemSlotsPerRow');
const gridPadding = number(common, /@DefaultItemGridPadding\s*=\s*(\d+)/, '@DefaultItemGridPadding');
const bottomMargin = number(hotbar, /@BottomMargin\s*=\s*(\d+)/, '@BottomMargin');
const containerMargin = number(hotbar, /@ContainerMargin\s*=\s*(\d+)/, '@ContainerMargin');
const slotSpacing = number(hotbar, /@HotbarSlotSpacingHud\s*=\s*(\d+)/, '@HotbarSlotSpacingHud');
if (!/@HotbarWidthHud\s*=\s*\$InGame\.@DefaultItemSlotSize \* \$InGame\.@DefaultItemSlotsPerRow \+ @HotbarSlotSpacingHud \* \$InGame\.@DefaultItemSlotsPerRow/.test(hotbar)
    || !/@HotbarHeight\s*=\s*\$InGame\.@DefaultItemSlotSize \+ \(2 \* \$InGame\.@DefaultItemGridPadding\)/.test(hotbar)
    || !/@InventoryClosedContainerMargin\s*=\s*@BottomMargin \+ @ContainerMargin/.test(hotbar)
    || !/Bottom: \$Hotbar\.@InventoryClosedContainerMargin \+ \$Hotbar\.@HotbarHeight \+ 6/.test(healthUi)) {
    throw new Error('vanilla layout changed: the hotbar or health formulas are not the ones this script knows');
}
const metrics = {
    slotSize, slotsPerRow, gridPadding, bottomMargin, containerMargin, slotSpacing,
    hotbarWidth: slotSize * slotsPerRow + slotSpacing * slotsPerRow,
    hotbarHeight: slotSize + 2 * gridPadding,
    closedContainerMargin: bottomMargin + containerMargin,
    barWidth: number(healthUi, /Anchor: \(Left: 0, Width: (\d+), Height: 12\)/, 'health bar width'),
    barHeight: 12,
    iconSize: number(healthUi, /Anchor: \(Left: 0, Width: (\d+), Height: \d+, Right: 4, Top: -8\)/, 'health icon'),
    healthIconRight: number(healthUi, /Height: \d+, Right: (\d+), Top: -8/, 'health icon gap'),
    iconTop: number(healthUi, /Right: 4, Top: (-?\d+)\)/, 'health icon top'),
    manaIconLeft: number(manaUi, /Width: 24, Height: 24, Left: (\d+), Top: -8/, 'mana icon gap'),
    barAboveHotbar: number(healthUi, /@HotbarHeight \+ (\d+),/, 'health bottom offset'),
};
metrics.rowBottom = metrics.closedContainerMargin + metrics.hotbarHeight + metrics.barAboveHotbar;
metrics.blockWidth = metrics.iconSize + metrics.healthIconRight + metrics.barWidth;      // icon, gap, bar
metrics.innerGap = metrics.hotbarWidth / 2 - metrics.blockWidth;                          // from the centre to the inner end of a bar
metrics.alert = {
    flipSeconds: number(staminaUi, /@LowAlertFlashTimer\s*=\s*([\d.]+)/, 'LowAlertFlashTimer'),
    outlineOpacityStart: number(staminaUi, /@LowAlertOutlineOpacityStart\s*=\s*([\d.]+)/, 'LowAlertOutlineOpacityStart'),
    animationSpeed: number(staminaUi, /@LowAlertAnimationSpeed\s*=\s*([\d.]+)/, 'LowAlertAnimationSpeed'),
    manaPulseThreshold: number(manaUi, /@ErrorPulseThreshold\s*=\s*([\d.]+)/, 'ErrorPulseThreshold'),
    manaPulseSpeed: number(manaUi, /@ErrorPulseSpeed\s*=\s*([\d.]+)/, 'ErrorPulseSpeed'),
    manaPulseFlips: number(manaUi, /@ErrorPulseFlips\s*=\s*(\d+)/, 'ErrorPulseFlips'),
};
write(path.join(__dirname, 'vanilla-metrics.json'), JSON.stringify(metrics, null, 2) + '\n');

// ---------------------------------------------------------------- the vanilla textures and where they come from
// name (without @2x.png) -> folder under InGame/Hud
const VANILLA = {
    HealthBackground: 'Health', HealthBarFill: 'Health', HealthBarEffect: 'Health', CreativeHealthBarFill: 'Health',
    HealthIcon: 'Health', CreativeHealthIcon: 'Health',
    ManaBackground: 'Mana', ManaFill: 'Mana', ManaEffect: 'Mana', CreativeManaFill: 'Mana',
    ManaBackgroundError: 'Mana', ManaBackgroundErrorOutline: 'Mana',
    StaminaIcon: 'Stamina',
};
const vanillaFile = name => path.join(HUD, VANILLA[name], name + '@2x.png');
function zipEntry(entry) {
    return execFileSync('unzip', ['-p', ASSETS_ZIP, entry], { maxBuffer: 1 << 26 });
}
const ZIP_ICONS = { Misc: 'Common/Icons/ItemCategories/Misc.png', 'Natural-Fluid': 'Common/Icons/ItemCategories/Natural-Fluid.png' };

// ---------------------------------------------------------------- derived images
function luminance(r, g, b) {
    return 0.299 * r + 0.587 * g + 0.114 * b;
}
/** A neutral (grey) fill with the shading of the vanilla one, so Bar: (Color) can give it any colour: out = tint * grey. */
function neutral(image) {
    const values = [];
    for (let i = 0; i < image.width * image.height; i++) {
        if (image.pixels[i * 4 + 3] > 128) values.push(luminance(image.pixels[i * 4], image.pixels[i * 4 + 1], image.pixels[i * 4 + 2]));
    }
    values.sort((a, b) => a - b);
    const median = values[Math.floor(values.length / 2)];
    const px = Buffer.alloc(image.pixels.length);
    for (let i = 0; i < image.width * image.height; i++) {
        const l = luminance(image.pixels[i * 4], image.pixels[i * 4 + 1], image.pixels[i * 4 + 2]);
        const g = Math.round(255 * Math.min(1, (l / median) * NEUTRAL_BODY));
        px[i * 4] = g; px[i * 4 + 1] = g; px[i * 4 + 2] = g; px[i * 4 + 3] = image.pixels[i * 4 + 3];
    }
    return { width: image.width, height: image.height, pixels: px };
}
/** The same shape in white: alpha kept, colour set to white, so Background.Color gives the glyph any colour. */
function silhouette(image) {
    const px = Buffer.from(image.pixels);
    for (let i = 0; i < image.width * image.height; i++) {
        px[i * 4] = 255; px[i * 4 + 1] = 255; px[i * 4 + 2] = 255;
    }
    return { width: image.width, height: image.height, pixels: px };
}
function mirror(image) {
    const px = Buffer.alloc(image.pixels.length);
    for (let y = 0; y < image.height; y++) {
        for (let x = 0; x < image.width; x++) {
            const from = (y * image.width + (image.width - 1 - x)) * 4;
            image.pixels.copy(px, (y * image.width + x) * 4, from, from + 4);
        }
    }
    return { width: image.width, height: image.height, pixels: px };
}
function multiplyAverage(image, hex) {
    const t = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16));
    let n = 0;
    const sum = [0, 0, 0];
    for (let i = 0; i < image.width * image.height; i++) {
        if (image.pixels[i * 4 + 3] < 200) continue;
        n++;
        for (let c = 0; c < 3; c++) sum[c] += (image.pixels[i * 4 + c] * t[c]) / 255;
    }
    return '#' + sum.map(v => Math.round(v / n).toString(16).padStart(2, '0')).join('');
}

fs.rmSync(derived, { recursive: true, force: true });
const report = [];   // [file, kind, reason]
function derive(name, image, reason) {
    write(path.join(derived, name + '@2x.png'), encodePng(image));
    report.push([name + '@2x.png', 'derived (both routes)', reason]);
}
const src = name => readPng(vanillaFile(name));
derive('StaminaIconWhite', silhouette(src('StaminaIcon')), 'white silhouette of the vanilla StaminaIcon (alpha kept, colour white): the vanilla bolt is orange, a tint would multiply to a dark colour');
derive('NeutralHealthBarFill', neutral(src('HealthBarFill')), 'grey copy of the vanilla HealthBarFill with its shading, so the Power bar can be teal: tinting the pink fill teal gives near black');
derive('NeutralCreativeHealthBarFill', neutral(src('CreativeHealthBarFill')), 'grey copy of CreativeHealthBarFill for the Power bar in creative mode');
derive('NeutralManaFill', neutral(src('ManaFill')), 'grey copy of the vanilla ManaFill with its shading, so the Heat Exposure bar can be amber and red: tinting the blue fill amber gives a dull green');
derive('NeutralCreativeManaFill', neutral(src('CreativeManaFill')), 'grey copy of CreativeManaFill for the Heat Exposure bar in creative mode');
derive('ErrorLeft', mirror(src('ManaBackgroundError')), 'mirror image of ManaBackgroundError: the vanilla overlay is slanted at the right end, the Health-shaped bars are slanted at the left');
derive('ErrorOutlineLeft', mirror(src('ManaBackgroundErrorOutline')), 'mirror image of ManaBackgroundErrorOutline, for the same reason');
// Plain copies, used only by the Copy documents (so that route works on its own).
for (const name of Object.keys(VANILLA)) {
    if (name === 'StaminaIcon') continue;
    write(path.join(derived, name + '@2x.png'), fs.readFileSync(vanillaFile(name)));
    report.push([name + '@2x.png', 'plain copy (Copy route only)', 'byte copy of the client file, so the Copy route does not depend on the client interface path']);
}
// The two Assets.zip icons are re-encoded (the same pixels, written by our own PNG writer), not byte copied: a byte copy has the same hash as
// the vanilla file and the server counts it as a duplicated asset (the join failure of the first build raised that count from 725 to 727).
for (const [name, entry] of Object.entries(ZIP_ICONS)) {
    write(path.join(derived, name + '.png'), encodePng(readPng(zipEntry(entry))));
    report.push([name + '.png', 'copy, re-encoded (Copy route only)', 'the pixels of ' + entry + ' from Assets.zip, re-encoded so the pack has no byte-identical duplicate of a vanilla asset']);
}

// ---------------------------------------------------------------- the documents
// Texture path of a vanilla file as seen from our .ui (Common/UI/Custom/Hud/Equipment_of_Arrakis/Hud/): the Custom tree and the client
// Interface folder are one virtual tree (an Assets.zip document, Hud/ReturnToHubButton.ui, names ../InGame/Pages/... from its own folder),
// so three folders up is the Interface root. Assets.zip art is reached from the asset root, five folders up.
const REF_VANILLA = name => `../../../InGame/Hud/${VANILLA[name]}/${name}.png`;
const REF_ZIP = name => `../../../../../Icons/ItemCategories/${name}.png`;
const COPY = name => `VanillaDerived/${name}.png`;
const DERIVED = name => `VanillaDerived/${name}.png`;

const NEUTRAL_OF = { Health: ['NeutralHealthBarFill', 'NeutralCreativeHealthBarFill'], Mana: ['NeutralManaFill', 'NeutralCreativeManaFill'] };
const BARS = [
    { id: 'Power', left: true, top: true, neutralFill: 'NeutralHealthBarFill', neutralCreativeFill: 'NeutralCreativeHealthBarFill', shape: 'Health', icon: { derived: 'StaminaIconWhite' } },
    { id: 'Health', left: true, top: false, fill: 'HealthBarFill', creativeFill: 'CreativeHealthBarFill', shape: 'Health', icon: { vanilla: 'HealthIcon', creative: 'CreativeHealthIcon' } },
    { id: 'Exposure', left: false, top: true, neutralFill: 'NeutralManaFill', neutralCreativeFill: 'NeutralCreativeManaFill', shape: 'Mana', icon: { zip: 'Misc' } },
    { id: 'Water', left: false, top: false, fill: 'ManaFill', creativeFill: 'CreativeManaFill', shape: 'Mana', icon: { zip: 'Natural-Fluid' } },
];

function paths(route, creative, bar) {
    const v = route === 'Reference' ? REF_VANILLA : COPY;
    const z = route === 'Reference' ? REF_ZIP : COPY;
    const result = {
        background: v(bar.shape === 'Health' ? 'HealthBackground' : 'ManaBackground'),
        effect: v(bar.shape === 'Health' ? 'HealthBarEffect' : 'ManaEffect'),
        error: bar.shape === 'Health' ? DERIVED('ErrorLeft') : v('ManaBackgroundError'),
        outline: bar.shape === 'Health' ? DERIVED('ErrorOutlineLeft') : v('ManaBackgroundErrorOutline'),
    };
    result.fill = bar.fill ? v(creative ? bar.creativeFill : bar.fill) : DERIVED(creative ? bar.neutralCreativeFill : bar.neutralFill);
    if (bar.icon.derived) result.icon = DERIVED(bar.icon.derived);
    else if (bar.icon.vanilla) result.icon = v(creative ? bar.icon.creative : bar.icon.vanilla);
    else result.icon = z(bar.icon.zip);
    return result;
}

function document(route, creative) {
    const m = metrics;
    let ui = `// The four-bar HUD of Equipment of Arrakis, generated by tools/assets/hud/make_hud.js: route ${route}, ${creative ? 'creative' : 'adventure'} art.\n`
        + '// Laid out like the vanilla Health.ui (left bars) and Mana.ui (right bars) with the vanilla ProgressBar: the server sets Value (0..1) and\n'
        + '// Bar.Color; the numbers below are the vanilla ones (see vanilla-metrics.json) and HudView sets them again from the Hud config section.\n'
        + `// ${route === 'Reference' ? 'Texture paths point at the vanilla files, so a texture pack that redraws them redraws these bars.' : 'Texture paths point at VanillaDerived/ (copies), so this document works on its own.'}\n\n`;
    for (const bar of BARS) {
        const p = paths(route, creative, bar);
        const n = bar.id;
        const bottom = m.rowBottom + (bar.top ? DEFAULT_ROW_GAP : 0);
        const iconColor = ICON_TINT[n];
        const icon = iconColor ? `Background: (TexturePath: "${p.icon}", Color: ${iconColor});` : `Background: "${p.icon}";`;
        const iconGroup = bar.left
            ? `        Group #${n}Icon {\n          Anchor: (Left: 0, Width: ${m.iconSize}, Height: ${m.iconSize}, Right: ${m.healthIconRight}, Top: ${m.iconTop});\n          ${icon}\n        }\n`
            : `        Group #${n}Icon {\n          Anchor: (Width: ${m.iconSize}, Height: ${m.iconSize}, Left: ${m.manaIconLeft}, Top: ${m.iconTop});\n          ${icon}\n        }\n`;
        const barGroup = `        Group #${n}Bar {\n`
            + `          Anchor: (${bar.left ? 'Left: 0, ' : ''}Width: ${m.barWidth}, Height: ${m.barHeight});\n`
            + `          Background: "${p.background}";\n\n`
            + `          Group #${n}Error {\n            Visible: false;\n            Background: PatchStyle(TexturePath: "${p.error}", Color: #ffffff);\n          }\n\n`
            + `          ProgressBar #${n}Fill {\n            Anchor: (Width: ${m.barWidth}, Height: ${m.barHeight});\n            BarTexturePath: "${p.fill}";\n            Bar: (Color: ${TINT[n]});\n            Value: 0;\n          }\n\n`
            + `          Group #${n}AlertGroup {
            Visible: false;

            ProgressBar #${n}Alert {
              Anchor: (Width: ${m.barWidth}, Height: ${m.barHeight});
              BarTexturePath: "${DERIVED(NEUTRAL_OF[bar.shape][creative ? 1 : 0])}";
              Bar: (Color: ${ALERT_RED});
              Value: 0;
            }
          }

`
            + `          ProgressBar #${n}Effect {\n            Anchor: (Width: ${m.barWidth}, Height: ${m.barHeight});\n            BarTexturePath: "${p.effect}";\n            Value: 0;\n          }\n\n`
            + `          Group #${n}Outline {\n            Visible: false;\n            Background: PatchStyle(TexturePath: "${p.outline}", Color: #ffffff);\n          }\n\n`
            + `          Group #${n}OutlineDim {\n            Visible: false;\n            Background: PatchStyle(TexturePath: "${p.outline}", Color: #ffffff(${m.alert.outlineOpacityStart}));\n          }\n        }\n`;
        const block = `      Group #${n}Block {\n        Anchor: (Width: ${m.blockWidth});\n        LayoutMode: Left;\n\n`
            + (bar.left ? iconGroup + '\n' + barGroup : barGroup + '\n' + iconGroup) + '      }\n';
        const spacer = `    Group #${n}Spacer {\n      FlexWeight: 1;\n    }\n`;
        const pad = `    Group #${n}Pad {\n      Anchor: (Width: ${m.innerGap});\n    }\n`;
        const half = `  Group #${n}Half {\n    FlexWeight: 1;\n    LayoutMode: Left;\n\n`
            + (bar.left ? spacer + '\n' + block + '\n' + pad : pad + '\n' + block + '\n' + spacer) + '  }\n';
        const other = `  Group #${n}OtherHalf {\n    FlexWeight: 1;\n  }\n`;
        ui += `Group #${n}Row {\n  Anchor: (Left: 0, Right: 0, Bottom: ${bottom}, Height: ${m.barHeight});\n  LayoutMode: Left;\n\n`
            + (bar.left ? half + '\n' + other : other + '\n' + half) + '}\n\n';
    }
    return ui.trimEnd() + '\n';
}
for (const creative of [false, true]) {
    write(path.join(out, `Arrakis_Hud_Copy${creative ? '_Creative' : ''}.ui`), document('Copy', creative));
}
for (const old of ['Arrakis_Hud.ui', 'Arrakis_Hud_Reference.ui', 'Arrakis_Hud_Reference_Creative.ui']) {
    fs.rmSync(path.join(out, old), { force: true });
}

// ---------------------------------------------------------------- documents that are NOT in the pack
// The first build shipped the Reference documents in the pack and the client refused to join ("Could not find document ...Arrakis_Hud_Reference.ui").
// They are written here instead, and a build flag adds one variant to the jar for a test:  gradlew jar deployMod -PhudExtra=<variant>
//   reference   the two Reference documents, texture paths outside our pack (the first build)
//   refinpack   documents of the same names whose texture paths all stay inside our pack (tests "extra documents" without "paths outside")
//   dupicons    byte copies of the two Assets.zip icons (tests the duplicated-asset hypothesis alone)
//   probe       one small document with one outside path, appended only by /arrakis hud probe
const variants = path.join(__dirname, 'variants');
fs.rmSync(variants, { recursive: true, force: true });
const inPack = variant => path.join(variants, variant, 'resources', 'Common', 'UI', 'Custom', 'Hud', 'Equipment_of_Arrakis', 'Hud');
for (const creative of [false, true]) {
    const name = `Arrakis_Hud_Reference${creative ? '_Creative' : ''}.ui`;
    write(path.join(inPack('reference'), name), document('Reference', creative));
    write(path.join(inPack('refinpack'), name), document('Copy', creative).replace('route Copy', 'route Reference (paths inside the pack)'));
}
for (const [name, entry] of Object.entries(ZIP_ICONS)) {
    write(path.join(inPack('dupicons'), 'VanillaDerived', name + '-Dup.png'), zipEntry(entry));
}
const PROBE = [
    '// Throw-away probe, built only with -PhudExtra=probe: one outside texture path (three folders up is the client Interface root, if that guess is right).',
    'Group #ProbeRow {',
    '  Anchor: (Left: 0, Right: 0, Bottom: 200, Height: 12);',
    '  LayoutMode: Left;',
    '',
    '  Group #ProbeBar {',
    '    Anchor: (Width: 318, Height: 12);',
    '    Background: "../../../InGame/Hud/Health/HealthBackground.png";',
    '  }',
    '}',
    '',
].join('\n');
write(path.join(inPack('probe'), 'Arrakis_Hud_Probe.ui'), PROBE);

for (const old of fs.readdirSync(out)) {
    if (/^Arrakis_Hud_(Bar|Icon)_.*@2x\.png$/.test(old)) fs.rmSync(path.join(out, old));   // the first, self-drawn art
}

// ---------------------------------------------------------------- report
console.log('vanilla metrics:', JSON.stringify(metrics));
console.log('\nVanillaDerived/ (' + report.length + ' files):');
for (const [file, kind, reason] of report) console.log('  ' + file.padEnd(40) + kind.padEnd(30) + reason);
console.log('\nTint check (average colour of the fill after the multiply; judge these):');
const check = (label, image, hex) => console.log('  ' + label.padEnd(52) + hex.padEnd(9) + '-> ' + multiplyAverage(image, hex));
check('Power: neutral health fill x teal', readPng(path.join(derived, 'NeutralHealthBarFill@2x.png')), TINT.Power);
check('Power: neutral creative health fill x teal', readPng(path.join(derived, 'NeutralCreativeHealthBarFill@2x.png')), TINT.Power);
check('Heat Exposure: neutral mana fill x amber', readPng(path.join(derived, 'NeutralManaFill@2x.png')), TINT.Exposure);
check('Heat Exposure: neutral creative mana fill x amber', readPng(path.join(derived, 'NeutralCreativeManaFill@2x.png')), TINT.Exposure);
check('Heat Exposure: neutral mana fill x red', readPng(path.join(derived, 'NeutralManaFill@2x.png')), '#e0201a');
check('for judging a plain tint: vanilla health fill x teal', src('HealthBarFill'), TINT.Power);
check('for judging a plain tint: vanilla mana fill x amber', src('ManaFill'), TINT.Exposure);
check('alert layer: neutral health fill x alert red', readPng(path.join(derived, 'NeutralHealthBarFill@2x.png')), ALERT_RED);
check('alert layer: neutral mana fill x alert red', readPng(path.join(derived, 'NeutralManaFill@2x.png')), ALERT_RED);
check('why not a tint of the vanilla fill: vanilla mana fill x alert', src('ManaFill'), ALERT_RED);
console.log('\nDocuments written to ' + out);
