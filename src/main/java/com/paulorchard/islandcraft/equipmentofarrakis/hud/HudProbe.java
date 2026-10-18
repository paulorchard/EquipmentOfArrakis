package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/** Development: a HUD that only appends one document (the bisect probe of /arrakis hud probe). */
final class HudProbe extends CustomUIHud {

    private final String document;

    HudProbe(PlayerRef playerRef, String document) {
        super(playerRef, "EquipmentOfArrakisProbe");
        this.document = document;
    }

    @Override
    protected void build(UICommandBuilder ui) {
        ui.append(document);
    }
}
