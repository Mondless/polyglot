package net.mondless.polyglot.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.mondless.polyglot.utils.PolyglotTooltipContext;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Shadow
    @Nullable
    protected Slot hoveredSlot;

    @Shadow
    @Final
    protected AbstractContainerMenu menu;

    @Inject(
            method = "extractTooltip(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V",
            at = @At("HEAD"),
            require = 1
    )
    private void polyglot$captureHoveredStack(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (this.menu.getCarried().isEmpty() && this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            ((PolyglotTooltipContext) graphics).polyglot$setPendingItemStack(this.hoveredSlot.getItem().copy());
        }
    }
}
