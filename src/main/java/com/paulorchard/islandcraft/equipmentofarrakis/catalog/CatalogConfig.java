package com.paulorchard.islandcraft.equipmentofarrakis.catalog;

import com.hypixel.hytale.codec.builder.BuilderCodec;

/** The "Catalog" section of Equipment_of_Arrakis.json. The tab is asset data, so there is nothing to tune yet. */
public class CatalogConfig {

    public static final BuilderCodec<CatalogConfig> CODEC = BuilderCodec.builder(CatalogConfig.class, CatalogConfig::new)
            .documentation("Settings of the catalog module (the IslandCraft creative-menu tab). None yet: the tab is asset data.")
            .build();
}
