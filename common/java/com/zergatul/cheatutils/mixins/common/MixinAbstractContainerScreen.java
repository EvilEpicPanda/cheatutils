package com.zergatul.cheatutils.mixins.common;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.zergatul.cheatutils.common.Events;
import com.zergatul.cheatutils.common.events.ContainerRenderLabelsEvent;
import com.zergatul.cheatutils.configs.ConfigStore;
import com.zergatul.cheatutils.configs.ContainerButtonsConfig;
import com.zergatul.cheatutils.configs.ContainerSummaryConfig;
import com.zergatul.cheatutils.controllers.ContainerButtonsController;
import com.zergatul.cheatutils.controllers.ContainerSummaryController;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(AbstractContainerScreen.class)
public abstract class MixinAbstractContainerScreen<T extends AbstractContainerMenu> extends Screen {

    @Shadow
    protected int topPos;

    @Shadow
    protected int leftPos;

    @Shadow
    protected int imageWidth;

    @Shadow
    @Final
    protected T menu;

    protected MixinAbstractContainerScreen(Component component) {
        super(component);
    }

    @Inject(at = @At("TAIL"), method = "init()V")
    private void onInit(CallbackInfo info) {
        Screen self = this;
        if (!ContainerButtonsController.instance.isValidScreen(self)) {
            return;
        }

        ContainerButtonsConfig config = ConfigStore.instance.getConfig().containerButtonsConfig;
        int cursor = this.leftPos + this.imageWidth;
        int space = 4;
        if (config.showTakeAll) {
            int btnWidth = 72;
            int btnHeight = 20;
            cursor -= btnWidth;
            addRenderableWidget(new Button(cursor, this.topPos - btnHeight, btnWidth, btnHeight, Component.translatable("button.take.all"), this::onTakeAllPress));
            cursor -= space;
        }
        if (config.showSmartPut) {
            int btnWidth = 72;
            int btnHeight = 20;
            cursor -= btnWidth;
            addRenderableWidget(new Button(cursor, this.topPos - btnHeight, btnWidth, btnHeight, Component.translatable("button.smart.put"), this::onSmartPutPress));
            cursor -= space;
        }
        if (config.showDropAll) {
            int btnWidth = 72;
            int btnHeight = 20;
            cursor -= btnWidth;
            addRenderableWidget(new Button(cursor, this.topPos - btnHeight, btnWidth, btnHeight, Component.translatable("button.drop.all"), this::onDropAllPress));
        }
    }

    @Inject(at = @At("TAIL"), method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;IIF)V")
    private void onRender(PoseStack poseStack, int slotIndex, int k, float l1, CallbackInfo info) {
        ContainerSummaryConfig config = ConfigStore.instance.getConfig().containerSummaryConfig;
        if (!config.enabled) {
            return;
        }

        NonNullList<Slot> slots = this.menu.slots;
        if (slots.size() == 0) {
            return;
        }

        List<ItemStack> items = new ArrayList<>();
        if (slots.get(0).container instanceof SimpleContainer container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack itemStack = container.getItem(i);
                if (!itemStack.isEmpty()) {
                    items.add(itemStack);
                }
            }
        } else {
            if (config.showForInventory) {
                Screen screen = this;
                if (screen instanceof InventoryScreen inventory) {
                    for (Slot slot : slots) {
                        if (slot.container instanceof ResultContainer) {
                            continue;
                        }
                        items.add(slot.getItem());
                    }
                }
            }
        }

        if (items.size() == 0) {
            return;
        }

        List<ContainerSummaryController.ItemDrawable> list = ContainerSummaryController.instance.groupItems(items);

        this.setBlitOffset(100);
        this.itemRenderer.blitOffset = 100.0F;
        RenderSystem.enableDepthTest();

        boolean group = !Screen.hasAltDown();

        list.forEach(d -> d.initDraw(this.font, group));
        List<ContainerSummaryController.ItemsColumn> columns = ContainerSummaryController.instance.split(list);
        columns.forEach(ContainerSummaryController.ItemsColumn::calculateWidth);

        int cursor = this.leftPos + this.imageWidth + 2;
        for (ContainerSummaryController.ItemsColumn column: columns) {
            cursor += column.draw(poseStack, this.font, this.itemRenderer, this.minecraft.player, cursor, this.topPos);
        }

        this.itemRenderer.blitOffset = 0.0F;
        this.setBlitOffset(0);
    }

    private void onTakeAllPress(Button button) {
        ContainerButtonsController.instance.takeAll(false);
    }

    private void onSmartPutPress(Button button) {
        ContainerButtonsController.instance.smartPut();
    }

    private void onDropAllPress(Button button) {
        ContainerButtonsController.instance.dropAll(false);
    }

    @Inject(
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderLabels(Lcom/mojang/blaze3d/vertex/PoseStack;II)V", shift = At.Shift.AFTER),
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;IIF)V")
    private void onRenderLabels(PoseStack poseStack, int x, int y, float partialTicks, CallbackInfo info) {
        Events.ContainerRenderLabels.trigger(new ContainerRenderLabelsEvent(poseStack, (AbstractContainerScreen<?>) (Object) this, x, y));
    }
}