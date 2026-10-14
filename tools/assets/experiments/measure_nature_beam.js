// EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments").
//
// How far does the vanilla NatureBeam (the Stoneskin wand's laser, in Server/Particles/_Test/NatureRnD) reach? Reads its
// particle system and spawners from an unzipped Assets.zip and works out, for each spawner, where its particles can be along the
// beam: the spawn range (the system's PositionOffset plus the spawner's EmitOffset) and how far they drift in their life (speed
// times life, along the default direction, spread by yaw and pitch).
//
// Frame: the Stoneskin cast turns the system by Pitch 180, which flips the local Z. So a particle at local z is at
// forward = ZOFF - z blocks from the system's attachment point, and the default particle direction (local -Z) runs forward.
//
//   unzip -o -q "$APPDATA/Hytale/install/release/package/game/latest/Assets.zip" "Server/Particles/_Test/NatureRnD/*" -d <dir>
//   node tools/assets/experiments/measure_nature_beam.js <dir> [ZOFF]
const fs = require('fs');
const path = require('path');

const dir = path.join(process.argv[2] || '.', 'Server/Particles/_Test/NatureRnD');
const zoff = process.argv[3] === undefined ? 3.2 : Number(process.argv[3]);
const system = JSON.parse(fs.readFileSync(path.join(dir, 'NatureBeam.particlesystem'), 'utf8'));
const rad = d => d * Math.PI / 180;

const rows = [];
for (const entry of system.Spawners) {
    const spawner = JSON.parse(fs.readFileSync(path.join(dir, entry.SpawnerId + '.particlespawner'), 'utf8'));
    const base = (entry.PositionOffset && entry.PositionOffset.Z) || 0;
    const emit = (spawner.EmitOffset && spawner.EmitOffset.Z) || { Min: 0, Max: 0 };
    const zMin = base + (emit.Min || 0);
    const zMax = base + (emit.Max || 0);
    const life = spawner.ParticleLifeSpan || { Min: 0, Max: 0 };
    const iv = spawner.InitialVelocity || {};
    const velocity = (iv.Speed) || { Min: 0, Max: 0 };
    const yaw = Math.max(Math.abs((iv.Yaw || {}).Min || 0), Math.abs((iv.Yaw || {}).Max || 0));
    const pitch = (iv.Pitch || {});
    const pitchMax = Math.max(Math.abs(pitch.Min || 0), Math.abs(pitch.Max || 0));
    // forward drift: from nothing (or backwards when the pitch spread goes past 90) to speed * life along the spread cone
    const cosWorst = Math.cos(rad(Math.min(90, Math.hypot(yaw, pitchMax))));
    const driftMin = (velocity.Min || 0) * (life.Min || 0) * cosWorst;
    const driftMax = (velocity.Max || 0) * (life.Max || 0);
    rows.push({
        id: entry.SpawnerId,
        from: zoff - zMax + driftMin * 0,
        to: zoff - zMin + driftMax,
        spawnFrom: zoff - zMax,
        spawnTo: zoff - zMin,
        drift: [driftMin, driftMax],
        render: spawner.RenderMode,
        rate: (spawner.SpawnRate || {}).Max,
        texture: (spawner.Particle || {}).Texture,
    });
}
console.log('NatureBeam placed at ZOFF = ' + zoff + ' (Pitch 180). Blocks forward of the attachment point:');
for (const r of rows) {
    console.log(r.id.padEnd(24), 'spawns ' + r.spawnFrom.toFixed(2) + ' .. ' + r.spawnTo.toFixed(2),
        ' drifts forward ' + r.drift[0].toFixed(2) + ' .. ' + r.drift[1].toFixed(2), ' => covers ' + r.from.toFixed(2) + ' .. ' + r.to.toFixed(2),
        ' ' + r.render + ' ' + (r.rate || '') + '/s');
}
console.log('whole beam: ' + Math.min(...rows.map(r => r.from)).toFixed(2) + ' .. ' + Math.max(...rows.map(r => r.to)).toFixed(2));
const end = rows.find(r => r.id === 'NatureBeam_End');
const start = rows.find(r => r.id === 'NatureBeam_Start');
console.log('start burst centre ' + ((start.spawnFrom + start.spawnTo) / 2).toFixed(2) + ', end burst centre ' +
    ((end.spawnFrom + end.spawnTo) / 2 + (end.drift[0] + end.drift[1]) / 4).toFixed(2));
