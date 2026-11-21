package net.mondless.polylingo;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.mondless.polylingo.config.PolylingoConfig;
import net.mondless.polylingo.language.AlternateLanguageCache;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PolylingoClient implements ClientModInitializer {
    public static final String MOD_ID = "polylingo";

    public static KeyMapping SHOW_TRANSLATIONS;
    public static KeyMapping SLIDER_PREV;
    public static KeyMapping SLIDER_NEXT;

    private static int sliderIndex;

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

        SLIDER_PREV = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.polylingo.slider_prev",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_COMMA,
                category
        ));

        SLIDER_NEXT = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.polylingo.slider_next",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_PERIOD,
                category
        ));

        ScreenEvents.BEFORE_INIT.register((client, screen, _, _) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) {
                return;
            }

            ScreenKeyboardEvents.beforeKeyPress(screen).register((_, e) -> {
                if (isSliderInputInactive()) {
                    return;
                }

                if (SLIDER_PREV.matches(e)) {
                    moveSliderPage(-1);
                    return;
                }

                if (SLIDER_NEXT.matches(e)) {
                    moveSliderPage(1);
                }
            });
        });
    }

    public static boolean isShowTranslationsDown() {
        if (SHOW_TRANSLATIONS == null) {
            return false;
        }

        var key = KeyMappingHelper.getBoundKeyOf(SHOW_TRANSLATIONS);

        return key.getType() == InputConstants.Type.KEYBOARD && InputConstants.isKeyDown(key.getValue());
    }

    public static boolean isSliderInputInactive() {
        return PolylingoConfig.getDisplayMode() != PolylingoConfig.DisplayMode.SLIDER
                || !isShowTranslationsDown();
    }

    public static List<String> getSliderLanguageIds() {
        var minecraft = Minecraft.getInstance();
        String activeLanguage = minecraft.getLanguageManager().getSelected();

        Set<String> usedIds = new HashSet<>();
        List<String> result = new ArrayList<>();

        for (String languageId : PolylingoConfig.getAdditionalLanguages()) {
            if (languageId.equals(activeLanguage) || !usedIds.add(languageId)) {
                continue;
            }

            if (minecraft.getLanguageManager().getLanguage(languageId) == null) {
                continue;
            }

            if (AlternateLanguageCache.get(languageId) == null) {
                continue;
            }

            result.add(languageId);
        }

        return List.copyOf(result);
    }

    public static int getSliderSelection(int languageCount) {
        if (languageCount <= 0) {
            sliderIndex = 0;

            return 0;
        }

        sliderIndex = Math.floorMod(sliderIndex, languageCount);

        return sliderIndex;
    }

    public static void moveSliderPage(int delta) {
        int languageCount = getSliderLanguageIds().size();

        if (languageCount == 0) {
            sliderIndex = 0;

            return;
        }

        sliderIndex = Math.floorMod(sliderIndex + delta, languageCount);
    }
}
