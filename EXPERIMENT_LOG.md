# IslandCraft - Equipment of Arrakis

Server 0.6.8. Stage one: models, inventory placement and animation of four tools, plus the `Arrakis_Power` stat.
Identifier form `Equipment_of_Arrakis` (asset folders), package `com.paulorchard.islandcraft.equipmentofarrakis`, jar
`IslandCraft-EquipmentOfArrakis-<version>.jar`. All in-world ids are prefixed `Arrakis_`.

Loop: `gradlew deployMod` (close the world first), `gradlew test`, headless boot `tools/headless/run.sh "wait 30"`.
Models are regenerated with `node tools/assets/make_models.js <folder holding the vanilla Common/>`.

## Verified (bytecode, assets, headless server)

- **Utility section.** `InventoryComponent$Utility` only accepts items whose `Utility.Usable` is true (the container filter
  is `item.getUtility().isUsable()`). A `Utility` block with `Usable: true` is therefore both necessary and sufficient.
- **Which hand gets a click** (`InteractionContext.forInteraction`). With a main-hand item M and an off-hand item O:
  M's priority for the type in slot MainHand is compared with O's in slot OffHand (default 0 each).
  Equal: Primary goes to M; Secondary goes to O only if M has `Utility.Compatible`. M higher: M, unless M has no
  interaction of that type (then O). M lower: O. So the three off-hand tools get right click whenever the main hand
  holds a `Compatible` item (swords, pickaxes, food) or an item with no Secondary. The Cutter Ray is not Compatible,
  has a Secondary and has priority 10: both clicks stay with it. That is how vanilla two-handed items (rifles) block the
  off-hand. The off-hand item is not cleared from the Utility slot; it just cannot be used.
- **A stat needs no code to reach existing players.** `EntityStatMap.update()` creates a value for every
  `EntityStatType` asset that the map lacks, at its InitialValue, when an entity's stat map is set up. A new asset
  `Arrakis_Power` (InitialValue 100) therefore appears on new and old characters. `ResetType: MaxValue` refills on respawn.
- **Block damage as a pickaxe.** `BlockHarvestUtils.performBlockDamage` takes the stack that does the damage. The
  vanilla pickaxe swing calls it with the held stack; the ray calls it with an unbreakable
  (`withMaxDurability(0)`) `Tool_Pickaxe_Mithril` stack, tool id "Pickaxe", `DamageScale` 0.24. Durability is skipped
  for unbreakable stacks (otherwise it is written into the hotbar slot).
- **Strongest pickaxe.** Compared every `Tool_Pickaxe_*` spec by gather type: Mithril (ItemLevel 50) matches or beats
  all others in every gather type (startup log: "does not match or beat ... none"). Onyxium ties on Rocks quality 6 but
  has Soils 0.5 against 1.0. Adamantite is below on rocks quality and ores.
- **Animation retiming works.** Item animation sets (`Server/Item/Animations`) take a per-animation `Speed`; the vanilla
  Gun uses 3. Torch `Swing_Left` is 25 frames at 60 fps = 0.41667 s; at Speed 0.2 it lasts 2.08333 s, and
  `Arrakis_Dew_Reaper_Swing` has `RunTime` 2.08333.
- **Pitch following.** The torch animation set has `Camera.Pitch` (target nodes Head and LShoulder, range -60..60), which
  rotates the left arm with the look pitch; the three tools inherit it (`Parent: Torch`).
- Headless boot: no asset errors; all four items, the stat, the animation sets, interactions and particles resolve
  (`AssetSelfCheck` logs them at start).

## Not verified (needs a client)

- How the models sit in the left hand (grip offset and orientation of the lantern and the pickaxe), and whether the
  scale values (0.5, 0.6667) are what the hand renderer uses.
- That the Z radial lists the tools and that Primary repeats while held (assumed from the Gun's 0.05 s RunTime).
- How the chosen vanilla poses look: Interact (Blood Extractor), Build (Compactor), Swing_Left (Dew Reaper) were picked
  from their keyframes (forearm straightens; hand turns 90 degrees forward; arm swing), not seen.
- The molten effect (prompt 04): how the globs look (size, spread, speed, glow), that they fall and vanish as meant, and that other players see them. Not seen on a client.

## Cutter Ray surface effect (prompt 04)

The beam is gone, in every form: no model variants, no stat, no entities, no particle line. The Cutter Ray now shows
only a molten effect on the surface it cuts. The gun is the vanilla Pulse Rifle, with no Shoot animation.

**Verified in the assets and the jar**
- Particle systems are lists of spawners, spawned at a point with `ParticleUtil.spawnParticleEffect(id, position,
  accessor)`. A spawner has initial velocity (speed, yaw, pitch ranges), `Attractors` with a `LinearAcceleration`
  (the vanilla lava splash uses Y -7.2 for gravity), and `ParticleCollision` (`BlockType: Solid`, `Action: Expire`
  makes a particle vanish when it hits something solid).
- `Particles/Textures/Fire/Lava_Bubble2.png` is 8 frames of 32 x 32; frame 0 is a glowing lava-textured ball.
  `Particles/Textures/Basic/Glow.png` is the soft glow sprite.
- The point on the surface is where the look ray enters the targeted block (`BeamMath.entryDistance`, tested), moved
  0.08 block back toward the player so the globs start in the air.

**Built**: system `Arrakis_Cutter_Molten` with one spawner, spawned each beam tick (while firing and a block is
targeted):
- `Arrakis_Cutter_Molten_Globs`, currently set to the **100% test setting**: a constant stream, 90 globs spread over 0.2 s
  (450 a second) from each beam tick, so the streams of consecutive ticks overlap. Sizes 0.02 to 0.06, tinted orange
  (#ff6a00), shrinking to almost nothing as they fall. Let go from rest (speed 0 to 0.12, pitch -90 to -60, nothing
  moves upward) with gravity -9. The engine has no per-particle delay, so the 0.03 s pause before falling is the
  natural start from rest: after 0.03 s a glob has moved 4 mm, after 0.1 s 4.5 cm. Life 0.5 to 0.9 s; they also vanish
  on touching a solid block. Lava bubble texture, not lit by the world. To scale back, lower `TotalParticles` (and
  `MaxConcurrentParticles`).
There is no glow spot: a first version had one, and in game it appeared about a second after the globs and after the
block broke, so it was removed. Nothing is shown when no block is in range. The effect is data: change sizes, speeds, counts or the texture in the
two spawner files; `ParticleId` in `Arrakis_Cutter_Beam.json` picks the system.

**Not verified (needs a client)**: all of it visually, in particular whether particle scale 0.04 to 0.12 is the
size wanted, whether the spread looks like globs thrown off a cut, and whether a burst per tick looks continuous.

## Power recharge

`PowerRechargeSystem` (a ticking system over players) adds `ArrakisPower.RECHARGE_PER_SECOND` (10) per second, up to 100,
while the player has not tried to spend power for 0.35 s (`IDLE_NANOS`; beam ticks come every 0.1 to 0.2 s). A spend
attempt counts as use even at 0 power, so holding the button on an empty pack does not recharge. Dead players do not
recharge. It is code and not a Regenerating rule in the stat asset because a stat rule cannot tell whether the Cutter
Ray is firing. Not tested with a player: the headless server only checks that the plugin loads.

## Molten trail (supersedes how the effect is spawned above)

In game the effect read as separate shots: one burst per beam tick (every 0.1 to 0.2 s), each from one fixed point.
The engine has no line or moving emitter, so the effect is now drawn as a trail:
- `MoltenTrailSystem` runs on every world tick (about 30 a second), not on the beam tick. For each firing player,
  `MoltenTrail` finds the surface point under the crosshair (look ray and `TargetUtil.getTargetBlock`, entry point of
  the block, 0.08 block out) and spawns the burst there, and at evenly spaced points (`TrailSpacing`, 0.1 block) along
  the line from the previous tick's point (`BeamMath.trailPoints`, tested). A jump of more than 1.5 blocks, or a miss,
  starts a new stroke instead of joining.
- The burst is now short (`TotalParticles` 5 within 0.01 s) from a thin spot (0.04 block). A still aim spawns
  `SpawnsPerTick` (3, the 100% setting) bursts per world tick at one point: 450 globs a second as before.
- The beam tick only keeps the effect on (`MoltenTrail.fire`); it is off 0.3 s after the last beam tick (button
  released), at once when power runs out, and when the player dies, leaves or holds another item.

Limits: the resolution is one world tick, with straight-line gaps; the engine cannot follow the crosshair pixel by
pixel. The effect continues up to 0.3 s after the button is released. Not tested with a player.

## Molten globs: directions, delay and intensity (latest)

- Intensity is about 50% of the test setting: `SpawnsPerTick` 5 bursts per world tick, each burst 3 parts (see below)
  of 1 or 2 globs, about 7.5 globs a tick, 225 a second (100% would be `SpawnsPerTick` 10).
- Directions: let go from rest with pitch -90 to -10 degrees (straight down to nearly sideways, never upward), any yaw,
  speed 0 to 0.5, then gravity. Some globs fall straight, some peel off outward.
- Gravity delay: the engine has no per-particle delay before gravity. The globs start from rest, so each hangs for
  about 0.03 s anyway. The variation of about +/- 0.05 s comes from starting the glob spawner three times in the
  system, with a `StartDelay` of 0, 0.04 and 0.08 s: some globs appear and start to fall later at the same spot.
  They are not visible while they wait, which is the difference from a glob that really hangs. A true hang would need a
  stationary sprite per delay followed by a falling one. Not tested with a player.

## Molten globs hang, then fall (latest; replaces the start-delay copies above)

A glob now hangs still where it is thrown off, with no gravity, for 0.5 to 0.75 s and then falls. The engine has no
per-particle delay before gravity, so `tools/assets/make_molten.js` generates four pairs of spawners: a **Hold** (a
stationary glob living for the delay, 0.5, 0.58, 0.67 and 0.75 s) and a **Fall** (the same glob, same size and
position, started after the delay with `StartDelay`: random direction from straight down to almost sideways, never
upward, speed 0 to 0.5, gravity -9, shrinking to almost nothing, life 0.5 to 0.9 s, vanishes on touching something
solid). Sizes 0.03 to 0.06. Each pair has a fixed offset (a few hundredths of a block) so the globs of a burst do not
overlap; this fixed offset replaces the random one so that a Fall starts exactly where its Hold hung. The Hold outlives
its delay by 0.04 s, hidden under the Fall.
A burst is 4 globs, so `SpawnsPerTick` 2 is about 240 globs a second (about 50%); 4 is about 100%. Globs now live
about 1.3 s instead of 0.7 s, so the number on screen at once is higher for the same rate. Not tested with a player.

## Molten globs: smaller, varied colour, cooling (latest)

Sizes are now 0.018 to 0.036 (about 0.6 of before). The four globs of a burst have different orange tints, from bright
(#ff6a00) to dark (#b84000); a Hold keeps its tint and a falling glob darkens to a quarter of it over its life
(for example #ff6a00 to #3f1a00). Whether the tint multiplies the lava texture or blends with it, and how dark the end
colour reads, are not seen in game.
