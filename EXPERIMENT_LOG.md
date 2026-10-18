# IslandCraft - Equipment of Arrakis, v2: experiment log

v1 (the Cutter Ray, guns, off-hand tools, Holtzman shield, Arrakis_Power stat and HUD) is thrown away; `git show equipment-v1-final:<path>`
reads any v1 file. v2 is built in modules, one package and one asset folder each: core, power, catalog (this prompt), then cutter,
compactor, litrejon, harvester, bloodbag, extractor, placeholders, light, shield (built, see "Module notes"), then weapons, stillsuit, armor.

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

## Module notes: the v2.1 tool set

Everything below was built on the foundation (core, power, catalog). **confirmed** = seen in a headless boot, in a unit test or in bytecode with javap;
**inferred** = reasoned from bytecode or vanilla data; **needs a client** = only a player on a client can show it. Nothing client-side could be tried here,
and no player or mob can be spawned headlessly, so every behaviour that needs one is on the human test sheet at the end.

**Order of the work (one commit each, `gradlew test` green before the next):** core additions and the cleanup of the earlier picker trial; picker; cutter; compactor;
litrejon + harvester; bloodbag; extractor; placeholders + light; shield. Each new module has an id in the config `Modules` list and its own section in
`Equipment_of_Arrakis.json` where it has settings. The headless boot loads all of them with no PROBLEM line, with and without the Water mod, and with the new modules
removed from `Modules` (each removes its own assets at BootEvent, see `ModuleAssets`).

### core additions

- `ToolRegistry` (picker tools by item id, display order, optional label key; detection is by this list, never by a prefix), `ToggleRegistry` (passive toggles: id, order, label
  key, icon item id, switch / read / reset callbacks; `forget(uuid)` runs every reset), `GiveRegistry` (what `/arrakis tools give` hands out: the six tools, the LitreJon and the
  Blood Bag, containers through their own factory so they start with their bar), `ToolUse.fired(PlayerRef)` with listeners (the shield listens; only the Cutter Ray calls it),
  `ContainerItem` (amount in stack metadata under a key, mirrored to the durability bar: max durability = capacity, durability = amount; pure clamp / add / room, unit-tested),
  `ContainerInventory` (carried containers, first with room: hotbar then main inventory, never the backpack or the utility slots; add to the first with room; registered by item id so
  the Harvester fills the LitreJon and the Extractor fills the Blood Bag without importing those modules), `Notice` (a short chat line from a translation key, at most one per
  second per player and key), `ModuleAssets` (switching a module off), shared `/arrakis` command groups (`CommandRoot.group`, attached after setup), `WorldAccess.healthSection`.
- power: `ManaRefusal.say(player, n)`: "This needs N Mana.", at most one per second per player, switched by the Power setting `ShowMessages` (default true).
  The Mana regeneration is untouched (the boot log and the hook in `PowerModule.start` stay).
- The toggles are reset on disconnect, on entering or leaving a world (`AddPlayerToWorldEvent`, `DrainPlayerFromWorldEvent`) and on death (`ToggleResetSystem`, once per death).
- Unit tests (engine-free): registry order and replace, toggle forget even after a failing hook, ToolUse listeners, ContainerItem clamp / add / room, first-with-room selection,
  Notice and ManaRefusal throttles. The stack side of `ContainerItem` (metadata and bar on a real `ItemStack`) cannot run in a unit test (the `ItemStack` class needs the engine's
  assets); it is exercised by `/arrakis tools give` and `/arrakis blood add` in the game. A raw stack taken from the creative menu has no saved amount, so it reads as
  empty (`StartsFull` false) but the bar shows full until the first change; `/arrakis tools give` makes proper ones.

### picker

- The earlier picker trial (page, swap rule, three `.ui` files, the `OpenCustomUI` interaction registered with `OpenCustomUIInteraction.registerSimple`) became this module:
  `ToolPicker` (page + swap adapter), `ToolSwap` (pure rule, 10 tests), `PickerCommands`, assets `Arrakis_Picker_Open` / `Arrakis_Picker_Secondary`, `Arrakis_Picker_{Page,Entry,Toggle}.ui`.
  Every tool's `Interactions.Secondary` is `Arrakis_Picker_Secondary`.
- Lists the registered tools found in the hotbar and the main inventory (not the backpack, not the utility container) in registry order, the held one marked "In hand" and not
  clickable, then always every registered toggle with ON / OFF. A toggle click flips it, sends "<label>: ON" and rebuilds (page stays open); a tool click validates against the
  inventory as it is now, does one `ItemContainer.swapItems` (the chosen tool into the active hotbar slot, the old one into the slot the chosen one came from; metadata travels
  with the stack; no free slot needed) and closes; if the inventory changed it says so and swaps nothing.
- `/arrakis tools give | state | open`. `state` prints the held item, the tools found with container and slot, the toggles and Mana.
- The page opens only from a held Arrakis tool because only those tools carry the Secondary; the engine interaction refuses to open over another custom page (bytecode).
- **needs a client**: that the `.ui` files parse and look right, the cursor, the swap latency and the refresh of the held model and the bar.

### cutter (Cutter Ray)

- Item `Arrakis_Tool_Cutter_Ray`: the vanilla `Weapon_Gun` (Pulse Rifle) by path (model, texture, animation set with the Shoot animations at speed 3, icon). Primary = `Arrakis_Cutter_Primary`
  = [`Arrakis_Cutter_Shot`, `Arrakis_Cutter_Effects`]. `Arrakis_Cutter_Effects` carries the vanilla shot feel: the `Shoot` item animation, the `Handgun_Shoot` camera effect, the `RifleShooting`
  muzzle particles at the vanilla offsets, `RunTime 0.25` (the vanilla `Gun_Shoot` pace, the `ShotInterval`). No projectile, no sound added.
- `Arrakis_Cutter_Shot` (custom type, `CutterShotInteraction`, first in the chain so a refusal, which ends it as Failed, stops the effects from ever starting): needs `ManaPerShot` 10
  (below that: no shot, no effects, the Mana refusal line), spends it (creative pays none), casts `TargetUtil.getTargetBlock` from the eye `Range` 5, and hits the block with
  `BlockHarvestUtils.performBlockDamage` with an unbreakable stand-in `Tool_Pickaxe_Crude`, tool id `Pickaxe`, `DamageScale 1`. Creative breaks the block at once. Then `ToolUse.fired`.
  It spends on every shot, with or without a block in reach. Gate unit-tested at Mana 9 / 10 / 100 and creative; 10 shots from full Mana.
- **How the damage was matched to the crude pickaxe:** the vanilla `BreakBlockInteraction` calls the same `performBlockDamage` with the held tool and a damage scale pushed with
  `fconst_1` (read in its bytecode, both call sites), so one real swing is scale 1.0 of the held tool's own gather specs, and v1 measured the same (0.24 per 0.1 s = one swing
  per 0.4167 s). The shot uses scale 1.0 with the crude pickaxe stack, so a shot is one swing by construction, and the crack accumulates on the block as with the pickaxe.
  **confirmed**: the bytecode and the compile; **not measured**: shots-to-break on sand, stone and an ore against the real crude pickaxe (that needs a player to swing the pickaxe):
  on the test sheet, the numbers must match.
- Settings are data in `Arrakis_Cutter_Shot.json` (`ManaPerShot`, `Range`, `DamageScale`, `ToolItemId`, `Silent`) and `Arrakis_Cutter_Effects.json` (`RunTime`).

### compactor

- v1 ported: model, texture, aim animation (first and third person), the `Arrakis_Compactor` animation set (parent `Torch`), the hum effect and its two sounds, the sand-dust particle,
  `CompactMath` + `BeamMath` (tests carried over), `CompactorInteraction` (the vanilla `Charging` interaction with a compacting tick), `CompactorTrail` (crack, repair, drop, hum),
  `CompactorSystem`. Now Primary (left click held), no `Utility.Usable`, no `RequireNewClick`, in the hand only. 1 Mana per second while really compacting (`PowerPerSecond 1`,
  spent in proportion to the tick time), stops at 0 Mana (creative never runs out), no `ToolUse.fired`. Any other block or no target: nothing, no Mana. Range 3, `CompactSeconds` 1,
  groups `Sand`, exclusions `Arrakis_Sand`, `Arrakis_Worm_Ripple_Layer_`, `Arrakis_Worm_Sunk_`, all data in `Arrakis_Compactor_Aim.json`.
- The block is never broken: the crack is the block health lowered to at most 90 % and given back at the end of each cycle (v1 logic unchanged).
- **needs a client**: the aim pose as a main-hand item (the set is the off-hand torch set), the hum, the dust, and that holding left click keeps the `Charging` interaction going.

### litrejon (LitreJon) and harvester (Dew Harvester)

- `Arrakis_Tool_Litrejon`: its own item, a second flask next to the Water mod's `Arrakis_Litrejon` (which is untouched, loaded and working next to it: the headless boot with both
  mods lists both items). Model and icon: `Potion_Template` parent, the Energy Potion icon and the Cyan texture, by path. Metadata `Arrakis_Water_Units`, `Capacity 500`,
  `StartsFull false`, `PourPerClick 50` (section `Litrejon`), the bar via `ContainerItem`. Left click = `Arrakis_Litrejon_Pour`, repeating every 0.5 s while held: moves
  min(flask, 50, what the player lacks) into the player's water; a player 19 below the cap takes 19 and the flask loses 19 (a fractional shortfall is offered rounded up and the flask
  pays for the water really gained, rounded up; the Water mod clamps at 100). Empty flask or full player: a short message. Right click opens the picker, so filling from water blocks
  is not available on this item; the Dew Harvester fills it (an open point for you).
- Water link: `WaterApi` / `WaterBridge` / `WaterLink` as in v1 (compiled against the Water jar `compileOnly`, never packed; `-PwaterJar=` points at another copy; the bridge class is
  loaded only after the plugin is found, inside a handler for linkage errors). The manifest lists Water as an **optional** dependency, not a required one: a required one would
  stop the whole mod from loading without Water, and the brief wants the LitreJon and the Harvester to degrade. **confirmed headless:** with Water loaded the log says
  "Water of Arrakis linked" and "the Water mod's own Arrakis_Litrejon is loaded and untouched"; without it, "Water of Arrakis not loaded: the LitreJon will refuse to pour (everything
  else works)", and a click says "Water of Arrakis is needed to pour from the LitreJon." The picker lists only `Arrakis_Tool_Litrejon` (it is the only one registered) and the Harvester
  fills only it (it looks the item up in `ContainerInventory` by that id).
- **What a later Water prompt would remove if the old item is retired:** `Items/Water_of_Arrakis/Arrakis_Litrejon.json`, the interactions `Arrakis_Condition_Flask` and `Arrakis_Flask_Charge`
  (and the `Arrakis_Root_Flask` root they feed), `FlaskInteraction`, the `Litrejon` class with its `FillSource` / `addFillSource` hook, the Litrejon settings in `WaterOfArrakisConfig`
  (`LitrejonCapacity`, `LitrejonDrinkAmount`, `LitrejonStartsFull`, the fill reach and water fluid ids), the references in `WaterOfArrakisPlugin` and `SunProbeCommand`, and the
  `flaskFilled` / `flaskEmpty` / `notThirsty` lines of its `server.lang`.
- Dew Harvester `Arrakis_Tool_Dew_Harvester`: the vanilla `Tool_Sickle_Crude` by path (model, texture, icon, animation set `Sickle`), no recipe, no durability. Primary =
  [`Arrakis_Harvester_Gate`, `Arrakis_Harvester_Swing`, `Arrakis_Harvester_Finish`], the v1 Dew Reaper's gate / swing / finish. The gate refuses (nothing spent, a short message)
  unless the block under the crosshair within 3 is `Arrakis_Primrose`, a LitreJon is carried, one has room and there are 10 Mana; otherwise it pays 10 Mana, starts the hum and remembers
  the swing. The swing is a 1 second `Simple` (the sickle's `SwingLeftSlow` animation). The finish moves 10 water into the first LitreJon with room (hotbar, then main inventory) if the
  Primrose is still there. The Primrose is not touched, there is no cooldown, no evening window and no cap, the player's own water is not touched. Held, the chain repeats. Rules
  unit-tested (order of the refusals, exact cost, creative). Without the Water mod there is no Primrose to aim at and no LitreJon to pour from; the tool says "Aim at a Primrose."
  **needs a client**: the sickle animation length (1 s is the interaction's time, not measured against the animation), the hum.

### bloodbag and extractor

- `Arrakis_Blood_Bag`: parent `Potion_Template`, Red texture by path, icon `Potion_Health`; metadata `Arrakis_Blood_Units`, `Capacity 500`, `StartsFull false` (section `Bloodbag`); Primary and
  Secondary are a root that does nothing (so the inherited drink is gone). Not registered as a picker tool. API: `BloodBag.isBag / getAmount / add / firstWithRoom / carried / capacity`
  for the future purifier. `/arrakis blood add <n>` fills the first bag with room.
- `Arrakis_Tool_Blood_Extractor`: the vanilla Handgun by path (model, texture, icon, animation set `Handgun`), no shot at all. Primary = [`Arrakis_Extractor_Gate`,
  `Arrakis_Extractor_Poke`, `Arrakis_Extractor_Finish`]. Refusals before any animation, nothing spent, a short message, in this order: no corpse in range and aim; already used; no bag;
  all bags full. No Mana. The gate claims the corpse, forces the crouch and remembers the poke; the poke is a 1 s `Simple` (`PokeSeconds`) playing the Handgun set's **`GuardBash`** item
  animation (the vanilla pistol jab, an existing animation, nothing custom); the finish lets the crouch go and puts `BloodPerBody` 10 into the first bag with room.
- **What a dead mob is, in 0.7.0 (bytecode):** a killed NPC keeps its entity. `DeathComponent` is added to it; `NPCSystems.OnDeathSystem` then adds `DeferredCorpseRemoval(role.getDeathAnimationTime(),
  deathParticles)`, which `DeathSystems.TickCorpseRemoval` counts down, and `CorpseRemoval` removes the entity when it is over. So a corpse exists only for the role's death animation time
  (a second or a few, per role) and then vanishes, as the brief expected. The rule is `CorpseAim.isCorpse`: not a player, has a `DeathComponent`, and (data `RequireNpc`, default true) an `NPCEntity`.
  Widen it by setting `RequireNpc` false in `Arrakis_Extractor_Gate.json`, or by editing the one pure method.
- **How a corpse is found:** not with `TargetUtil.getTargetEntity` (a dead entity may no longer have a hitbox the engine ray takes; not tried). `TargetUtil.getAllEntitiesInSphere` around the eye,
  filtered by the rule, then a pure ray test (`CorpseAim.pick`): the nearest corpse whose aim point (feet + `AimHeight` 0.5) is within `AimRadius` 0.9 of the look ray and `Range` 2.5 along it.
  Unit-tested. **inferred**: that the radius and height suit every mob size (they are data).
- **One body, one use:** the corpse's entity ref goes into a set at the gate (claimed), so nobody can use it again; refs of removed entities are dropped from the set, so it clears itself when the body
  vanishes. A poke that does not reach its finish (button released, player left) gives the corpse back. This is a runtime set, not an ECS marker component on the entity (registering a
  component type was not worth the risk for a tool that is to be refined); the effect asked for is the same.
- **Forcing the crouch:** the client owns the movement state (it sends its `MovementStates` every tick; `MovementEffects` can only disable crouch, and the `SetMovementStates` packet
  carries only the flying flag). What is possible from the server is writing the server-side `MovementStatesComponent`: `crouching = true` and `forcedCrouching = true`, every tick of the
  poke (`ExtractorSystem`), restored at the end; the engine's movement-states system sends that state to the players who can see this one, so **others** see the crouch. Whether the owner's own client
  overrides it in between is **needs a client**. Fallback, if it does: set `ForceCrouch` false and `RequireCrouched` true in `Arrakis_Extractor_Gate.json` and the tool refuses unless the player is
  already crouching ("Crouch first."). The default is forcing.
- `/arrakis blood corpse` spawns a cow two blocks ahead (`NPCPlugin.spawnNPC`) and kills it with a `DeathComponent` (`COMMAND` damage); it vanishes within seconds like a real corpse.
  Spawning an NPC failed in the headless world (no player), so this is **needs a client**.

### placeholders, light, shield

- Placeholders: `Arrakis_Tool_Vehicle_Summoner`, `Arrakis_Tool_Build_Repair`: the crude pickaxe's model, texture, icon and animation set `Pickaxe` by path, no `Tool` block, no Primary (the engine fills it
  with its own empty interaction: confirmed in the self-check), no durability, Secondary = the picker, descriptions "Not built yet.", registered in the picker.
- Personal Light (`light`): a toggle on the page, no item, no cost. The light is the entity component `DynamicLight(ColorLight)` (the component the engine gives a dropped torch; the light of a held
  torch is made by the client from the block's Light), added while the toggle is ON and removed when OFF by `Reconcile`, a system that makes the entities match the ON set every tick (so a death, a
  disconnect or a world change cannot leave a stuck light). **How it was matched to a torch:** the torch's light is the `Light` of its block type, `{Radius 0, Color #ba9}`; the module reads
  `BlockType Furniture_Crude_Torch` `.getLight()` at start (**confirmed**: radius 0, colour 11/10/9) and uses its radius and its brightest channel (11) with the tint 0.85 / 0.93 / 1.0 (section
  `Light`: `TintRed/Green/Blue`, or `Radius/Red/Green/Blue` to override each number; `-1` = from the torch). **Final values: radius 0, red 9, green 10, blue 11** (cool white: pale, leaning blue,
  as bright as the torch's brightest channel). That the numbers look the same as a held torch "side by side in the dark" is **needs a client**; the radius reading 0 for the torch is why the
  radius is copied, not chosen. `/arrakis light on | off | state`. Reset to OFF on relog, death, respawn and world change.
- Holtzman Shield (`shield`), ported from v1: the entity effects, ModelVFX, bubble / hit / shatter particles and the eight sounds are byte-for-byte v1 (moved into `Shield` folders; they reference
  vanilla textures and sounds by path, so no art is shipped), `HoltzmanSystem`, `HoltzmanDamageSystem` (in the engine's filter damage group, after the armour reductions, cancels the hit), `HoltzmanField`,
  `ShieldCooldown` (with its modifier hook and override), the two projectile hooks `ShieldShotLegacySystem` / `ShieldShotSystem` (any projectile a player creates) and `/arrakis shield hit | state | shot
  | cooldown | diag`. **Differences from v1:** (1) no item, no Z slot, no off hand: the condition "shield selected" is now "the toggle is ON"; (2) the power is Mana through `Power`, `ManaPerDamage 1.0`,
  creative pays nothing; (3) a hit that takes Mana to 0 or is at least the Mana left is absorbed whole, Mana goes to 0, the shatter burst and break sound play, the field goes down and the **toggle
  stays ON**; the state is "broken" and the field returns by itself, with the normal power-up sound and no message, when Mana is back to `RearmMana` 100 (or to the maximum if that is lower);
  (4) the toggle is not saved: it is OFF after a relog, a world change and death; (5) the Cutter Ray's `ToolUse.fired` drops the field for `CooldownSeconds` 1.0 like any projectile, restarting on each
  shot, with the off / on sounds; the Compactor, Extractor, Harvester and LitreJon never call it; (6) the v1 `RangedWatch` diagnostic (a dev check of Ranged-tagged interactions) and the hook
  self-test were not ported; `/arrakis shield diag` now switches a trace of every projectile creation with its creator. **ON with less than 100 Mana:** the field rises with any Mana above 0
  unless the shield has just broken and is waiting (the v1 rule); with 0 Mana it does not rise; toggling OFF and ON does not clear a break (only a death, a relog or a world change does), so the
  wait for 100 cannot be skipped. Rules unit-tested (absorb, break at equal, re-arm at 100, a lowered maximum, drop and restart of the timer, ON with low Mana, a script of the per-tick plan and its sounds).
  **needs a client**: the look and the sound mix (as v1, unchanged), the fall of the field at a Cutter shot, an arrow costing 1 Mana per damage.

### Experiment cleanup, summary

Removed from the throw-away trial module when the picker was promoted: the four stand-in tools (`Arrakis_Tool_Axe`, `_Pickaxe`, `_Hoe`, `_Shovel`: items and translations), the trial
interaction and root interaction of the picker, its command class, and its page and swap classes (moved and renamed into `picker`), the three `.ui`
files (moved and renamed), the edits made to the trial module and to its isolation test (the `Arrakis_Tool_` allow-list and reserved prefix are gone; the prefix now belongs to the real tools),
and `/arrakis exp tools`. The earlier trials 1 to 3 are unchanged. **Removal rehearsal (run for real on a copy after everything above was built):** the removal script of the trial module (see its section, below)
leaves no trace of the trial names in the sources and `gradlew build` passes.

### Human test sheet (a client; `/arrakis tools give` first, survival and creative)

1. Creative menu, IslandCraft tab, Tools: Cutter Ray, Compactor, LitreJon, Dew Harvester, Blood Extractor, Vehicle Summoner, Build/Repair Tool, Blood Bag, each with icon and name; only the two
   containers show a bar (and a raw one shows a full bar until first used); nothing wears or breaks.
2. Picker: right-click any tool: it opens once with the tools you carry (icons, "Hotbar n" / "Inventory", "In hand"), then Holtzman Shield and Personal Light with ON / OFF. Click a tool: right model and
   animation in the hand, the old tool in the slot it came from, works with a full inventory, the LitreJon keeps its water. Escape closes with no change. Note the swap latency and any lag of the held model.
3. Cutter Ray: hold left click: vanilla gun look, 4 shots per second, 10 Mana each, stops after 10 at full Mana, "This needs 10 Mana." (once a second) at 9 or less; mobs are not hurt. Break a sand, a stone and an
   ore block with it and with the real `Tool_Pickaxe_Crude`: count the shots and the swings (they must match).
4. Compactor: hold left click on sand within 3: crack, a drop per second, hum, the block stays; 1 Mana per second; stops at 0; nothing on other blocks.
5. LitreJon: with Water loaded: left click gives up to 50, only what you lack (test at 81 water: 19); the bar follows; empty and full messages; right click opens the picker. Without Water: the message.
6. Dew Harvester: left click on a Primrose with a LitreJon with room: a swing per second, +10 water in the flask, -10 Mana, Primrose unchanged, your water unchanged; no Mana, no flask, full flask, other block:
   no swing and a message.
7. Blood Extractor: `/arrakis blood corpse`, left click on it within 2.5: crouch (do you and others see it? does your own client fight it?), the jab, +10 blood in the first bag; the same corpse again: refused;
   no bag or full bags: refused with no animation; no Mana spent. `/arrakis blood add 495` fills a bag to 500.
8. Holtzman: ON from the picker: field, hum and power-up sound as v1; `/arrakis shield hit 20` costs 20 Mana; firing the Cutter drops it for 1 s and it returns; `/arrakis shield hit 500` breaks it with the
   shatter, the toggle stays ON, it returns by itself at 100 Mana; OFF removes everything; relog and death leave it OFF.
9. Personal Light: ON gives a torch-equivalent cool white light that other players see (compare with a held `Furniture_Crude_Torch` in the dark); OFF removes it; relog and death leave it OFF.
10. Vehicle Summoner and Build/Repair: appear in the picker, show a pickaxe, left click does nothing.

## Module notes: the weapons and the H key

Same labels as above: **confirmed** (headless boot, unit test, javap), **inferred** (reasoned from bytecode or vanilla data), **needs a client**. Two commits: core (the toggle key), then the
`weapons` module. The headless boot loads all ten weapons with no PROBLEM line, with the module on, and with `weapons` and `shield` both removed from `Modules` (the weapons module removes
21 assets of its own; the toggle interaction stays and does nothing without the shield).

### core: the Ability4 toggle (the H key)

- `ToggleRegistry.press(id, store, ref, player, now, minGap)` is the one way a player flips a toggle: the picker page calls it with no debounce, the key through `Arrakis_Toggle` with 0.2 s.
  Outcomes `UNKNOWN` (nothing registered under the id: the module is off) / `DEBOUNCED` / `ON` / `OFF`. A press with gap 0 leaves no debounce stamp, so a click and then H are independent.
  The shield toggle id is now `Holtzman` (was `shield`); the shield module's asset guard was narrowed to ids starting `Arrakis_Holtzman_` / `SFX_Arrakis_Holtzman_`, because it used to drop
  anything containing "Holtzman", which would have dropped the core root with the shield module.
- Interaction type `Arrakis_Toggle` (`ToggleInteraction`, registered by the core module): fields `ToggleId`, `DebounceSeconds`. Interaction asset `Arrakis_Toggle_Holtzman`
  (`ToggleId Holtzman`), root `Arrakis_Root_Toggle_Holtzman`: `RequireNewClick`, `Cooldown 0.2`, `Rules {"BlockedBy": []}`. It sends the same chat line as the page ("Holtzman Shield: ON") and logs
  one INFO line per press that arrives (`Arrakis_Toggle arrived for <name> holding <item>: toggle Holtzman -> ON/OFF/DEBOUNCED/UNKNOWN`); `/arrakis weapons trace off` silences it.
  No item glow, particle or HUD was added: the bubble on the character is the only indicator.
- `Ability4 = Arrakis_Root_Toggle_Holtzman` is on the seven hand tools (Cutter Ray, Blood Extractor, Compactor, Dew Harvester, LitreJon, Vehicle Summoner, Build/Repair Tool) and on the ten
  weapons; not on the Blood Bag (unit test and the boot log). Their right click is unchanged: the picker on tools, the vanilla guard / bash on weapons.

**Gate analysis: what can stop an Ability4 chain.** The client binds H to Ability4 (the user's finding). On the server (javap of `InteractionManager`, `InteractionRules`, `InteractionTypeUtils`):
1. **The item must have an Ability4 entry.** `Item.getInteractions()` is looked up by type; no entry, nothing runs. (confirmed: the boot log lists `Ability4=Arrakis_Root_Toggle_Holtzman` on all 17.)
2. **Nothing unlocks it.** There is no ability-unlock list or client setting in the server path. The vanilla signature gate is not on the slot: it is the first interaction of the Ability1 chain, a
   `StatsCondition` with `Costs {SignatureEnergy: 100}` (`Weapon_Sword_Signature_Vortexstrike`), and the `Weapon` block's `StatModifiers` / `EntityStatsToClear` only fill and clear that stat; the glow is
   `ItemAppearanceConditions`. Our root has none of these. (confirmed from the vanilla data and the bytecode.)
3. **The root cooldown.** `RootInteraction.Cooldown` is per root; with none the engine uses a default 0.35 s (`InteractionTypeUtils.DEFAULT_COOLDOWN`). Ours is set to 0.2 s, which is the debounce.
4. **The default rules block it while another hand chain runs.** `InteractionTypeUtils.DEFAULT_INTERACTION_BLOCKED_BY` makes every standard input type (Primary, Secondary, Ability1 to 4, Use, Pick, SwapTo,
   SwapFrom) blocked by every other one while its chain is running, so by default H pressed during a swing, a shot or a held guard is silently dropped (the swing is not cancelled, but H is lost). We set
   `Rules.BlockedBy` to an empty list on the root (the boot log shows `blockedBy=[]`, `interrupting=null`, `interruptedBy=null`), so H works mid-swing and mid-shot and cancels neither (nothing interrupts:
   the codec fields `Interrupting` / `InterruptedBy` are unset). **Inferred:** the other direction (a click arriving while the instant toggle chain is alive) is blocked by the click's default rules for the
   one tick the toggle lives; it is on the test sheet.
5. **Not verifiable here:** whether the client sends Ability4 at all for each kind of item, and whether it does with a page open (a client concern; the server never sees it). The result of pressing H on a
   melee weapon, a gun and a tool is therefore **needs a client**: press H and read the log line above; if it never appears for an item kind, the client is not sending it for that kind (a missing piece
   would then be on the client side, not in the item data, and this note must be updated with what it was). Holding the Blood Bag or nothing: the item has no Ability4, so nothing arrives (by design).

### weapons

- **Melee (8), built as children:** `Arrakis_Weapon_Sword_{Crude,Atreides,Choam,Harkonnen}` and `Arrakis_Weapon_Daggers_{...}`. Each has `Parent: Weapon_Sword_Crude` (or `Weapon_Daggers_Crude`), so the whole
  `InteractionVars` (every damage number, `DamageEffects`, swing effects and sounds), `ItemSoundSetId`, the `Weapon` block (signature energy), the appearance conditions and the icon properties are the Crude
  item's and the template's, byte for byte, with nothing copied. Overridden by `tools/assets/weapons/make_weapons.js` (it reads the vanilla files from Assets.zip): `Model`, `Texture`, `Icon`, `Quality` of the
  house's own vanilla tier (Atreides = Thorium, Choam = Cobalt, Harkonnen = Adamantite; Crude = Crude), `Recipe: null`, `MaxDurability 0`, `DurabilityLossOnHit 0`, `MaxStack 1`, `Categories
  ["IslandCraft.Weapons"]`, the names, and the `Interactions` map written out in full (the template's Primary, Secondary, Ability1, plus Ability4) so no merge rule has to be trusted.
  **Proof that all eight deal the Crude damage (confirmed):** the unit test requires that no melee file has an `InteractionVars`, `ItemSoundSetId` or `Weapon` of its own and that its `Parent` is the Crude
  item; the boot self-check compares the resolved `Item.getInteractionVars()` map of each of the eight with the Crude item's and prints `InteractionVars same as Weapon_Sword_Crude: true` (swords) and
  `... Weapon_Daggers_Crude: true` (daggers) for all eight (the map holds the interaction ids, so they are the same generated damage interactions). `Recipe: null` removed the inherited recipe
  (`hasRecipesToGenerate` false), durability 0 and the tab are in the same self-check line. Left as vanilla: the Primary swing chain, the Secondary guard / bash and its animation, the Ability1
  signature move, `Utility.Compatible` (swords true, daggers false, from the templates), the animations. Not a picker tool: weapons keep the vanilla right click.
- **Guns (2), ported from v1 (tag equipment-v1-final):** `Arrakis_Weapon_Maula_Pistol` (`Parent: Weapon_Handgun`, `Utility.Compatible false`) and `Arrakis_Weapon_Dartgun` (`Parent: Weapon_Assault_Rifle`,
  two-handed as vanilla). Roots / interactions renamed `Arrakis_Weapon_Maula_*` / `Arrakis_Weapon_Dartgun_*` (Primary, Gate, Shoot). **Kept in the v1 form:** the projectiles `Arrakis_Maula_Dart` /
  `Arrakis_Dartgun_Dart`, the sounds `SFX_Arrakis_Maula_Fire(_Local)` / `SFX_Arrakis_Dartgun_Fire(_Local)` and the particle `Arrakis_Dart_Shot` (smoke only: the vanilla `Rifle_Smoke` spawner; no muzzle
  flash, no brass). The fire sound events are copies of the vanilla ones with the first layer 6 dB quieter (a sound event cannot inherit layers); they name the vanilla clips by path. Behaviour as v1:
  `RequireNewClick`, cooldown 0.33 s on the shared `Shoot` id (about 3 a second at most), dart damage 1, speed 100, the Secondary `Gun_Attack` bash and its animation, the `Handgun_Shoot` camera shake.
  The range rule is ported unchanged (`DartMath`, `DartFlight`, `DartFlightSystem`, `DartRangeSystem`, `DartRangeDamageSystem`): a dart is removed 40 blocks from where it was launched and a hit past
  40 blocks does no damage; the settings are the `Weapons` section (`Range`, `ProjectileIds`). The component is now registered by the module (`ctx.plugin().getEntityStoreRegistry()`).
- **The removed ammo gate and where a cost goes:** `DartAmmoGateInteraction`, `Arrakis_Ammo` and the arrow `ModifyInventory` are gone. Each Primary root still runs one gate interaction,
  `Arrakis_Weapon_<Gun>_Gate` of type `Arrakis_Gun_Gate` with `Cost 0` (generator constant `COST_PER_SHOT`): it calls `GunCost.charge(buffer, shooter, cost)` and goes to the shot, or ends as Failed and runs the
  vanilla dry click `Gun_Shoot_Fail`. `GunCost.charge` returns true for a cost of 0 and false for any cost above 0 (there is no ammo system yet, so a priced gate refuses rather than firing free). An ammo system
  later raises `Cost` and fills in `GunCost.charge`; nothing else changes. No Mana and no ammo are used.
- **Holtzman interplay (inferred, by reading the code; needs a client):** the shield drops on any projectile entity a player creates (`ShieldShotSystem` / `ShieldShotLegacySystem`, plus `ToolUse.fired`); a
  `LaunchProjectile` dart is such an entity with the player as creator, so firing drops the field for 1 s with no extra code. A melee swing creates no projectile and calls no `ToolUse`, so it never drops
  it. `/arrakis shield diag on` logs each projectile and its creator to check this. The weapons module never imports the shield: the key goes through core.
- Operator helpers: `/arrakis weapons give` (all ten), `/arrakis weapons state` (held item, its Ability4, the Holtzman toggle, the trace flag), `/arrakis weapons trace [on|off]`, and the existing
  `/arrakis shield state`.
- Unit tests: `TogglePressTest` (flip, unknown id, module off, debounce window and its edge, picker gap 0, per-player, forget), `DartMathTest` (the v1 range rule), `WeaponAssetsTest` (ids, tab, no recipe, no
  durability, the eight children of the Crude items with no damage of their own, the look of each tier, Primary / Secondary / Ability1 kept, Ability4 on ten weapons and seven tools and absent from the Blood Bag,
  the semi-auto root and the 0.33 s cooldown, the gate cost 0 and its hook, smoke only, dart damage 1 and speed 100, no picker on weapons, the toggle root: 0.2 s, empty `BlockedBy`, no interrupt).
  The catalog test (every item in the tab names an existing child and has an Icon of its own) covers the ten as well. Self-check lines for every item, interaction, root, sound and particle.
  A Projectile asset is not removed when the module is off (`ModuleAssets` does not handle that type); the two dart projectiles stay loaded and unreferenced.

### The missing ability HUD on the weapons (found after the first client test)

A client test showed that holding an Arrakis weapon no longer shows the vanilla weapon HUD on the right of the screen (the ability status and the buttons that activate them). The ability
buttons are wanted later, so the cause was looked for by comparing the resolved Crude daggers and the resolved Arrakis Crude daggers (and the swords) field by field. New dev command
`/arrakis weapons diff <itemA> <itemB>` (`ItemDiff`) prints every field of two loaded items whose values differ. Result for `Weapon_Daggers_Crude` against `Arrakis_Weapon_Daggers_Crude` (**confirmed**,
the swords differ in the same four ways):

| field | vanilla Crude | Arrakis Crude |
|---|---|---|
| `categories` | `[Items.Weapons]` | `[IslandCraft.Weapons]` |
| `interactions` | Primary, Secondary, Ability1, Ability3 (empty), Use, Pick, SwapFrom | the same plus `Ability4 = Arrakis_Root_Toggle_Holtzman` |
| `maxDurability` | 120 | 0 |
| `durabilityLossOnHit` | 0.06 (swords 0.13) | 0 |

Other differences are only the id, the translation keys, the recipe (removed on purpose) and the asset bookkeeping (`data`). Everything else is identical, including `weapon` (signature energy
modifiers), `utility`, `ability`, `tags`, `reticleId`, `carryHudInputBindings`, `displayEntityStatsHUD`, `hudUI`, `itemAppearanceConditions`, `interactionVars` and the model and sounds. So the HUD is
not lost through the Weapon block, the tags, the HUD fields or the interaction variables: the cause is one of the four rows above. **Suspects, in order (inferred, not yet separated):**
1. `categories`: the client may treat the category `Items.Weapons` as "this is a weapon" for the HUD. The catalog needs `IslandCraft.Weapons`; both can be listed, at the price of the item also
   appearing in the vanilla weapons tab.
2. `Ability4` on the item (the H key): the HUD may be laid out from the item's ability slots, and a fourth slot with a root that has no icon or energy could hide it.
3. `maxDurability 0` (a weapon with no durability): least likely, the HUD shows abilities, not wear.
**Next step (needs a client):** three throw-away daggers, one with `Items.Weapons` added to the categories, one without Ability4, one with durability 120, to see which brings the HUD back.

### Human test sheet, weapons and the H key (a client; `/arrakis weapons give`, survival and creative)

11. Creative menu, IslandCraft tab, Weapons: eight melee and the Maula Pistol and Dartgun, with icons and names; each type has its own vanilla look (Crude, Thorium, Cobalt, Adamantite models).
12. Swing each sword and each dagger set at the same target: the same damage for all four houses (equal to the Crude weapon), the T1 swing and impact sounds, right click guards or bashes as vanilla, the
    signature move on its own key still works when its energy is full, no durability bar, nothing breaks.
13. Maula Pistol and Dartgun: one click one dart, no more than 3 a second, 1 damage, nothing past 40 blocks, smoke only, the bash works, no ammo or Mana used, firing drops the Holtzman field for 1 s if it is on.
    Melee swings never drop it.
14. H key, holding each of the ten weapons and the seven tools: the shield toggles (field, hum and power sounds as from the picker) and the chat line appears; the picker page shows the same ON / OFF; the server log
    has one `Arrakis_Toggle arrived` line per press. Holding the Blood Bag or an empty hand: nothing. H during a swing and during a shot: it toggles and the swing / shot goes on. Holding H or tapping it fast:
    no flicker and no sound spam (0.2 s). Right click on a weapon: vanilla guard, no picker; on a tool: the picker. After death, respawn and relog the toggle is OFF and H still works. H with a page open: nothing.
    If H never arrives on some item kind, note which and read gate 5 above.

## Module notes: the four-bar HUD (`hud`)

Labels as above: **confirmed** (boot, unit test, javap, the reference screenshot), **inferred**, **needs a client**. Nothing client-side could be tried here (no player can be created headlessly), so the
whole look, the placement against the hotbar and everything the per-player tick does are on the test sheet at the end. The headless boot is clean with the module on and off, without Water, with the
Water jar that exists today (no `setHudHidden`, no colour rules) and with a scratch copy of that jar that has the prompt 06 methods (the real Water repo was not edited).

### What it draws (first version; the art and layout are replaced by the section "The HUD rebuilt on the vanilla art" below)

Two rows above the hotbar, each split in half at the middle of the screen: Power (teal, bolt) over Health (red, heart) on the left, Heat Exposure (amber to red, sun) over Water (blue, drop) on the
right, a round icon at the outer end of each bar, no numbers. `HudView` (a `CustomUIHud` with its own key `EquipmentOfArrakisBars`, so it sits beside the Water HUD) builds
`Common/UI/Custom/Hud/Equipment_of_Arrakis/Hud/Arrakis_Hud.ui`; `HudRuntime.tick` (run by `HudSystem` for every player every tick) reads the four values and sends what changed.
Sources, all unchanged and display only: Power = `Power.fraction` (the vanilla Mana stat), Health = the `Health` entry of the player's `EntityStatMap` (value / max), Heat Exposure and Water = the
Water mod `getExposure` / `getWater` (0 to 100, `max()` is 100). A bar that cannot be read (no Water) is not drawn. Fill lengths use the Water bars' technique: a track of an empty part and a fill
part that share its length by `FlexWeight` (1000 - v and v, integers, 0.1% steps, always adding up to 1000, 0 and 100 exact), so the fill is exactly the value at any screen size; the fill is a
white texture tinted by `Background.Color`, with an untinted sheen on top. Left bars fill from their outer (left) end, right bars from their outer (right) end (**inferred**: the reference
screenshot shows all four full, so the direction is a choice; changing it means swapping the two parts in the generator).

- **Updates:** only what moved by `MinPercentChange` 0.25 percent (always at 0 and 100); colour changes only when the colour string changes (Heat Exposure as it passes the red-start percent,
  and the flash). **Flash:** Power and Water below `FlashBelowPercent` 5; the fill alternates between its colour and the same colour moved `FlashTintPercent` 55% toward white, from a clock all
  bars share. **Interpretation to check:** the request says "about 2 Hz" and "at most 2 updates a second", which differ by a factor of two; `FlashHz` is the number of colour changes a second
  (2: bright half a second, normal half a second, one full blink a second); set 4 for two full blinks a second. Health and Heat Exposure never flash. It stops the moment the value is 5 or more.
- **Heat Exposure colour:** orange up to the red-start percent, then a straight blend to red at 100 (`HudMath.exposureColor`, same rule as the Water bar). The three numbers come from the Water mod
  when its jar has the prompt 06 accessors (`getExposureRedStartPercent`, `getExposureColorOrange`, `getExposureColorRed`, found by reflection, so it works with both jars). **The Water jar that
  exists today does not have them**: the module logs a WARN once and uses the Hud config fallback (`ExposureRedStartPercent 75`, `#ff8a1f`, `#e0201a` = the Water defaults) and `/arrakis hud state`
  says "fallback". **Confirmed** both ways in a boot (the scratch jar printed "rules come from the Water mod"). The prompt names a `String exposureColor(double)` helper; the module does not call it
  (it needs the three numbers to blend itself, and the helper is only needed to avoid duplicating the rule, which `HudMath` does in 6 lines with a test); if the helper exists later it could replace
  `HudMath.exposureColor`.
- **The Water link** moved from `litrejon` to `core.water` (`WaterApi`, `WaterBridge`, `WaterLink`) so that the LitreJon and the HUD share it without importing each other; Water stays compile
  only and is never packed. New in `WaterApi`: `getExposure`, `setHudHidden(boolean)` (reflection; false when the jar has no such method), `exposureColors()`. With Water missing the bridge class is
  never loaded. At start the module calls `setHudHidden(true)` (log: "Water bars switched off"), or logs the WARN "the Water bars are still on" for an older jar; Water adds its HUD every tick,
  so nothing else is done. On stop it calls `setHudHidden(false)`.

### Hiding the vanilla bars, and what that touches

- Only `HudComponent.Health` and `HudComponent.Mana` are hidden, through `HudManager.hideHudComponents`. Every tick the module looks at `getVisibleHudComponents()` and hides them again if either
  is present, so every reset is undone within one tick: the join (`PlayerHudManagerSystems$InitializeSystem` sends the set), a respawn or world change (`Player.resetManagers` calls
  `resetUserInterface` / `resetHud`), and a game mode change (`GameModeTypeState.apply` sets the set from the game mode asset's `HudComponents`, `resetToBaseline` resets it). Polling was chosen over
  hooks because those engine calls are not events. **Our own HUD** is added again whenever `getCustomHud(key)` is not our instance (the same test as the Water HUD).
- **It changes no behaviour (confirmed by reading the server classes under `com.hypixel.hytale.server.core`):** `HudComponent` is referenced only by `HudManager`, `GameModeType`,
  `GameModeTypeState`, `PlayerHudManagerSystems$InitializeSystem` and two debug commands; it only builds the `UpdateVisibleHudComponents` packet that tells the client what to draw. No stat, regeneration,
  damage, low-health, death or Mana-use code reads it. (The scope of that search was `server.core`; the protocol classes only carry the packet.)
- **Game modes:** `Creative.json` and `Adventure.json` list no `HudComponents`, so vanilla draws its own bars in creative and so do ours. The spectator modes list their own set without Health, Mana
  or (`Spectator`) the Hotbar; the module draws the bars only while the Hotbar part is visible (so none in `Spectator`; `SpectatorInteractive` has a Hotbar and no Health / Mana and would show ours).
- **Restoring:** `/arrakis hud off` (everyone) removes our HUD, shows Health and Mana again within a tick and lets the Water bars show; `on` reverses it. A new `EquipmentModule.stop` hook,
  called from the plugin `shutdown()`, does the same for every online player and for Water. The module state is per player in memory and dropped on disconnect. The Z utility slot, stamina,
  oxygen, the ammo indicator, the status icons and the hotbar are never touched.

### The ability HUD and where the bars sit

The vanilla ability indicator (`HudComponent.Abilities`) is drawn by the client; nothing on the server says where. It is on the right of the screen (as reported). The bars end 346 virtual pixels
from the centre, so on a 16:9 screen (about 960 virtual pixels either side of the centre at 1080 high) they leave the whole right-hand third free; that is **inferred** from the reference screenshot
and the geometry, not measured: **needs a client with a vanilla sword in the hand**. No screenshot with that HUD was available, so its position is not reported. The weapons module loses that HUD
for the Arrakis weapons (the cause is being looked for separately, see "The missing ability HUD on the weapons"); this module neither hides nor draws anything of it.

### The held-item name text

- **What draws it: not determined.** The client draws it; the server has no field for it that was found. Checked: `PlayerSettings` (pickup locations, armour hiding, creative and voice settings; no
  HUD or name option), the `ItemBase` packet (HUD related fields are only `carryHudInputBindings`, `displayEntityStatsHUD` and `hudUI`), the 27 `HudComponent` values (no item-name part; the
  likeliest owners are `Hotbar` or `InputBindings`), and the item JSON (no name-display property in the sword, dagger, gun or tool templates). So it **cannot be switched off from the server in any way
  that was found**, and hiding the whole Hotbar is excluded.
- The v1 `EquipmentHud` (`ToolName`, a panel with the tool icon and name) is not in this build: the only `CustomUIHud` here is the new `HudView`, and the v1 `.ui` was not brought over (checked).
- **Options:** `/arrakis hud component hide|show <Name>` hides or shows one vanilla part for you (and `reset`), to try each candidate on a client and see which one carries the text; and the Hud
  setting `BarsLift` (default 0, the reference layout) raises both rows if the text collides with them. Neither was done for you because the text was not seen.

### The art and the numbers

All our own, drawn by `tools/assets/hud/make_hud.js` (it also writes the `.ui`): the bar background (dark body, lighter rim, dark outline, slanted outer end), the white fill, the sheen, and four round
icons (bolt, heart, sun, drop: white glyph on a disc in the bar colour with a dark ring). Nothing is read from the game or the Water mod, and the Water icons were not reused. **Measured from
`hud-layout-reference.jpg`** (902 x 317, the nine hotbar slots 160 to 770, so slot 9 ends 305 screenshot pixels from the centre: 360 virtual pixels as the v1 HUD measured, 0.847 screenshot pixels per
virtual pixel): bar thickness 9.5 screenshot = 12 virtual; row bottoms 195.5 and 217.5 = 26 apart; slot top 225, 9 above the bottom row; slant about 45 degrees on the outer end only (top edge
shorter); the bottom row is longer than the top (outer ends 335 and 314 from the centre) and its icons are further out (350 and 334); inner ends 7; colours teal `#57bbaf`, red `#d64d58`,
amber `#d49c1b`, blue `#599de3`. All of these are in `HudConfig` (the `Hud` section) and at the top of the generator. **A human's eye is needed for:** `BottomRowBottom` (111 = hotbar baseline 24 from the
v1 HUD + slot 78 + gap 9; the slot height 78 comes from the screenshot, the 24 from v1), the slant (`CAP` in the generator) against the hotbar, `TopOuter` / `BottomOuter` / the icon centres, the colours
(sampled from a JPEG over a wooden scene, so a few percent off), the sheen strength, and the flash tint.

**Anchoring.** The screen is split in two halves of equal `FlexWeight` so the middle (the middle of the hotbar, which the client draws centred) is exact at every resolution and aspect ratio; every
offset from it is a fixed number of virtual pixels, the unit the hotbar is measured in, so the bars keep their place against it when the window scales. The vertical position is a fixed distance
above the bottom of the screen. **Not verifiable without a client:** that the hotbar really is centred and nine slots 720 virtual pixels wide at 1080 high (taken from the v1 HUD, which measured it) and
that the virtual pixel scales the same way for the vanilla hotbar and for our `Anchor` values (the Water bars and the v1 HUD rely on the same assumption).

### Commands and checks

`/arrakis hud on | off | state | demo <power|health|exposure|water> <percent> | component hide|show <Name> | reset`. `state` prints module on or off, whether our HUD is added, whether vanilla Health
and Mana are shown or hidden and hidden by us, the visible vanilla parts, the four values with their colour and flash state, any demo pin, and the Water link, its hide switch and where the colour
rules come from. `demo` pins a bar for `DemoSeconds` 6 (values below 5 on Power or Water flash).
Unit tests (engine free): `HudMathTest` (weights always add up to 1000, 0 and 100 exact, the threshold, flash only below the limit and only on Power and Water, the phase and 2 flips a second, the tint,
the exposure colour and its midpoint, the command words), `HudAssetsTest` (every id the view sets is in the `.ui`, every texture named there exists at 2x with the right size). The self-check lists the
`.ui` and the ten textures.

### Human test sheet, the HUD (a client; survival and creative; with and without the Water mod)

15. The four bars sit above the hotbar as in `hud-layout-reference.jpg`: Power (teal, bolt) over Health (red, heart) on the left, Heat Exposure (amber, sun) over Water (blue, drop) on the right, icons at
    the outer ends, the thin stamina bar above untouched, the Z slot untouched. Compare the slant, the thickness, the gap to the hotbar and the colours with the screenshot; note what to change in the
    `Hud` section.
16. The vanilla Health and Mana bars are gone and nothing else vanilla moved or went missing (oxygen when swimming, the ammo indicator, status icons, stamina). No duplicate Water edge bars (needs the Water
    jar with prompt 06; with the current jar both draw and the log says so).
17. Take damage, heal, use a tool that spends Mana, regenerate: the fills follow smoothly and the stats behave exactly as before. `/arrakis hud state` agrees with the bars.
18. `/arrakis hud demo exposure 40`, `80`, `100`: orange, then toward red above the red-start, red at 100. `/arrakis hud demo power 3` and `water 3`: flash about once a second (see the note on `FlashHz`) and stop at
    5; `health 3` and `exposure 3` never flash. No numbers anywhere.
19. Respawn after death, change world, relog, switch creative and survival: the vanilla Health and Mana stay hidden, ours are right, nothing is stuck; `/arrakis hud off` brings the vanilla bars back and
    removes ours, `on` reverses it.
20. Without the Water mod: only the left half shows, no error in the log. Hold a vanilla sword: the ability HUD on the right is not covered. Resize the window and change the resolution: the bars stay
    over the hotbar and never overlap it.
21. The held-item name text: does it collide with the bars? If so try `/arrakis hud component hide InputBindings` (then `show`, `reset`) and the other parts to find what draws it, or raise
    `BarsLift`, and write the answer here.

### The HUD rebuilt on the vanilla art (replaces "What it draws", "The art and the numbers" and the flash notes above)

The first version drew its own bars (`make_hud.js` with a pixel rasteriser); they looked crude. This version builds the four bars from the game own Health and Mana bar art, laid out like the vanilla
Health.ui and Mana.ui, with the vanilla `ProgressBar` element. Labels as before: **confirmed** (read from the client files, a boot, a unit test), **inferred**, **needs a client**. Nothing was seen on
a client. Everything else of the module is unchanged (hiding only Health and Mana, the data sources, the Water link and the colour rule, the stop hook, `on|off|state|demo|component`).

**Where the vanilla numbers come from (confirmed).** The generator reads the client `Interface` folder (`...\Client\Data\Game\Interface`, a variable at the top of `make_hud.js`) and writes what it read to
`tools/assets/hud/vanilla-metrics.json`; `HudMetricsTest` fails if `HudMetrics.java` disagrees with it. `InGame/Common.ui`: `@DefaultItemSlotSize` 74, `@DefaultItemSlotsPerRow` 9, `@DefaultItemGridPadding` 2.
`InGame/Hud/Hotbar.ui`: `@BottomMargin` 30, `@ContainerMargin` 6, `@HotbarSlotSpacingHud` 4, so `@HotbarWidthHud` = 74 * 9 + 4 * 9 = **702**, `@HotbarHeight` = 74 + 2 * 2 = **78**,
`@InventoryClosedContainerMargin` = **36**. `Health/Health.ui` and `Mana/Mana.ui`: the group is `Width: @HotbarWidthHud, Height: 12` with `Bottom: 36 + 78 + 6` = **120**; the bar is 318 x 12;
the icon is 24 x 24, `Top: -8`, a 4 gap (`Right: 4` for Health with the icon at the left end, `Left: 4` for Mana with the icon at the right end). So a block (icon, gap, bar) is 346 wide and its
inner end is 351 - 346 = **5** from the middle of the screen. The first version put the bottom row at 111; the vanilla row is at **120** (`BarsLift` is now 0 by default, see the config note below).
The top row is `RowGap` above (26, measured from the reference screenshot: 22 screenshot pixels at 0.847 per virtual pixel; editable).

**ProgressBar (confirmed that it takes server updates).** The server jar sets `#MemoriesProgressBar.Value` as a float (0..1) in the Memories page and `#ProgressBar.Value` in the prefab editor pages
(`UICommandBuilder.set(String, float)`), and the vanilla documents declare `ProgressBar #X { BarTexturePath: "..."; }`. Properties seen in the client binary and the vanilla `.ui` files: `BarTexturePath`,
`EffectTexturePath`, `EffectWidth`, `EffectHeight`, `EffectOffset`, `Direction` (Start), `Alignment` (Vertical / Horizontal), `Value`, and `Bar: (Color: #...)` (in `VoiceInputLevelSetting.ui`); `EffectTexturePath`
is the glow at the tip of the fill (used by the Memories bar and `Common.ui @ProgressBar`), while the Health and Mana bars use a second ProgressBar with `HealthBarEffect` / `ManaEffect` as its bar texture,
which is what is copied here. So the FlexWeight split was dropped: each bar sets `#XFill.Value` and `#XEffect.Value`. The fill direction is left to the engine default, as in the vanilla files (no
`Direction` is set there). **Inferred at the time, then shown wrong on a client (see "The tint failed on a client"):** that `Bar.Color` multiplies the bar texture the way `Background.Color` does on a group, and that `ui.set("#XFill.Bar.Color", ...)` is accepted at run time;
if the tint is ignored the Power and Heat Exposure bars show the grey neutral fill.

**The two routes (reference and copy), four documents.** `make_hud.js` writes `Arrakis_Hud_Reference.ui`, `Arrakis_Hud_Reference_Creative.ui`, `Arrakis_Hud_Copy.ui` and `Arrakis_Hud_Copy_Creative.ui` (same ids,
anchors and layout; only the texture paths differ). One more pair than the prompt asked for: a texture path set at run time on a live document is not something that can be relied on, so the creative fills
are separate documents and the view is rebuilt (removed and added) when the game mode changes.
- **Reference:** `../../../InGame/Hud/Health/HealthBackground.png` and so on. **Why that path (inferred from vanilla data):** the Assets.zip document `Hud/ReturnToHubButton.ui` names
  `../InGame/Pages/MapIcons/ReturnToCrossroads.png`, which only makes sense if `Common/UI/Custom` and the client `Interface` folder are one virtual tree (`Common/UI/Custom` holds `Common.ui`, `Common/`, `Hud/`,
  `Pages/`, `Sounds.ui`, the client `Interface` holds the same names beside `InGame/`), so three folders up from `Hud/Equipment_of_Arrakis/Hud/` is the Interface root and `InGame/Hud/...` follows. The client appends `@2x`
  to a name (the files are `HealthBackground@2x.png`, the documents say `HealthBackground.png`). Whether a texture pack that overrides the built-in path also changes ours cannot be tested here.
- **Assets.zip art** (`Common/Icons/ItemCategories/Misc.png`, `Natural-Fluid.png`, 48 x 48 white glyphs): referenced as `../../../../../Icons/ItemCategories/Misc.png` (five folders up from our document is the `Common`
  asset root of the zip). **This is the least certain path:** no vanilla document reaches the `Icons` folder by a relative path, and the virtual tree above suggests the UI root is `Interface`, not the asset root.
  If the Heat Exposure and Water icons are missing on a client while the bars show, that is why; `/arrakis hud art copy` uses copies.
- **Copy:** `VanillaDerived/<Name>.png` for everything (plain copies and the derived images), so it works whatever the client does with the paths. `ArtSource` in the Hud section (`reference` default or `copy`) and
  `/arrakis hud art reference|copy|state` (live for everyone, saved, every view rebuilt).

**Files in `VanillaDerived/` (one folder, listed with the reason; they come from Hypixel client art and Assets.zip: the user decides what ships when the mod is published).** Derived, used by both routes:
`StaminaIconWhite@2x.png` (white silhouette of `StaminaIcon@2x.png`, alpha kept: the vanilla bolt is orange and a tint would multiply to a dark colour); `NeutralHealthBarFill@2x.png`,
`NeutralCreativeHealthBarFill@2x.png` (grey copies with the shading of the Health fills, for the Power bar and its alert layer), `NeutralManaFill@2x.png`, `NeutralCreativeManaFill@2x.png` (the same from the Mana fills,
for the Heat Exposure bar and its alert layer); `ErrorLeft@2x.png`, `ErrorOutlineLeft@2x.png` (mirror images of `ManaBackgroundError` and its outline: the vanilla overlay is slanted at the right end and the
Health-shaped bars are slanted at the left, which the prompt did not expect, so the overlays could not be shared by all four bars). Plain byte copies, used by the Copy documents only: `HealthBackground`,
`HealthBarFill`, `HealthBarEffect`, `CreativeHealthBarFill`, `HealthIcon`, `CreativeHealthIcon`, `ManaBackground`, `ManaFill`, `ManaEffect`, `CreativeManaFill`, `ManaBackgroundError`, `ManaBackgroundErrorOutline`
(`@2x.png`), and `Misc.png`, `Natural-Fluid.png` from Assets.zip. The Reference documents use only the seven derived ones from our pack (unit test: no other `VanillaDerived/` path appears in them). Sizes are the
vanilla sizes (636 x 24, 635 x 24 for the Mana files, 522 x 16 for the effects, 48 x 48 icons: `HudAssetsTest`). A derived image will not follow a texture pack; the other textures in the Reference documents will.

**Colour judged from pixels (the generator prints these).** A multiply cannot brighten. Vanilla Health fill x teal `#57bbaf` = `#4b3f45` (a dull brown): unusable, so Power uses a neutral copy (x teal = `#4ea89e`).
Vanilla Mana fill x amber `#d49c1b` = `#515f17` (olive): unusable, so Heat Exposure uses a neutral copy (x amber = `#bb8a18`, x red `#c61c17`). Health uses the vanilla pink-red fill untinted and Water the
vanilla blue Mana fill untinted. **Neutral copies are not as bright as the target** (about 10% darker than the tint colour) because the median pixel maps to 0.88; if they look dull raise `PowerColor` or
`NEUTRAL_BODY` in the generator. The creative fills need their own neutral copies for the tinted bars (the creative Health and Mana fills are lavender gradients with different shading), so there are four neutral
fills. Tinted icons: Power = the white silhouette of the vanilla bolt, `#7fd0ff`; Water = `Natural-Fluid.png`, `#4da3ff`; Heat Exposure = `Misc.png` going from `#ffd23f` at 0% to `#ff8a1f` at 100% (config
`ExposureIconLow` / `ExposureIconHigh`); Health = the vanilla `HealthIcon` untinted. The Heat Exposure bar keeps the orange-to-red rule (Water API colours or the config fallback).

**The low alert (Health and Water by default).** Read from the vanilla files: `Mana.ui` has `@ErrorPulseThreshold` 0.1667, `@ErrorPulseSpeed` 10, `@ErrorPulseFlips` 4, a one-shot pulse on the error overlay when a use
of Mana fails (the comment says it is matched to the ability icon error overlay); `StaminaPanel.ui` has `@LowAlertFlashTimer` 0.25, `@LowAlertAnimationSpeed` 20, `@LowAlertOutlineOpacityStart` 0.5, a repeating
alert while the value is low. **The stamina low alert was copied**, because it is a state that lasts while the value is low, like the bars here; the Mana pulse is an event. Below `FlashBelowPercent` (5) a bar
whose switch is on (`FlashHealth` and `FlashWater` true; `FlashPower` and `FlashExposure` false, but the mechanism exists on all four) shows a red fill layer and flips every `AlertFlipSeconds` = 0.25 s
(4 updates a second at most, from one clock all bars share, starting bright): bright phase = the error overlay at full opacity and its outline at full; dim phase = the overlay at 0 and the outline at
`AlertOutlineOpacity` 0.5 (the vanilla outline start opacity, so the outline never goes away while alerting). The red fill is a second ProgressBar (`#XAlert`, in a group that is hidden otherwise) made from the
neutral fill of that shape tinted `AlertColor` `#e6004f` (the vanilla error rim `#bf0046` brightened a fifth); it is a separate layer because a red tint of the vanilla blue Mana fill gives `#580044`, dark
purple (the generator prints the check), while the neutral fill x alert red gives `#cb0046`. The overlays use the vanilla error textures. **Differences from the client alert:** the vanilla bars fade (animation speed
20), ours switch at once because the server sets the opacity on each flip. The alert stops the moment the value is 5% or more.

**Creative mode (confirmed how it is read).** `Creative.json` and `Adventure.json` carry no `HudComponents`, and `Player.getGameMode()` (the protocol `GameMode`, which `Power.isCreative` already uses) is the
signal. The runtime reads it every tick; when it differs from the mode the view was built for, the view is removed and built again from the `_Creative` document: the two left bars use `CreativeHealthBarFill`
(neutral copy for Power) and the Health icon becomes `CreativeHealthIcon`; the two right bars use `CreativeManaFill` (neutral copy for Heat Exposure). The tinted icons are the same in both modes.

**Nudges.** `/arrakis hud lb|rb|lt|rt [x[,] y]` (lb Health, rb Water, lt Power, rt Heat Exposure; commas or spaces; a third number is ignored and the reply says so). The step is (value - 1) x 10 virtual pixels
(1.1 = +1, 0.9 = -1, 1.5 = +5, 0.5 = -5, 1 = 0); x + moves right, y + moves up; they accumulate and are rounded to hundredths. No numbers prints the offsets; `reset` clears that bar; `/arrakis hud nudge reset`
clears all four. Stored in the config (`Hud.Nudges.LeftBottom|RightBottom|LeftTop|RightTop`, `X` and `Y` in pixels), written on every change through the new `ModuleContext.saveConfig()`, loaded at start, and applied to
every player at once (the views are built again on their next tick) and to later joiners. x moves a left bar by shrinking the pad between the middle of the screen and the bar (a right bar by growing it), so a
bar cannot be pushed past the middle of the screen (the reply says so); y moves the row. `state` prints the four nudges. The parsing, the step rule, accumulation and the saved format are unit tested
(`HudNudgeTest`), the layout arithmetic in `HudLayoutTest`.

**Config keys.** Removed: `BarThickness`, `TopOuter`, `BottomOuter`, `InnerGap`, `IconSize`, `TopIconCentre`, `BottomIconCentre`, `BottomRowBottom`, `RowPitch` (now `RowGap`), `HealthColor`, `WaterColor`
(the vanilla fills are used untinted), `FlashHz`, `FlashTintPercent` (replaced by the alert keys) and the slant numbers that only the first art had. Kept: `BarsLift`, `PowerColor`, the exposure fallback colours,
`FlashBelowPercent`, `MinPercentChange`, `DemoSeconds`. New: `ArtSource`, `RowGap`, `PowerIconColor`, `WaterIconColor`, `ExposureIconLow`, `ExposureIconHigh`, `AlertColor`, `AlertFlipSeconds`,
`AlertOutlineOpacity`, `FlashPower`, `FlashHealth`, `FlashExposure`, `FlashWater`, `Nudges`. **`BarsLift` default is now 0** (the bottom row is where the vanilla bars were); the test save had 13 from the first
geometry, which sat 9 below the vanilla row, so it was set to 0 there too.

**The generator.** `tools/assets/hud/make_hud.js` (with `png.js`, a small 8-bit PNG reader and writer that also reads the palette files the vanilla art uses) reads the client Interface folder and Assets.zip, and writes
the four documents, `VanillaDerived/` and `vanilla-metrics.json`; it removes the files of the first version. Re-run on this machine it gives byte-identical output (checked with a hash of every file). If a
game update changes the vanilla formulas the script stops with "vanilla layout changed" instead of writing wrong numbers.

**Not verifiable without a client:** the look against the vanilla bars and the reference screenshot, that the reference paths and the Assets.zip icon paths resolve (the unit tests and the boot only see our
pack), that `Bar.Color` tints and can be set at run time, the fill direction of the right bars, the dimness of the neutral fills, the 0.25 s flip, that `Visible` on the alert group and the colour strings with an
alpha (`#ffffff(0.50)`) are accepted when set from the server, and the nudge feel.

### Human test sheet, the vanilla-art HUD (replaces items 15 to 21 where they differ; a client)

22. Four bars above the hotbar as in the screenshot but with the vanilla art: Power (teal, light-blue bolt) over Health (vanilla pink-red, vanilla heart) on the left, Heat Exposure (amber, yellow glyph) over Water
    (vanilla blue, blue drop) on the right. The bottom row is exactly where the vanilla Health and Mana bars were; the vanilla ones are gone; stamina, the Z slot and the ability HUD are untouched. If a bar or
    icon is missing, `/arrakis hud state` and then `/arrakis hud art copy`; say which parts were missing with `reference`.
23. `/arrakis hud demo health 3` and `water 3`: the fill turns red and the red overlay and outline pulse every quarter second, and it stops at 5. `power 3` and `exposure 3` do nothing (their switches are off);
    set `FlashPower` or `FlashExposure` to true in the Hud section, restart, and they do it.
24. Switch to creative and back: the left bars use the creative health fill and the creative heart, the right bars the creative mana fill; in survival they return. Power and Heat Exposure keep their colours.
25. `/arrakis hud lt 1.1,1` moves the Power bar one pixel right, `lt 0.9,1` back, `lt 1,1.5` five pixels up, `lt 1,1,7` ignores the third number, `lt` shows the offsets, `lt reset` clears it, `nudge reset` clears
    all. Restart the server: the offsets are still there. A second player sees them at once. Check that nudging a left bar right stops at the middle of the screen.
26. Heat Exposure: the icon is yellow at low exposure and orange at high, the bar goes orange to red as before; Water drops as it should; no duplicate Water edge bars (needs the Water jar with its prompt 06 change).
27. Resize the window and change resolution: the bars keep their place against the hotbar. Compare with the vanilla Health and Mana bars with `/arrakis hud off`.
28. Look at the colours: are the neutral-fill bars (Power, Heat Exposure) as bright as the vanilla ones? Note the numbers to change (`PowerColor`, the generator `NEUTRAL_BODY`, `TINT`).

### The client could not join with the vanilla-art HUD (commit ce873f4): evidence, the change, how to bisect

**Symptom (from the client log `2026-10-18_11-30-20_client.log`).** At 13:44:05 `Disconnecting with error during stage GameLoading: Failed to apply CustomUI HUD commands (Could not find document
Hud/Water_of_Arrakis/Arrakis_Bars.ui for Custom UI Append command)`; at 13:44:43, with Water disabled, the same for `Hud/Equipment_of_Arrakis/Hud/Arrakis_Hud_Reference.ui`. The runs of 13:40 and 13:41 (before the build) joined.

**What the logs show (read, not assumed).**
- The self-check lists the four new `.ui` and every `VanillaDerived` image with no PROBLEM, and the client registered them: 23 `Added cached reference to asset` lines for exactly our new files (4 documents and the 19
  images), in the failing run and not in the good one. `Misc.png` and `Natural-Fluid.png` are missing from that list: the client only logs hashes it did not already have, and those two were byte copies of
  Assets.zip files. So the files reached the client; the client could not find the document **by name** when the HUD was applied at the join.
- The server counted `Duplicated Asset Count` 727 in the failing runs (724 / 725 in every earlier run): two new byte-identical assets, which are those two icons.
- The first failing run named **Water own unchanged document**; the second, with Water disabled, named ours. The Water HUD is applied before ours, so the first missing name is whichever the client was asked for
  first. This fits one explanation better than the others: **the client registers the pack documents in order and stops at the first one it cannot accept**, so every document after it is missing. Sorted by
  path, `Hud/Equipment_of_Arrakis/...` comes before `Hud/Water_of_Arrakis/...`, and in `Equipment_of_Arrakis` the order is `Arrakis_Hud_Copy.ui`, `Arrakis_Hud_Copy_Creative.ui`, `Arrakis_Hud_Reference.ui`: the
  Reference document is the first one that fails, as the second run says. (A hypothesis from the order of the names; the client prints no parse error.)
- Why that document could be rejected: it names texture paths outside the pack (`../../../InGame/Hud/Health/HealthBackground.png`, a guess that the pack `UI/Custom/` root and the client `Interface/` root are one
  tree, taken from `Hud/ReturnToHubButton.ui` in Assets.zip). The Copy documents name only files inside the pack. The client log has no line about it ("nothing available": the only client logs are
  `...\UserData\Logs\*_client.log`, which embeds the server log; there is no verbose switch for document compile errors that was found, and the singleplayer launch line has no debug flag).

**What was changed (the prompt step 2, all at once, because there is no client here to bisect with; the bisect is set up below).**
1. The Reference documents are no longer in the pack. `make_hud.js` writes them (and the bisect variants) to `tools/assets/hud/variants/<variant>/resources/...`; only `Arrakis_Hud_Copy.ui` and
   `Arrakis_Hud_Copy_Creative.ui` are in `src/main/resources`. `ArtSource` now defaults to `copy`.
2. `Misc.png` and `Natural-Fluid.png` in `VanillaDerived/` are re-encoded (the same pixels, written by the generator own PNG writer), so they are no longer byte-identical to the Assets.zip files. I did not use the
   prompt option of referencing the two vanilla icon paths from a Copy document: that would put an outside path into the one route that must be safe.
   `Duplicated Asset Count` of the headless boot is 724 / 725 again (727 before).
3. **The module can no longer append a document the pack does not have** (`HudDocs.pick`, unit tested): it asks the pack registry (`CommonAssetRegistry`) and picks the route asked for, else the Copy one, else
   nothing. With no document it shows no bars, leaves the vanilla Health and Mana bars alone, and logs a warning once. `/arrakis hud art reference` is refused while the pack has no Reference documents, and `art state` says
   what the pack has. A document that is in the pack but that the client rejects cannot be detected from the server; that is what the bisect is for.
4. `HudAssetsTest` checks that the Reference documents are not in the pack, that the two icons are our re-encode, and still checks the Reference documents under `variants/` for ids and paths.

**What the player must test now:** join the world with this jar (Water on and off). If it joins, the Reference documents (outside paths) or the duplicate icons were the cause. If it still fails, see "if it still fails".

**Bisect, one change per build** (`gradlew jar deployMod -PhudExtra=<variant>` adds one variant to the jar; a normal build never contains them; stop at the first one that breaks the join):
| variant | adds to the pack | answers |
|---|---|---|
| `dupicons` | `VanillaDerived/Misc-Dup.png` and `Natural-Fluid-Dup.png`, byte copies of the vanilla icons | do duplicate hashes alone break the join (the asset count goes back to 727) |
| `refinpack` | documents named `Arrakis_Hud_Reference*.ui` whose texture paths all stay inside the pack | do extra documents, or those names, break it (paths are not the cause) |
| `reference` | the two Reference documents with outside paths (the first build) | do outside texture paths break it |
| `probe` | `Arrakis_Hud_Probe.ui` (one outside path), appended only by `/arrakis hud probe` | does the guess "pack `UI/Custom` root = client `Interface` root" resolve (the bar-shaped background shows) |
Results: **none recorded yet** (no client was available while this was written); fill the table in after the tests.

**If it still fails with only the Copy documents** (not done yet): diff `Arrakis_Hud_Copy.ui` against the last document that joined (`5959df3`, `Arrakis_Hud.ui`) and bisect the new constructs in a minimal document: `ProgressBar` with `BarTexturePath`,
`Value`, `Bar: (Color: ...)`; `PatchStyle(TexturePath: ..., Color: #ffffff(0))`; `Background: (TexturePath: ..., Color: ...)`; `Visible: false`; the file name with a hyphen (`Natural-Fluid.png`); ids that are the
same in two documents (the four documents share every id); two documents with nearly the same content. The Water mod `Arrakis_Bars.ui` and the vanilla `Common.ui` `@ProgressBar` are the references for forms the client
accepts from a pack document (`Common.ui` uses `ProgressBar { Anchor; Background; BarTexturePath; EffectTexturePath; EffectWidth; EffectHeight; EffectOffset }`; a `Value:` is set in the UI gallery document).

**What the reference route needs, and its status (honest).** *Untested; the evidence points at it as the cause of the join failure, but that is not proven.* A pack document naming paths outside the pack
(`../../../InGame/Hud/...`) was in the build that could not join. Whether the client resolves such a path from a pack document, and whether an unresolved path rejects the whole document (and then every document
after it), is exactly what the `reference` and `probe` variants decide. Until then the art rule "use the vanilla art by reference" is **not met by the HUD**: the Copy route ships copies (`VanillaDerived/`), which
is the documented exception. The earlier notes ("Reference ... confirmed/inferred") above describe the design, not a working route.

**Human test sheet additions.** 29. Join with the new jar, Water on and Water off: it joins and the four bars show with the Copy art. 30. `/arrakis hud art state` says the pack has the Copy documents and not the Reference ones;
`/arrakis hud art reference` is refused. 31. If it still cannot join, say which build variant (none / `dupicons` / `refinpack` / `reference` / `probe`) and quote the client log line.

### The client crash at Water below 5% (first client test of the Copy build)

The Copy build joined. Typing `water set 5` then crashed the client; the server log only says `left with reason: Crash`, and every join after that crashed again (the Water value was saved at 5). The real
message is in the **client** log (`2026-10-18_11-30-20_client.log`, 14:07:40): `Disconnecting with error during stage InGame: Failed to apply CustomUI HUD commands (CustomUI Set command couldn't set value.
Selector: #WaterError.Background.Color -> Failed to convert JSON value (String) to specified type (UInt32Color))`. **Cause:** the alert set the colour of the error overlay with the string `#ffffff(1.00)`. The
`(alpha)` form is `.ui` syntax; a colour set from the server must be a plain `#rrggbb`, and the client disconnects on a string it cannot convert. **Fix:** no opacity is ever set at run time now. Each bar has
three overlay groups that start hidden (`Visible: false`): `#XError` and `#XOutline` (opaque) and `#XOutlineDim` (a fixed half-opaque outline, `Color: #ffffff(0.5)` in the document, the vanilla
`@LowAlertOutlineOpacityStart`); the alert shows the first two in the bright phase and only the dim outline in the dim phase (`HudMath.overlays`, unit tested; `HudAssetsTest` checks the groups start hidden and that
no document sets an alpha of 0). **Config:** `AlertOutlineOpacity` was removed (the number is the vanilla one written into the documents by the generator). **Confirmed from the same log:** in that command batch
`#WaterAlert.Value` and `#WaterAlertGroup.Visible` were applied before the failing line and the client accepted them, so `ProgressBar.Value` and `Visible` on a group work from the server; plain `#rrggbb` strings on
`Bar.Color` and `Background.Color` were accepted at the join. **Still needs a client:** that the alert now shows and flashes (test 23).

### The tint failed on a client: all four fills are now Group fills (replaces "inferred" about Bar.Color above)

**Result on a real client:** Power and Heat Exposure came out white/grey. `Bar: (Color: ...)` and `ui.set("#XFill.Bar.Color", ...)` on a **textured** ProgressBar do not tint (only the Voice level meter in the vanilla files uses
`Bar: (Color)`, and that one has no `BarTexturePath`, i.e. a flat bar). Health and Water looked right because they use the vanilla fills untinted. The red alert background that was seen is the pre-coloured vanilla error overlay.
**Rules learned on this client (also saved as a reference memory):** a bad run-time Set disconnects the player; a colour with an alpha is refused at run time; `Bar.Color` does not tint a textured ProgressBar fill; `Background.Color` on a Group does;
relative texture paths cannot leave the pack; `.Visible`, `.FlexWeight` and `Value` sets are safe. Only run-time properties already proven on a client are used.

**What changed.** One technique for all four bars: a fill is a Group with `Background: (TexturePath: ..., HorizontalBorder: 9, Color: #rrggbb)` and an empty partner Group, sharing the 318 pixel bar by FlexWeight (fill v, empty 1000 - v,
`HudMath.split`, integer parts, 0 and 100 exact). Health and Water use the vanilla fill with `Color: #ffffff` (one code path, looks the same), Power and Heat Exposure the neutral copies tinted by their colour. The red alert fill is
also a Group (neutral fill, `Color: #e6004f`, in `#XAlertGroup`, shown with `.Visible`, sized with `#XAlert` / `#XAlertEmpty` FlexWeight). The vanilla effect layer stays a ProgressBar (untinted, `Value` is proven), the vanilla background,
the three overlay groups, icons, positions, nudges and creative documents are unchanged. Run-time sets are now only `.FlexWeight`, `.Visible`, `Effect.Value` and `Background.Color` with a plain `#rrggbb`; every colour string passes
`HudMath.safeColor` (a bad config value is replaced by a default before it is sent, so a typo cannot disconnect a player). No `Bar.Color` exists anywhere (a test scans the documents and the code).
Tests added: weights always add up to 1000, the colour guard, every colour the HUD can send matches `^#[0-9a-fA-F]{6}# IslandCraft - Equipment of Arrakis, v2: experiment log

v1 (the Cutter Ray, guns, off-hand tools, Holtzman shield, Arrakis_Power stat and HUD) is thrown away; `git show equipment-v1-final:<path>`
reads any v1 file. v2 is built in modules, one package and one asset folder each: core, power, catalog (this prompt), then cutter,
compactor, litrejon, harvester, bloodbag, extractor, placeholders, light, shield (built, see "Module notes"), then weapons, stillsuit, armor.

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

## Module notes: the v2.1 tool set

Everything below was built on the foundation (core, power, catalog). **confirmed** = seen in a headless boot, in a unit test or in bytecode with javap;
**inferred** = reasoned from bytecode or vanilla data; **needs a client** = only a player on a client can show it. Nothing client-side could be tried here,
and no player or mob can be spawned headlessly, so every behaviour that needs one is on the human test sheet at the end.

**Order of the work (one commit each, `gradlew test` green before the next):** core additions and the cleanup of the earlier picker trial; picker; cutter; compactor;
litrejon + harvester; bloodbag; extractor; placeholders + light; shield. Each new module has an id in the config `Modules` list and its own section in
`Equipment_of_Arrakis.json` where it has settings. The headless boot loads all of them with no PROBLEM line, with and without the Water mod, and with the new modules
removed from `Modules` (each removes its own assets at BootEvent, see `ModuleAssets`).

### core additions

- `ToolRegistry` (picker tools by item id, display order, optional label key; detection is by this list, never by a prefix), `ToggleRegistry` (passive toggles: id, order, label
  key, icon item id, switch / read / reset callbacks; `forget(uuid)` runs every reset), `GiveRegistry` (what `/arrakis tools give` hands out: the six tools, the LitreJon and the
  Blood Bag, containers through their own factory so they start with their bar), `ToolUse.fired(PlayerRef)` with listeners (the shield listens; only the Cutter Ray calls it),
  `ContainerItem` (amount in stack metadata under a key, mirrored to the durability bar: max durability = capacity, durability = amount; pure clamp / add / room, unit-tested),
  `ContainerInventory` (carried containers, first with room: hotbar then main inventory, never the backpack or the utility slots; add to the first with room; registered by item id so
  the Harvester fills the LitreJon and the Extractor fills the Blood Bag without importing those modules), `Notice` (a short chat line from a translation key, at most one per
  second per player and key), `ModuleAssets` (switching a module off), shared `/arrakis` command groups (`CommandRoot.group`, attached after setup), `WorldAccess.healthSection`.
- power: `ManaRefusal.say(player, n)`: "This needs N Mana.", at most one per second per player, switched by the Power setting `ShowMessages` (default true).
  The Mana regeneration is untouched (the boot log and the hook in `PowerModule.start` stay).
- The toggles are reset on disconnect, on entering or leaving a world (`AddPlayerToWorldEvent`, `DrainPlayerFromWorldEvent`) and on death (`ToggleResetSystem`, once per death).
- Unit tests (engine-free): registry order and replace, toggle forget even after a failing hook, ToolUse listeners, ContainerItem clamp / add / room, first-with-room selection,
  Notice and ManaRefusal throttles. The stack side of `ContainerItem` (metadata and bar on a real `ItemStack`) cannot run in a unit test (the `ItemStack` class needs the engine's
  assets); it is exercised by `/arrakis tools give` and `/arrakis blood add` in the game. A raw stack taken from the creative menu has no saved amount, so it reads as
  empty (`StartsFull` false) but the bar shows full until the first change; `/arrakis tools give` makes proper ones.

### picker

- The earlier picker trial (page, swap rule, three `.ui` files, the `OpenCustomUI` interaction registered with `OpenCustomUIInteraction.registerSimple`) became this module:
  `ToolPicker` (page + swap adapter), `ToolSwap` (pure rule, 10 tests), `PickerCommands`, assets `Arrakis_Picker_Open` / `Arrakis_Picker_Secondary`, `Arrakis_Picker_{Page,Entry,Toggle}.ui`.
  Every tool's `Interactions.Secondary` is `Arrakis_Picker_Secondary`.
- Lists the registered tools found in the hotbar and the main inventory (not the backpack, not the utility container) in registry order, the held one marked "In hand" and not
  clickable, then always every registered toggle with ON / OFF. A toggle click flips it, sends "<label>: ON" and rebuilds (page stays open); a tool click validates against the
  inventory as it is now, does one `ItemContainer.swapItems` (the chosen tool into the active hotbar slot, the old one into the slot the chosen one came from; metadata travels
  with the stack; no free slot needed) and closes; if the inventory changed it says so and swaps nothing.
- `/arrakis tools give | state | open`. `state` prints the held item, the tools found with container and slot, the toggles and Mana.
- The page opens only from a held Arrakis tool because only those tools carry the Secondary; the engine interaction refuses to open over another custom page (bytecode).
- **needs a client**: that the `.ui` files parse and look right, the cursor, the swap latency and the refresh of the held model and the bar.

### cutter (Cutter Ray)

- Item `Arrakis_Tool_Cutter_Ray`: the vanilla `Weapon_Gun` (Pulse Rifle) by path (model, texture, animation set with the Shoot animations at speed 3, icon). Primary = `Arrakis_Cutter_Primary`
  = [`Arrakis_Cutter_Shot`, `Arrakis_Cutter_Effects`]. `Arrakis_Cutter_Effects` carries the vanilla shot feel: the `Shoot` item animation, the `Handgun_Shoot` camera effect, the `RifleShooting`
  muzzle particles at the vanilla offsets, `RunTime 0.25` (the vanilla `Gun_Shoot` pace, the `ShotInterval`). No projectile, no sound added.
- `Arrakis_Cutter_Shot` (custom type, `CutterShotInteraction`, first in the chain so a refusal, which ends it as Failed, stops the effects from ever starting): needs `ManaPerShot` 10
  (below that: no shot, no effects, the Mana refusal line), spends it (creative pays none), casts `TargetUtil.getTargetBlock` from the eye `Range` 5, and hits the block with
  `BlockHarvestUtils.performBlockDamage` with an unbreakable stand-in `Tool_Pickaxe_Crude`, tool id `Pickaxe`, `DamageScale 1`. Creative breaks the block at once. Then `ToolUse.fired`.
  It spends on every shot, with or without a block in reach. Gate unit-tested at Mana 9 / 10 / 100 and creative; 10 shots from full Mana.
- **How the damage was matched to the crude pickaxe:** the vanilla `BreakBlockInteraction` calls the same `performBlockDamage` with the held tool and a damage scale pushed with
  `fconst_1` (read in its bytecode, both call sites), so one real swing is scale 1.0 of the held tool's own gather specs, and v1 measured the same (0.24 per 0.1 s = one swing
  per 0.4167 s). The shot uses scale 1.0 with the crude pickaxe stack, so a shot is one swing by construction, and the crack accumulates on the block as with the pickaxe.
  **confirmed**: the bytecode and the compile; **not measured**: shots-to-break on sand, stone and an ore against the real crude pickaxe (that needs a player to swing the pickaxe):
  on the test sheet, the numbers must match.
- Settings are data in `Arrakis_Cutter_Shot.json` (`ManaPerShot`, `Range`, `DamageScale`, `ToolItemId`, `Silent`) and `Arrakis_Cutter_Effects.json` (`RunTime`).

### compactor

- v1 ported: model, texture, aim animation (first and third person), the `Arrakis_Compactor` animation set (parent `Torch`), the hum effect and its two sounds, the sand-dust particle,
  `CompactMath` + `BeamMath` (tests carried over), `CompactorInteraction` (the vanilla `Charging` interaction with a compacting tick), `CompactorTrail` (crack, repair, drop, hum),
  `CompactorSystem`. Now Primary (left click held), no `Utility.Usable`, no `RequireNewClick`, in the hand only. 1 Mana per second while really compacting (`PowerPerSecond 1`,
  spent in proportion to the tick time), stops at 0 Mana (creative never runs out), no `ToolUse.fired`. Any other block or no target: nothing, no Mana. Range 3, `CompactSeconds` 1,
  groups `Sand`, exclusions `Arrakis_Sand`, `Arrakis_Worm_Ripple_Layer_`, `Arrakis_Worm_Sunk_`, all data in `Arrakis_Compactor_Aim.json`.
- The block is never broken: the crack is the block health lowered to at most 90 % and given back at the end of each cycle (v1 logic unchanged).
- **needs a client**: the aim pose as a main-hand item (the set is the off-hand torch set), the hum, the dust, and that holding left click keeps the `Charging` interaction going.

### litrejon (LitreJon) and harvester (Dew Harvester)

- `Arrakis_Tool_Litrejon`: its own item, a second flask next to the Water mod's `Arrakis_Litrejon` (which is untouched, loaded and working next to it: the headless boot with both
  mods lists both items). Model and icon: `Potion_Template` parent, the Energy Potion icon and the Cyan texture, by path. Metadata `Arrakis_Water_Units`, `Capacity 500`,
  `StartsFull false`, `PourPerClick 50` (section `Litrejon`), the bar via `ContainerItem`. Left click = `Arrakis_Litrejon_Pour`, repeating every 0.5 s while held: moves
  min(flask, 50, what the player lacks) into the player's water; a player 19 below the cap takes 19 and the flask loses 19 (a fractional shortfall is offered rounded up and the flask
  pays for the water really gained, rounded up; the Water mod clamps at 100). Empty flask or full player: a short message. Right click opens the picker, so filling from water blocks
  is not available on this item; the Dew Harvester fills it (an open point for you).
- Water link: `WaterApi` / `WaterBridge` / `WaterLink` as in v1 (compiled against the Water jar `compileOnly`, never packed; `-PwaterJar=` points at another copy; the bridge class is
  loaded only after the plugin is found, inside a handler for linkage errors). The manifest lists Water as an **optional** dependency, not a required one: a required one would
  stop the whole mod from loading without Water, and the brief wants the LitreJon and the Harvester to degrade. **confirmed headless:** with Water loaded the log says
  "Water of Arrakis linked" and "the Water mod's own Arrakis_Litrejon is loaded and untouched"; without it, "Water of Arrakis not loaded: the LitreJon will refuse to pour (everything
  else works)", and a click says "Water of Arrakis is needed to pour from the LitreJon." The picker lists only `Arrakis_Tool_Litrejon` (it is the only one registered) and the Harvester
  fills only it (it looks the item up in `ContainerInventory` by that id).
- **What a later Water prompt would remove if the old item is retired:** `Items/Water_of_Arrakis/Arrakis_Litrejon.json`, the interactions `Arrakis_Condition_Flask` and `Arrakis_Flask_Charge`
  (and the `Arrakis_Root_Flask` root they feed), `FlaskInteraction`, the `Litrejon` class with its `FillSource` / `addFillSource` hook, the Litrejon settings in `WaterOfArrakisConfig`
  (`LitrejonCapacity`, `LitrejonDrinkAmount`, `LitrejonStartsFull`, the fill reach and water fluid ids), the references in `WaterOfArrakisPlugin` and `SunProbeCommand`, and the
  `flaskFilled` / `flaskEmpty` / `notThirsty` lines of its `server.lang`.
- Dew Harvester `Arrakis_Tool_Dew_Harvester`: the vanilla `Tool_Sickle_Crude` by path (model, texture, icon, animation set `Sickle`), no recipe, no durability. Primary =
  [`Arrakis_Harvester_Gate`, `Arrakis_Harvester_Swing`, `Arrakis_Harvester_Finish`], the v1 Dew Reaper's gate / swing / finish. The gate refuses (nothing spent, a short message)
  unless the block under the crosshair within 3 is `Arrakis_Primrose`, a LitreJon is carried, one has room and there are 10 Mana; otherwise it pays 10 Mana, starts the hum and remembers
  the swing. The swing is a 1 second `Simple` (the sickle's `SwingLeftSlow` animation). The finish moves 10 water into the first LitreJon with room (hotbar, then main inventory) if the
  Primrose is still there. The Primrose is not touched, there is no cooldown, no evening window and no cap, the player's own water is not touched. Held, the chain repeats. Rules
  unit-tested (order of the refusals, exact cost, creative). Without the Water mod there is no Primrose to aim at and no LitreJon to pour from; the tool says "Aim at a Primrose."
  **needs a client**: the sickle animation length (1 s is the interaction's time, not measured against the animation), the hum.

### bloodbag and extractor

- `Arrakis_Blood_Bag`: parent `Potion_Template`, Red texture by path, icon `Potion_Health`; metadata `Arrakis_Blood_Units`, `Capacity 500`, `StartsFull false` (section `Bloodbag`); Primary and
  Secondary are a root that does nothing (so the inherited drink is gone). Not registered as a picker tool. API: `BloodBag.isBag / getAmount / add / firstWithRoom / carried / capacity`
  for the future purifier. `/arrakis blood add <n>` fills the first bag with room.
- `Arrakis_Tool_Blood_Extractor`: the vanilla Handgun by path (model, texture, icon, animation set `Handgun`), no shot at all. Primary = [`Arrakis_Extractor_Gate`,
  `Arrakis_Extractor_Poke`, `Arrakis_Extractor_Finish`]. Refusals before any animation, nothing spent, a short message, in this order: no corpse in range and aim; already used; no bag;
  all bags full. No Mana. The gate claims the corpse, forces the crouch and remembers the poke; the poke is a 1 s `Simple` (`PokeSeconds`) playing the Handgun set's **`GuardBash`** item
  animation (the vanilla pistol jab, an existing animation, nothing custom); the finish lets the crouch go and puts `BloodPerBody` 10 into the first bag with room.
- **What a dead mob is, in 0.7.0 (bytecode):** a killed NPC keeps its entity. `DeathComponent` is added to it; `NPCSystems.OnDeathSystem` then adds `DeferredCorpseRemoval(role.getDeathAnimationTime(),
  deathParticles)`, which `DeathSystems.TickCorpseRemoval` counts down, and `CorpseRemoval` removes the entity when it is over. So a corpse exists only for the role's death animation time
  (a second or a few, per role) and then vanishes, as the brief expected. The rule is `CorpseAim.isCorpse`: not a player, has a `DeathComponent`, and (data `RequireNpc`, default true) an `NPCEntity`.
  Widen it by setting `RequireNpc` false in `Arrakis_Extractor_Gate.json`, or by editing the one pure method.
- **How a corpse is found:** not with `TargetUtil.getTargetEntity` (a dead entity may no longer have a hitbox the engine ray takes; not tried). `TargetUtil.getAllEntitiesInSphere` around the eye,
  filtered by the rule, then a pure ray test (`CorpseAim.pick`): the nearest corpse whose aim point (feet + `AimHeight` 0.5) is within `AimRadius` 0.9 of the look ray and `Range` 2.5 along it.
  Unit-tested. **inferred**: that the radius and height suit every mob size (they are data).
- **One body, one use:** the corpse's entity ref goes into a set at the gate (claimed), so nobody can use it again; refs of removed entities are dropped from the set, so it clears itself when the body
  vanishes. A poke that does not reach its finish (button released, player left) gives the corpse back. This is a runtime set, not an ECS marker component on the entity (registering a
  component type was not worth the risk for a tool that is to be refined); the effect asked for is the same.
- **Forcing the crouch:** the client owns the movement state (it sends its `MovementStates` every tick; `MovementEffects` can only disable crouch, and the `SetMovementStates` packet
  carries only the flying flag). What is possible from the server is writing the server-side `MovementStatesComponent`: `crouching = true` and `forcedCrouching = true`, every tick of the
  poke (`ExtractorSystem`), restored at the end; the engine's movement-states system sends that state to the players who can see this one, so **others** see the crouch. Whether the owner's own client
  overrides it in between is **needs a client**. Fallback, if it does: set `ForceCrouch` false and `RequireCrouched` true in `Arrakis_Extractor_Gate.json` and the tool refuses unless the player is
  already crouching ("Crouch first."). The default is forcing.
- `/arrakis blood corpse` spawns a cow two blocks ahead (`NPCPlugin.spawnNPC`) and kills it with a `DeathComponent` (`COMMAND` damage); it vanishes within seconds like a real corpse.
  Spawning an NPC failed in the headless world (no player), so this is **needs a client**.

### placeholders, light, shield

- Placeholders: `Arrakis_Tool_Vehicle_Summoner`, `Arrakis_Tool_Build_Repair`: the crude pickaxe's model, texture, icon and animation set `Pickaxe` by path, no `Tool` block, no Primary (the engine fills it
  with its own empty interaction: confirmed in the self-check), no durability, Secondary = the picker, descriptions "Not built yet.", registered in the picker.
- Personal Light (`light`): a toggle on the page, no item, no cost. The light is the entity component `DynamicLight(ColorLight)` (the component the engine gives a dropped torch; the light of a held
  torch is made by the client from the block's Light), added while the toggle is ON and removed when OFF by `Reconcile`, a system that makes the entities match the ON set every tick (so a death, a
  disconnect or a world change cannot leave a stuck light). **How it was matched to a torch:** the torch's light is the `Light` of its block type, `{Radius 0, Color #ba9}`; the module reads
  `BlockType Furniture_Crude_Torch` `.getLight()` at start (**confirmed**: radius 0, colour 11/10/9) and uses its radius and its brightest channel (11) with the tint 0.85 / 0.93 / 1.0 (section
  `Light`: `TintRed/Green/Blue`, or `Radius/Red/Green/Blue` to override each number; `-1` = from the torch). **Final values: radius 0, red 9, green 10, blue 11** (cool white: pale, leaning blue,
  as bright as the torch's brightest channel). That the numbers look the same as a held torch "side by side in the dark" is **needs a client**; the radius reading 0 for the torch is why the
  radius is copied, not chosen. `/arrakis light on | off | state`. Reset to OFF on relog, death, respawn and world change.
- Holtzman Shield (`shield`), ported from v1: the entity effects, ModelVFX, bubble / hit / shatter particles and the eight sounds are byte-for-byte v1 (moved into `Shield` folders; they reference
  vanilla textures and sounds by path, so no art is shipped), `HoltzmanSystem`, `HoltzmanDamageSystem` (in the engine's filter damage group, after the armour reductions, cancels the hit), `HoltzmanField`,
  `ShieldCooldown` (with its modifier hook and override), the two projectile hooks `ShieldShotLegacySystem` / `ShieldShotSystem` (any projectile a player creates) and `/arrakis shield hit | state | shot
  | cooldown | diag`. **Differences from v1:** (1) no item, no Z slot, no off hand: the condition "shield selected" is now "the toggle is ON"; (2) the power is Mana through `Power`, `ManaPerDamage 1.0`,
  creative pays nothing; (3) a hit that takes Mana to 0 or is at least the Mana left is absorbed whole, Mana goes to 0, the shatter burst and break sound play, the field goes down and the **toggle
  stays ON**; the state is "broken" and the field returns by itself, with the normal power-up sound and no message, when Mana is back to `RearmMana` 100 (or to the maximum if that is lower);
  (4) the toggle is not saved: it is OFF after a relog, a world change and death; (5) the Cutter Ray's `ToolUse.fired` drops the field for `CooldownSeconds` 1.0 like any projectile, restarting on each
  shot, with the off / on sounds; the Compactor, Extractor, Harvester and LitreJon never call it; (6) the v1 `RangedWatch` diagnostic (a dev check of Ranged-tagged interactions) and the hook
  self-test were not ported; `/arrakis shield diag` now switches a trace of every projectile creation with its creator. **ON with less than 100 Mana:** the field rises with any Mana above 0
  unless the shield has just broken and is waiting (the v1 rule); with 0 Mana it does not rise; toggling OFF and ON does not clear a break (only a death, a relog or a world change does), so the
  wait for 100 cannot be skipped. Rules unit-tested (absorb, break at equal, re-arm at 100, a lowered maximum, drop and restart of the timer, ON with low Mana, a script of the per-tick plan and its sounds).
  **needs a client**: the look and the sound mix (as v1, unchanged), the fall of the field at a Cutter shot, an arrow costing 1 Mana per damage.

### Experiment cleanup, summary

Removed from the throw-away trial module when the picker was promoted: the four stand-in tools (`Arrakis_Tool_Axe`, `_Pickaxe`, `_Hoe`, `_Shovel`: items and translations), the trial
interaction and root interaction of the picker, its command class, and its page and swap classes (moved and renamed into `picker`), the three `.ui`
files (moved and renamed), the edits made to the trial module and to its isolation test (the `Arrakis_Tool_` allow-list and reserved prefix are gone; the prefix now belongs to the real tools),
and `/arrakis exp tools`. The earlier trials 1 to 3 are unchanged. **Removal rehearsal (run for real on a copy after everything above was built):** the removal script of the trial module (see its section, below)
leaves no trace of the trial names in the sources and `gradlew build` passes.

### Human test sheet (a client; `/arrakis tools give` first, survival and creative)

1. Creative menu, IslandCraft tab, Tools: Cutter Ray, Compactor, LitreJon, Dew Harvester, Blood Extractor, Vehicle Summoner, Build/Repair Tool, Blood Bag, each with icon and name; only the two
   containers show a bar (and a raw one shows a full bar until first used); nothing wears or breaks.
2. Picker: right-click any tool: it opens once with the tools you carry (icons, "Hotbar n" / "Inventory", "In hand"), then Holtzman Shield and Personal Light with ON / OFF. Click a tool: right model and
   animation in the hand, the old tool in the slot it came from, works with a full inventory, the LitreJon keeps its water. Escape closes with no change. Note the swap latency and any lag of the held model.
3. Cutter Ray: hold left click: vanilla gun look, 4 shots per second, 10 Mana each, stops after 10 at full Mana, "This needs 10 Mana." (once a second) at 9 or less; mobs are not hurt. Break a sand, a stone and an
   ore block with it and with the real `Tool_Pickaxe_Crude`: count the shots and the swings (they must match).
4. Compactor: hold left click on sand within 3: crack, a drop per second, hum, the block stays; 1 Mana per second; stops at 0; nothing on other blocks.
5. LitreJon: with Water loaded: left click gives up to 50, only what you lack (test at 81 water: 19); the bar follows; empty and full messages; right click opens the picker. Without Water: the message.
6. Dew Harvester: left click on a Primrose with a LitreJon with room: a swing per second, +10 water in the flask, -10 Mana, Primrose unchanged, your water unchanged; no Mana, no flask, full flask, other block:
   no swing and a message.
7. Blood Extractor: `/arrakis blood corpse`, left click on it within 2.5: crouch (do you and others see it? does your own client fight it?), the jab, +10 blood in the first bag; the same corpse again: refused;
   no bag or full bags: refused with no animation; no Mana spent. `/arrakis blood add 495` fills a bag to 500.
8. Holtzman: ON from the picker: field, hum and power-up sound as v1; `/arrakis shield hit 20` costs 20 Mana; firing the Cutter drops it for 1 s and it returns; `/arrakis shield hit 500` breaks it with the
   shatter, the toggle stays ON, it returns by itself at 100 Mana; OFF removes everything; relog and death leave it OFF.
9. Personal Light: ON gives a torch-equivalent cool white light that other players see (compare with a held `Furniture_Crude_Torch` in the dark); OFF removes it; relog and death leave it OFF.
10. Vehicle Summoner and Build/Repair: appear in the picker, show a pickaxe, left click does nothing.

## Module notes: the weapons and the H key

Same labels as above: **confirmed** (headless boot, unit test, javap), **inferred** (reasoned from bytecode or vanilla data), **needs a client**. Two commits: core (the toggle key), then the
`weapons` module. The headless boot loads all ten weapons with no PROBLEM line, with the module on, and with `weapons` and `shield` both removed from `Modules` (the weapons module removes
21 assets of its own; the toggle interaction stays and does nothing without the shield).

### core: the Ability4 toggle (the H key)

- `ToggleRegistry.press(id, store, ref, player, now, minGap)` is the one way a player flips a toggle: the picker page calls it with no debounce, the key through `Arrakis_Toggle` with 0.2 s.
  Outcomes `UNKNOWN` (nothing registered under the id: the module is off) / `DEBOUNCED` / `ON` / `OFF`. A press with gap 0 leaves no debounce stamp, so a click and then H are independent.
  The shield toggle id is now `Holtzman` (was `shield`); the shield module's asset guard was narrowed to ids starting `Arrakis_Holtzman_` / `SFX_Arrakis_Holtzman_`, because it used to drop
  anything containing "Holtzman", which would have dropped the core root with the shield module.
- Interaction type `Arrakis_Toggle` (`ToggleInteraction`, registered by the core module): fields `ToggleId`, `DebounceSeconds`. Interaction asset `Arrakis_Toggle_Holtzman`
  (`ToggleId Holtzman`), root `Arrakis_Root_Toggle_Holtzman`: `RequireNewClick`, `Cooldown 0.2`, `Rules {"BlockedBy": []}`. It sends the same chat line as the page ("Holtzman Shield: ON") and logs
  one INFO line per press that arrives (`Arrakis_Toggle arrived for <name> holding <item>: toggle Holtzman -> ON/OFF/DEBOUNCED/UNKNOWN`); `/arrakis weapons trace off` silences it.
  No item glow, particle or HUD was added: the bubble on the character is the only indicator.
- `Ability4 = Arrakis_Root_Toggle_Holtzman` is on the seven hand tools (Cutter Ray, Blood Extractor, Compactor, Dew Harvester, LitreJon, Vehicle Summoner, Build/Repair Tool) and on the ten
  weapons; not on the Blood Bag (unit test and the boot log). Their right click is unchanged: the picker on tools, the vanilla guard / bash on weapons.

**Gate analysis: what can stop an Ability4 chain.** The client binds H to Ability4 (the user's finding). On the server (javap of `InteractionManager`, `InteractionRules`, `InteractionTypeUtils`):
1. **The item must have an Ability4 entry.** `Item.getInteractions()` is looked up by type; no entry, nothing runs. (confirmed: the boot log lists `Ability4=Arrakis_Root_Toggle_Holtzman` on all 17.)
2. **Nothing unlocks it.** There is no ability-unlock list or client setting in the server path. The vanilla signature gate is not on the slot: it is the first interaction of the Ability1 chain, a
   `StatsCondition` with `Costs {SignatureEnergy: 100}` (`Weapon_Sword_Signature_Vortexstrike`), and the `Weapon` block's `StatModifiers` / `EntityStatsToClear` only fill and clear that stat; the glow is
   `ItemAppearanceConditions`. Our root has none of these. (confirmed from the vanilla data and the bytecode.)
3. **The root cooldown.** `RootInteraction.Cooldown` is per root; with none the engine uses a default 0.35 s (`InteractionTypeUtils.DEFAULT_COOLDOWN`). Ours is set to 0.2 s, which is the debounce.
4. **The default rules block it while another hand chain runs.** `InteractionTypeUtils.DEFAULT_INTERACTION_BLOCKED_BY` makes every standard input type (Primary, Secondary, Ability1 to 4, Use, Pick, SwapTo,
   SwapFrom) blocked by every other one while its chain is running, so by default H pressed during a swing, a shot or a held guard is silently dropped (the swing is not cancelled, but H is lost). We set
   `Rules.BlockedBy` to an empty list on the root (the boot log shows `blockedBy=[]`, `interrupting=null`, `interruptedBy=null`), so H works mid-swing and mid-shot and cancels neither (nothing interrupts:
   the codec fields `Interrupting` / `InterruptedBy` are unset). **Inferred:** the other direction (a click arriving while the instant toggle chain is alive) is blocked by the click's default rules for the
   one tick the toggle lives; it is on the test sheet.
5. **Not verifiable here:** whether the client sends Ability4 at all for each kind of item, and whether it does with a page open (a client concern; the server never sees it). The result of pressing H on a
   melee weapon, a gun and a tool is therefore **needs a client**: press H and read the log line above; if it never appears for an item kind, the client is not sending it for that kind (a missing piece
   would then be on the client side, not in the item data, and this note must be updated with what it was). Holding the Blood Bag or nothing: the item has no Ability4, so nothing arrives (by design).

### weapons

- **Melee (8), built as children:** `Arrakis_Weapon_Sword_{Crude,Atreides,Choam,Harkonnen}` and `Arrakis_Weapon_Daggers_{...}`. Each has `Parent: Weapon_Sword_Crude` (or `Weapon_Daggers_Crude`), so the whole
  `InteractionVars` (every damage number, `DamageEffects`, swing effects and sounds), `ItemSoundSetId`, the `Weapon` block (signature energy), the appearance conditions and the icon properties are the Crude
  item's and the template's, byte for byte, with nothing copied. Overridden by `tools/assets/weapons/make_weapons.js` (it reads the vanilla files from Assets.zip): `Model`, `Texture`, `Icon`, `Quality` of the
  house's own vanilla tier (Atreides = Thorium, Choam = Cobalt, Harkonnen = Adamantite; Crude = Crude), `Recipe: null`, `MaxDurability 0`, `DurabilityLossOnHit 0`, `MaxStack 1`, `Categories
  ["IslandCraft.Weapons"]`, the names, and the `Interactions` map written out in full (the template's Primary, Secondary, Ability1, plus Ability4) so no merge rule has to be trusted.
  **Proof that all eight deal the Crude damage (confirmed):** the unit test requires that no melee file has an `InteractionVars`, `ItemSoundSetId` or `Weapon` of its own and that its `Parent` is the Crude
  item; the boot self-check compares the resolved `Item.getInteractionVars()` map of each of the eight with the Crude item's and prints `InteractionVars same as Weapon_Sword_Crude: true` (swords) and
  `... Weapon_Daggers_Crude: true` (daggers) for all eight (the map holds the interaction ids, so they are the same generated damage interactions). `Recipe: null` removed the inherited recipe
  (`hasRecipesToGenerate` false), durability 0 and the tab are in the same self-check line. Left as vanilla: the Primary swing chain, the Secondary guard / bash and its animation, the Ability1
  signature move, `Utility.Compatible` (swords true, daggers false, from the templates), the animations. Not a picker tool: weapons keep the vanilla right click.
- **Guns (2), ported from v1 (tag equipment-v1-final):** `Arrakis_Weapon_Maula_Pistol` (`Parent: Weapon_Handgun`, `Utility.Compatible false`) and `Arrakis_Weapon_Dartgun` (`Parent: Weapon_Assault_Rifle`,
  two-handed as vanilla). Roots / interactions renamed `Arrakis_Weapon_Maula_*` / `Arrakis_Weapon_Dartgun_*` (Primary, Gate, Shoot). **Kept in the v1 form:** the projectiles `Arrakis_Maula_Dart` /
  `Arrakis_Dartgun_Dart`, the sounds `SFX_Arrakis_Maula_Fire(_Local)` / `SFX_Arrakis_Dartgun_Fire(_Local)` and the particle `Arrakis_Dart_Shot` (smoke only: the vanilla `Rifle_Smoke` spawner; no muzzle
  flash, no brass). The fire sound events are copies of the vanilla ones with the first layer 6 dB quieter (a sound event cannot inherit layers); they name the vanilla clips by path. Behaviour as v1:
  `RequireNewClick`, cooldown 0.33 s on the shared `Shoot` id (about 3 a second at most), dart damage 1, speed 100, the Secondary `Gun_Attack` bash and its animation, the `Handgun_Shoot` camera shake.
  The range rule is ported unchanged (`DartMath`, `DartFlight`, `DartFlightSystem`, `DartRangeSystem`, `DartRangeDamageSystem`): a dart is removed 40 blocks from where it was launched and a hit past
  40 blocks does no damage; the settings are the `Weapons` section (`Range`, `ProjectileIds`). The component is now registered by the module (`ctx.plugin().getEntityStoreRegistry()`).
- **The removed ammo gate and where a cost goes:** `DartAmmoGateInteraction`, `Arrakis_Ammo` and the arrow `ModifyInventory` are gone. Each Primary root still runs one gate interaction,
  `Arrakis_Weapon_<Gun>_Gate` of type `Arrakis_Gun_Gate` with `Cost 0` (generator constant `COST_PER_SHOT`): it calls `GunCost.charge(buffer, shooter, cost)` and goes to the shot, or ends as Failed and runs the
  vanilla dry click `Gun_Shoot_Fail`. `GunCost.charge` returns true for a cost of 0 and false for any cost above 0 (there is no ammo system yet, so a priced gate refuses rather than firing free). An ammo system
  later raises `Cost` and fills in `GunCost.charge`; nothing else changes. No Mana and no ammo are used.
- **Holtzman interplay (inferred, by reading the code; needs a client):** the shield drops on any projectile entity a player creates (`ShieldShotSystem` / `ShieldShotLegacySystem`, plus `ToolUse.fired`); a
  `LaunchProjectile` dart is such an entity with the player as creator, so firing drops the field for 1 s with no extra code. A melee swing creates no projectile and calls no `ToolUse`, so it never drops
  it. `/arrakis shield diag on` logs each projectile and its creator to check this. The weapons module never imports the shield: the key goes through core.
- Operator helpers: `/arrakis weapons give` (all ten), `/arrakis weapons state` (held item, its Ability4, the Holtzman toggle, the trace flag), `/arrakis weapons trace [on|off]`, and the existing
  `/arrakis shield state`.
- Unit tests: `TogglePressTest` (flip, unknown id, module off, debounce window and its edge, picker gap 0, per-player, forget), `DartMathTest` (the v1 range rule), `WeaponAssetsTest` (ids, tab, no recipe, no
  durability, the eight children of the Crude items with no damage of their own, the look of each tier, Primary / Secondary / Ability1 kept, Ability4 on ten weapons and seven tools and absent from the Blood Bag,
  the semi-auto root and the 0.33 s cooldown, the gate cost 0 and its hook, smoke only, dart damage 1 and speed 100, no picker on weapons, the toggle root: 0.2 s, empty `BlockedBy`, no interrupt).
  The catalog test (every item in the tab names an existing child and has an Icon of its own) covers the ten as well. Self-check lines for every item, interaction, root, sound and particle.
  A Projectile asset is not removed when the module is off (`ModuleAssets` does not handle that type); the two dart projectiles stay loaded and unreferenced.

### The missing ability HUD on the weapons (found after the first client test)

A client test showed that holding an Arrakis weapon no longer shows the vanilla weapon HUD on the right of the screen (the ability status and the buttons that activate them). The ability
buttons are wanted later, so the cause was looked for by comparing the resolved Crude daggers and the resolved Arrakis Crude daggers (and the swords) field by field. New dev command
`/arrakis weapons diff <itemA> <itemB>` (`ItemDiff`) prints every field of two loaded items whose values differ. Result for `Weapon_Daggers_Crude` against `Arrakis_Weapon_Daggers_Crude` (**confirmed**,
the swords differ in the same four ways):

| field | vanilla Crude | Arrakis Crude |
|---|---|---|
| `categories` | `[Items.Weapons]` | `[IslandCraft.Weapons]` |
| `interactions` | Primary, Secondary, Ability1, Ability3 (empty), Use, Pick, SwapFrom | the same plus `Ability4 = Arrakis_Root_Toggle_Holtzman` |
| `maxDurability` | 120 | 0 |
| `durabilityLossOnHit` | 0.06 (swords 0.13) | 0 |

Other differences are only the id, the translation keys, the recipe (removed on purpose) and the asset bookkeeping (`data`). Everything else is identical, including `weapon` (signature energy
modifiers), `utility`, `ability`, `tags`, `reticleId`, `carryHudInputBindings`, `displayEntityStatsHUD`, `hudUI`, `itemAppearanceConditions`, `interactionVars` and the model and sounds. So the HUD is
not lost through the Weapon block, the tags, the HUD fields or the interaction variables: the cause is one of the four rows above. **Suspects, in order (inferred, not yet separated):**
1. `categories`: the client may treat the category `Items.Weapons` as "this is a weapon" for the HUD. The catalog needs `IslandCraft.Weapons`; both can be listed, at the price of the item also
   appearing in the vanilla weapons tab.
2. `Ability4` on the item (the H key): the HUD may be laid out from the item's ability slots, and a fourth slot with a root that has no icon or energy could hide it.
3. `maxDurability 0` (a weapon with no durability): least likely, the HUD shows abilities, not wear.
**Next step (needs a client):** three throw-away daggers, one with `Items.Weapons` added to the categories, one without Ability4, one with durability 120, to see which brings the HUD back.

### Human test sheet, weapons and the H key (a client; `/arrakis weapons give`, survival and creative)

11. Creative menu, IslandCraft tab, Weapons: eight melee and the Maula Pistol and Dartgun, with icons and names; each type has its own vanilla look (Crude, Thorium, Cobalt, Adamantite models).
12. Swing each sword and each dagger set at the same target: the same damage for all four houses (equal to the Crude weapon), the T1 swing and impact sounds, right click guards or bashes as vanilla, the
    signature move on its own key still works when its energy is full, no durability bar, nothing breaks.
13. Maula Pistol and Dartgun: one click one dart, no more than 3 a second, 1 damage, nothing past 40 blocks, smoke only, the bash works, no ammo or Mana used, firing drops the Holtzman field for 1 s if it is on.
    Melee swings never drop it.
14. H key, holding each of the ten weapons and the seven tools: the shield toggles (field, hum and power sounds as from the picker) and the chat line appears; the picker page shows the same ON / OFF; the server log
    has one `Arrakis_Toggle arrived` line per press. Holding the Blood Bag or an empty hand: nothing. H during a swing and during a shot: it toggles and the swing / shot goes on. Holding H or tapping it fast:
    no flicker and no sound spam (0.2 s). Right click on a weapon: vanilla guard, no picker; on a tool: the picker. After death, respawn and relog the toggle is OFF and H still works. H with a page open: nothing.
    If H never arrives on some item kind, note which and read gate 5 above.

## Module notes: the four-bar HUD (`hud`)

Labels as above: **confirmed** (boot, unit test, javap, the reference screenshot), **inferred**, **needs a client**. Nothing client-side could be tried here (no player can be created headlessly), so the
whole look, the placement against the hotbar and everything the per-player tick does are on the test sheet at the end. The headless boot is clean with the module on and off, without Water, with the
Water jar that exists today (no `setHudHidden`, no colour rules) and with a scratch copy of that jar that has the prompt 06 methods (the real Water repo was not edited).

### What it draws (first version; the art and layout are replaced by the section "The HUD rebuilt on the vanilla art" below)

Two rows above the hotbar, each split in half at the middle of the screen: Power (teal, bolt) over Health (red, heart) on the left, Heat Exposure (amber to red, sun) over Water (blue, drop) on the
right, a round icon at the outer end of each bar, no numbers. `HudView` (a `CustomUIHud` with its own key `EquipmentOfArrakisBars`, so it sits beside the Water HUD) builds
`Common/UI/Custom/Hud/Equipment_of_Arrakis/Hud/Arrakis_Hud.ui`; `HudRuntime.tick` (run by `HudSystem` for every player every tick) reads the four values and sends what changed.
Sources, all unchanged and display only: Power = `Power.fraction` (the vanilla Mana stat), Health = the `Health` entry of the player's `EntityStatMap` (value / max), Heat Exposure and Water = the
Water mod `getExposure` / `getWater` (0 to 100, `max()` is 100). A bar that cannot be read (no Water) is not drawn. Fill lengths use the Water bars' technique: a track of an empty part and a fill
part that share its length by `FlexWeight` (1000 - v and v, integers, 0.1% steps, always adding up to 1000, 0 and 100 exact), so the fill is exactly the value at any screen size; the fill is a
white texture tinted by `Background.Color`, with an untinted sheen on top. Left bars fill from their outer (left) end, right bars from their outer (right) end (**inferred**: the reference
screenshot shows all four full, so the direction is a choice; changing it means swapping the two parts in the generator).

- **Updates:** only what moved by `MinPercentChange` 0.25 percent (always at 0 and 100); colour changes only when the colour string changes (Heat Exposure as it passes the red-start percent,
  and the flash). **Flash:** Power and Water below `FlashBelowPercent` 5; the fill alternates between its colour and the same colour moved `FlashTintPercent` 55% toward white, from a clock all
  bars share. **Interpretation to check:** the request says "about 2 Hz" and "at most 2 updates a second", which differ by a factor of two; `FlashHz` is the number of colour changes a second
  (2: bright half a second, normal half a second, one full blink a second); set 4 for two full blinks a second. Health and Heat Exposure never flash. It stops the moment the value is 5 or more.
- **Heat Exposure colour:** orange up to the red-start percent, then a straight blend to red at 100 (`HudMath.exposureColor`, same rule as the Water bar). The three numbers come from the Water mod
  when its jar has the prompt 06 accessors (`getExposureRedStartPercent`, `getExposureColorOrange`, `getExposureColorRed`, found by reflection, so it works with both jars). **The Water jar that
  exists today does not have them**: the module logs a WARN once and uses the Hud config fallback (`ExposureRedStartPercent 75`, `#ff8a1f`, `#e0201a` = the Water defaults) and `/arrakis hud state`
  says "fallback". **Confirmed** both ways in a boot (the scratch jar printed "rules come from the Water mod"). The prompt names a `String exposureColor(double)` helper; the module does not call it
  (it needs the three numbers to blend itself, and the helper is only needed to avoid duplicating the rule, which `HudMath` does in 6 lines with a test); if the helper exists later it could replace
  `HudMath.exposureColor`.
- **The Water link** moved from `litrejon` to `core.water` (`WaterApi`, `WaterBridge`, `WaterLink`) so that the LitreJon and the HUD share it without importing each other; Water stays compile
  only and is never packed. New in `WaterApi`: `getExposure`, `setHudHidden(boolean)` (reflection; false when the jar has no such method), `exposureColors()`. With Water missing the bridge class is
  never loaded. At start the module calls `setHudHidden(true)` (log: "Water bars switched off"), or logs the WARN "the Water bars are still on" for an older jar; Water adds its HUD every tick,
  so nothing else is done. On stop it calls `setHudHidden(false)`.

### Hiding the vanilla bars, and what that touches

- Only `HudComponent.Health` and `HudComponent.Mana` are hidden, through `HudManager.hideHudComponents`. Every tick the module looks at `getVisibleHudComponents()` and hides them again if either
  is present, so every reset is undone within one tick: the join (`PlayerHudManagerSystems$InitializeSystem` sends the set), a respawn or world change (`Player.resetManagers` calls
  `resetUserInterface` / `resetHud`), and a game mode change (`GameModeTypeState.apply` sets the set from the game mode asset's `HudComponents`, `resetToBaseline` resets it). Polling was chosen over
  hooks because those engine calls are not events. **Our own HUD** is added again whenever `getCustomHud(key)` is not our instance (the same test as the Water HUD).
- **It changes no behaviour (confirmed by reading the server classes under `com.hypixel.hytale.server.core`):** `HudComponent` is referenced only by `HudManager`, `GameModeType`,
  `GameModeTypeState`, `PlayerHudManagerSystems$InitializeSystem` and two debug commands; it only builds the `UpdateVisibleHudComponents` packet that tells the client what to draw. No stat, regeneration,
  damage, low-health, death or Mana-use code reads it. (The scope of that search was `server.core`; the protocol classes only carry the packet.)
- **Game modes:** `Creative.json` and `Adventure.json` list no `HudComponents`, so vanilla draws its own bars in creative and so do ours. The spectator modes list their own set without Health, Mana
  or (`Spectator`) the Hotbar; the module draws the bars only while the Hotbar part is visible (so none in `Spectator`; `SpectatorInteractive` has a Hotbar and no Health / Mana and would show ours).
- **Restoring:** `/arrakis hud off` (everyone) removes our HUD, shows Health and Mana again within a tick and lets the Water bars show; `on` reverses it. A new `EquipmentModule.stop` hook,
  called from the plugin `shutdown()`, does the same for every online player and for Water. The module state is per player in memory and dropped on disconnect. The Z utility slot, stamina,
  oxygen, the ammo indicator, the status icons and the hotbar are never touched.

### The ability HUD and where the bars sit

The vanilla ability indicator (`HudComponent.Abilities`) is drawn by the client; nothing on the server says where. It is on the right of the screen (as reported). The bars end 346 virtual pixels
from the centre, so on a 16:9 screen (about 960 virtual pixels either side of the centre at 1080 high) they leave the whole right-hand third free; that is **inferred** from the reference screenshot
and the geometry, not measured: **needs a client with a vanilla sword in the hand**. No screenshot with that HUD was available, so its position is not reported. The weapons module loses that HUD
for the Arrakis weapons (the cause is being looked for separately, see "The missing ability HUD on the weapons"); this module neither hides nor draws anything of it.

### The held-item name text

- **What draws it: not determined.** The client draws it; the server has no field for it that was found. Checked: `PlayerSettings` (pickup locations, armour hiding, creative and voice settings; no
  HUD or name option), the `ItemBase` packet (HUD related fields are only `carryHudInputBindings`, `displayEntityStatsHUD` and `hudUI`), the 27 `HudComponent` values (no item-name part; the
  likeliest owners are `Hotbar` or `InputBindings`), and the item JSON (no name-display property in the sword, dagger, gun or tool templates). So it **cannot be switched off from the server in any way
  that was found**, and hiding the whole Hotbar is excluded.
- The v1 `EquipmentHud` (`ToolName`, a panel with the tool icon and name) is not in this build: the only `CustomUIHud` here is the new `HudView`, and the v1 `.ui` was not brought over (checked).
- **Options:** `/arrakis hud component hide|show <Name>` hides or shows one vanilla part for you (and `reset`), to try each candidate on a client and see which one carries the text; and the Hud
  setting `BarsLift` (default 0, the reference layout) raises both rows if the text collides with them. Neither was done for you because the text was not seen.

### The art and the numbers

All our own, drawn by `tools/assets/hud/make_hud.js` (it also writes the `.ui`): the bar background (dark body, lighter rim, dark outline, slanted outer end), the white fill, the sheen, and four round
icons (bolt, heart, sun, drop: white glyph on a disc in the bar colour with a dark ring). Nothing is read from the game or the Water mod, and the Water icons were not reused. **Measured from
`hud-layout-reference.jpg`** (902 x 317, the nine hotbar slots 160 to 770, so slot 9 ends 305 screenshot pixels from the centre: 360 virtual pixels as the v1 HUD measured, 0.847 screenshot pixels per
virtual pixel): bar thickness 9.5 screenshot = 12 virtual; row bottoms 195.5 and 217.5 = 26 apart; slot top 225, 9 above the bottom row; slant about 45 degrees on the outer end only (top edge
shorter); the bottom row is longer than the top (outer ends 335 and 314 from the centre) and its icons are further out (350 and 334); inner ends 7; colours teal `#57bbaf`, red `#d64d58`,
amber `#d49c1b`, blue `#599de3`. All of these are in `HudConfig` (the `Hud` section) and at the top of the generator. **A human's eye is needed for:** `BottomRowBottom` (111 = hotbar baseline 24 from the
v1 HUD + slot 78 + gap 9; the slot height 78 comes from the screenshot, the 24 from v1), the slant (`CAP` in the generator) against the hotbar, `TopOuter` / `BottomOuter` / the icon centres, the colours
(sampled from a JPEG over a wooden scene, so a few percent off), the sheen strength, and the flash tint.

**Anchoring.** The screen is split in two halves of equal `FlexWeight` so the middle (the middle of the hotbar, which the client draws centred) is exact at every resolution and aspect ratio; every
offset from it is a fixed number of virtual pixels, the unit the hotbar is measured in, so the bars keep their place against it when the window scales. The vertical position is a fixed distance
above the bottom of the screen. **Not verifiable without a client:** that the hotbar really is centred and nine slots 720 virtual pixels wide at 1080 high (taken from the v1 HUD, which measured it) and
that the virtual pixel scales the same way for the vanilla hotbar and for our `Anchor` values (the Water bars and the v1 HUD rely on the same assumption).

### Commands and checks

`/arrakis hud on | off | state | demo <power|health|exposure|water> <percent> | component hide|show <Name> | reset`. `state` prints module on or off, whether our HUD is added, whether vanilla Health
and Mana are shown or hidden and hidden by us, the visible vanilla parts, the four values with their colour and flash state, any demo pin, and the Water link, its hide switch and where the colour
rules come from. `demo` pins a bar for `DemoSeconds` 6 (values below 5 on Power or Water flash).
Unit tests (engine free): `HudMathTest` (weights always add up to 1000, 0 and 100 exact, the threshold, flash only below the limit and only on Power and Water, the phase and 2 flips a second, the tint,
the exposure colour and its midpoint, the command words), `HudAssetsTest` (every id the view sets is in the `.ui`, every texture named there exists at 2x with the right size). The self-check lists the
`.ui` and the ten textures.

### Human test sheet, the HUD (a client; survival and creative; with and without the Water mod)

15. The four bars sit above the hotbar as in `hud-layout-reference.jpg`: Power (teal, bolt) over Health (red, heart) on the left, Heat Exposure (amber, sun) over Water (blue, drop) on the right, icons at
    the outer ends, the thin stamina bar above untouched, the Z slot untouched. Compare the slant, the thickness, the gap to the hotbar and the colours with the screenshot; note what to change in the
    `Hud` section.
16. The vanilla Health and Mana bars are gone and nothing else vanilla moved or went missing (oxygen when swimming, the ammo indicator, status icons, stamina). No duplicate Water edge bars (needs the Water
    jar with prompt 06; with the current jar both draw and the log says so).
17. Take damage, heal, use a tool that spends Mana, regenerate: the fills follow smoothly and the stats behave exactly as before. `/arrakis hud state` agrees with the bars.
18. `/arrakis hud demo exposure 40`, `80`, `100`: orange, then toward red above the red-start, red at 100. `/arrakis hud demo power 3` and `water 3`: flash about once a second (see the note on `FlashHz`) and stop at
    5; `health 3` and `exposure 3` never flash. No numbers anywhere.
19. Respawn after death, change world, relog, switch creative and survival: the vanilla Health and Mana stay hidden, ours are right, nothing is stuck; `/arrakis hud off` brings the vanilla bars back and
    removes ours, `on` reverses it.
20. Without the Water mod: only the left half shows, no error in the log. Hold a vanilla sword: the ability HUD on the right is not covered. Resize the window and change the resolution: the bars stay
    over the hotbar and never overlap it.
21. The held-item name text: does it collide with the bars? If so try `/arrakis hud component hide InputBindings` (then `show`, `reset`) and the other parts to find what draws it, or raise
    `BarsLift`, and write the answer here.

### The HUD rebuilt on the vanilla art (replaces "What it draws", "The art and the numbers" and the flash notes above)

The first version drew its own bars (`make_hud.js` with a pixel rasteriser); they looked crude. This version builds the four bars from the game own Health and Mana bar art, laid out like the vanilla
Health.ui and Mana.ui, with the vanilla `ProgressBar` element. Labels as before: **confirmed** (read from the client files, a boot, a unit test), **inferred**, **needs a client**. Nothing was seen on
a client. Everything else of the module is unchanged (hiding only Health and Mana, the data sources, the Water link and the colour rule, the stop hook, `on|off|state|demo|component`).

**Where the vanilla numbers come from (confirmed).** The generator reads the client `Interface` folder (`...\Client\Data\Game\Interface`, a variable at the top of `make_hud.js`) and writes what it read to
`tools/assets/hud/vanilla-metrics.json`; `HudMetricsTest` fails if `HudMetrics.java` disagrees with it. `InGame/Common.ui`: `@DefaultItemSlotSize` 74, `@DefaultItemSlotsPerRow` 9, `@DefaultItemGridPadding` 2.
`InGame/Hud/Hotbar.ui`: `@BottomMargin` 30, `@ContainerMargin` 6, `@HotbarSlotSpacingHud` 4, so `@HotbarWidthHud` = 74 * 9 + 4 * 9 = **702**, `@HotbarHeight` = 74 + 2 * 2 = **78**,
`@InventoryClosedContainerMargin` = **36**. `Health/Health.ui` and `Mana/Mana.ui`: the group is `Width: @HotbarWidthHud, Height: 12` with `Bottom: 36 + 78 + 6` = **120**; the bar is 318 x 12;
the icon is 24 x 24, `Top: -8`, a 4 gap (`Right: 4` for Health with the icon at the left end, `Left: 4` for Mana with the icon at the right end). So a block (icon, gap, bar) is 346 wide and its
inner end is 351 - 346 = **5** from the middle of the screen. The first version put the bottom row at 111; the vanilla row is at **120** (`BarsLift` is now 0 by default, see the config note below).
The top row is `RowGap` above (26, measured from the reference screenshot: 22 screenshot pixels at 0.847 per virtual pixel; editable).

**ProgressBar (confirmed that it takes server updates).** The server jar sets `#MemoriesProgressBar.Value` as a float (0..1) in the Memories page and `#ProgressBar.Value` in the prefab editor pages
(`UICommandBuilder.set(String, float)`), and the vanilla documents declare `ProgressBar #X { BarTexturePath: "..."; }`. Properties seen in the client binary and the vanilla `.ui` files: `BarTexturePath`,
`EffectTexturePath`, `EffectWidth`, `EffectHeight`, `EffectOffset`, `Direction` (Start), `Alignment` (Vertical / Horizontal), `Value`, and `Bar: (Color: #...)` (in `VoiceInputLevelSetting.ui`); `EffectTexturePath`
is the glow at the tip of the fill (used by the Memories bar and `Common.ui @ProgressBar`), while the Health and Mana bars use a second ProgressBar with `HealthBarEffect` / `ManaEffect` as its bar texture,
which is what is copied here. So the FlexWeight split was dropped: each bar sets `#XFill.Value` and `#XEffect.Value`. The fill direction is left to the engine default, as in the vanilla files (no
`Direction` is set there). **Inferred at the time, then shown wrong on a client (see "The tint failed on a client"):** that `Bar.Color` multiplies the bar texture the way `Background.Color` does on a group, and that `ui.set("#XFill.Bar.Color", ...)` is accepted at run time;
if the tint is ignored the Power and Heat Exposure bars show the grey neutral fill.

**The two routes (reference and copy), four documents.** `make_hud.js` writes `Arrakis_Hud_Reference.ui`, `Arrakis_Hud_Reference_Creative.ui`, `Arrakis_Hud_Copy.ui` and `Arrakis_Hud_Copy_Creative.ui` (same ids,
anchors and layout; only the texture paths differ). One more pair than the prompt asked for: a texture path set at run time on a live document is not something that can be relied on, so the creative fills
are separate documents and the view is rebuilt (removed and added) when the game mode changes.
- **Reference:** `../../../InGame/Hud/Health/HealthBackground.png` and so on. **Why that path (inferred from vanilla data):** the Assets.zip document `Hud/ReturnToHubButton.ui` names
  `../InGame/Pages/MapIcons/ReturnToCrossroads.png`, which only makes sense if `Common/UI/Custom` and the client `Interface` folder are one virtual tree (`Common/UI/Custom` holds `Common.ui`, `Common/`, `Hud/`,
  `Pages/`, `Sounds.ui`, the client `Interface` holds the same names beside `InGame/`), so three folders up from `Hud/Equipment_of_Arrakis/Hud/` is the Interface root and `InGame/Hud/...` follows. The client appends `@2x`
  to a name (the files are `HealthBackground@2x.png`, the documents say `HealthBackground.png`). Whether a texture pack that overrides the built-in path also changes ours cannot be tested here.
- **Assets.zip art** (`Common/Icons/ItemCategories/Misc.png`, `Natural-Fluid.png`, 48 x 48 white glyphs): referenced as `../../../../../Icons/ItemCategories/Misc.png` (five folders up from our document is the `Common`
  asset root of the zip). **This is the least certain path:** no vanilla document reaches the `Icons` folder by a relative path, and the virtual tree above suggests the UI root is `Interface`, not the asset root.
  If the Heat Exposure and Water icons are missing on a client while the bars show, that is why; `/arrakis hud art copy` uses copies.
- **Copy:** `VanillaDerived/<Name>.png` for everything (plain copies and the derived images), so it works whatever the client does with the paths. `ArtSource` in the Hud section (`reference` default or `copy`) and
  `/arrakis hud art reference|copy|state` (live for everyone, saved, every view rebuilt).

**Files in `VanillaDerived/` (one folder, listed with the reason; they come from Hypixel client art and Assets.zip: the user decides what ships when the mod is published).** Derived, used by both routes:
`StaminaIconWhite@2x.png` (white silhouette of `StaminaIcon@2x.png`, alpha kept: the vanilla bolt is orange and a tint would multiply to a dark colour); `NeutralHealthBarFill@2x.png`,
`NeutralCreativeHealthBarFill@2x.png` (grey copies with the shading of the Health fills, for the Power bar and its alert layer), `NeutralManaFill@2x.png`, `NeutralCreativeManaFill@2x.png` (the same from the Mana fills,
for the Heat Exposure bar and its alert layer); `ErrorLeft@2x.png`, `ErrorOutlineLeft@2x.png` (mirror images of `ManaBackgroundError` and its outline: the vanilla overlay is slanted at the right end and the
Health-shaped bars are slanted at the left, which the prompt did not expect, so the overlays could not be shared by all four bars). Plain byte copies, used by the Copy documents only: `HealthBackground`,
`HealthBarFill`, `HealthBarEffect`, `CreativeHealthBarFill`, `HealthIcon`, `CreativeHealthIcon`, `ManaBackground`, `ManaFill`, `ManaEffect`, `CreativeManaFill`, `ManaBackgroundError`, `ManaBackgroundErrorOutline`
(`@2x.png`), and `Misc.png`, `Natural-Fluid.png` from Assets.zip. The Reference documents use only the seven derived ones from our pack (unit test: no other `VanillaDerived/` path appears in them). Sizes are the
vanilla sizes (636 x 24, 635 x 24 for the Mana files, 522 x 16 for the effects, 48 x 48 icons: `HudAssetsTest`). A derived image will not follow a texture pack; the other textures in the Reference documents will.

**Colour judged from pixels (the generator prints these).** A multiply cannot brighten. Vanilla Health fill x teal `#57bbaf` = `#4b3f45` (a dull brown): unusable, so Power uses a neutral copy (x teal = `#4ea89e`).
Vanilla Mana fill x amber `#d49c1b` = `#515f17` (olive): unusable, so Heat Exposure uses a neutral copy (x amber = `#bb8a18`, x red `#c61c17`). Health uses the vanilla pink-red fill untinted and Water the
vanilla blue Mana fill untinted. **Neutral copies are not as bright as the target** (about 10% darker than the tint colour) because the median pixel maps to 0.88; if they look dull raise `PowerColor` or
`NEUTRAL_BODY` in the generator. The creative fills need their own neutral copies for the tinted bars (the creative Health and Mana fills are lavender gradients with different shading), so there are four neutral
fills. Tinted icons: Power = the white silhouette of the vanilla bolt, `#7fd0ff`; Water = `Natural-Fluid.png`, `#4da3ff`; Heat Exposure = `Misc.png` going from `#ffd23f` at 0% to `#ff8a1f` at 100% (config
`ExposureIconLow` / `ExposureIconHigh`); Health = the vanilla `HealthIcon` untinted. The Heat Exposure bar keeps the orange-to-red rule (Water API colours or the config fallback).

**The low alert (Health and Water by default).** Read from the vanilla files: `Mana.ui` has `@ErrorPulseThreshold` 0.1667, `@ErrorPulseSpeed` 10, `@ErrorPulseFlips` 4, a one-shot pulse on the error overlay when a use
of Mana fails (the comment says it is matched to the ability icon error overlay); `StaminaPanel.ui` has `@LowAlertFlashTimer` 0.25, `@LowAlertAnimationSpeed` 20, `@LowAlertOutlineOpacityStart` 0.5, a repeating
alert while the value is low. **The stamina low alert was copied**, because it is a state that lasts while the value is low, like the bars here; the Mana pulse is an event. Below `FlashBelowPercent` (5) a bar
whose switch is on (`FlashHealth` and `FlashWater` true; `FlashPower` and `FlashExposure` false, but the mechanism exists on all four) shows a red fill layer and flips every `AlertFlipSeconds` = 0.25 s
(4 updates a second at most, from one clock all bars share, starting bright): bright phase = the error overlay at full opacity and its outline at full; dim phase = the overlay at 0 and the outline at
`AlertOutlineOpacity` 0.5 (the vanilla outline start opacity, so the outline never goes away while alerting). The red fill is a second ProgressBar (`#XAlert`, in a group that is hidden otherwise) made from the
neutral fill of that shape tinted `AlertColor` `#e6004f` (the vanilla error rim `#bf0046` brightened a fifth); it is a separate layer because a red tint of the vanilla blue Mana fill gives `#580044`, dark
purple (the generator prints the check), while the neutral fill x alert red gives `#cb0046`. The overlays use the vanilla error textures. **Differences from the client alert:** the vanilla bars fade (animation speed
20), ours switch at once because the server sets the opacity on each flip. The alert stops the moment the value is 5% or more.

**Creative mode (confirmed how it is read).** `Creative.json` and `Adventure.json` carry no `HudComponents`, and `Player.getGameMode()` (the protocol `GameMode`, which `Power.isCreative` already uses) is the
signal. The runtime reads it every tick; when it differs from the mode the view was built for, the view is removed and built again from the `_Creative` document: the two left bars use `CreativeHealthBarFill`
(neutral copy for Power) and the Health icon becomes `CreativeHealthIcon`; the two right bars use `CreativeManaFill` (neutral copy for Heat Exposure). The tinted icons are the same in both modes.

**Nudges.** `/arrakis hud lb|rb|lt|rt [x[,] y]` (lb Health, rb Water, lt Power, rt Heat Exposure; commas or spaces; a third number is ignored and the reply says so). The step is (value - 1) x 10 virtual pixels
(1.1 = +1, 0.9 = -1, 1.5 = +5, 0.5 = -5, 1 = 0); x + moves right, y + moves up; they accumulate and are rounded to hundredths. No numbers prints the offsets; `reset` clears that bar; `/arrakis hud nudge reset`
clears all four. Stored in the config (`Hud.Nudges.LeftBottom|RightBottom|LeftTop|RightTop`, `X` and `Y` in pixels), written on every change through the new `ModuleContext.saveConfig()`, loaded at start, and applied to
every player at once (the views are built again on their next tick) and to later joiners. x moves a left bar by shrinking the pad between the middle of the screen and the bar (a right bar by growing it), so a
bar cannot be pushed past the middle of the screen (the reply says so); y moves the row. `state` prints the four nudges. The parsing, the step rule, accumulation and the saved format are unit tested
(`HudNudgeTest`), the layout arithmetic in `HudLayoutTest`.

**Config keys.** Removed: `BarThickness`, `TopOuter`, `BottomOuter`, `InnerGap`, `IconSize`, `TopIconCentre`, `BottomIconCentre`, `BottomRowBottom`, `RowPitch` (now `RowGap`), `HealthColor`, `WaterColor`
(the vanilla fills are used untinted), `FlashHz`, `FlashTintPercent` (replaced by the alert keys) and the slant numbers that only the first art had. Kept: `BarsLift`, `PowerColor`, the exposure fallback colours,
`FlashBelowPercent`, `MinPercentChange`, `DemoSeconds`. New: `ArtSource`, `RowGap`, `PowerIconColor`, `WaterIconColor`, `ExposureIconLow`, `ExposureIconHigh`, `AlertColor`, `AlertFlipSeconds`,
`AlertOutlineOpacity`, `FlashPower`, `FlashHealth`, `FlashExposure`, `FlashWater`, `Nudges`. **`BarsLift` default is now 0** (the bottom row is where the vanilla bars were); the test save had 13 from the first
geometry, which sat 9 below the vanilla row, so it was set to 0 there too.

**The generator.** `tools/assets/hud/make_hud.js` (with `png.js`, a small 8-bit PNG reader and writer that also reads the palette files the vanilla art uses) reads the client Interface folder and Assets.zip, and writes
the four documents, `VanillaDerived/` and `vanilla-metrics.json`; it removes the files of the first version. Re-run on this machine it gives byte-identical output (checked with a hash of every file). If a
game update changes the vanilla formulas the script stops with "vanilla layout changed" instead of writing wrong numbers.

**Not verifiable without a client:** the look against the vanilla bars and the reference screenshot, that the reference paths and the Assets.zip icon paths resolve (the unit tests and the boot only see our
pack), that `Bar.Color` tints and can be set at run time, the fill direction of the right bars, the dimness of the neutral fills, the 0.25 s flip, that `Visible` on the alert group and the colour strings with an
alpha (`#ffffff(0.50)`) are accepted when set from the server, and the nudge feel.

### Human test sheet, the vanilla-art HUD (replaces items 15 to 21 where they differ; a client)

22. Four bars above the hotbar as in the screenshot but with the vanilla art: Power (teal, light-blue bolt) over Health (vanilla pink-red, vanilla heart) on the left, Heat Exposure (amber, yellow glyph) over Water
    (vanilla blue, blue drop) on the right. The bottom row is exactly where the vanilla Health and Mana bars were; the vanilla ones are gone; stamina, the Z slot and the ability HUD are untouched. If a bar or
    icon is missing, `/arrakis hud state` and then `/arrakis hud art copy`; say which parts were missing with `reference`.
23. `/arrakis hud demo health 3` and `water 3`: the fill turns red and the red overlay and outline pulse every quarter second, and it stops at 5. `power 3` and `exposure 3` do nothing (their switches are off);
    set `FlashPower` or `FlashExposure` to true in the Hud section, restart, and they do it.
24. Switch to creative and back: the left bars use the creative health fill and the creative heart, the right bars the creative mana fill; in survival they return. Power and Heat Exposure keep their colours.
25. `/arrakis hud lt 1.1,1` moves the Power bar one pixel right, `lt 0.9,1` back, `lt 1,1.5` five pixels up, `lt 1,1,7` ignores the third number, `lt` shows the offsets, `lt reset` clears it, `nudge reset` clears
    all. Restart the server: the offsets are still there. A second player sees them at once. Check that nudging a left bar right stops at the middle of the screen.
26. Heat Exposure: the icon is yellow at low exposure and orange at high, the bar goes orange to red as before; Water drops as it should; no duplicate Water edge bars (needs the Water jar with its prompt 06 change).
27. Resize the window and change resolution: the bars keep their place against the hotbar. Compare with the vanilla Health and Mana bars with `/arrakis hud off`.
28. Look at the colours: are the neutral-fill bars (Power, Heat Exposure) as bright as the vanilla ones? Note the numbers to change (`PowerColor`, the generator `NEUTRAL_BODY`, `TINT`).

### The client could not join with the vanilla-art HUD (commit ce873f4): evidence, the change, how to bisect

**Symptom (from the client log `2026-10-18_11-30-20_client.log`).** At 13:44:05 `Disconnecting with error during stage GameLoading: Failed to apply CustomUI HUD commands (Could not find document
Hud/Water_of_Arrakis/Arrakis_Bars.ui for Custom UI Append command)`; at 13:44:43, with Water disabled, the same for `Hud/Equipment_of_Arrakis/Hud/Arrakis_Hud_Reference.ui`. The runs of 13:40 and 13:41 (before the build) joined.

**What the logs show (read, not assumed).**
- The self-check lists the four new `.ui` and every `VanillaDerived` image with no PROBLEM, and the client registered them: 23 `Added cached reference to asset` lines for exactly our new files (4 documents and the 19
  images), in the failing run and not in the good one. `Misc.png` and `Natural-Fluid.png` are missing from that list: the client only logs hashes it did not already have, and those two were byte copies of
  Assets.zip files. So the files reached the client; the client could not find the document **by name** when the HUD was applied at the join.
- The server counted `Duplicated Asset Count` 727 in the failing runs (724 / 725 in every earlier run): two new byte-identical assets, which are those two icons.
- The first failing run named **Water own unchanged document**; the second, with Water disabled, named ours. The Water HUD is applied before ours, so the first missing name is whichever the client was asked for
  first. This fits one explanation better than the others: **the client registers the pack documents in order and stops at the first one it cannot accept**, so every document after it is missing. Sorted by
  path, `Hud/Equipment_of_Arrakis/...` comes before `Hud/Water_of_Arrakis/...`, and in `Equipment_of_Arrakis` the order is `Arrakis_Hud_Copy.ui`, `Arrakis_Hud_Copy_Creative.ui`, `Arrakis_Hud_Reference.ui`: the
  Reference document is the first one that fails, as the second run says. (A hypothesis from the order of the names; the client prints no parse error.)
- Why that document could be rejected: it names texture paths outside the pack (`../../../InGame/Hud/Health/HealthBackground.png`, a guess that the pack `UI/Custom/` root and the client `Interface/` root are one
  tree, taken from `Hud/ReturnToHubButton.ui` in Assets.zip). The Copy documents name only files inside the pack. The client log has no line about it ("nothing available": the only client logs are
  `...\UserData\Logs\*_client.log`, which embeds the server log; there is no verbose switch for document compile errors that was found, and the singleplayer launch line has no debug flag).

**What was changed (the prompt step 2, all at once, because there is no client here to bisect with; the bisect is set up below).**
1. The Reference documents are no longer in the pack. `make_hud.js` writes them (and the bisect variants) to `tools/assets/hud/variants/<variant>/resources/...`; only `Arrakis_Hud_Copy.ui` and
   `Arrakis_Hud_Copy_Creative.ui` are in `src/main/resources`. `ArtSource` now defaults to `copy`.
2. `Misc.png` and `Natural-Fluid.png` in `VanillaDerived/` are re-encoded (the same pixels, written by the generator own PNG writer), so they are no longer byte-identical to the Assets.zip files. I did not use the
   prompt option of referencing the two vanilla icon paths from a Copy document: that would put an outside path into the one route that must be safe.
   `Duplicated Asset Count` of the headless boot is 724 / 725 again (727 before).
3. **The module can no longer append a document the pack does not have** (`HudDocs.pick`, unit tested): it asks the pack registry (`CommonAssetRegistry`) and picks the route asked for, else the Copy one, else
   nothing. With no document it shows no bars, leaves the vanilla Health and Mana bars alone, and logs a warning once. `/arrakis hud art reference` is refused while the pack has no Reference documents, and `art state` says
   what the pack has. A document that is in the pack but that the client rejects cannot be detected from the server; that is what the bisect is for.
4. `HudAssetsTest` checks that the Reference documents are not in the pack, that the two icons are our re-encode, and still checks the Reference documents under `variants/` for ids and paths.

**What the player must test now:** join the world with this jar (Water on and off). If it joins, the Reference documents (outside paths) or the duplicate icons were the cause. If it still fails, see "if it still fails".

**Bisect, one change per build** (`gradlew jar deployMod -PhudExtra=<variant>` adds one variant to the jar; a normal build never contains them; stop at the first one that breaks the join):
| variant | adds to the pack | answers |
|---|---|---|
| `dupicons` | `VanillaDerived/Misc-Dup.png` and `Natural-Fluid-Dup.png`, byte copies of the vanilla icons | do duplicate hashes alone break the join (the asset count goes back to 727) |
| `refinpack` | documents named `Arrakis_Hud_Reference*.ui` whose texture paths all stay inside the pack | do extra documents, or those names, break it (paths are not the cause) |
| `reference` | the two Reference documents with outside paths (the first build) | do outside texture paths break it |
| `probe` | `Arrakis_Hud_Probe.ui` (one outside path), appended only by `/arrakis hud probe` | does the guess "pack `UI/Custom` root = client `Interface` root" resolve (the bar-shaped background shows) |
Results: **none recorded yet** (no client was available while this was written); fill the table in after the tests.

**If it still fails with only the Copy documents** (not done yet): diff `Arrakis_Hud_Copy.ui` against the last document that joined (`5959df3`, `Arrakis_Hud.ui`) and bisect the new constructs in a minimal document: `ProgressBar` with `BarTexturePath`,
`Value`, `Bar: (Color: ...)`; `PatchStyle(TexturePath: ..., Color: #ffffff(0))`; `Background: (TexturePath: ..., Color: ...)`; `Visible: false`; the file name with a hyphen (`Natural-Fluid.png`); ids that are the
same in two documents (the four documents share every id); two documents with nearly the same content. The Water mod `Arrakis_Bars.ui` and the vanilla `Common.ui` `@ProgressBar` are the references for forms the client
accepts from a pack document (`Common.ui` uses `ProgressBar { Anchor; Background; BarTexturePath; EffectTexturePath; EffectWidth; EffectHeight; EffectOffset }`; a `Value:` is set in the UI gallery document).

**What the reference route needs, and its status (honest).** *Untested; the evidence points at it as the cause of the join failure, but that is not proven.* A pack document naming paths outside the pack
(`../../../InGame/Hud/...`) was in the build that could not join. Whether the client resolves such a path from a pack document, and whether an unresolved path rejects the whole document (and then every document
after it), is exactly what the `reference` and `probe` variants decide. Until then the art rule "use the vanilla art by reference" is **not met by the HUD**: the Copy route ships copies (`VanillaDerived/`), which
is the documented exception. The earlier notes ("Reference ... confirmed/inferred") above describe the design, not a working route.

**Human test sheet additions.** 29. Join with the new jar, Water on and Water off: it joins and the four bars show with the Copy art. 30. `/arrakis hud art state` says the pack has the Copy documents and not the Reference ones;
`/arrakis hud art reference` is refused. 31. If it still cannot join, say which build variant (none / `dupicons` / `refinpack` / `reference` / `probe`) and quote the client log line.

### The client crash at Water below 5% (first client test of the Copy build)

The Copy build joined. Typing `water set 5` then crashed the client; the server log only says `left with reason: Crash`, and every join after that crashed again (the Water value was saved at 5). The real
message is in the **client** log (`2026-10-18_11-30-20_client.log`, 14:07:40): `Disconnecting with error during stage InGame: Failed to apply CustomUI HUD commands (CustomUI Set command couldn't set value.
Selector: #WaterError.Background.Color -> Failed to convert JSON value (String) to specified type (UInt32Color))`. **Cause:** the alert set the colour of the error overlay with the string `#ffffff(1.00)`. The
`(alpha)` form is `.ui` syntax; a colour set from the server must be a plain `#rrggbb`, and the client disconnects on a string it cannot convert. **Fix:** no opacity is ever set at run time now. Each bar has
three overlay groups that start hidden (`Visible: false`): `#XError` and `#XOutline` (opaque) and `#XOutlineDim` (a fixed half-opaque outline, `Color: #ffffff(0.5)` in the document, the vanilla
`@LowAlertOutlineOpacityStart`); the alert shows the first two in the bright phase and only the dim outline in the dim phase (`HudMath.overlays`, unit tested; `HudAssetsTest` checks the groups start hidden and that
no document sets an alpha of 0). **Config:** `AlertOutlineOpacity` was removed (the number is the vanilla one written into the documents by the generator). **Confirmed from the same log:** in that command batch
`#WaterAlert.Value` and `#WaterAlertGroup.Visible` were applied before the failing line and the client accepted them, so `ProgressBar.Value` and `Visible` on a group work from the server; plain `#rrggbb` strings on
`Bar.Color` and `Background.Color` were accepted at the join. **Still needs a client:** that the alert now shows and flashes (test 23).

 for percent 0 to 100, no textured ProgressBar sets `Bar:`, no source mentions a `Bar.Color` selector.

**HorizontalBorder 9, how it was measured.** The generator-side check of the vanilla PNGs: the first opaque column of the slanted end is 16, 12, 8, 4, 0 on rows 0, 6, 12, 18, 23 of the 24 row fill (Health fill, creative fill, and mirrored for the Mana fills: 15, 11, 7, 3, 2 from the right
edge), so the slant is 16 texture pixels wide; the texture is drawn at 2x, so the cap is 8 virtual pixels and 9 (18 texture pixels) covers it with the 1 to 2 pixel rim. The 9-slice stretches only the middle, so the slanted end keeps its shape
at every fill. (HorizontalBorder is in virtual pixels, like the Water bars VerticalBorder 12 for 24 texture pixels.) Both ends get the border: the square end of a Health-shaped fill and the left end of a Mana-shaped fill are 1 to 2 pixels, so they are
unaffected. A fill shorter than 18 virtual pixels (under about 5.7%) is squeezed by the 9-slice; that is the same limit as the Water bars and the lowest part of the alert range.

**Colours (printed by the generator; the neutral body was raised from 0.88 to 1.0 because the first copies came out about 10% dark).** Power: neutral health fill x teal `#57bbaf` = `#55b6aa` (creative `#54b4a8`); Heat Exposure: neutral mana fill x
amber `#d49c1b` = `#d0991b` (creative `#cc961a`), x red `#e0201a` = `#dc1f1a`; alert: neutral fill x `#e6004f` = `#e0004d` (health shape) and `#e2004e` (mana shape), against the vanilla error overlay body `#960025` and rim `#bf0046`.
Those are averages over the opaque pixels; the rim stays at 1.0 and reads slightly lighter. A plain tint of the vanilla fills for comparison: health x teal `#4b3f45`, mana x amber `#515f17`, mana x alert `#580044`.
**Only a client can show:** the colours against the reference screenshot, the slanted end when a bar is partly full (check at 10%, 50%, 90%), whether the Heat Exposure fill and icon change smoothly, and that `/arrakis hud demo health 3` and `water 3` flash red.

## Harness notes

- `tools/headless/run.sh "wait 35" "arrakis modules" ...` boots the server with this jar; world commands from the console run in world `default`.
  After `stop` the log shows `Removing world exceptionally` / `Reloading crashed world 'default'`; it appears at shutdown and was not investigated.

## Experiments (delete this section with the module)

Throw-away look-and-feel experiments, all in the module `experiments`. They are replaced by the real modules when a design is chosen,
so everything of them is findable by one search (`Arrakis_Exp_` for assets and ids, `Exp` for classes, the word `Experiments` for the
package and every asset folder) and leaves no trace when removed. Experiment 1 is the Flame Cutter, experiment 2 the Flame Beam Cutter (with the calibration tools), experiment 4 the tool picker page.

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

Then `gradlew build`, delete `Experiments_Calibration.json` from the plugin data folder if it was saved, and delete `Equipment_of_Arrakis.json` in the plugin's data folder (or remove `"experiments"` from its `Modules` and
the `Experiments` section) if the game has already written one with them. Check: `grep -rIli -E "arrakis_exp_|experiments" .` finds nothing
(ignoring build, .git, .gradle).

**Rehearsal (run for real, on a copy of the project in a temp folder, discarded afterwards):** the script above was applied; `grep` found
no trace; `gradlew build` passed with 20 tests (the 30 experiment tests gone with the module, the other 20 unchanged); a headless boot
loaded `core, power, catalog` (`modules loaded: 3 of 3`, `self-check: no problems`), logged nothing mentioning an experiment, no
exception, and wrote a config with no Experiments section.

**Switching the module off without a rebuild** (remove `"experiments"` from `Modules`): the server boots cleanly and
`/arrakis modules` shows `experiments: disabled`. The asset pack loads every asset whatever the config says, and one asset whose
interaction type nobody registered stops the whole boot (`Asset validation FAILED ... Mod ... failed to load`, seen in the first try).
So the module's constructor (which runs even when the module is off) registers the `Arrakis_Exp_Cutter_Tick` type and a `BootEvent`
listener; when `setup` did not run, the listener removes every `Arrakis_Exp_` asset (Flame Cutter: 1 item, 1 root interaction, 2 interactions,
1 particle system, 2 spawners = 7; Flame Beam Cutter: 1 item, 1 root interaction, 2 interactions, 4 calibration systems, 5 spawners = 13; 20 in all) before any player can join. Left in the pack and in the client download: the 17 KB texture copy
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
as described, the removal rehearsal, and the unit tests (50 in all: the 20 from before, `ExperimentsAssetsTest` 7, `ExperimentsIsolationTest` 7, `ExpCalibrationTest` 11 (with the aim maths), `ExperimentsBeamAssetsTest` 5) (round 3 later made it 34 experiment tests, 54 in all).
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

### Experiment 2: the Flame Beam Cutter, and the calibration tools

**The Drill Cutter (first version of experiment 2) was dropped: "no good at all".** Everything of it is gone (item, chain, gate type, test, script). What it
taught, in case the native route comes back: `BreakBlock` honours the item's `InteractionConfiguration.UseDistance` (keys `Adventure` and `Creative` only, defaults
5.0 and 6.0), the client picks the block and the server re-checks it in `InteractionValidation.canEntityInteractWithBlock`; a `Parallel` forks its branches
as chains of their own (`InteractionContext.fork`), so a Mana gate must sit in front of it, not inside; the vanilla `NatureBeam` (Stoneskin laser)
spans about 1.2 to 5.2 blocks when placed at Z 3.2 (`tools/assets/experiments/measure_nature_beam.js`), its end burst centred at 4.97; Mithril still beats every
pickaxe on 0.7.0; the comparison of the native route with the custom one was: native = engine does targeting, tool rules and events, 0.3 s swings, data only;
custom = server ray, 0.1 s ticks, Mana only when something is cut, version-sensitive Java. The decision between them is still open.

**What was built.** `Arrakis_Exp_Flame_Beam` ("EXPERIMENT Flame Beam Cutter", IslandCraft tab, Tools): a duplicate of the Flame Cutter (same model, recoloured texture
copy, `Goblin_Flamethrower` animations, tags, and the same tick `Arrakis_Exp_Cutter_Tick`: Mana, range 5, cutting like a Mithril pickaxe) with the ring of dots
replaced by the Stoneskin wand's laser, the vanilla `NatureBeam` by id. Primary = `Arrakis_Exp_Flame_Beam_Primary` = [`Arrakis_Exp_Cutter_Tick`,
`Arrakis_Exp_Flame_Beam_Spawn`, `Arrakis_Exp_Flame_Beam_Pace`]. There is **no `ItemAnimationId` and no `ItemPlayerAnimationsId`** anywhere in the chain (the pace is a bare
0.1 s `Simple`), so the weapon stays in its hold pose. The original Flame Cutter (experiment 1) is untouched: it still plays the Burn animation and keeps the aim
measured from the screenshots. The prompt's other asks for it (a static weapon, an FP/TP stream generator with collision and an impact particle) were NOT done.

**Why the first version of the laser was wrong, and the fix (world space).** The first version attached the NatureBeam to a model node (the calibration
`fp`/`tp` numbers: node, offset, rotation, as vanilla Stoneskin does). The test with `Screenshots/vanishing point.png` showed the flaw: an emitter attached to a node points
where the node's frame points, so the numbers moved where the beam STARTS and the stream's vanishing point stayed wherever the node's axis happens to aim; it was
not the look target, and no number set the end point. The fix, which does not depend on any node frame: the **server spawns the beam in world space every tick**
(`ExpBeamGeometry`, interaction type `Arrakis_Exp_Beam_Spawn`). Each tick it takes the look ray (`TargetUtil.getLook`), the START = eye + right x Right + up x Up + forward x Forward
(the camera frame, `ExpAim.cameraPoint`), the END = the look target (the point `Apex` blocks along the ray, or where the ray enters a block with `EndAtBlock`, the same point
the `target` marker shows), turns the system so that its forward axis points from the start to the end (`ExpAim.yaw` / `pitch`, the exact inverse of the engine's
`Transform.getDirection`, checked in a unit test) and sends a `SpawnParticleSystem` packet with a short `maxDuration`. The NatureBeam has a fixed length (its end burst sits
1.8 blocks in front of the origin, `measure_nature_beam.js`), so its origin is put 1.8 before the end point: its far end lands ON the look target by construction, and its
start is about 4 blocks back along the line (with the start at about 1 block from the eye and the apex at 5, that is the muzzle). The vanishing point of the beam is the
look target, so in first person (where the head ray is the camera ray) it is the crosshair dot. The only numbers left to calibrate are where the beam STARTS (`cam`: right, up, forward
of the eye); the end is not a number.
**Limits.** In third person the look ray is the head's, not the camera's (see above), so the beam ends on the head ray's target and the crosshair is off by the parallax; and
there is one start for both views (the camera frame has no idea which view is on), so in third person the beam starts at the same offset from the eye, not at the model's gun.
A world-space beam is not attached: it is re-spawned every 0.1 s and lags the turn of the head by at most that. Whether the packet's rotation turns the system the way the
maths assumes (default emission along -Z, the look direction at yaw 0) is **inferred** from `Transform.getDirection` and not seen: `beam world` shows it.

**Confirmed vs inferred, headless.** Confirmed at boot: the item, root, the `Arrakis_Exp_Beam_Spawn` and pace interactions and the four calibration particle systems
load with no warning; `$Comment` is accepted everywhere; the `/arrakis exp cal ...` command tree registers (the console is told to run it as a player);
**the engine's ModelParticle codec takes `RotationOffset` in degrees as written**; and the unit tests (yaw, pitch and the camera frame against the engine's own
`Transform.getDirection`, the JSON block, the +/-/set handling, the data files). Not testable without a client: every rig, the look of the beam, and whether the packet
turns the system as assumed.

**Where the game aims in third person (from the bytecode).** `TargetUtil.getLook(ref, store)` builds its `Transform` from the entity's `TransformComponent` position plus
`ModelComponent.getEyeHeight`, and the `HeadRotation` component: the player's own head ray, with no knowledge of the camera. The `Raycast` selector of the vanilla
wand (Offset Y 1.6) starts at the entity the same way. Interactions that act on a block do not use that ray: the client picks the block (`InteractionSyncData.blockPosition`)
and the server only validates its distance from the eye. In first person the camera sits at the eye, so the head ray should equal the camera ray; in third
person the camera is behind and above the head, so the ray through the screen centre and the head ray are two different lines, and the dots of `collapse` will not hide
behind the crosshair there (parallax, **inferred**; the rig measures it by eye: the dots should lie on a line that passes beside the dot and meets it at the distance of
the thing the player looks at). The server has no camera pose to compute the true crosshair ray in third person, so a beam that must land on the crosshair in third person
has to be calibrated by eye (the `tp` numbers) or aimed at a block the client picked.

**Which node follows the camera** is what the `nodes` rig finds out; not run (needs a client), so no answer yet. The player model has no `Body` node (nodes:
Origin, Pelvis, Belly, Chest, Head, Neck, arms, hands, attachments ...), the first person arms model has its own.

#### How to calibrate (a recipe for the Flame Beam Cutter, or any beam)

Operator, in game, standing about 10 blocks from a long plain wall, with the Flame Beam Cutter in hand. All commands are `/arrakis exp cal ...`; one player's state is kept
in memory (cleared on disconnect); `save` / `load` keep it in the plugin data folder as `Experiments_Calibration.json`. `step 0.01` and `astep 0.5` set what `+` and `-` add
to a position and to an angle. `off` switches every rig off.
1. `collapse on`. Rows of small white dots appear along the server's look ray, 1 to 12 blocks. **First person:** turn your head up, down, left, right. **Aligned**
   means every dot hides behind the white crosshair dot and they all stay there while you turn (a streak, a spiral or drift means the server ray is not the view).
   **Third person** (V): the dots will not collapse onto the dot; note which way and how much they sit off (that is the parallax). `collapse off`.
2. `nodes` (or `nodes Head Neck Chest ...`; the chat prints a colour legend; a name the client does not accept shows nothing). Look at the wall, straight up, straight
   down, left and right, in first person and then in third person. **The node that follows the camera** is the one whose ruler (small dots on its + side, big dots on its
   - side, 10 blocks) stays glued to the crosshair dot as you look around; in first person it collapses into the dot like step 1. Write down the node for each view.
   `nodes off`.
3. `target on`: a magenta cross appears at the block the look ray enters (or at the apex distance when nothing is hit). It should sit exactly on the spot the dot points at.
4. Aim, in the way that does not depend on any node: `beam world` (a fine stream) or `beam worldnature` (the NatureBeam itself), with `target on` too. The server spawns it
   every tick from the camera-frame start `cam` to the look target, so **the far end is exactly the magenta cross, which is where the crosshair dot points**. Only the START is yours to move:
   `cam x +` (right), `cam y -` (up), `cam z 0.6` (forward of the eye), `cam scale 1`; `end block` or `end apex` chooses whether it ends where the ray enters a block or at the
   apex distance (`apex 5`). **Aligned** means the beam leaves where the gun muzzle looks to be and its far end sits in the magenta cross on the dot, from close up and far away, and while
   you turn. (The older node-attached beams stay for comparison: `fp node Body`, `fp x +` ..., `view fp`, `beam nature`; their direction is the node's, not the look's.)
5. `show` prints the paste-ready block (JSON with `apex`, the steps and the `fp` and `tp` objects with node x y z yaw pitch roll scale detached, plus the date). `save` writes it
   to `Experiments_Calibration.json`. Paste the block to me, or replace `tools/assets/experiments/calibration.json` with it, run
   `node tools/assets/experiments/make_flame_beam.js`, `gradlew build` and `gradlew deployMod`.

How the numbers map: `cam x y z` are blocks in the camera frame (right, up, forward of the eye) where the beam starts; `apex` and `end` choose where it ends along the look ray; `fp` and `tp` (node, x y z, yaw pitch roll in degrees as the engine takes them, scale, detached) are for beams attached to a model node, as Stoneskin does.

#### Round 3: a laser on every tick with a pulsing glow, and the Flame Cutter's ring from the hand (world space, body frame)

What the user saw in game before this round: the Stoneskin laser (`beam world` / the Flame Beam Cutter) lands on the crosshair in first AND third
person (the world-space spawn works, so the packet turns a system as `ExpAim.yaw/pitch` assume); the Flame Cutter's ring landed on the crosshair in first
person but in third person it still started at the gun.

* **Every tick.** The Flame Beam Cutter's chain now passes every 0.03 s (one server tick at 30 a second): `Arrakis_Exp_Flame_Beam_Tick` (generated copy of
  the tick: same Mana and cutting per second, in steps of 0.03 s, `DamageScale` scaled), then the spawn, then the pace. Each laser lasts 0.05 s, so there
  is no gap and the laser follows the look to within a tick. The server cannot draw more often than it ticks; the client draws what lasts.
* **Pulsing glow.** `make_beam_glow.js` writes `Arrakis_Exp_Beam_Glow_0..3`: soft green sprites on a thin ring around the axis, flown along it for 4.5 blocks,
  the same halo at four brightnesses. With each laser the server also spawns ONE of them, chosen by `ExpAim.pulseLevel` (a sine of `pulseHz`, default 2
  cycles a second, `/arrakis exp cal pulse <hz>`). Switching levels pulses the glow without stretching it (a scale pulse would scale its length too).
  Look (opacity per level, radius, size, colour) is at the top of the script. **Not seen in game.**
* **The Flame Cutter's ring from the hand.** The node-attached ring is gone (it could only be aimed for one view). `Arrakis_Exp_Flame_Spawn` spawns
  `Arrakis_Exp_Flame_Focus` in world space like the laser, with `Frame: Body`, origin 3.8 blocks (= the ring's travel) before the look target, so its
  focus is the target by construction in both views. The start is the **grip**, in the body frame (`ExpAim.bodyPoint`): heading of the body (look
  direction with the height removed: the body does not pitch with the head), right/up/forward as numbers, signed right (negative for a left-hander).
  This is a change in first person too (the old node-attached version, measured to land on the dot, is in git: `ce45eb1`).
* **The maths of the right-handed grip** (`ExpAim.gripPoint(h)`, player height h = 1.8 blocks, eyes at 0.9 h = 1.62): the middle of the body (the `Body`
  node, the "centre of mass") is at 0.55 h; a one-handed gun is held at chest height 0.72 h = 1.296, i.e. 0.324 below the eye; the right shoulder is
  0.125 h = 0.225 from the middle and the grip 0.05 further out, so **0.275 to the right**; the arm reaches 0.25 h = **0.45 forward**. So the start is
  (right 0.275, up -0.324, forward 0.45) from the eye along the body's heading; a left-hander is (-0.275, ...). Tune with `/arrakis exp cal body x|y|z`,
  preview with `/arrakis exp cal frame body` + `beam world` or `worldnature` + `target on`, then `show`, paste into `calibration.json`
  (`body`), `node tools/assets/experiments/make_flame_beam.js`, rebuild.
* New calibration commands: `body` (like `cam`), `frame cam|body`, `pulse <hz>`. `show` prints `body`, `frame`, `pulseHz` too.
* Checked headlessly: 34 experiment tests pass (54 with the base 20) (new: the grip numbers and the body frame, the pulse levels, the JSON round trip, the beam tick's rates,
  the ring's spawn data); boot loads everything with no warning; with the module off the boot removes 30 `Arrakis_Exp_` assets (up from 20) and the
  server runs. Not testable without a client: what any of it looks like.
* Why the packet is the only way to be right in both views: the server does not know the view, so one start per spawn; the body-frame grip is near where the
  gun is in both (lower right, in front), which is the closest one number can be to "from the gun" in first person and in third.
* Removal rehearsal repeated after this round (script above on a temp copy): no trace, `gradlew build` with the 20 base tests, no experiment file left.

#### Round 4: why the laser showed for 2 frames (fixed: cast like the vanilla one)

The screenshots of round 3 showed a thin laser and a faint glow for about 2 frames in 30; the `Vanilla ...` screenshots show a big green cloud and a beam.
Reading the vanilla assets explained it:
* `Stoneskin_Cast` spawns `NatureBeam` ONCE per cast, with `RunTime 0.2`: the system emits for 0.2 s. The beam streak (`NatureBeam_Beam`, `_Beam_Overlay`) is a burst
  with `WaveDelay 0.1`, life 0.2 and fades in over its first 15-50 %; the continuous parts (`_Core`, `_End`, `_Sparks`, 150-200 particles a second) fade in over 20 % of 0.1-0.2 s.
  The packet's `maxDuration` is the emission time (0 is what the vanilla server code sends: no cut), so my 0.05 s cut the streak off before it had started and let only the first
  fade-in of the rest show. Pressing the button rapidly overlaps full 0.2 s casts: the always-on beam you saw.
* My own glow had the same cut (0.05 s) and no fade, so it flickered.
* Fix: each cast lasts 0.2 s (the vanilla time) and a new one is cast every 0.1 s (pace 0.1, the tick back at 0.1 s), so two overlap. The glow's sprites live 0.2 s with a fade in
  and out, and the calibration preview (`beam worldnature`) also casts for 0.2 s. Cost: a head turn leaves the old casts at the old aim for up to 0.2 s (a short trail), and the
  intensity is that of two overlapping casts. **Not seen in game.** If it is too bright: lower `OPACITY` in `make_beam_glow.js`, or raise the pace in `make_flame_beam.js`
  (casts every 0.15 s overlap 1.3 times; below 0.1 s the streak may gap).

#### Round 5: the Flame Cutter as a single dot (test)

The start and vanishing point are right in game (user), so the Flame Cutter's ring of dots was replaced by ONE dot per cast that flies from the grip along the path to the
vanishing point and shrinks from about 10 px to 1 px wide (1920x1080, relative sizes for the test). `make_flame_focus.js`: a one-particle burst, life 0.3 s, speed
(5 - 1.2) / 0.3 = 12.7 blocks a second, size 0.031 -> 0.013 blocks. The sizes are computed from the pixel targets: size = px * 2 * distance * tan(VFOV/2) / 1080 * SPRITE_GAIN (the soft Glow sprite fills about
half its square), distance 1.2 at the start and 5 at the end, **VFOV 70 degrees is a guess** (the client's field of view is not known to the server): if the dot looks too big or small, change VFOV
or SPRITE_GAIN. The chain passes every 0.3 s (new `Arrakis_Exp_Flame_Tick`, same Mana and cutting per second; `Burn` 0.3 s; each cast emits 0.35 s) so a new dot is born as the last arrives.
The ring version is in git (`da9b732`). Not seen in game.

Follow-up: the user liked it and asked for 50 px down to 1 px (`START_PX = 50`: world size 0.156 -> 0.013 blocks).

Follow-up 2: **a line of white dots** instead of one. The maths (`make_flame_focus.js`): the dots are released at a constant rate and fly at one speed, so they are evenly spaced in the world; on screen
the path (about 0.35 blocks off the view axis at 1.2 blocks, ending on the axis at 5) is about 225 px long, and the spacing at fraction t is the rate of change of the screen position divided by the number
of dots. A line is solid where that spacing is at most 0.8 of the dot's width; the binding place is the far end (a 1 px dot on a path that still moves), and the least number is **68 dots**; 100 is used
(spacing 9.4 px against a 50 px dot at the grip, 0.54 px against 1 px at the far end). 100 dots per 0.3 s is a rate of 333 a second; each cast emits for exactly one pass (0.3 s) so the casts join up.
Not seen in game; if it looks beaded near the grip or too bright where the dots overlap, change `DOTS` or `SOLIDITY`.

#### Round 6: the laser as part of the weapon model (test)

The Flame Cutter is now a model test: no particles, no animation, the laser is geometry of the weapon.
* `make_flame_model.js` (reads the vanilla model/animations from an unzipped Assets.zip, see its header) writes **a second vanilla copy** (the first is the texture):
  `Arrakis_Exp_Flame_Cutter.blockymodel` = `Scrap.blockymodel` + a node `Arrakis_Beam` on `Origin_Projectile` (the muzzle: its y axis is the gun's forward +Z, checked by composing the
  node quaternions, `Origin_Projectile` sits at the barrel end) holding 24 unlit (`shadingMode fullbright`) white boxes, 96 units = 3 blocks (32 units a block, assumed),
  stepping from 50 px to 2 px wide on screen (1920x1080; world width = px * 2 d tan(35 deg) / 1080 with d from 1 block at the muzzle; VFOV 70 and the 1 block are guesses; the world width
  first rises a little, the perspective shrinks a line faster than this taper). It points along the old measured aim (yaw 4.49, pitch 0.73 from the gun axis, tweak `AIM_YAW` / `AIM_PITCH`).
  The boxes' faces point at a white 16x16 patch that `make_flame_texture.js` now paints in an empty corner (48,112) of the texture copy.
* `Arrakis_Exp_Flame_Static`: the vanilla `Rifle` animation set with **only the third-person Idle** replaced by one fixed pose (first all 43 third-person animations were; walk, run, jump, swim, shoot ... were given back to the vanilla rifle afterwards)
  (the first frame of the rifle idle, a copy of `Idle.blockyanim` reduced to one key per channel), so the gun does not move against the body while standing; first person keeps the vanilla rifle animations.
  The server only accepts animation files under `Characters/` or `NPC/` (found at boot), so the pose lives under `Characters/Animations/Items/Equipment_of_Arrakis/Experiments`.
* The Flame Cutter's chain is `[Arrakis_Exp_Flame_Tick, Arrakis_Exp_Flame_Burn]`, the latter now a bare 0.3 s pass (no `Effects`). The world-space dot-line assets (`Arrakis_Exp_Flame_Spawn`, `Arrakis_Exp_Flame_Focus`) stay in the pack, unused.
  The Flame Beam Cutter now sets its own model (vanilla, by path) and animations (`Goblin_Flamethrower`) so it did not change.
* **Swapping the model while firing is NOT done.** What exists: item `State` variants with a `ChangeState` interaction are for BLOCKS (the class extends `SimpleBlockInteraction`); `ItemAppearanceConditions` swap
  model/texture/particles by the value of an entity STAT (Health in the editor tools), and nothing here changes a stat only while firing (and no new stat is allowed); `ModifyInventory` would move
  item stacks around. The animation files that the item animations point at move the character skeleton, not the item's own nodes. So the beam is always visible for now (that is what the test
  needs: its size, start, direction and the fixed hold). Options for the swap, if wanted: the Mana percentage (`ItemAppearanceConditions` on Mana with a range) is the only stat that moves while firing.
* Checked: 59 tests pass; the model, the animation set, the texture patch are validated by `ExperimentsModelTest` (5); the boot loads the item and the set with no warning; with the module off the boot removes 31 assets (the
  animation set included). Not seen in game: the model's look in either view, the beam's start and aim, the unit size (32/block), the third-person pose.
* Removal rehearsal repeated after round 6: no trace (the `Common/Characters/.../Experiments` folder goes with the others), build OK with the 20 base tests.

### Experiment 4 was promoted to the picker module

The tool picker page (a right-click page listing the Arrakis tools you carry, click to swap into the hand, plus passive toggles) worked as a mechanism on the
server (an engine `OpenCustomUI` interaction with the page registered through `OpenCustomUIInteraction.registerSimple`, one atomic
`ItemContainer.swapItems`, a pure tested swap rule) and became the real `picker` module (see "Module notes: picker"). Its code and its three `.ui` files moved there, renamed.
What was removed from the experiments module: the four stand-in tools `Arrakis_Tool_Axe/Pickaxe/Hoe/Shovel` (items, translations), the interaction
`Arrakis_Exp_Tool_Picker_Open` and root interaction `Arrakis_Exp_Tool_Picker_Secondary`, `ExpToolCommands` (`/arrakis exp tools`), and the `ExperimentsModule` and
`ExperimentsIsolationTest` edits (the `Arrakis_Tool_` allow-list is gone: the prefix now belongs to the real tools, outside the experiments module). Experiments 1 to 3 are unchanged.
Removal rehearsal after the cleanup: the experiments-removal script on a copy leaves no `arrakis_exp_` or `experiments` trace and the build passes (see below, "Module notes").
