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
