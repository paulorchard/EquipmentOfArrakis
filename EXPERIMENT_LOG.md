# IslandCraft - Equipment of Arrakis, v2: experiment log

v1 (the Cutter Ray, guns, off-hand tools, Holtzman shield, Arrakis_Power stat and HUD) is thrown away; `git show equipment-v1-final:<path>`
reads any v1 file. v2 is built in modules, one package and one asset folder each: core, power, catalog (this prompt), then cutter,
compactor, dewreaper, bloodextractor, weapons, shield, bloodbag, litrejon, stillsuit, armor.

Legend: **confirmed** = seen in a headless boot or in bytecode with javap; **inferred** = reasoned from bytecode or from vanilla data,
not seen working; **needs a client** = only a player on a client can show it.

## core

- Boundary rule: a module imports only its own package, `core` and `power`; `core` imports no module, `power` imports `core` only; the root
  package (plugin + config) is the only place that lists modules. `ModuleBoundaryTest` scans `src/main/java` for any reference to a package
  of the mod (imports and fully qualified names) and fails with the file and the offending package. Tried once with a `power` file importing
  `catalog`: the test failed and named it; reverted.
- Module interface: `EquipmentModule { String id(); void setup(ModuleContext) throws Exception; default void start(ModuleContext); default
  List<String> selfCheck(); }`. A module that throws in `setup` is logged at SEVERE (`Module x: setup FAILED: ...`) and the others go on.
- Config `Equipment_of_Arrakis.json` (plugin data folder, written with defaults when missing): `Modules` (enabled ids, default all three; core
  always loads), then `Core`, `Power`, `Catalog` sections. **confirmed**: removing `catalog` from `Modules` gives `Module catalog: disabled in the
  config`, `/arrakis modules` shows `catalog: disabled`, and the other two run. (The catalog's assets still load, they are asset data; only its
  Java checks stop.)
- `WorldAccess` (adapted from the 0.7.0 reference; the pre-load path and fluid reads were left out, `isTicking`, `height`, `loadSection`,
  `loadTestArea` kept or added). **confirmed headless** with `/arrakis worldtest`: the column and the empty sky section at y 200 load and are held
  (`1 of 1`), `setBlock` Rock_Stone then Empty works and reads back, an unloaded position reads as no block, `height(0,0)` = 107, and `isTicking`
  is true for a column loaded with `SET_TICKING` (so the reference's claim about the NonTicking marker holds).
- Threading: the raw `ChunkStore` futures complete off the world thread; `loadColumn`/`loadSection` hop back with `.thenApplyAsync(x -> x, world)`.
  Self-tests that use them end in `whenCompleteAsync(..., world)`.
- NPC dummies cannot be spawned in the headless world: `NPCPlugin.spawnNPC` ends in `Unable to handle non-spawned entity` (Store.addEntity
  returned null) and `spawnNPCWithSpaceValidation` says `FAIL_INVALID_POSITION` for `Test_Dummy_Stationary`, `Test_Dummy` and `Test_Dummy_Damage`,
  on natural ground at two places and on a placed stone floor in the sky, with the column ticking. Cause not found (`SpawningContext.setExact` /
  `validatePosition`). v1's tests used the same role on 0.6.8. So `/arrakis power selftest` prints a SKIPPED line headlessly.

## power

- Power is the vanilla **Mana** stat. No custom stat, HUD or recharge. Regeneration is vanilla's: logged at start (**confirmed**):
  `initial 100, min 0, max 100, shared false, reset MaxValue`; regen 0: every 0.3 s, ADDITIVE 1, `[AliveCondition]`; regen 1: every 0.5 s,
  PERCENTAGE 1, `[AliveCondition, CheckPlayerGameModeCondition]`; 0 modifiers each.
- **Place for the regeneration change** (later prompt): vanilla refills Mana in about 30 s whether or not it is spent. To change that, override the
  `Regenerating` entry of `Mana.json` in this mod's asset pack, or replace the loaded asset at runtime with `AssetStore.loadAssets` /
  `LoadedAssetsEvent`. The marker is a comment in `PowerModule.start`.
- Service `Power.get()` (also `EquipmentOfArrakisPlugin.get().power()`): `get/max/fraction/has/isCreative`, `spend` (all or nothing, creative
  spends nothing, zero/negative is a no-op that succeeds, a 0.001 tolerance so 29.9995 pays for 30), `spendUpTo`, `add`, `set`, `fill`. Arithmetic is in
  `PowerMath` (unit-tested, 9 tests). A missing stat map or stat is logged once and returns 0/false.
- `PowerModifiers`: `putMax/removeMax/list`, key = the caller's id (e.g. `IslandCraft:Stillsuit`); same id replaces. ADDITIVE adds to the max,
  MULTIPLICATIVE multiplies it (`CalculationType.compute` is `a+b` and `a*b`, **confirmed** in bytecode, so `mul 1.5` is x1.5).
- The four questions:
  1. *Raising the max does not raise the current value*: not yet seen (needs an entity; headless spawn failed). Check by hand: `/arrakis power set 80`,
     `modifier add t add 16`, `get`.
  2. *Lowering the max clamps*: same, by hand: `set 116` with the modifier, then `modifier remove t`.
  3. *A modifier put directly survives the engine's recalculation*: **inferred yes**. `StatModifiersManager.recalculateEntityStatModifiers` only
     removes and puts modifiers under its own keys (`Armor...`/`Effect...` per calculation type, and `*Weapon_`/`*Utility_` + stat index for held
     items) and never clears the whole map, so a key like `IslandCraft:Test` is not touched. Needs a client to see: equip and unequip the
     Cindercloth gloves with a modifier listed. If it is lost, route through `getStatModifiersManager()`.
  4. *Client prediction*: the plain `EntityStatMap` overloads are `Predictable.NONE` in bytecode and still record the change for the client, so the
     bar should follow the server. **inferred**; `Predictable.SELF`/`ALL` are for changes the client already applied itself. Needs a client:
     watch the bar while using `/arrakis power set`.
- Commands (operator-only, all under `/arrakis power`): `get`, `set <n>`, `add <n>`, `fill`, `spend <n>`, `max`, `modifier add|remove|list`,
  `selftest`; each takes an optional online player last.

## catalog

- Tab `Server/Item/Category/CreativeLibrary/IslandCraft.json`: the file name is the id (**confirmed**: `ItemCategory.getAssetMap().getAsset("IslandCraft")`
  is found with order 4, vanilla tabs are Blocks 0, Furniture 1, Items 2, Tool 3). Children Tools, Weapons, Armor, Resources with the vanilla icons
  `Items-Tools/Weapons/Armor/Potion.png` (referenced by path, all found in the loaded common assets).
- Our art (the only art shipped): `Common/Icons/ItemCategories/Arrakis.png` and `ArrakisActive.png`, byte for byte from the two reference PNGs (88x88).
  Both are found in the common-asset registry (**confirmed**). That the client picks `ArrakisActive.png` for the selected tab by adding `Active` to the
  `Icon` path is **inferred** from the vanilla files (`Items.png`/`ItemsActive.png`): needs a client.
- Item convention for every later module: `"Categories": ["IslandCraft.Tools"]` (or Weapons / Armor / Resources) in the item JSON, by string only,
  so no module imports catalog.
- `Arrakis_Dev_Marker` (name "Dev Marker", MaxStack 1, Tools) borrows the vanilla `Ingredient_Stick` model, texture and icon by path. It is dev-only:
  **delete it, its `$Comment`-mentioned lang key and `CatalogModule.DEV_MARKER` when the first real item lands.** `CatalogAssetsTest` requires
  at least one item in the tab, so it must be edited then.
- Empty children (Weapons, Armor, Resources have no items): whether the client shows them or hides them: **needs a client**.
- The tab has no `Name` field in the vanilla shape, so `server.ui.itemcategory.islandcraft` is defined in `server.lang` but the self-check reports
  `name null` for the tab itself.
- Tests: `CatalogAssetsTest` (valid JSON, unique child ids, all name keys in `server.lang`, icons ours or in Assets.zip, order after the vanilla tabs,
  items in the tab name an existing child), `EquipmentOfArrakisConfigTest`, `PowerMathTest`, `ModuleBoundaryTest`.

## Harness notes

- `tools/headless/run.sh "wait 35" "arrakis modules" ...` boots the server with this jar; world commands from the console run in world `default`.
  After `stop` the log shows `Removing world exceptionally` / `Reloading crashed world 'default'`; it appears at shutdown and was not investigated.

## Experiments (delete this section with the module)

Throw-away look-and-feel experiments, all in the module `experiments`. They are replaced by the real modules when a design is chosen,
so everything of them is findable by one search (`Arrakis_Exp_` for assets and ids, `Exp` for classes, the word `Experiments` for the
package and every asset folder) and leaves no trace when removed. Experiment 1 is the Flame Cutter, experiment 2 the Drill Cutter.

Rules (enforced by `ExperimentsIsolationTest`, which scans the project): everything lives under an `experiments` / `Experiments` path
(Java package, `Server/.../Equipment_of_Arrakis/Experiments/`, `Common/.../Experiments/`, `tools/assets/experiments/`, the test package);
every file there starts with an `EXPERIMENT:` marker (Java header comment, JSON `$Comment`, a comment in scripts, a `tEXt` comment in
PNGs); no other module imports the package; the only edits outside those paths are the three below, each marked.

Edits outside the Experiments paths (each marked `// EXPERIMENT: remove with the experiments module`, or fenced):
1. `EquipmentOfArrakisPlugin.java`: one line in `setup()` that appends `new ExperimentsModule(this)` to the module list.
2. `EquipmentOfArrakisConfig.java`: the `Experiments` section (field, accessor, `section()` case, codec entry) and the one line that adds
   `"experiments"` to the default Modules list. Each line carries the marker.
3. `server.lang`: the two translation entries between `# EXPERIMENT BEGIN` and `# EXPERIMENT END`.
Two generic changes were needed in tests written for prompt 01, and are NOT experiment-specific (they stay after removal):
`ModuleBoundaryTest` gave an unlisted module only its own package (now: its own package, core and power, the rule as written), and
`EquipmentOfArrakisConfigTest` asserted the default module list is exactly core, power, catalog (now: contains them).

### Removing the experiments

From the project root (this is the exact script the rehearsal below ran):

```
P=src/main/java/com/paulorchard/islandcraft/equipmentofarrakis
T=src/test/java/com/paulorchard/islandcraft/equipmentofarrakis
rm -r $P/experiments $T/experiments                                  # 1 the Java package and its tests
find src -type d -iname experiments -prune -exec rm -r {} +          # 2 every Experiments folder of the assets
rm -r tools/assets/experiments                                       # 3 the generator scripts
sed -i '/\/\/ EXPERIMENT: remove with the experiments module/d' $P/EquipmentOfArrakisPlugin.java $P/EquipmentOfArrakisConfig.java   # 4 the plugin line and the config entries
sed -i '/^# EXPERIMENT BEGIN$/,/^# EXPERIMENT END$/d' src/main/resources/Server/Languages/en-US/server.lang                           # 5 the lang block
sed -i '/^## Experiments (delete this section with the module)$/,$d' EXPERIMENT_LOG.md                                                 # 6 this section (it is the last one; stop at the next "## " if another follows)
find src tools -type d -empty -delete                                # 7 folders left empty
```

Then `gradlew build`, and delete `Equipment_of_Arrakis.json` in the plugin's data folder (or remove `"experiments"` from its `Modules` and
the `Experiments` section) if the game has already written one with them. Check: `grep -rIli -E "arrakis_exp_|experiments" .` finds nothing
(ignoring build, .git, .gradle).

**Rehearsal (run for real, on a copy of the project in a temp folder, discarded afterwards):** the script above was applied; `grep` found
no trace; `gradlew build` passed with 20 tests (the 18 experiment tests gone with the module, the other 20 unchanged); a headless boot
loaded `core, power, catalog` (`modules loaded: 3 of 3`, `self-check: no problems`), logged nothing mentioning an experiment, no
exception, and wrote a config with no Experiments section.

**Switching the module off without a rebuild** (remove `"experiments"` from `Modules`): the server boots cleanly and
`/arrakis modules` shows `experiments: disabled`. The asset pack loads every asset whatever the config says, and one asset whose
interaction type nobody registered stops the whole boot (`Asset validation FAILED ... Mod ... failed to load`, seen in the first try).
So the module's constructor (which runs even when the module is off) registers the `Arrakis_Exp_Cutter_Tick` type and a `BootEvent`
listener; when `setup` did not run, the listener removes every `Arrakis_Exp_` asset (Flame Cutter: 1 item, 1 root interaction, 2 interactions,
1 particle system, 2 spawners = 7; Drill Cutter: 1 item, 1 root interaction, 4 interactions = 6; 13 in all) before any player can join. Left in the pack and in the client download: the 17 KB texture copy
(a common asset cannot be removed at runtime). **confirmed** headless.

### Experiment 1: the Flame Cutter

What was built: `Arrakis_Exp_Flame_Cutter` ("EXPERIMENT Flame Cutter", tab IslandCraft, Tools), a clone of `Weapon_Flamethrower_Scrap`
(same model, animations `Goblin_Flamethrower`, icon, tags, Weapon block; the model and icon are referenced by their vanilla paths) with
no recipe and no pilot flame; Primary = `Arrakis_Exp_Flame_Primary` = [`Arrakis_Exp_Cutter_Tick`, `Arrakis_Exp_Flame_Burn`]; the particle
system `Arrakis_Exp_Flame_Focus`; and one texture copy (below). No fire, no entity damage, no knockback, no sound anywhere in it
(`ExperimentsAssetsTest` rejects any key or value mentioning Fire, PlaceFluid, Damage*, Knockback, ApplyEffect, Selector, Sound or
Projectile in the chain). The vanilla `Selector` (hit entities, place fire on blocks) is simply left out: with it gone there is nothing
to hurt or ignite, and the block is found by the tick's own ray, so keeping it would only have cost work.

**Why the chain is sequential and not the vanilla Parallel.** v1's lesson: the engine repeats a held Primary, and a refusal must come
before any animation or effect starts. A Failed interaction cancels the rest of its chain, so the tick comes first: when the player
cannot pay, the animation and the beam interactions after it never run, and nothing shows. In a Parallel both branches would start at
once. Cost of this choice: each 0.1 s the `Burn` interaction ends and starts again, so the particle system is re-spawned every tick.
Whether that shows as a flicker at the muzzle (if the engine kills the live particles of a cancelled system) or runs smoothly (if it
only stops emitting) is **needs a client**. If it flickers: raise `RunTime` of `Arrakis_Exp_Flame_Burn` and `Arrakis_Exp_Cutter_Tick`
together (cost per tick is `ManaPerSecond * RunTime`, so Mana per second stays the same), or move to a Parallel with a pre-check gate.

**The 0.7.0 block-damage call as built** (re-checked with javap on the 0.7.0 jar; the signature is the one v1's last Cutter Ray used):
`BlockHarvestUtils.performBlockDamage(Ref<EntityStore> attacker, Ref<EntityStore> owner, Vector3i block, ItemStack tool, ItemTool null,
String "Pickaxe", boolean false, float damageScale, int settings, boolean false, Ref<ChunkStore> section, ComponentAccessor<EntityStore>,
ComponentAccessor<ChunkStore>)` returning boolean, with `settings = SetBlockSettings.NO_SEND_AUDIO`, and in Creative
`performBlockBreak(Ref<EntityStore>, ItemStack, Vector3i, int settings, Ref<ChunkStore> section, accessor, accessor)`. The section ref comes from
`WorldAccess.sectionRef` (prompt 01's core helper); block health lives in the section, which the engine call finds itself. The tool is
`new ItemStack("Tool_Pickaxe_Mithril", 1).withMaxDurability(0)` (unbreakable: no durability is written into any held item). The look ray
is `TargetUtil.getTargetBlock(player, Range, buffer)`. Order inside a tick: canPay (else Failed, silently), find the block, cut, pay
(`Power.spend`, all or nothing; Creative pays nothing). With no block in range nothing is paid unless `SpendWithoutTarget` is true.
All settings are data in `Arrakis_Exp_Cutter_Tick.json`: `Range 5`, `ManaPerSecond 3.3` (about vanilla regen: the first regenerating entry gives 1 every
0.3 s = 3.33/s, and the second adds 1% of the max every 0.5 s under a game-mode condition, so holding the beam is about sustainable), `DamageScale 0.24` (v1's measurement: 0.24 per 0.1 s is one full pickaxe swing per
0.4167 s), `ToolItemId`, `Silent`. **confirmed**: compiles against the 0.7.0 jar, the codec registers, the interaction and the item load.
**inferred, needs a client**: that the block cracks and breaks at pickaxe speed, that blocks the pickaxe cannot mine stay untouched,
that Mana falls about 3.3 per second, that Creative breaks at once. (No player or dummy can be spawned headlessly: see the core section.)
Not mineable blocks still cost Mana (the cost is paid when a block is in range; `performBlockDamage`'s result is not used to refund).

**Strongest pickaxe, re-run on 0.7.0** (`tools/assets/experiments/compare_pickaxes.js` over the 10 `Tool_Pickaxe_*` items, Parent
resolved, every gather type, Power and Quality): `Tool_Pickaxe_Mithril` is still at least as good as every other pickaxe in every
gather type that the other one has (Rocks 1.0 / quality 6, equal only to Onyxium and above Adamantite's quality 5; Metals 0.5 where
every other pickaxe has 0.001; Soils 1.0 where Onyxium has 0.5). So it is the stand-in, as a data setting (`ToolItemId`).

**The red glow went away with data, apart from one painted flask.** The three properties (`Particles` Candle_Fire, `Light`,
`SoundEventId`) are simply not in the clone (`ExperimentsAssetsTest` checks). In the model (`inspect_flame_model.js`): the `Flame` node
has no geometry (type none, an attachment point), and the `Wick` quads are dark brown (#090707 to #32170e), not glowing. What remains
drawn after the data removals is **painted red-orange**: `Flamethrower_Eye` (all six faces, texture x 23..68, y 57..78, 511 pixels,
mostly #ff1b03), its `Flamethrower_Pupil`, and the top and bottom of `Flamethrower_Bottle_Top` (x 82..92, y 62..72, #f7570f). It is
the goblin's fuel flask; a Hytale texture has no emissive layer, so it does not light anything, but it looks like a glowing coal. No
data-only fix exists (it is baked into the texture), so, as the prompt allows, **this experiment ships ONE edited copy of a vanilla
asset**: `Common/Items/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Cutter_Texture.png`, written by
`tools/assets/experiments/make_flame_texture.js`: only the warm pixels (red exceeds blue by 30 or more) inside the faces of those
three nodes (920 pixels) are turned steel blue at the same brightness; every other pixel is byte-identical to `Scrap_Texture.png`
(2743 differing bytes of 49152). The vanilla model is still referenced by path. To undo it, point `Texture` in
`Arrakis_Exp_Flame_Cutter.json` back at `NPC/Intelligent/Goblin/Models/Weapons/Flamethrower/Scrap_Texture.png` and delete the copy.
Whether the flask looks right in blue is **needs a client**. The item also drops `ItemSoundSetId` (`ISS_Weapons_Wood`, the inventory
handling sound set of the vanilla item) because the prompt wants no sounds; `ExperimentsAssetsTest` checks that too.

**The focusing beam: how, and the numbers.** `tools/assets/experiments/make_flame_focus.js` writes the system and its spawners; the
parameters are at the top (`START_RADIUS, END_RADIUS, START_SIZE, END_SIZE, RANGE, MUZZLE, LIFETIME, SPEED, SPAWN_RATE, COLORS`, plus
`RENDER_MODE, TEXTURE, SIZE_JITTER, RING_JITTER, FOCUS_GLOW`). How the convergence is made, all data:
- *Spawn on a ring*: `Shape` Sphere with an `EmitOffset` that gives only X and Y, both `Min = Max = START_RADIUS`. The engine's own text
  says two ranges give "a 2D form of the shape, e.g. rings", and `Min` is the hollow inside, so `Min = Max` is the rim: every particle
  starts on one circle of radius 0.45 around the beam axis. (The engine has no line emitter; this is the closest it has to a lens
  aperture.)
- *Fly along the beam*: `InitialVelocity` speed = (RANGE - MUZZLE) / LIFETIME = 15.2 blocks/s, yaw and pitch 0.
- *Be pulled to the axis*: one `Attractor` with `RadialAxis (0, 0, 1)` (the beam axis in the system's frame) and `RadialImpulse =
  -(START_RADIUS - END_RADIUS) / LIFETIME` = -1.72: a constant speed towards the axis ("radial impulse: constant speed towards the
  RadialAxis"), so the radius falls linearly and every particle of the ring reaches radius END_RADIUS at the end of its life, at the same
  place. `DampingMultiplier` 1 on all axes leaves the forward speed alone. END_RADIUS stays above 0 (0.02) so no particle is ever on the
  axis itself, where the pull has no direction.
- *Shrink*: initial scale 0.3 (+-15%), animated to 0.1 of it at frame 100 = 0.03 (the vanilla flame goes 0.2-0.3 up to 6x). Opacity fades
  in over the first 12% and stays at 1; colour keyframes `#fff3b0` (pale hot yellow-white), `#fff6c8`, `#ffffee` (bright white-yellow), no
  red or orange (`ExperimentsAssetsTest` checks). Render mode BlendAdd, texture `Particles/Textures/Basic/Glow.png` (vanilla, by path),
  160 particles per second, life 0.25 s, so about 40 alive at once; `Arrakis_Exp_Flame_Focus_Point` adds a steady 0.1 glow at the focus.
- *Where the system sits*: the same way the vanilla flame is placed (`Arrakis_Exp_Flame_Burn.json`: Y 0.15, Z 1.2, yaw 180). Vanilla puts
  the system at Z 3.7 and its spawners at +2.5 with yaw 180, which only makes sense if the offsets flip: the flame starts 1.2 in front.
  So MUZZLE = 1.2 and the focus spawner is at local Z -(RANGE - MUZZLE) = -3.8.
- The numbers along one particle (blocks from the player / ring radius / size): 0%: 1.20 / 0.450 / 0.300; 25%: 2.15 / 0.343 / 0.232;
  50%: 3.10 / 0.235 / 0.165; 75%: 4.05 / 0.128 / 0.097; 100%: 5.00 / 0.020 / 0.030. `ExperimentsAssetsTest` checks that
  MUZZLE + speed x life = Range (5), that the ring is hollow and X = Y, that the pull ends the radius between 0 and 0.05, that the size shrinks to
  at most 0.05 and that the colours have no red.
**Could make:** a ring that narrows in a straight cone to a point at 5 blocks while the particles shrink; all of it loaded without any
warning (an unknown key would be logged as "Unused key(s)", none was). **Could not make (or not confirm):** (1) nothing was *seen*:
whether `Shape`/two-range rings mean what the engine's text says, whether the radial impulse acts in the system's frame (rotated with
the player's look) and not in the world's, and that the default velocity direction of the system is the beam's direction (inferred
from the vanilla flame, which is placed the same way). (2) A true lens focus: particles follow straight paths to one point; light
and depth are not modelled, and a cone of 40 dots is a stream, not a solid line. (3) The muzzle is at the vanilla flame's position
relative to the player, not the model's barrel node; the barrel is probably a little different. If the beam appears mirrored, shifted
or pointing the wrong way, MUZZLE and the yaw/pitch in `Arrakis_Exp_Flame_Burn.json` are the knobs.
If `RadialImpulse` on a particle after it crosses the axis misbehaves, the fallback is `UseEmitDirection` with a negative speed (not
tried).

**Confirmed vs inferred.** Confirmed in a headless boot: everything loads without a single warning from the experiment (item, root,
both interactions, system, both spawners, texture, `$Comment` in every asset type), `Module experiments: setup ok`, `/arrakis modules`
lists it, the self-check has no PROBLEM line (the item has no light or sound; the stand-in pickaxe has 16 gather specs), disabling it works
as described, the removal rehearsal, and the unit tests (38 in all: the 20 from before, `ExperimentsAssetsTest` 7, `ExperimentsIsolationTest` 7, `ExperimentsDrillAssetsTest` 4).
`ExperimentsIsolationTest` was also tried on the real project with a `power` file importing the experiments package: it and
`ModuleBoundaryTest` both failed naming the file and line; reverted. Inferred, not seen: everything the beam does on a screen, the tick
in play (cutting, Mana per second, Creative), whether the repeated 0.1 s `Burn` flickers.

**Needs a human on a client** (a new world, or delete `Equipment_of_Arrakis.json` in the world's mod folder, or add `"experiments"` to its
`Modules`; a config written before this module existed does not have it): take the item from the IslandCraft tab, Tools (or `/give`);
no red on the model (the flask should be steel blue), no light, no sound; hold Primary: a wide stream of large dots that narrows and
shrinks to a fine white-yellow point about 5 blocks out, not growing or reddening, no flicker at the muzzle; a rock within 5 blocks
cracks and breaks like under a pickaxe, a block at 6 does not; no fire anywhere; a creature hit by the beam takes nothing and is not
pushed; Mana drops about 3.3 per second while cutting; at 0 Mana nothing shows and nothing breaks; in Creative blocks break at once and
no Mana is spent; size, colour, ring radius, muzzle position are the settings of `make_flame_focus.js`.

### Experiment 2: the Drill Cutter

What was built: `Arrakis_Exp_Drill_Cutter` ("EXPERIMENT Drill Cutter", IslandCraft tab, Tools): a clone of `Tool_Drill_Scrap` with the same model,
texture, `Drill` animations, icon, tags, drop animation and icon placement (all by vanilla path; nothing copied), and no recipe, no
sound set. Primary and Secondary are both the root `Arrakis_Exp_Drill_Mine` (cooldown 0.3 s, like the vanilla drill) =
[`Arrakis_Exp_Drill_Gate`, `Arrakis_Exp_Drill_Swing`]. `Arrakis_Exp_Drill_Swing` is the vanilla `Drill_Mine` chain (Drill animation, a
Parallel with the 0.05 s break delay into `Arrakis_Exp_Drill_Block_Break`, a copy of `Drill_Block_Break`: UseBlock, else BreakBlock with tool
`Pickaxe` on the target the client sends, then the vanilla `Drill_Mine` / `Drill_Spin` particles at the `DrillHead` node and the
`Pickaxe_Mine_Impact` / `Drill_Mine` camera effects) without its sound effect (`Drill_Spin_Source`, only sounds) and without the `Stab`
selector and `Drill_Mine_Damage` (nothing hurts a creature), and with the beam as a branch of its own (`Arrakis_Exp_Drill_Beam`).
The only Java is `ExpManaGateInteraction` (type `Arrakis_Exp_Mana_Gate`): there is no tick of ours here to pay in. `tools/assets/experiments/make_drill_cutter.js`
writes the item and the beam; `measure_nature_beam.js` measures the beam. `ExperimentsDrillAssetsTest` (4 tests) checks the data.

**Strongest pickaxe, re-run on 0.7.0** (the same `compare_pickaxes.js` as experiment 1): `Tool_Pickaxe_Mithril` still dominates all ten pickaxes
in every gather type (Rocks 1.0 quality 6, Metals 0.5, Soils 1.0, ores 0.25 to 1.0). Its 16 `Specs` are copied into the item as data by the generator
and compared at boot against the live Mithril item (**confirmed**: `tool specs: 16, same as the Mithril pickaxe's: true`). The vanilla drill's own
Specs are weak (Rocks 0.25 quality 1), so without this replacement it would not cut stone of higher quality at all.

**Single block, no wear.** `BreakShape` Box 1x1x1, centred, no offset (vanilla: 2x2, offset Y -1): `forEachLocalOffset` visits exactly 1 cell
(**confirmed** at boot). No `MaxDurability` and no `DurabilityLossBlockTypes`: a stack of the item is unbreakable and the item's max durability is 0
(**confirmed** at boot, `ItemStack.isUnbreakable()` true). That the held item stays at full condition after real mining is **inferred** (needs a client).

**Range: does BreakBlock honour a 5 block UseDistance?** Yes, by data, nothing else needed; read from the bytecode: `InteractionConfiguration` has
`UseDistance` per game mode (keys `Adventure` and `Creative` only; defaults 5.0 and 6.0, the drill's 1.75 overrides Adventure only). It is sent to the
client in the item packet (the client picks the target block, which is why the interaction is in the `client` package and reads the block from the client's sync data
with `UseLatestTarget`), and the server checks it again in `SimpleBlockInteraction` through `InteractionValidation.canEntityInteractWithBlock`
("failed block interaction distance check" in the log), which uses the held item's `getUseDistance(gameMode)` plus a small buffer; in Creative it also
allows the player's own creative interaction distance. So both keys are set to 5 (**confirmed** at boot: `use distance: Adventure 5.0, Creative 5.0`),
no selector length and no other distance appear in the chain. `BreakBlockInteraction` itself has no distance logic. That a 5 block block is really
targeted and broken is **inferred** (needs a client).

**Mana gate placement, and what the chain does on a refusal.** `Arrakis_Exp_Drill_Gate` is the FIRST interaction of the root, in front of the swing. The
engine runs the interactions of a root one after the other and ends the chain when one ends as Failed (`Interaction.tick` throws `ChainCancelledException`,
as v1 verified), so on a refusal the swing, the animation, the beam and the mining never start. Why it cannot sit inside the `Parallel`: the bytecode
of `ParallelInteraction` shows each branch is started with `InteractionContext.fork` / `execute` as a chain of its own, so a Failed branch does not
cancel its siblings (the spin, the beam and the mining would go on). A gate in front is the only place that stops all of them. The gate
pays `ManaPerSecond * Seconds` = 3.3 x 0.3 = 0.99 per run of the chain (Creative pays nothing); with the 0.3 s cooldown that is 3.3 Mana per second while held.
Unlike the tick of experiment 1 it cannot see whether a block was in reach or was mined, so every swing costs, also in the air. **Not run** (no player headlessly):
the refusal at 0 Mana is **inferred** from the bytecode and from v1's behaviour, needs a client.

**The laser: how far the NatureBeam really reaches, and how it was aligned.** `NatureBeam` (`Server/Particles/_Test/NatureRnD`) is referenced by id, never
copied; it sits in a test folder, so **a game update may remove it** (the boot self-check prints a PROBLEM line when it is gone, and the item then
simply has no beam). The entries are Stoneskin's, in `Arrakis_Exp_Drill_Beam.json`. `measure_nature_beam.js` reads its eight spawners and works out where
their particles can be along the beam (the Pitch 180 turns the local Z around, so a particle at local z is `Z - z` forward of the attachment point, and the
default particle direction runs forward): with the vanilla Z 3.2 the **Start** burst is at 1.2 blocks, the **Core** (the green leaf stream, 200 per second)
spawns from 1.2 to 3.2 and drifts up to 1.6 further, the **End** burst spawns from 4.2 to 5.2 and drifts up to 1 block (centre of mass 4.97), the **Beam** quad
is a single 0.2 s particle in the middle, and the **Sparks** (damped, falling) can fly out to about 8 at the far edge. So the beam is about 1.2 to 5.2
blocks long, with the sprays at both ends; vanilla's Z 3.2 (Z 3.0 in the first-person entry, which would end at 4.8) already puts the end burst at the 5 block
mark where the cut happens, so the entries keep Z 3.2 for both (a single `BEAM_Z` in the generator moves both; no `Scale` is used). The sideways offsets are
Stoneskin's (-0.5 third person; -0.2, Y 1.5 first person), which centre the beam on the player's line of sight; the first-person beam therefore runs along the
crosshair from just in front of the face, not out of the drill head. If it should leave the drill instead, tilt it as the Flame Cutter's focus was
(`RotationOffset` Yaw and Pitch around the 180) or attach it to the `DrillHead` node: **needs a client**. All of this is inferred from the data; the look is **not seen**.
Colour: the vanilla green (`#346830` and darker in the spawners) is left alone; `BEAM_COLOR` in the generator writes `Color` on both entries. Neither an amber
nor a white-blue tint was tried on a screen (needs a client), so there is no verdict on them; the entry's `Color` loads without a warning, but whether it
multiplies the green (which would make amber brown and white-blue grey) or replaces it is not known: try `#ffb347` and `#9fd8ff` and tell me.
The beam is its own interaction, `RunTime` 0.3 (the cooldown), in a Parallel branch after the gate, so it shows for exactly the runs the gate let through
and is continuous while the button is held; with no Mana nothing shows (**inferred**).

**Sounds and creatures.** None of the item's own: the chain has no sound key (`ExperimentsDrillAssetsTest` rejects any key or value mentioning Sound, Damage,
Selector, ApplyEffect, Knockback, Fire, PlaceFluid, Projectile), the item has no `ItemSoundSetId`. The block's own break sound is the engine's and `BreakBlock` has no
setting to switch it off, so breaking a block may still be heard: **needs a client**. No selector, no damage: a creature in the way is not hurt.

#### The two routes compared (a deliverable)

| | Flame Cutter (experiment 1): custom `performBlockDamage` | Drill Cutter (experiment 2): native `BreakBlock` |
|---|---|---|
| Who picks the block | the server, a ray from the player's look each tick (`TargetUtil.getTargetBlock`, Range as data) | the client picks it, the server validates it against the item's `UseDistance` |
| Rate | a tick every 0.1 s, damage `0.24` per tick = 2.4 scale per second = one Mithril swing per 0.4167 s (v1's measurement) | one `BreakBlock` per run of the chain, a run every 0.3 s (the cooldown); each call is a full swing of the item's Specs |
| Mana | 0.33 per tick = 3.3 per second, paid only when a block is in range; one pickaxe swing costs about 1.4 Mana | 0.99 per run = 3.3 per second, paid for every run, also in the air; about 1 Mana per swing |
| Mining speed per Mana | on paper the same order: both ask 3.3 per second, speeds are pickaxe-like vs up to 3.3 swings/s (**not measured**; needs a client) | |
| Refusal at 0 Mana | first in the chain: Failed cancels the animation and beam after it | same, a gate in front of everything (a gate inside the Parallel would not stop the siblings) |
| What it needs from the engine | custom Java: the section lookup, the stand-in stack, `performBlockDamage` / `performBlockBreak` signatures that changed in 0.7.0 (v1 needed a migration) | data only, apart from the Mana gate; the signatures are the engine's own |
| Tool rules | emulated: tool id `Pickaxe`, a stand-in stack, a damage scale | real: the item's own `Tool.Specs`, `BreakShape`, `BreakShapeDurabilityMode`; a different shape or a mining tier is a data edit |
| Protection and events | goes through the engine's `performBlockDamage` / `performBlockBreak`; the interaction's own checks (distance, world rules) are not done | the interaction validates distance, and checks the world's rules (`isBlockBreakingAllowed`, `isBlockGatheringAllowed`) and pickup before breaking |
| Feels like cutting with a beam? | a continuous 0.1 s drain, the crack grows smoothly; the beam has no idea what is cut (the particles are placed by data) | discrete 0.3 s swings like a drill, the crack in jumps; the beam is decoration next to the spinning drill head |
| Not mineable blocks | cost Mana, nothing happens | cost Mana, the swing and the drill particles play |

**Recommendation (provisional: neither route has been run on a client).** Build the final Cutter Ray on the **native route**: the engine does the mining, so the
tool rules, the break events, the world's protection rules and the client's own block targeting come for free, and a later change of tier or shape is
data. The Java it needs is one small, stable gate, and the signatures that broke between 0.6.8 and 0.7.0 (v1's costly migration) are not in the mod at all.
Keep the custom route for one case: if a client test shows that 0.3 s swings do not read as a beam, a continuous tick like experiment 1's is the way to get
a smooth crack and Mana paid only when something is cut, and it can be added on top of the native item (the same Specs) without throwing the native work away.
What decides it is the feel on a client, so: hold both items against the same rock and compare how quickly it breaks, how the crack grows, and whether the
swing rhythm of the drill fits a laser.
