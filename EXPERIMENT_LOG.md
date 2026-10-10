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
- The molten effect (prompt 04): how the globs look (size, spread, speed, glow), that they fall and vanish as meant, and that other players see them. Not seen on a client. See the lock-on section for the laser line.

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

## Lock-on, laser line, globs at the beam end (replaces "Molten trail"; supersedes how the effect is spawned above)

The aim-following trail is gone (`BeamMath.trailPoints`, `MAX_LINK`, `TrailSpacing`, the per-player last point). The Cutter Ray now
locks onto a block and draws a red line to it.

**Lock-on** (`CutterBeamInteraction`, `MoltenTrail`)
- First beam tick with power left and a block within `Range` (5) under the crosshair: that block, the face the look ray entered
  (`BeamMath.entry`: distance, face normal, face centre) and the globs' point (face centre plus 0.08 along the normal) are fixed for
  the whole lock. No block in range: nothing happens and no power is spent. Later ticks pay `PowerPerSecond * RunTime` and damage the
  locked block (same call, same Mithril stand-in, same `DamageScale`); they never re-target.
- Every world tick (`MoltenTrailSystem`, now an entity-ticking system over players because adding and removing the laser entities needs
  the command buffer): the lock breaks when the block id at the locked position changed (broken or replaced, by the beam or anything
  else), or when `TargetUtil.getTargetBlock` has not been the locked block for more than `LockGraceSeconds` (0.15, in
  `Arrakis_Cutter_Beam.json`; the time counts from when the crosshair left and resets when it is back).
- A break sets `needsRelease`. While it is set, beam ticks do nothing: no damage, no power, no globs, and they do not count as use of
  power (`ArrakisPower.spend` is not called), so recharge starts. Release is told by the gap between beam ticks of any kind
  (`lastArrival`): a tick that arrives more than `EXPIRY_NANOS` (0.3 s) after the previous one is a new press and clears the flag.
  Refused ticks are recorded as arrivals (that is what lets a held button be told from a release) but not as use. The prompt asked that
  refused ticks "do not extend the idle time"; read literally that would clear the flag while the button is still down, so the idle time
  here is the power idle time. Limit: a release and re-press faster than 0.3 s counts as still held and is refused once.
- The lock also ends, without needing a release, when no beam tick has fired for 0.3 s (button released), the player dies, holds
  another item, leaves, or power reaches 0 (Creative mode breaks the block at once, which ends it the first way).
- Trying with an empty pack still counts as use (so holding the button at 0 does not recharge), as before.

**Ticks after Failed.** Verified in the jar: `SimpleInstantInteraction` runs `firstRun` and then `SimpleInteraction.tick0`, which on a
Failed state only jumps to the failure label or ends the chain; the only server code that restarts a held interaction
(`InteractionManager.tryRunHeldInteraction`) is called by the NPC systems, not for players. So for a player the next tick is started by
the client, and whether a Failed chain is started again while the button is down is not something the server can show. The beam
therefore ends refused ticks as Finished (the default), which come again after `RunTime` like every other tick; release detection
relies only on that. Not tested with a client.

**Laser line** (`CutterLaser`, `BeamSegment`, `BeamSegmentSystem`, `tools/assets/make_laser.js`) - second version
The first version (one-block rods of 3 units, red) was seen in game: far too thick, with gaps between the segments. Now:
- A white-hot core that tapers from 4 "pixels" at the barrel to 1 at the locked face, plus a translucent white glow around it that
  pulses in and out. A "pixel" is `CutterLaser.PIXEL` = 0.003 block (4 px = 0.012 block, about 0.38 model unit; the vanilla Laser_Red
  sight is 0.6 unit): the one number to change to make the whole line thicker or thinner. The glow reaches 1 to 3 pixels out on each
  side, 3 times a second.
- Entities can only be scaled evenly, so the line is a row of pieces of different scale (`BeamMath.pieces`, tested): both models
  (`Arrakis_Cutter_Laser` white opaque, `Arrakis_Cutter_Laser_Glow` white alpha 110) are a 1 x 1 x 96 unit box, a piece of width w is
  96 w long, so the pieces get shorter towards the thin end (about 9 for a 5 block line); each starts 0.85 of a length after the one
  before (15% overlap), and the last is cut to end exactly at the face. The scale of every piece is set from its width, and the glow
  pieces' scale is changed every tick (`EntityScaleComponent`, which the engine sends as a model update when it changes).
- Not known without a client, and possible causes of the gaps and the thickness seen: the model's long axis may not lie along the look
  direction (the first version would then show as slabs across the line, which is both thick and gappy); pitch may not be applied
  to the body of a model entity; 32 units to a block may not hold. `arrakis laser rig` (as a player) puts three 1.5 block rods
  ahead of you: the middle one should point away from you, the one on your right straight up, the one on your left to your right;
  `arrakis laser clear` removes them. If they do not, the model axis or rotation is what to change.
- The glow relies on the client blending a translucent texture; with an alpha test only, it is not drawn and the white core is
  seen alone (the texture alpha of 110 was chosen to fail that way).
- A broken lock now needs a gap of 0.5 s (`RELEASE_NANOS`) between beam ticks to count as a release, up from 0.3 s, so a server or
  network hiccup while the button is down cannot clear it and let the gun jump to another block.
- The pieces of the core are kept in `State.segments`, those of the glow in `State.glows`; nothing is spawned per tick.
- Entity components, hand point and safety nets: unchanged (see above: Transform, HeadRotation, UUID, Model, EntityScale, BoundingBox,
  NetworkId, Visible, Intangible, NonSerialized, `BeamSegment`; hand point eye + 0.9 forward, 0.22 right, 0.32 down; the sweeper).
- Globs: unchanged effect, thrown from the fixed point at the end of the beam, `SpawnsPerTick` bursts per world tick, tinted with the
  locked block's `getParticleColor()`. The glob is a soft white glow (`Basic/Glow.png`, additive), not the yellow lava bubble.

**Verified in the headless server (no client)**: all assets load (`model asset Arrakis_Cutter_Laser: ok`, `particle system
Arrakis_Cutter_Molten_1: ok`), a segment is accepted by the store with that component set, `BeamSegmentSystem` removes an unowned segment within a
tick, an owned one stays, and after a restart (chunk loaded again) the count is 0, so it is not saved. `arrakis laser spawn [keep] [x y z]`
and `arrakis laser count` are the commands for this (operators only). An entity in a chunk that is not loaded logs `Entity has moved into a
chunk that isn't currently loaded` every tick and is dropped, which does not happen next to a player.

**Not verified (needs a client)**: that the line is visible and red, 3 units thick reads right, the unit size of 32 per block for entity models
(from the Rat's hitbox against its model), that the segment lies along the look direction and not across it, that pitch is applied to a model
entity's body (it is set on both the transform and the head rotation), that clients interpolate the 30-per-second moves, the hand offset,
the `LockGraceSeconds` feel, release detection and the Failed behaviour above with a real held button, a second player seeing the line and globs, and
that no beam entity is left after release, power 0, slot change, death or relog (the code paths are there; only the unowned/owned cases
were run).

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
