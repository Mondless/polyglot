package net.mondless.polylingo;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.mondless.polylingo.config.PolylingoConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class PolylingoClient implements ClientModInitializer {
    public static final String MOD_ID = "polylingo";

    public static KeyMapping SHOW_TRANSLATIONS;

    @Override
    public void onInitializeClient() {
        PolylingoConfig.load();

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "general")
        );

        SHOW_TRANSLATIONS = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.polylingo.show_translations",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_LSHIFT,
                category
        ));
    }
}
