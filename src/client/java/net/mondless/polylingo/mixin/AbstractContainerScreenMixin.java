package net.mondless.polylingo.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.mondless.polylingo.PolylingoClient;
import net.mondless.polylingo.utils.TooltipContext;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Shadow
    @Nullable
    protected Slot hoveredSlot;

    @Shadow
    @Final
    protected AbstractContainerMenu menu;

    @Inject(method = "extractTooltip(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V",
            at = @At("HEAD"), require = 1)
    private void polylingo$captureHoveredStack(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (this.menu.getCarried().isEmpty() && this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            ((TooltipContext) graphics).polylingo$setPendingItemStack(this.hoveredSlot.getItem().copy());
        }
    }

    @Inject(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void polylingo$scrollSlider(double x, double y, double scrollX, double scrollY,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (PolylingoClient.isSliderInputInactive() || scrollY == 0.0 || !this.menu.getCarried().isEmpty()
                || this.hoveredSlot == null || !this.hoveredSlot.hasItem()) {
            return;
        }

        if (PolylingoClient.getSliderLanguageIds().size() <= 1) {
            return;
        }

        PolylingoClient.moveSliderPage(scrollY > 0.0 ? -1 : 1);

        cir.setReturnValue(true);
    }
}
