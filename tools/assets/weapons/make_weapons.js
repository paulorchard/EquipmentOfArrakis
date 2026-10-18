// Builds the data of the weapons module: the eight melee weapons (swords and daggers in four houses), the Maula Pistol and the
// Dartgun (ported from v1). Nothing vanilla is edited or copied except where a sound event has to be (its layers cannot be
// inherited); models, textures, icons, animations, particles and clips are named by path.
//
//   node tools/assets/weapons/make_weapons.js [path of the game's Assets.zip]
//
// What it writes (all under src/main/resources/Server/):
//   Item/Items/Equipment_of_Arrakis/Weapons/            the ten items
//   Item/RootInteractions/Equipment_of_Arrakis/Weapons/ the two gun Primary roots (RequireNewClick, the Shoot cooldown)
//   Item/Interactions/Equipment_of_Arrakis/Weapons/     per gun: the cost gate (cost 0 for now) and the shot
//   Projectiles/Equipment_of_Arrakis/Weapons/           per gun: the vanilla bullet with Damage and speed changed
//   Particles/Equipment_of_Arrakis_Darts/               Arrakis_Dart_Shot: the vanilla Rifle_Smoke spawner only
//   Audio/SoundEvents/Equipment_of_Arrakis/Weapons/     per gun: the vanilla fire event (world and _Local) with the Transient quieter
//
// The melee weapons are children (Parent) of the vanilla Crude sword / Crude daggers: they inherit the whole InteractionVars (every
// damage number, DamageEffects, swing effects and sounds), ItemSoundSetId, the Weapon block and the appearance conditions. Overridden:
// the name, the look (model, texture, icon, quality of the house's own vanilla tier), no recipe, no durability, the creative tab,
// MaxStack and the Interactions map (the template's Primary / Secondary / Ability1, plus Ability4 = the Holtzman toggle).
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

// ------------------------------------------------------------------ tunables
const COOLDOWN = 0.33;          // seconds between shots on the shared "Shoot" cooldown (a cap on clicking, not an auto-repeat)
const SPEED = 100;              // MuzzleVelocity and TerminalVelocity of both darts, blocks per second
const DAMAGE = 1;               // damage of one dart (the projectile's Damage is a whole number)
const COST_PER_SHOT = 0;        // what the gate asks for each shot: nothing for now (see GunCost.java)
const TRANSIENT_DB = -6;        // added to the volume (dB) of the first layer, the Transient crack: -6 dB is half the amplitude
const SMOKE_OFFSET = { X: 0.0, Y: 0.3, Z: -0.08 };   // the vanilla offset of Rifle_Smoke in RifleShooting
const VANILLA_TAB = 'Items.Weapons';
const TAB = 'IslandCraft.Weapons';
const ABILITY4 = 'Arrakis_Root_Toggle_Holtzman';
// ------------------------------------------------------------------

const res = path.join(__dirname, '..', '..', '..', 'src', 'main', 'resources');
const zip = process.argv[2] || path.join(process.env.APPDATA, 'Hytale', 'install', 'release', 'package', 'game', 'latest', 'Assets.zip');
const vanilla = rel => JSON.parse(execFileSync('unzip', ['-p', zip, rel], { maxBuffer: 1 << 26 }).toString('utf8'));
function write(rel, obj) {
    const file = path.join(res, rel);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, JSON.stringify(obj, null, 2) + '\n');
}

const SERVER = 'Server/';
const items = SERVER + 'Item/Items/Equipment_of_Arrakis/Weapons/';
const roots = SERVER + 'Item/RootInteractions/Equipment_of_Arrakis/Weapons/';
const inter = SERVER + 'Item/Interactions/Equipment_of_Arrakis/Weapons/';
const projs = SERVER + 'Projectiles/Equipment_of_Arrakis/Weapons/';
const sounds = SERVER + 'Audio/SoundEvents/Equipment_of_Arrakis/Weapons/';

// ---------------------------------------------------------------- melee
const HOUSES = [
    { house: 'Crude', tier: 'Crude' },
    { house: 'Atreides', tier: 'Thorium' },
    { house: 'Choam', tier: 'Cobalt' },
    { house: 'Harkonnen', tier: 'Adamantite' },
];
const KINDS = [
    {
        kind: 'Sword', noun: 'Sword', template: 'Template_Weapon_Sword',
        description: 'A sword of house %H. It looks like the %T sword and fights like the Crude one: the same swings, the same damage, the same sounds. Right click guards. It does not wear out. H toggles the Holtzman shield.',
    },
    {
        kind: 'Daggers', noun: 'Daggers', template: 'Template_Weapon_Daggers',
        description: 'A pair of daggers of house %H. They look like the %T daggers and fight like the Crude ones: the same swings, the same damage, the same sounds. Right click guards. They do not wear out. H toggles the Holtzman shield.',
    },
];
const lang = [];
for (const k of KINDS) {
    const template = vanilla(SERVER + 'Item/Items/Weapon/' + k.kind + '/' + k.template + '.json');
    for (const h of HOUSES) {
        const tier = vanilla(SERVER + 'Item/Items/Weapon/' + k.kind + '/Weapon_' + k.kind + '_' + h.tier + '.json');
        const id = 'Arrakis_Weapon_' + k.kind + '_' + h.house;
        const interactions = {};
        for (const slot of ['Primary', 'Secondary', 'Ability1']) {
            interactions[slot] = template.Interactions[slot];
        }
        interactions.Ability4 = ABILITY4;
        write(items + id + '.json', {
            $Comment: 'Looks like the vanilla ' + h.tier + ' ' + k.kind.toLowerCase() + ' (model, texture, icon, quality by path), plays like the Crude one: Parent is Weapon_'
                + k.kind + '_Crude, so the whole InteractionVars (damage numbers, DamageEffects, swing effects and sounds) and ItemSoundSetId are the Crude ones. No recipe, '
                + 'no durability. Interactions are the template\'s Primary, Secondary and Ability1 plus Ability4, the Holtzman toggle (tools/assets/weapons/make_weapons.js).',
            Parent: 'Weapon_' + k.kind + '_Crude',
            TranslationProperties: {
                Name: 'server.items.' + id + '.name',
                Description: 'server.items.' + id + '.description',
            },
            Model: tier.Model,
            Texture: tier.Texture,
            Icon: tier.Icon,
            Quality: tier.Quality,
            Recipe: null,
            MaxDurability: 0,
            DurabilityLossOnHit: 0,
            MaxStack: 1,
            Categories: [VANILLA_TAB, TAB],
            Interactions: interactions,
        });
        lang.push([id, 'Arrakis ' + k.noun + ' (' + h.house + ')', k.description.replace('%H', h.house).replace('%T', h.tier)]);
        console.log(id + ': ' + tier.Model + ', ' + tier.Quality);
    }
}

// ---------------------------------------------------------------- guns
const GUNS = [
    {
        key: 'Maula', item: 'Arrakis_Weapon_Maula_Pistol', name: 'Maula Pistol', parent: 'Weapon_Handgun',
        bullet: 'GunPvP_Handgun_Bullet', fireEvent: 'SFX_Handgun_Fire', camera: 'Handgun_Shoot',
        muzzle: { Z: 0.35, Y: 0.15 },
        description: 'A spring-gun pistol that fires darts. Hold it in your main hand: left click fires one dart (click again for the next, about three a second at most); a dart does 1 damage out to 40 blocks. Right click bashes. H toggles the Holtzman shield. Cannot go in the off-hand slot.',
    },
    {
        key: 'Dartgun', item: 'Arrakis_Weapon_Dartgun', name: 'Dartgun', parent: 'Weapon_Assault_Rifle',
        bullet: 'GunPvP_Assault_Rifle_Bullet', fireEvent: 'SFX_Rifle_Fire', camera: 'Handgun_Shoot',
        muzzle: { Z: 0.85, Y: 0.15 },
        description: 'A two-handed spring gun that fires darts. Left click fires one dart (click again for the next, about three a second at most); a dart does 1 damage out to 40 blocks. Right click bashes. H toggles the Holtzman shield.',
    },
];

// ---- the smoke: the vanilla spawner by id, at the vanilla offset, and nothing else (no flash, no glow, no casing)
write(SERVER + 'Particles/Equipment_of_Arrakis_Darts/Arrakis_Dart_Shot.particlesystem', {
    Spawners: [{
        SpawnerId: 'Rifle_Smoke',
        PositionOffset: SMOKE_OFFSET,
        RotationOffset: { Yaw: 0, Pitch: 0, Roll: 0 },
    }],
    IsImportant: true,
    LifeSpan: 1.0,
});

for (const gun of GUNS) {
    const id = 'Arrakis_Weapon_' + gun.key + '_';       // roots and interactions
    const asset = 'Arrakis_' + gun.key + '_';           // projectile and sound ids keep the v1 form

    // ---- the item: the vanilla one by Parent, so the model, texture, icon, animations, quality, tags, the Secondary bash and the swing
    // InteractionVars stay the vanilla ones. Changed: the name, the Primary, Ability4, the tab, no recipe, and (the pistol) no Compatible.
    const item = {
        $Comment: 'The vanilla ' + gun.parent + ' by Parent: model, texture, icon, animations, quality, tags, the Secondary bash and the swing InteractionVars are the vanilla ones. '
            + 'Changed: the name, the Primary (one dart a click, free), Ability4 (the Holtzman toggle), the creative tab, no recipe, no durability'
            + (gun.key === 'Maula' ? ' and Utility.Compatible false' : ' (the vanilla rifle is already two-handed: no Utility block)') + ' (tools/assets/weapons/make_weapons.js).',
        Parent: gun.parent,
        Icon: vanilla(SERVER + "Item/Items/Hypixel/Minigames/GunPvP/" + gun.parent + ".json").Icon,
        TranslationProperties: {
            Name: 'server.items.' + gun.item + '.name',
            Description: 'server.items.' + gun.item + '.description',
        },
        Categories: [VANILLA_TAB, TAB],
        Recipe: null,
        MaxDurability: 0,
        MaxStack: 1,
        Interactions: {
            Primary: id + 'Primary',
            Secondary: 'Gun_Attack',
            Ability4: ABILITY4,
        },
    };
    if (gun.key === 'Maula') {
        item.Utility = { Compatible: false };
    }
    write(items + gun.item + '.json', item);
    lang.push([gun.item, gun.name, gun.description]);

    // ---- the Primary root: one shot a click (RequireNewClick) and at most one every COOLDOWN seconds
    write(roots + id + 'Primary.json', {
        $Comment: 'RequireNewClick: the button has to be released and pressed again for the next dart, so holding it fires once. The Shoot cooldown '
            + 'caps fast clicking at one shot every ' + COOLDOWN + ' s.',
        RequireNewClick: true,
        Cooldown: { Id: 'Shoot', Cooldown: COOLDOWN },
        Interactions: [id + 'Gate'],
        Tags: { Attack: ['Ranged'] },
    });

    // ---- the gate: the one place a cost (ammo, later) is asked for each shot. Cost 0: it lets every shot through.
    write(inter + id + 'Gate.json', {
        $Comment: 'First and only interaction of the Primary root. It asks GunCost.charge for Cost per shot (0 now: free) and runs Next, the shot. When the cost '
            + 'cannot be paid it ends as Failed and runs the vanilla Gun_Shoot_Fail (the dry click). An ammo system raises Cost here and implements GunCost.charge; nothing else changes.',
        Type: 'Arrakis_Gun_Gate',
        Cost: COST_PER_SHOT,
        Next: id + 'Shoot',
        Failed: 'Gun_Shoot_Fail',
    });

    // ---- the shot: Gun_Shoot with the dart, the smoke instead of RifleShooting, and the gun's own sounds
    write(inter + id + 'Shoot.json', {
        $Comment: 'The vanilla Gun_Shoot (LaunchProjectile, animation Shoot, Handgun_Shoot camera shake) with the dart, Arrakis_Dart_Shot (smoke only) '
            + 'at the placement of the vanilla ' + gun.parent + ', and the gun\'s own fire sounds. RunTime 0 as the vanilla items set it.',
        Type: 'LaunchProjectile',
        RunTime: 0,
        Effects: {
            ItemAnimationId: 'Shoot',
            CameraEffect: gun.camera,
            Particles: [{
                SystemId: 'Arrakis_Dart_Shot',
                PositionOffset: gun.muzzle,
                RotationOffset: { Pitch: 90 },
            }],
            WorldSoundEventId: 'SFX_' + asset + 'Fire',
            LocalSoundEventId: 'SFX_' + asset + 'Fire_Local',
        },
        ProjectileId: asset + 'Dart',
    });

    // ---- the dart: the vanilla bullet, only Damage and the speeds changed (and its range is kept by code, see DartRangeSystem)
    const bullet = vanilla(SERVER + 'Projectiles/Hypixel/Minigames/GunPvP/' + gun.bullet + '.json');
    bullet.Damage = DAMAGE;
    bullet.MuzzleVelocity = SPEED;
    bullet.TerminalVelocity = SPEED;
    bullet.$Comment = 'The vanilla ' + gun.bullet + ' with Damage ' + DAMAGE + ' and MuzzleVelocity / TerminalVelocity ' + SPEED + '. Everything else is '
        + 'the vanilla value. TimeToLive is never read by this server version, so the 40 block range is kept by DartRangeSystem.';
    write(projs + asset + 'Dart.json', bullet);

    // ---- the sounds: the vanilla fire events, the first layer (the Transient) TRANSIENT_DB quieter, nothing else touched
    for (const suffix of ['', '_Local']) {
        const event = vanilla(SERVER + 'Audio/SoundEvents/SFX/Weapons/Guns/' + gun.fireEvent + suffix + '.json');
        const transient = event.Layers[0];
        if (!/Transient/.test(transient.Files[0])) {
            throw new Error(gun.fireEvent + suffix + ': the first layer is not the Transient: ' + transient.Files[0]);
        }
        const before = transient.Volume === undefined ? 0 : transient.Volume;
        transient.Volume = before + TRANSIENT_DB;
        console.log(gun.fireEvent + suffix + ' Transient ' + before + ' -> ' + transient.Volume + ' dB');
        write(sounds + 'SFX_' + asset + 'Fire' + suffix + '.json', event);
    }
}

// ---- the lines for server.lang (printed, pasted by hand into the weapons section)
console.log('\n# server.lang lines');
for (const [id, name, description] of lang) {
    console.log('items.' + id + '.name = ' + name);
    console.log('items.' + id + '.description = ' + description);
}
console.log('Weapon assets written');
