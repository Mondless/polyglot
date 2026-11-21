package net.mondless.polylingo.utils;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import org.jspecify.annotations.NonNull;

public final class SliderDotsComponent implements ClientTooltipComponent {
    private final int tooltipWidth;
    private final int languageCount;
    private final int selectedIndex;

    public SliderDotsComponent(
            int tooltipWidth,
            int languageCount,
            int selectedIndex
    ) {
        this.tooltipWidth = Math.max(0, tooltipWidth);
        this.languageCount = Math.max(0, languageCount);
        this.selectedIndex = selectedIndex;
    }

    @Override
    public int getWidth(@NonNull Font font) {
        return tooltipWidth;
    }

    @Override
    public int getHeight(@NonNull Font font) {
        return 8;
    }

    @Override
    public void extractText(@NonNull GuiGraphicsExtractor graphics, @NonNull Font font, int x, int y) {
        if (languageCount <= 0 || tooltipWidth <= 0) {
            return;
        }

        int dotWidth = font.width("●");

        int right = x + Math.max(0, tooltipWidth - dotWidth);

        if (languageCount == 1) {
            drawDot(graphics, font, x + (tooltipWidth - dotWidth) / 2, y, 0);

            return;
        }

        for (int i = 0; i < languageCount; i++) {
            double fraction = (double) i / (languageCount - 1);
            int dotX = x + (int) Math.round(fraction * (right - x));

            drawDot(graphics, font, dotX, y, i);
        }
    }

    private void drawDot(GuiGraphicsExtractor graphics, Font font, int x, int y, int index) {
        graphics.text(font, "●", x, y, index == selectedIndex ? 0xFFFFFF55 : 0xFF555555, false);
    }
}
