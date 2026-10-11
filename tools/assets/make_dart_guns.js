// Builds the data of the two spring guns, the Maula Pistol (Arrakis_Maula_Pistol, from the vanilla Weapon_Handgun) and the
// Dartgun (Arrakis_Dartgun, from the vanilla Weapon_Assault_Rifle). Nothing vanilla is edited or copied except where a sound event
// has to be (its layers cannot be inherited); models, textures, icons, animations, particles and clips are named by path.
//
//   node tools/assets/make_dart_guns.js [path of the game's Assets.zip]
//
// Every number you are likely to tune is in the block below. What it writes (all under src/main/resources/Server/):
//   Item/Items/Equipment_of_Arrakis/            the two items (Parent = the vanilla item)
//   Item/RootInteractions/Equipment_of_Arrakis/ the two Primary roots: RequireNewClick (semi-auto) and the Shoot cooldown
//   Item/Interactions/Equipment_of_Arrakis/     per gun: the ammo gate (Failed -> the vanilla Gun_Shoot_Fail dry click) and the shot
//   Projectiles/Equipment_of_Arrakis/           per gun: the vanilla bullet with Damage and speed changed
//   Particles/Equipment_of_Arrakis_Darts/       Arrakis_Dart_Shot: the vanilla Rifle_Smoke spawner only
//   Audio/SoundEvents/Equipment_of_Arrakis/     per gun: the vanilla fire event (world and _Local) with the Transient layer quieter
// The vanilla sound events and bullets are read out of the zip, so a copy stays exactly the vanilla one except for the listed change.
const fs = require('fs');
const path = require('path');
const { readZipEntry, assetsZip } = require('./trim_ogg');

// ------------------------------------------------------------------ tunables
const COOLDOWN = 0.33;          // seconds between shots on the shared "Shoot" cooldown (a cap on clicking, not an auto-repeat)
const SPEED = 100;              // MuzzleVelocity and TerminalVelocity of both darts, blocks per second
const DAMAGE = 1;               // damage of one dart (the projectile's Damage is a whole number)
const AMMO_PER_SHOT = 1;        // Arrakis_Ammo taken by each shot, by the gate
const TRANSIENT_DB = -6;        // added to the volume (dB) of the first layer, the Transient crack: -6 dB is half the amplitude
const SMOKE_OFFSET = { X: 0.0, Y: 0.3, Z: -0.08 };   // the vanilla offset of Rifle_Smoke in RifleShooting
// ------------------------------------------------------------------

const res = path.join(__dirname, '..', '..', 'src', 'main', 'resources');
const zip = assetsZip(process.argv[2]);
const vanilla = rel => JSON.parse(readZipEntry(zip, rel).toString('utf8'));
function write(rel, obj) {
    const file = path.join(res, rel);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, JSON.stringify(obj, null, 2) + '\n');
}

const GUNS = [
    {
        key: 'Maula', item: 'Arrakis_Maula_Pistol', name: 'Maula Pistol', parent: 'Weapon_Handgun',
        bullet: 'GunPvP_Handgun_Bullet', fireEvent: 'SFX_Handgun_Fire', camera: 'Handgun_Shoot',
        muzzle: { Z: 0.35, Y: 0.15 },
        description: 'A spring-gun pistol that fires darts. Hold it in your main hand: left click fires one dart (click again for the next, about three a second at most); '
            + 'each shot costs 1 ammo, and a dart does 1 damage out to 40 blocks. Right click bashes. Cannot go in the off-hand slot.',
    },
    {
        key: 'Dartgun', item: 'Arrakis_Dartgun', name: 'Dartgun', parent: 'Weapon_Assault_Rifle',
        bullet: 'GunPvP_Assault_Rifle_Bullet', fireEvent: 'SFX_Rifle_Fire', camera: 'Handgun_Shoot',
        muzzle: { Z: 0.85, Y: 0.15 },
        description: 'A two-handed spring gun that fires darts. Left click fires one dart (click again for the next, about three a second at most); '
            + 'each shot costs 1 ammo, and a dart does 1 damage out to 40 blocks. Right click bashes.',
    },
];

const SERVER = 'Server/';
const items = SERVER + 'Item/Items/Equipment_of_Arrakis/';
const roots = SERVER + 'Item/RootInteractions/Equipment_of_Arrakis/';
const inter = SERVER + 'Item/Interactions/Equipment_of_Arrakis/';
const projs = SERVER + 'Projectiles/Equipment_of_Arrakis/';
const sounds = SERVER + 'Audio/SoundEvents/Equipment_of_Arrakis/';

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
    const id = 'Arrakis_' + gun.key + '_';

    // ---- the item: the vanilla one by Parent, so the model, texture, icon, animations, categories, quality, tags, the Secondary bash
    // and the swing InteractionVars all stay the vanilla ones. Changed: the name, the Primary, and (the pistol) no Compatible.
    const item = {
        $Comment: 'The vanilla ' + gun.parent + ' by Parent: model, texture, icon, animations, categories, quality, tags, the Secondary bash and the '
            + 'swing InteractionVars are the vanilla ones. Changed: the name, the Primary (one dart a click, 1 ammo from Arrakis_Ammo, no inventory item) '
            + (gun.key === 'Maula' ? 'and Utility.Compatible false' : 'and nothing else (the vanilla rifle is already two-handed: no Utility block)') + ' (tools/assets/make_dart_guns.js).',
        Parent: gun.parent,
        TranslationProperties: {
            Name: 'server.items.' + gun.item + '.name',
            Description: 'server.items.' + gun.item + '.description',
        },
        Interactions: {
            Primary: id + 'Primary',
            Secondary: 'Gun_Attack',
        },
    };
    if (gun.key === 'Maula') {
        item.Utility = { Compatible: false };
    }
    write(items + gun.item + '.json', item);

    // ---- the Primary root: one shot a click (RequireNewClick) and at most one every COOLDOWN seconds
    write(roots + id + 'Primary.json', {
        $Comment: 'RequireNewClick: the button has to be released and pressed again for the next dart, so holding it fires once. The Shoot cooldown '
            + 'caps fast clicking at one shot every ' + COOLDOWN + ' s.',
        RequireNewClick: true,
        Cooldown: { Id: 'Shoot', Cooldown: COOLDOWN },
        Interactions: [id + 'Gate'],
        Tags: { Attack: ['Ranged'] },
    });

    // ---- the gate: takes AmmoPerShot from Arrakis_Ammo and lets the shot through, or refuses and runs the vanilla dry click
    write(inter + id + 'Gate.json', {
        $Comment: 'First and only interaction of the Primary root. With ammo it takes AmmoPerShot and runs Next, the shot. With none it ends as '
            + 'Failed and runs the vanilla Gun_Shoot_Fail (the dry click): no projectile, no smoke, no ammo change.',
        Type: 'Arrakis_Dart_Ammo_Gate',
        AmmoPerShot: AMMO_PER_SHOT,
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
            WorldSoundEventId: 'SFX_' + id + 'Fire',
            LocalSoundEventId: 'SFX_' + id + 'Fire_Local',
        },
        ProjectileId: id + 'Dart',
    });

    // ---- the dart: the vanilla bullet, only Damage and the speeds changed (and its range is kept by code, see DartRangeSystem)
    const bullet = vanilla(SERVER + 'Projectiles/Hypixel/Minigames/GunPvP/' + gun.bullet + '.json');
    bullet.Damage = DAMAGE;
    bullet.MuzzleVelocity = SPEED;
    bullet.TerminalVelocity = SPEED;
    bullet.$Comment = 'The vanilla ' + gun.bullet + ' with Damage ' + DAMAGE + ' and MuzzleVelocity / TerminalVelocity ' + SPEED + '. Everything else is '
        + 'the vanilla value. TimeToLive is never read by this server version, so the 40 block range is kept by DartRangeSystem.';
    write(projs + id + 'Dart.json', bullet);

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
        write(sounds + 'SFX_' + id + 'Fire' + suffix + '.json', event);
    }
}
console.log('Dart gun assets written');
