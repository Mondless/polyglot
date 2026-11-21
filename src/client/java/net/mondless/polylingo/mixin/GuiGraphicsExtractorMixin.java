package net.mondless.polylingo.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.mondless.polylingo.PolylingoClient;
import net.mondless.polylingo.config.PolylingoConfig;
import net.mondless.polylingo.language.AlternateLanguageCache;
import net.mondless.polylingo.utils.SliderDotsComponent;
import net.mondless.polylingo.utils.TooltipContext;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.regex.Pattern;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin implements TooltipContext {
    @Unique
    private static final int POLYGLOT_GAP = 2;
    @Unique
    private static final int POLYGLOT_MARGIN = 4;
    @Unique
    private static final Pattern POLYGLOT_RAW_ID = Pattern.compile("(?i)^#?[a-z0-9_.-]+:[a-z0-9_./-]+$");

    @Unique
    private ItemStack polylingo$pendingItemStack = ItemStack.EMPTY;
    @Unique
    private boolean polylingo$renderingSecondary = false;

//    @Inject(
//            method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
//            at = @At("HEAD")
//    )
//    private void polylingo$captureStack(Font font, ItemStack stack, int mouseX, int mouseY, CallbackInfo callbackInfo) {
//        this.polylingo$pendingItemStack = stack.copy();
//    }

    @Override
    public void polylingo$setPendingItemStack(ItemStack stack) {
        this.polylingo$pendingItemStack = stack;
    }

    @Inject(method = "tooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;Z)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1)
    private void polylingo$renderSliderTooltip(Font font, List<ClientTooltipComponent> lines, int xo, int yo,
                                               ClientTooltipPositioner positioner, Identifier style,
                                               boolean extraSpaceAfterFirstLine, CallbackInfo callbackInfo) {
        if (this.polylingo$renderingSecondary
                || PolylingoConfig.getDisplayMode() != PolylingoConfig.DisplayMode.SLIDER) {
            return;
        }

        var stack = this.polylingo$pendingItemStack;
        this.polylingo$pendingItemStack = ItemStack.EMPTY;

        if (stack.isEmpty() || !PolylingoClient.isShowTranslationsDown()) {
            return;
        }

        List<String> languageIds = PolylingoClient.getSliderLanguageIds();

        if (languageIds.isEmpty()) {
            return;
        }

        int selectedIndex = PolylingoClient.getSliderSelection(languageIds.size());

        ClientLanguage targetLanguage = AlternateLanguageCache.get(
                languageIds.get(selectedIndex)
        );

        if (targetLanguage == null) {
            return;
        }

        var sliderLines = polylingo$createSliderTooltip(stack, targetLanguage, selectedIndex, languageIds.size(), font);

        if (sliderLines == null || sliderLines.isEmpty()) {
            return;
        }

        var graphics = (GuiGraphicsExtractor) (Object) this;
        this.polylingo$renderingSecondary = true;

        try {
            graphics.tooltip(font, sliderLines, xo, yo, positioner,
                    style, extraSpaceAfterFirstLine);
        } finally {
            this.polylingo$renderingSecondary = false;
        }

        callbackInfo.cancel();
    }

    @Inject(method = "tooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;Z)V",
            at = @At("TAIL"),
            require = 1)
    private void polylingo$renderSecondaryTooltips(Font font, List<ClientTooltipComponent> lines, int xo, int yo,
                                                   ClientTooltipPositioner positioner, Identifier style,
                                                   boolean extraSpaceAfterFirstLine, CallbackInfo callbackInfo) {
        if (this.polylingo$renderingSecondary
                || PolylingoConfig.getDisplayMode() == PolylingoConfig.DisplayMode.SLIDER) {
            return;
        }

        ItemStack stack = this.polylingo$pendingItemStack;
        this.polylingo$pendingItemStack = ItemStack.EMPTY;

        var key = KeyMappingHelper.getBoundKeyOf(PolylingoClient.SHOW_TRANSLATIONS);
        var isKeyDown = key.getType() == InputConstants.Type.KEYBOARD && InputConstants.isKeyDown(key.getValue());

//        System.out.println((key.getType() == InputConstants.Type.KEYBOARD) + " " + InputConstants.isKeyDown(key.getValue()));

        if (stack.isEmpty() || PolylingoClient.SHOW_TRANSLATIONS == null || !isKeyDown) {
//            System.out.println(stack.isEmpty() + " " + (PolylingoClient.SHOW_TRANSLATIONS == null) + " " + (!PolylingoClient.SHOW_TRANSLATIONS.isDown()));
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        String activeLanguage = minecraft.getLanguageManager().getSelected();
        List<SecondaryPanel> panels = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();

        for (String languageId : PolylingoConfig.getAdditionalLanguages()) {
            if (languageId.equals(activeLanguage) || !usedIds.add(languageId)) {
                continue;
            }

            ClientLanguage language = AlternateLanguageCache.get(languageId);
            if (language == null) {
                continue;
            }

            SecondaryPanel panel = polylingo$createPanel(stack, font, language, extraSpaceAfterFirstLine);
            if (panel != null) {
                panels.add(panel);
            }
        }

        if (panels.isEmpty()) {
            return;
        }

        GuiGraphicsExtractor graphics = (GuiGraphicsExtractor) (Object) this;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int mainWidth = polylingo$measureWidth(lines, font);
        int mainHeight = polylingo$measureHeight(lines, font, extraSpaceAfterFirstLine);
        Vector2ic mainPosition = positioner.positionTooltip(
                screenWidth, screenHeight, xo, yo, mainWidth, mainHeight
        );

        List<PanelPosition> positions = polylingo$layout(
                panels, mainPosition.x(), mainPosition.y(), mainWidth, mainHeight, screenWidth, screenHeight
        );

        this.polylingo$renderingSecondary = true;
        try {
            for (int index = 0; index < panels.size(); index++) {
                SecondaryPanel panel = panels.get(index);
                PanelPosition position = positions.get(index);
                ClientTooltipPositioner fixedPositioner = (width, height, x, y, tooltipWidth, tooltipHeight) ->
                        new Vector2i(position.x(), position.y());
                graphics.tooltip(
                        font,
                        panel.lines(),
                        xo,
                        yo,
                        fixedPositioner,
                        style,
                        extraSpaceAfterFirstLine
                );
            }
        } finally {
            this.polylingo$renderingSecondary = false;
        }
    }

    @Unique
    private static List<ClientTooltipComponent> polylingo$createSliderTooltip(
            ItemStack stack, ClientLanguage targetLanguage, int selectedIndex, int languageCount, Font font) {
        var minecraft = Minecraft.getInstance();
        var origLanguage = Language.getInstance();

        try {
            var originalItemLines = Screen.getTooltipFromItem(minecraft, stack);
            int origTooltipWidth = 0;

            for (var line : originalItemLines) {
                origTooltipWidth = Math.max(origTooltipWidth,
                        font.width(origLanguage.getVisualOrder(line)));
            }

            List<ClientTooltipComponent> originalLines = new ArrayList<>(
                    originalItemLines.size()
            );

            for (var line : originalItemLines) {
                originalLines.add(ClientTooltipComponent.create(origLanguage.getVisualOrder(line)));
            }

            Language.inject(targetLanguage);

            var translatedItemLines = Screen.getTooltipFromItem(minecraft, stack);

            List<ClientTooltipComponent> result = new ArrayList<>(
                    Math.max(originalLines.size(), translatedItemLines.size()) + 2
            );

            int lineCount = Math.max(
                    originalLines.size(),
                    translatedItemLines.size()
            );

            for (int i = 0; i < lineCount; i++) {
                if (i >= translatedItemLines.size()) {
                    result.add(originalLines.get(i));
                    continue;
                }

                Component translatedLine = translatedItemLines.get(i);

                boolean hasUsableTranslation =
                        polylingo$containsTranslation(translatedLine)
                                && !polylingo$hasMissingTranslation(translatedLine, targetLanguage)
                                && !polylingo$isTechnicalLine(translatedLine.getString());

                if (hasUsableTranslation || i >= originalLines.size()) {
                    result.add(
                            ClientTooltipComponent.create(
                                    targetLanguage.getVisualOrder(translatedLine)
                            )
                    );
                } else {
                    result.add(originalLines.get(i));
                }
            }

            Optional<TooltipComponent> image = stack.getTooltipImage();

            image.ifPresent(tooltipImage ->
                    result.add(Math.min(1, result.size()), ClientTooltipComponent.create(tooltipImage)));

            var dots = Component.empty();

            for (int index = 0; index < languageCount; index++) {
                if (index > 0) {
                    dots.append(Component.literal(" "));
                }

                dots.append(
                        Component.literal("●").withStyle(
                                index == selectedIndex
                                        ? ChatFormatting.YELLOW
                                        : ChatFormatting.DARK_GRAY
                        )
                );
            }

//            result.add(ClientTooltipComponent.create(targetLanguage.getVisualOrder(dots)));
            result.add(new SliderDotsComponent(origTooltipWidth, languageCount, selectedIndex));

            return List.copyOf(result);

        } catch (RuntimeException exception) {
            return null;
        } finally {
            Language.inject(origLanguage);
        }
    }

    @Unique
    private static SecondaryPanel polylingo$createPanel(ItemStack stack, Font font,
                                                        ClientLanguage targetLanguage, boolean extraSpaceAfterFirstLine) {
        Language previousLanguage = Language.getInstance();
        Language.inject(targetLanguage);
        try {
            List<Component> itemLines = Screen.getTooltipFromItem(Minecraft.getInstance(), stack);
            List<ClientTooltipComponent> renderedLines = new ArrayList<>();

            for (int index = 0; index < itemLines.size(); index++) {
                Component line = itemLines.get(index);
                if (polylingo$hasMissingTranslation(line, targetLanguage)) {
                    continue;
                }

                // 1st row - the item name
                if (index > 0) {
//                    if (!polylingo$containsTranslation(line) || polylingo$isTechnicalLine(line.getString())) {
//                        continue;
//                    }

                    if (line.getString().isBlank()) {
                        if (renderedLines.isEmpty() || itemLines.get(index - 1).getString().isBlank()) {
                            continue;
                        }
                    } else if (!polylingo$containsTranslation(line) || polylingo$isTechnicalLine(line.getString())) {
                        continue;
                    }
                }

                renderedLines.add(ClientTooltipComponent.create(targetLanguage.getVisualOrder(line)));
            }

            // remove the last empty string
            while (!renderedLines.isEmpty() && itemLines.getLast().getString().isBlank()) {
                renderedLines.removeLast();
                itemLines.removeLast();
            }

            Optional<TooltipComponent> image = stack.getTooltipImage();

            image.ifPresent(tooltipImage -> renderedLines.add(
                    Math.min(1, renderedLines.size()), ClientTooltipComponent.create(tooltipImage)
            ));

            if (renderedLines.isEmpty()) {
                return null;
            }
            return new SecondaryPanel(List.copyOf(renderedLines), polylingo$measureWidth(renderedLines, font),
                    polylingo$measureHeight(renderedLines, font, extraSpaceAfterFirstLine));
        } catch (RuntimeException exception) {
            return null;
        } finally {
            Language.inject(previousLanguage);
        }
    }

    @Unique
    private static boolean polylingo$containsTranslation(Component component) {
        if (component.getContents() instanceof TranslatableContents) {
            return true;
        }
        for (Component sibling : component.getSiblings()) {
            if (polylingo$containsTranslation(sibling)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static boolean polylingo$hasMissingTranslation(Component component, Language language) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            if (!language.has(translatable.getKey())) {
                return true;
            }
            for (Object argument : translatable.getArgs()) {
                if (argument instanceof Component argumentComponent
                        && polylingo$hasMissingTranslation(argumentComponent, language)) {
                    return true;
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            if (polylingo$hasMissingTranslation(sibling, language)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static boolean polylingo$isTechnicalLine(String rawText) {
        String text = rawText.trim();
        if (text.isEmpty() || POLYGLOT_RAW_ID.matcher(text).matches()) {
            return true;
        }

        String normalized = text.toLowerCase(Locale.ROOT);
        if (normalized.equals("minecraft") || normalized.startsWith("minecraft ")
                || normalized.startsWith("minecraft:") || normalized.startsWith("#minecraft:")) {
            return true;
        }

        for (var mod : FabricLoader.getInstance().getAllMods()) {
            String modId = mod.getMetadata().getId().toLowerCase(Locale.ROOT);
            String modName = mod.getMetadata().getName().toLowerCase(Locale.ROOT);
            if (normalized.equals(modId) || normalized.equals(modName)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static int polylingo$measureWidth(List<ClientTooltipComponent> lines, Font font) {
        int maximum = 0;
        for (ClientTooltipComponent line : lines) {
            maximum = Math.max(maximum, line.getWidth(font));
        }
        return maximum + 8;
    }

    @Unique
    private static int polylingo$measureHeight(List<ClientTooltipComponent> lines, Font font, boolean extraSpaceAfterFirstLine) {
        int height = 8;
        for (ClientTooltipComponent line : lines) {
            height += line.getHeight(font);
        }
        if (extraSpaceAfterFirstLine && lines.size() > 1) {
            height += 2;
        }
        return height;
    }

    //        @Unique
//        private static List<PanelPosition> polylingo$layout(
//                List<SecondaryPanel> panels,
//                int mainX,
//                int mainY,
//                int mainWidth,
//                int mainHeight,
//                int screenWidth,
//                int screenHeight
//        ) {
//            List<PanelPosition> result = new ArrayList<>(panels.size());
//            int groupWidth = panels.stream().mapToInt(SecondaryPanel::width).sum()
//                    + POLYGLOT_GAP * Math.max(0, panels.size() - 1);
//            int groupHeight = panels.stream().mapToInt(SecondaryPanel::height).sum()
//                    + POLYGLOT_GAP * Math.max(0, panels.size() - 1);
//
//            // Prefer a row to the right of the original tooltip.
//            if (mainX + mainWidth + POLYGLOT_GAP + groupWidth <= screenWidth - POLYGLOT_MARGIN) {
//                int x = mainX + mainWidth + POLYGLOT_GAP;
//                for (SecondaryPanel panel : panels) {
//                    int y = polylingo$clamp(mainY, POLYGLOT_MARGIN, Math.max(POLYGLOT_MARGIN, screenHeight - panel.height() - POLYGLOT_MARGIN));
//                    result.add(new PanelPosition(x, y));
//                    x += panel.width() + POLYGLOT_GAP;
//                }
//                return result;
//            }
//
//            // If the group is left of the original tooltip, keep configured order from left to right.
//            if (mainX - POLYGLOT_GAP - groupWidth >= POLYGLOT_MARGIN) {
//                int x = mainX - POLYGLOT_GAP - groupWidth;
//                for (SecondaryPanel panel : panels) {
//                    int y = polylingo$clamp(mainY, POLYGLOT_MARGIN, Math.max(POLYGLOT_MARGIN, screenHeight - panel.height() - POLYGLOT_MARGIN));
//                    result.add(new PanelPosition(x, y));
//                    x += panel.width() + POLYGLOT_GAP;
//                }
//                return result;
//            }
//
//            // When a horizontal row does not fit, stack panels below, preserving top-to-bottom order.
//            if (mainY + mainHeight + POLYGLOT_GAP + groupHeight <= screenHeight - POLYGLOT_MARGIN) {
//                int y = mainY + mainHeight + POLYGLOT_GAP;
//                for (SecondaryPanel panel : panels) {
//                    int x = polylingo$clamp(mainX, POLYGLOT_MARGIN, Math.max(POLYGLOT_MARGIN, screenWidth - panel.width() - POLYGLOT_MARGIN));
//                    result.add(new PanelPosition(x, y));
//                    y += panel.height() + POLYGLOT_GAP;
//                }
//                return result;
//            }
//
//            // Last preferred direction: stack above.
//            if (mainY - POLYGLOT_GAP - groupHeight >= POLYGLOT_MARGIN) {
//                int y = mainY - POLYGLOT_GAP - groupHeight;
//                for (SecondaryPanel panel : panels) {
//                    int x = polylingo$clamp(mainX, POLYGLOT_MARGIN, Math.max(POLYGLOT_MARGIN, screenWidth - panel.width() - POLYGLOT_MARGIN));
//                    result.add(new PanelPosition(x, y));
//                    y += panel.height() + POLYGLOT_GAP;
//                }
//                return result;
//            }
//
//            // Extremely small windows: best-effort placement, clamped to the visible GUI area.
//            int y = POLYGLOT_MARGIN;
//            for (SecondaryPanel panel : panels) {
//                int x = polylingo$clamp(mainX, POLYGLOT_MARGIN, Math.max(POLYGLOT_MARGIN, screenWidth - panel.width() - POLYGLOT_MARGIN));
//                int safeY = polylingo$clamp(y, POLYGLOT_MARGIN, Math.max(POLYGLOT_MARGIN, screenHeight - panel.height() - POLYGLOT_MARGIN));
//                result.add(new PanelPosition(x, safeY));
//                y = safeY + panel.height() + POLYGLOT_GAP;
//            }
//            return result;
//        }
    @Unique
    private static List<PanelPosition> polylingo$layout(
            List<SecondaryPanel> panels,
            int mainX,
            int mainY,
            int mainWidth,
            int mainHeight,
            int screenWidth,
            int screenHeight
    ) {
        int count = panels.size();
        if (count == 0) {
            return List.of();
        }

        int maxAvailableHeight = Math.max(1, screenHeight - POLYGLOT_MARGIN * 2);

        int chosenCols = count;
        List<List<SecondaryPanel>> columns = List.of();

        for (int cols = 1; cols <= count; cols++) {
            List<List<SecondaryPanel>> candidate = polylingo$buildColumns(panels, cols);
            int maxColHeight = candidate.stream()
                    .mapToInt(col -> col.stream().mapToInt(SecondaryPanel::height).sum()
                            + POLYGLOT_GAP * Math.max(0, col.size() - 1))
                    .max()
                    .orElse(0);

            columns = candidate;
            chosenCols = cols;
            if (maxColHeight <= maxAvailableHeight) {
                break;
            }
        }

        int[] colWidths = new int[chosenCols];
        int[] colHeights = new int[chosenCols];
        int totalGridWidth = 0;
        int totalGridHeight = 0;

        for (int c = 0; c < chosenCols; c++) {
            List<SecondaryPanel> col = columns.get(c);
            int w = col.stream().mapToInt(SecondaryPanel::width).max().orElse(0);
            int h = col.stream().mapToInt(SecondaryPanel::height).sum()
                    + POLYGLOT_GAP * Math.max(0, col.size() - 1);
            colWidths[c] = w;
            colHeights[c] = h;
            totalGridWidth += w + (c > 0 ? POLYGLOT_GAP : 0);
            totalGridHeight = Math.max(totalGridHeight, h);
        }

        int spaceRight = screenWidth - (mainX + mainWidth) - POLYGLOT_GAP - POLYGLOT_MARGIN;
        int spaceLeft = mainX - POLYGLOT_GAP - POLYGLOT_MARGIN;
        boolean placeRight = spaceRight >= totalGridWidth || spaceRight >= spaceLeft;

        int startX = placeRight
                ? mainX + mainWidth + POLYGLOT_GAP
                : mainX - POLYGLOT_GAP - totalGridWidth;

        startX = polylingo$clamp(startX, POLYGLOT_MARGIN, Math.max(POLYGLOT_MARGIN, screenWidth - totalGridWidth - POLYGLOT_MARGIN));
        int startY = polylingo$clamp(mainY, POLYGLOT_MARGIN, Math.max(POLYGLOT_MARGIN, screenHeight - totalGridHeight - POLYGLOT_MARGIN));

        List<PanelPosition> result = new ArrayList<>(count);
        int currentX = startX;

        for (int c = 0; c < chosenCols; c++) {
            List<SecondaryPanel> col = columns.get(c);
            int currentY = startY;
            for (SecondaryPanel panel : col) {
                result.add(new PanelPosition(currentX, currentY));
                currentY += panel.height() + POLYGLOT_GAP;
            }
            currentX += colWidths[c] + POLYGLOT_GAP;
        }

        return result;
    }

    @Unique
    private static List<List<SecondaryPanel>> polylingo$buildColumns(List<SecondaryPanel> panels, int cols) {
        int total = panels.size();
        int actualCols = Math.min(cols, total);
        List<List<SecondaryPanel>> result = new ArrayList<>(actualCols);

        int baseSize = total / actualCols;
        int remainder = total % actualCols;
        int index = 0;

        for (int c = 0; c < actualCols; c++) {
            int size = baseSize + (c < remainder ? 1 : 0);
            result.add(panels.subList(index, index + size));
            index += size;
        }
        return result;
    }

    @Unique
    private static int polylingo$clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private record SecondaryPanel(List<ClientTooltipComponent> lines, int width, int height) {
    }

    private record PanelPosition(int x, int y) {
    }
}
