package net.mondless.polylingo.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A small dependency-free Mod Menu screen with an ordered language list. */
public final class PolylingoConfigScreen extends Screen {
    private static final int ROWS_PER_PAGE = 6;

    private final Screen parent;
    private final List<String> working = new ArrayList<>();
    private List<String> availableLanguageIds = List.of();
    private String selectedToAdd = "en_us";
    private int page;

    public PolylingoConfigScreen(Screen parent) {
        super(Component.translatable("screen.polylingo.title"));
        this.parent = parent;
        this.working.addAll(PolylingoConfig.getAdditionalLanguages());
    }

    @Override
    protected void init() {
        Map<String, LanguageInfo> languageMap = Minecraft.getInstance().getLanguageManager().getLanguages();
        this.availableLanguageIds = new ArrayList<>(languageMap.keySet());
        if (this.availableLanguageIds.isEmpty()) {
            this.availableLanguageIds = List.of("en_us");
        }
        if (!this.availableLanguageIds.contains(this.selectedToAdd)) {
            this.selectedToAdd = this.availableLanguageIds.getFirst();
        }
        rebuildWidgets();
    }

    public void rebuildWidgets() {
        clearWidgets();
        int center = this.width / 2;
        int selectorWidth = Math.min(250, Math.max(150, this.width - 170));
        int selectorX = center - selectorWidth / 2 - 42;

        CycleButton<String> languageSelector = CycleButton.<String>builder(this::languageLabel, this.selectedToAdd)
                .withValues(this.availableLanguageIds)
                .displayOnlyValue()
                .create(selectorX, 66, selectorWidth, 20,
                        Component.translatable("screen.polylingo.add_language"),
                        (button, value) -> this.selectedToAdd = value);
        addRenderableWidget(languageSelector);

        addRenderableWidget(Button.builder(Component.translatable("screen.polylingo.add"), button -> {
            if (!this.working.contains(this.selectedToAdd)) {
                this.working.add(this.selectedToAdd);
                this.page = Math.max(0, (this.working.size() - 1) / ROWS_PER_PAGE);
                rebuildWidgets();
            }
        }).bounds(selectorX + selectorWidth + 6, 66, 78, 20).build());

        int pageCount = Math.max(1, (this.working.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        this.page = Math.min(this.page, pageCount - 1);
        int start = this.page * ROWS_PER_PAGE;
        int end = Math.min(this.working.size(), start + ROWS_PER_PAGE);

        for (int index = start; index < end; index++) {
            final int itemIndex = index;
            int y = 112 + (index - start) * 25;
            int controlsRight = Math.min(this.width - 30, center + 176);

            if (index > 0) {
                addRenderableWidget(Button.builder(Component.literal("↑"), button -> move(itemIndex, itemIndex - 1))
                        .bounds(controlsRight - 104, y - 2, 24, 20).build());
            }
            if (index < this.working.size() - 1) {
                addRenderableWidget(Button.builder(Component.literal("↓"), button -> move(itemIndex, itemIndex + 1))
                        .bounds(controlsRight - 76, y - 2, 24, 20).build());
            }
            addRenderableWidget(Button.builder(Component.translatable("screen.polylingo.remove"), button -> {
                this.working.remove(itemIndex);
                rebuildWidgets();
            }).bounds(controlsRight - 48, y - 2, 70, 20).build());
        }

        if (pageCount > 1) {
            int navY = 270;
            addRenderableWidget(Button.builder(Component.literal("‹"), button -> {
                this.page = Math.max(0, this.page - 1);
                rebuildWidgets();
            }).bounds(center - 64, navY, 24, 20).build());
            addRenderableWidget(Button.builder(Component.literal("›"), button -> {
                this.page = Math.min(pageCount - 1, this.page + 1);
                rebuildWidgets();
            }).bounds(center + 40, navY, 24, 20).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("screen.polylingo.cancel"), button ->
                Minecraft.getInstance().setScreenAndShow(this.parent)
        ).bounds(center - 154, this.height - 32, 150, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.polylingo.done"), button -> {
            PolylingoConfig.setAdditionalLanguages(this.working);
            Minecraft.getInstance().setScreenAndShow(this.parent);
        }).bounds(center + 4, this.height - 32, 150, 20).build());
    }

    private void move(int from, int to) {
        if (from < 0 || to < 0 || from >= this.working.size() || to >= this.working.size()) {
            return;
        }
        String value = this.working.remove(from);
        this.working.add(to, value);
        this.page = Math.min(to / ROWS_PER_PAGE, Math.max(0, (this.working.size() - 1) / ROWS_PER_PAGE));
        rebuildWidgets();
    }

    private Component languageLabel(String languageId) {
        LanguageInfo info = Minecraft.getInstance().getLanguageManager().getLanguage(languageId);
        return info == null
                ? Component.literal(languageId + " (" + Component.translatable("screen.polylingo.missing").getString() + ")")
                : info.toComponent();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 25, 0xFFFFFFFF);
        graphics.centeredText(this.font, Component.translatable("screen.polylingo.description"), this.width / 2, 43, 0xFFAAAAAA);

        int center = this.width / 2;
        graphics.text(this.font, Component.translatable("screen.polylingo.add_language"), center - 190, 54, 0xFFE0E0E0);

        if (this.working.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("screen.polylingo.empty"), center, 128, 0xFFAAAAAA);
        } else {
            int start = this.page * ROWS_PER_PAGE;
            int end = Math.min(this.working.size(), start + ROWS_PER_PAGE);
            for (int index = start; index < end; index++) {
                int y = 114 + (index - start) * 25;
                Component row = Component.literal((index + 1) + ". ").append(languageLabel(this.working.get(index)));
                graphics.text(this.font, row, center - 190, y, 0xFFFFFFFF);
            }
        }

        int pageCount = Math.max(1, (this.working.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        if (pageCount > 1) {
            graphics.centeredText(this.font,
                    Component.translatable("screen.polylingo.page", this.page + 1, pageCount),
                    center, 275, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(this.parent);
    }
}
