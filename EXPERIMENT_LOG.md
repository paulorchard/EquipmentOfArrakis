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
- The beam (prompt 02): that the Arrakis_Beam stat swaps the held model in the client, that the red box sits on the muzzle, that 32 model units are one block for a held item (the 9 lengths 1 to 5 blocks assume it), and how the line looks. Not seen on a client.


## Cutter Ray beam (prompt 02)

The dotted particle beam is gone: the `drawBeam` code, the `Arrakis_Cutter_Beam*` particle files, the Shoot animation
override and the `ItemAnimationId` on the beam tick. The beam is now part of the weapon model.

**Verified in the jar and assets**
- Item JSON `ItemAppearanceConditions` swaps the held model and texture of an item by the value of an entity stat on the
  holder (`Condition` [min, max], `Model`, `Texture`). Vanilla uses it for the glider (stat GlidingActive) and bows
  (SignatureCharges). The client does the swap, so a laser box that is a node of the model moves with the gun with no
  network delay; only the choice of length is server-driven.
- The vanilla Laser_Red sight is a node chain R-Attachment > Base > Lens > Laser, with Laser a 3 x 3 x 18 box stretched
  100 times along z and offset by half its stretched length. Copied as the shape; here the box is stretched to the wanted length.
- Rifle muzzle: in the frame of the Pulse Rifle's Main node the barrel runs along -x and ends at x = -27.8 (from the
  transformed corners of all its boxes). The Laser node is a child of Main at (-28, 0.5, 0), turned so its length runs
  out of the muzzle.
- Variant items were not needed, so the held stack never changes and the Primary repeat is not affected. Animation node
  scale was not needed either, so whether held items honour it was not tested.

**Built**
- Stat `Arrakis_Beam` (0 off, 1 to 9 a step; Shared so other players see the beam too).
- Nine models `Arrakis_Cutter_Ray_Beam_1..9`: lengths 1.0, 1.5 ... 5.0 blocks.
- A texture with a red #ff2a1a patch added under the rifle texture; the laser box is drawn fullbright.
- `BeamMath.stepForDistance`: the smallest step whose length reaches the surface, clamped to 1..9 (tested).
- `BeamDisplay` sets the stat each tick. `BeamExpirySystem` sets it back to 0 when no tick has refreshed it for 0.15 s
  (button released, power 0, hotbar switch, death, disconnect, or a stale value in a save). At 0 power the tick also
  sets it to 0 at once. With nothing in range the step is 9.
- Generators: `tools/assets/make_models.js` and `tools/assets/make_ray_texture.ps1`.

**Not verified (needs a client)**: all of it visually; whether 32 model units are a block for a held item (if the beam
is too long or too short, change UNITS_PER_BLOCK in make_models.js); the release delay is up to about 0.15 s plus one
network trip.
