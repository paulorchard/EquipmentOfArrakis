# Archive: what was tried and abandoned, and the client rules learned

The full history (every experiment, probe and bisect) is recoverable from git: tag `equipment-v2.1-baseline` is the last commit before the cleanup, and `equipment-v1-final` is the v1 mod.

## Client rules learned on a real client (0.7.0)

The server log only says "Crash"; the real message is in the client log, `%APPDATA%\Hytale\UserData\Logs\<date>_client.log`.

1. **A bad run-time `Set` disconnects the player** ("Failed to apply CustomUI HUD commands"). Validate every string before sending it.
2. **No colour with an alpha at run time.** `#ffffff(0.5)` is `.ui` syntax; sent from the server it fails with `Failed to convert JSON value (String) to specified type (UInt32Color)`. Send plain
   `#rrggbb`; do opacity with `Visible` on groups whose alpha is fixed in the document.
3. **`Bar.Color` does not tint a textured `ProgressBar`** (the fill showed grey). `Background: (TexturePath, HorizontalBorder, Color)` on a `Group` does. A fill's length is two groups sharing
   `FlexWeight` (value and 1000 minus value).
4. **Safe run-time sets (proven):** `.FlexWeight`, `.Visible`, `ProgressBar .Value`, `.Background.Color` (`#rrggbb`), `.Anchor` objects. Texture paths are not changed at run time; use separate documents.
5. **A relative texture path cannot leave the pack.** A pack document naming `../../../InGame/Hud/...` made the client reject it, and the client registers pack documents in path order and stops at the first
   it cannot accept, so every later document was "not found" and the join failed. Never ship such a document; append only a document the pack has.
6. **A custom HUD cannot address elements of the built-in client UI.** `#ActiveItemNameLabel.Anchor` gave "Selected element in CustomUI command was not found" and ended the session. Vanilla labels
   cannot be moved, hidden or restyled from the server; make room with our own layout.
7. **A byte-identical copy of a vanilla file counts as a "Duplicated Asset"** on the server (the count went from 725 to 727). Re-encode the pixels instead.
8. **The category `Items.Weapons` drives the client's vanilla weapon ability HUD** (the ability status and the buttons on the right). An Arrakis weapon listing only `IslandCraft.Weapons` lost it;
   listing both keeps it. (Found through the community mod WeaponCustomHUD and a field-by-field diff of the Crude daggers against ours: categories, Ability4, `maxDurability 0` and
   `durabilityLossOnHit 0` were the only differences. The category was the first suspect; this is the change that was made, with the client result still to be confirmed in the test sheet.)
9. **Engine behaviour found by reading bytecode:** the default `InteractionRules` make every standard input type blocked by every other while a chain runs, so an Ability4 root needs
   `Rules {"BlockedBy": []}` to work mid-swing; a killed NPC keeps its entity for the death animation time only in vanilla (the extractor module now stretches it to `CorpseSeconds`, see MODULES.md); a `Projectile` asset is not removed by `ModuleAssets` when a module is off; the asset pack
   loads every asset whatever the config says, and one asset whose interaction type nobody registered stops the whole boot.

## Tried and abandoned

- **Flame Cutter and Flame Beam Cutter (the experiments module).** A flamethrower-style tool and a beam tool with a calibration rig (about 17 `exp cal` commands, particles, a measured angle unit) to place a
  beam on the player. They answered what the engine allows for held-item particles and beams; the Cutter Ray took the useful part (a vanilla-gun look, no projectile). Removed in 0.3.0 with its 34 assets
  and generators.
- **The Z-wheel / Z slot idea.** A utility slot as the way to select equipment; replaced by the picker page (right click on any tool) and the H key for the shield.
- **The Reference art route of the HUD.** Documents whose textures pointed at the vanilla files by relative path (`../../../InGame/Hud/...`), so a texture pack would restyle the bars. The client rejected
  them (rule 5). The Copy route (`VanillaDerived/`) is the only route; the `ArtSource` setting and `/arrakis hud art` are gone.
- **The held-item name label.** The client draws it (`Label #ActiveItemNameLabel` in `Hotbar.ui`, Bottom 126, Height 40, centred). A dev command and an `ItemNameMove` setting tried to move it; the client
  refused the selector (rule 6). The fallback is `BarsLift` (50 or 64).
- **The bisect that found the join failure.** Four build variants (`dupicons`, `refinpack`, `reference`, `probe`, added with `-PhudExtra`) were set up to separate the cause; the evidence and the fix
  (Reference documents out of the pack, icons re-encoded) were enough, so none was needed. Removed with the flag.
- **The first HUD art.** Bars drawn by a pixel rasteriser (`make_hud.js`); they looked crude and were rebuilt from the vanilla Health and Mana art.
- **The ammo gate of the guns.** v1 consumed arrows; now `Arrakis_Gun_Gate` has `Cost 0` and `GunCost.charge` is the place to put an ammo system.
- **Investigation commands:** `weapons diff` (a field-by-field item comparison), `weapons trace`, `shield diag`, `hud probe`, `hud itemname`, `hud art`: removed in 0.3.0.
