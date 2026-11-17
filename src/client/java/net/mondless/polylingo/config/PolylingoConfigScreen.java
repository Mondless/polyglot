package net.mondless.polylingo.config;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.NonNull;

import java.util.*;

public final class PolylingoConfigScreen extends Screen {
    private static final int ROWS_PER_PAGE = 6;
    private static final int MAX_SUGGESTIONS = 7;
    private static final int SUGGESTION_ROW_HEIGHT = 18;
    private static final int SCROLLBAR_HIT_WIDTH = 8;

    private final Screen parent;
    private final List<String> working = new ArrayList<>();
    private List<String> availableLanguageIds = List.of();
    private List<String> filteredSuggestions = List.of();

    private EditBox searchBox;
    private String searchQuery = "";
    private String selectedToAdd = "";
    private boolean dropdownOpen = false;
    private int suggestionScroll = 0;
    private boolean draggingSuggestionScrollbar = false;
    private double scrollbarDragOffset = 0.0;
    private int page;

    private int dropdownX;
    private int dropdownY;
    private int dropdownWidth;

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
            this.availableLanguageIds = new ArrayList<>(List.of("af_za"));
        }

        this.availableLanguageIds.sort(Comparator.comparing(
                id -> languageLabel(id).getString(), String.CASE_INSENSITIVE_ORDER));

        updateSuggestions(this.searchQuery);
        rebuildWidgets();
    }

    public void rebuildWidgets() {
        clearWidgets();

        int center = this.width / 2;
        int selectorWidth = Math.clamp(this.width - 80, 220, 420);
        int selectorX = center - selectorWidth / 2;

        this.dropdownX = selectorX;
        this.dropdownY = 88;
        this.dropdownWidth = selectorWidth;

        int searchY = 72;

        this.searchBox = new EditBox(this.font, selectorX, searchY, selectorWidth, 20,
                Component.translatable("screen.polylingo.add_language"));
        this.searchBox.setHint(Component.translatable("screen.polylingo.search_hint"));
        this.searchBox.setValue(this.searchQuery);
        this.searchBox.setResponder(text -> {
            this.searchQuery = text;
            this.dropdownOpen = true;
            this.suggestionScroll = 0;
            this.draggingSuggestionScrollbar = false;
            updateSuggestions(text);
        });
        addRenderableWidget(this.searchBox);

//        addRenderableWidget(Button.builder(Component.translatable("screen.polylingo.add"), _ -> addSelectedLanguage())
//                .bounds(selectorX + selectorWidth + 6, searchY, 78, 20)
//                .build());

        int pageCount = Math.max(1, (this.working.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        this.page = Math.min(this.page, pageCount - 1);
        int start = this.page * ROWS_PER_PAGE;
        int end = Math.min(this.working.size(), start + ROWS_PER_PAGE);

        for (int index = start; index < end; index++) {
            final int itemIndex = index;
            int y = 112 + (index - start) * 25;
            int controlsRight = Math.min(this.width - 30, center + 188);

            boolean hasUp = index > 0;
            boolean hasDown = index < this.working.size() - 1;

            if (hasUp) {
                int upX = hasDown ? controlsRight - 104 : controlsRight - 76;

                addRenderableWidget(Button.builder(Component.literal("↑"),
                                _ -> move(itemIndex, itemIndex - 1))
                        .bounds(upX, y - 2, 24, 20)
                        .build());
            }

            if (hasDown) {
                addRenderableWidget(Button.builder(Component.literal("↓"),
                                _ -> move(itemIndex, itemIndex + 1))
                        .bounds(controlsRight - 76, y - 2, 24, 20)
                        .build());
            }

            addRenderableWidget(Button.builder(Component.translatable("screen.polylingo.remove"), _ -> {
                this.working.remove(itemIndex);
                rebuildWidgets();
            }).bounds(controlsRight - 48, y - 2, 70, 20).build());
        }

        if (pageCount > 1) {
            int navY = 270;
            addRenderableWidget(Button.builder(Component.literal("‹"), _ -> {
                this.page = Math.max(0, this.page - 1);
                rebuildWidgets();
            }).bounds(center - 64, navY, 24, 20).build());
            addRenderableWidget(Button.builder(Component.literal("›"), _ -> {
                this.page = Math.min(pageCount - 1, this.page + 1);
                rebuildWidgets();
            }).bounds(center + 40, navY, 24, 20).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("screen.polylingo.cancel"), _ ->
                Minecraft.getInstance().setScreenAndShow(this.parent)
        ).bounds(center - 154, this.height - 32, 150, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.polylingo.done"), _ -> {
            PolylingoConfig.setAdditionalLanguages(this.working);
            Minecraft.getInstance().setScreenAndShow(this.parent);
        }).bounds(center + 4, this.height - 32, 150, 20).build());
    }

    /**
     * Matches every query fragment against any word in the language name or ID.
     * For example, "ру дореф" matches "Русский дореформенный", while "rpr"
     * matches the language ID even if that ID is not shown in the language name.
     */
    private void updateSuggestions(String query) {
        List<String> queryTokens = searchTokens(query);

        if (queryTokens.isEmpty()) {
            this.filteredSuggestions = new ArrayList<>(this.availableLanguageIds);
            this.selectedToAdd = "";
            this.suggestionScroll = 0;
            return;
        }

        String normalizedQuery = String.join(" ", queryTokens);
        List<String> matches = new ArrayList<>();
        String exactMatch = "";

        for (String id : this.availableLanguageIds) {
            List<String> labelTokens = searchTokens(languageLabel(id).getString());
            List<String> idTokens = searchTokens(id);
            List<String> searchableTokens = new ArrayList<>(labelTokens);
            searchableTokens.addAll(idTokens);

            boolean matchesEveryToken = queryTokens.stream().allMatch(queryToken ->
                    searchableTokens.stream().anyMatch(word -> word.contains(queryToken)));

            if (!matchesEveryToken) {
                continue;
            }

            matches.add(id);

            String normalizedLabel = String.join(" ", labelTokens);
            String normalizedId = String.join(" ", idTokens);
            if (normalizedLabel.equals(normalizedQuery) || normalizedId.equals(normalizedQuery)) {
                exactMatch = id;
            }
        }

        // Keep exact/prefix matches above looser substring matches.
        matches.sort(Comparator.comparingInt(id -> getSuggestionMatchScore(id, queryTokens)));
        this.filteredSuggestions = matches;

        if (!exactMatch.isEmpty()) {
            this.selectedToAdd = exactMatch;
        } else if (matches.size() == 1) {
            this.selectedToAdd = matches.getFirst();
        } else {
            this.selectedToAdd = "";
        }

        this.suggestionScroll = Math.clamp(this.suggestionScroll, 0, getMaxSuggestionScroll());
    }

    private int getSuggestionMatchScore(String languageId, List<String> queryTokens) {
        List<String> labelTokens = searchTokens(languageLabel(languageId).getString());
        List<String> idTokens = searchTokens(languageId);
        String normalizedQuery = String.join(" ", queryTokens);

        if (String.join(" ", labelTokens).equals(normalizedQuery)
                || String.join(" ", idTokens).equals(normalizedQuery)) {
            return 0;
        }

        List<String> searchableTokens = new ArrayList<>(labelTokens);
        searchableTokens.addAll(idTokens);

        boolean allArePrefixes = queryTokens.stream().allMatch(queryToken ->
                searchableTokens.stream().anyMatch(word -> word.startsWith(queryToken)));

        return allArePrefixes ? 1 : 2;
    }

    private static List<String> searchTokens(String value) {
        return Arrays.stream(value.toLowerCase(Locale.ROOT).replace('ё', 'е')
                        .split("[^\\p{L}\\p{N}]+"))
                .filter(token -> !token.isBlank())
                .toList();
    }

//    private void addSelectedLanguage() {
//        String toAdd = !this.selectedToAdd.isEmpty()
//                ? this.selectedToAdd
//                : (!this.filteredSuggestions.isEmpty() ? this.filteredSuggestions.getFirst() : "");
//
//        if (!toAdd.isEmpty() && !this.working.contains(toAdd)) {
//            this.working.add(toAdd);
//            this.page = Math.max(0, (this.working.size() - 1) / ROWS_PER_PAGE);
//            this.searchQuery = "";
//            this.selectedToAdd = "";
//            this.dropdownOpen = false;
//            this.suggestionScroll = 0;
//            this.draggingSuggestionScrollbar = false;
//
//            rebuildWidgets();
//        }
//    }

    private void move(int from, int to) {
        if (from < 0 || to < 0 || from >= this.working.size() || to >= this.working.size()) {
            return;
        }

        String value = this.working.remove(from);
        this.working.add(to, value);
        this.page = Math.clamp((this.working.size() - 1) / ROWS_PER_PAGE, 0, to / ROWS_PER_PAGE);

        rebuildWidgets();
    }

    private Component languageLabel(String languageId) {
        LanguageInfo info = Minecraft.getInstance().getLanguageManager().getLanguage(languageId);

        return info == null
                ? Component.literal(languageId + " (" + Component.translatable("screen.polylingo.missing").getString() + ")")
                : info.toComponent();
    }

    private int getVisibleDropdownRows() {
        return Math.min(MAX_SUGGESTIONS, this.filteredSuggestions.size());
    }

    private int getDropdownListHeight() {
        return getVisibleDropdownRows() * SUGGESTION_ROW_HEIGHT;
    }

    private int getMaxSuggestionScroll() {
        return Math.max(0, this.filteredSuggestions.size() - MAX_SUGGESTIONS);
    }

    private boolean hasSuggestionScrollbar() {
        return this.filteredSuggestions.size() > MAX_SUGGESTIONS;
    }

    private int getScrollbarHitX() {
        return this.dropdownX + this.dropdownWidth - SCROLLBAR_HIT_WIDTH;
    }

    private int getScrollbarThumbHeight(int listHeight) {
        if (!hasSuggestionScrollbar() || listHeight <= 0) {
            return listHeight;
        }

        int proportionalHeight = (int) ((float) getVisibleDropdownRows()
                / this.filteredSuggestions.size() * listHeight);
        return Math.clamp(proportionalHeight, 12, listHeight);
    }

    private int getScrollbarThumbY(int listHeight) {
        int maxScroll = getMaxSuggestionScroll();
        int thumbHeight = getScrollbarThumbHeight(listHeight);
        int thumbTravel = Math.max(0, listHeight - thumbHeight);

        if (maxScroll == 0 || thumbTravel == 0) {
            return this.dropdownY;
        }

        return this.dropdownY + (int) Math.round(
                (double) this.suggestionScroll / maxScroll * thumbTravel);
    }

    private void scrollScrollbarTo(double mouseY) {
        int listHeight = getDropdownListHeight();
        int thumbHeight = getScrollbarThumbHeight(listHeight);
        int thumbTravel = listHeight - thumbHeight;
        int maxScroll = getMaxSuggestionScroll();

        if (thumbTravel <= 0 || maxScroll <= 0) {
            this.suggestionScroll = 0;
            return;
        }

        double requestedThumbTop = mouseY - this.dropdownY - this.scrollbarDragOffset;
        int thumbTop = (int) Math.round(Math.clamp(requestedThumbTop, 0.0, thumbTravel));
        double progress = (double) thumbTop / thumbTravel;

        this.suggestionScroll = Math.clamp((int) Math.round(progress * maxScroll), 0, maxScroll);
    }

    private void addLanguage(String languageId) {
        if (languageId == null || languageId.isEmpty()) {
            return;
        }

        if (!this.working.contains(languageId)) {
            this.working.add(languageId);

            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
            );

            this.page = Math.max(0, (this.working.size() - 1) / ROWS_PER_PAGE);
        }

        this.searchQuery = "";
        this.selectedToAdd = "";
        this.dropdownOpen = false;
        this.suggestionScroll = 0;
        this.draggingSuggestionScrollbar = false;

        rebuildWidgets();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        if (this.dropdownOpen && !this.filteredSuggestions.isEmpty()
                && event.button() == 1) {
            int listHeight = getDropdownListHeight();
            boolean insideDropdown = mouseX >= this.dropdownX
                    && mouseX < this.dropdownX + this.dropdownWidth
                    && mouseY >= this.dropdownY
                    && mouseY < this.dropdownY + listHeight;

            if (insideDropdown) {
                if (hasSuggestionScrollbar() && mouseX >= getScrollbarHitX()) {
                    int thumbHeight = getScrollbarThumbHeight(listHeight);
                    int thumbY = getScrollbarThumbY(listHeight);

                    if (mouseY >= thumbY && mouseY < thumbY + thumbHeight) {
                        this.scrollbarDragOffset = mouseY - thumbY;
                    } else {
                        this.scrollbarDragOffset = thumbHeight / 2.0;
                    }

                    this.draggingSuggestionScrollbar = true;
                    scrollScrollbarTo(mouseY);
                    return true;
                }

                int clickedRow = (int) ((mouseY - this.dropdownY) / SUGGESTION_ROW_HEIGHT);
                int index = this.suggestionScroll + clickedRow;

                if (index >= 0 && index < this.filteredSuggestions.size()) {
                    addLanguage(this.filteredSuggestions.get(index));
                    return true;
                }
            }
        }

        if (this.searchBox != null
                && mouseX >= this.searchBox.getX() && mouseX <= this.searchBox.getX() + this.searchBox.getWidth()
                && mouseY >= this.searchBox.getY() && mouseY <= this.searchBox.getY() + this.searchBox.getHeight()) {
            this.dropdownOpen = true;
            updateSuggestions(this.searchBox.getValue());
        } else {
            this.dropdownOpen = false;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingSuggestionScrollbar) {
            scrollScrollbarTo(event.y());

            return true;
        }

        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (this.draggingSuggestionScrollbar) {
            this.draggingSuggestionScrollbar = false;

            return true;
        }

        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.dropdownOpen && hasSuggestionScrollbar()
                && mouseX >= this.dropdownX && mouseX < this.dropdownX + this.dropdownWidth
                && mouseY >= this.dropdownY && mouseY < this.dropdownY + getDropdownListHeight()
                && scrollY != 0.0) {
            int scrollSteps = Math.max(1, (int) Math.ceil(Math.abs(scrollY)));
            int direction = scrollY > 0.0 ? -1 : 1;
            this.suggestionScroll = Math.clamp(this.suggestionScroll + direction * scrollSteps,
                    0, getMaxSuggestionScroll());

            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.centeredText(this.font, this.title, this.width / 2, 25, 0xFFFFFFFF);
        graphics.centeredText(this.font, Component.translatable("screen.polylingo.description"), this.width / 2, 43, 0xFFAAAAAA);

        int center = this.width / 2;
        graphics.text(this.font, Component.translatable("screen.polylingo.add_language"), center - 210, 60, 0xFFE0E0E0);

        if (this.working.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("screen.polylingo.empty"), center, 128, 0xFFAAAAAA);
        } else {
            int start = this.page * ROWS_PER_PAGE;
            int end = Math.min(this.working.size(), start + ROWS_PER_PAGE);

            for (int index = start; index < end; index++) {
                int y = 114 + (index - start) * 25;
                var row = Component.literal((index + 1) + ". ").append(languageLabel(this.working.get(index)));
                graphics.text(this.font, row, center - 210, y, 0xFFFFFFFF);
            }
        }

        int pageCount = Math.max(1, (this.working.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);

        if (pageCount > 1) {
            graphics.centeredText(this.font,
                    Component.translatable("screen.polylingo.page", this.page + 1, pageCount),
                    center, 275, 0xFFAAAAAA);
        }

        if (this.dropdownOpen && !this.filteredSuggestions.isEmpty()) {
            int visibleRows = getVisibleDropdownRows();
            int listHeight = visibleRows * SUGGESTION_ROW_HEIGHT;

            boolean hoveringList = mouseX >= this.dropdownX && mouseX <= this.dropdownX + this.dropdownWidth &&
                            mouseY >= this.dropdownY && mouseY < this.dropdownY + listHeight;

            if (hoveringList) {
                graphics.requestCursor(CursorTypes.POINTING_HAND);
            }
        }

        renderDropdown(graphics, mouseX, mouseY);
    }

    private void renderDropdown(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!this.dropdownOpen || this.filteredSuggestions.isEmpty()) {
            return;
        }

        int visibleRows = getVisibleDropdownRows();
        int listHeight = visibleRows * SUGGESTION_ROW_HEIGHT;

        graphics.fill(this.dropdownX - 1, this.dropdownY - 1,
                this.dropdownX + this.dropdownWidth + 1, this.dropdownY + listHeight + 1, 0xFFA0A0A0);
        graphics.fill(this.dropdownX, this.dropdownY,
                this.dropdownX + this.dropdownWidth, this.dropdownY + listHeight, 0xF0101010);

        int itemAreaRight = hasSuggestionScrollbar() ? getScrollbarHitX() : this.dropdownX + this.dropdownWidth;

        for (int i = 0; i < visibleRows; i++) {
            int itemIndex = this.suggestionScroll + i;

            if (itemIndex >= this.filteredSuggestions.size()) {
                break;
            }

            String langId = this.filteredSuggestions.get(itemIndex);
            int rowY = this.dropdownY + i * SUGGESTION_ROW_HEIGHT;
            boolean hovered = mouseX >= this.dropdownX && mouseX < itemAreaRight
                    && mouseY >= rowY && mouseY < rowY + SUGGESTION_ROW_HEIGHT;

            if (hovered) {
                graphics.fill(this.dropdownX, rowY,
                        this.dropdownX + this.dropdownWidth, rowY + SUGGESTION_ROW_HEIGHT, 0xFF3A3A3A);
            }

            Component label = languageLabel(langId);
            int textColor = hovered ? 0xFFFFFF55 : 0xFFE0E0E0;
            graphics.text(this.font, label, this.dropdownX + 5, rowY + 5, textColor);
        }

        if (hasSuggestionScrollbar()) {
            int thumbHeight = getScrollbarThumbHeight(listHeight);
            int thumbY = getScrollbarThumbY(listHeight);
            int trackX = this.dropdownX + this.dropdownWidth - 5;

            graphics.fill(trackX, this.dropdownY,
                    this.dropdownX + this.dropdownWidth, this.dropdownY + listHeight, 0xFF202020);
            graphics.fill(this.dropdownX + this.dropdownWidth - 4, thumbY,
                    this.dropdownX + this.dropdownWidth - 1, thumbY + thumbHeight, 0xFFCCCCCC);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(this.parent);
    }
}
