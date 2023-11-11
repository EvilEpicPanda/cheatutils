package com.zergatul.cheatutils.controllers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.zergatul.cheatutils.common.Events;
import com.zergatul.cheatutils.common.Registries;
import com.zergatul.cheatutils.common.events.RenderWorldLastEvent;
import com.zergatul.cheatutils.configs.ConfigStore;
import com.zergatul.cheatutils.configs.ScriptedBlockPlacerConfig;
import com.zergatul.cheatutils.modules.utilities.RenderUtilities;
import com.zergatul.cheatutils.render.LineRenderer;
import com.zergatul.cheatutils.render.Primitives;
import com.zergatul.cheatutils.utils.BlockPlacingMethod;
import com.zergatul.cheatutils.utils.BlockUtils;
import com.zergatul.cheatutils.utils.NearbyBlockEnumerator;
import com.zergatul.cheatutils.utils.SlotSelector;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ScriptedBlockPlacerController {

    public static final ScriptedBlockPlacerController instance = new ScriptedBlockPlacerController();

    private final Minecraft mc = Minecraft.getInstance();
    private final SlotSelector slotSelector = new SlotSelector();
    private Runnable script;
    private String blockId;
    private BlockPlacingMethod method;
    private BlockUtils.PlaceBlockPlan debugPlan;
    private volatile boolean debugStep;

    private ScriptedBlockPlacerController() {
        Events.ClientTickEnd.add(this::onClientTickEnd);
        Events.RenderWorldLast.add(this::onRenderWorldLast);
    }

    public void setScript(Runnable script) {
        this.script = script;
    }

    public void setBlock(String blockId, BlockPlacingMethod method) {
        this.blockId = blockId;
        this.method = method;
    }

    public void placeOne() {
        debugStep = true;
    }

    private void onClientTickEnd() {
        ScriptedBlockPlacerConfig config = ConfigStore.instance.getConfig().scriptedBlockPlacerConfig;
        if (!config.enabled) {
            return;
        }

        if (mc.player == null || mc.level == null) {
            return;
        }

        if (script == null) {
            return;
        }

        Vec3 eyePos = mc.player.getEyePosition(1);
        for (BlockPos pos : NearbyBlockEnumerator.getPositions(eyePos, config.maxRange)) {
            BlockState state = mc.level.getBlockState(pos);
            if (!state.canBeReplaced()) {
                continue;
            }

            blockId = null;
            CurrentBlockController.instance.set(pos, state);
            script.run();
            CurrentBlockController.instance.clear();

            if (blockId == null) {
                continue;
            }

            Block block = Registries.BLOCKS.getValue(new ResourceLocation(blockId));
            if (block == Blocks.AIR) {
                continue;
            }

            int slot = slotSelector.selectBlock(config, block);
            if (slot < 0) {
                continue;
            }

            BlockUtils.PlaceBlockPlan plan = BlockUtils.getPlacingPlan(pos, config.attachToAir, method);
            if (plan != null) {
                if (config.debugMode && !debugStep) {
                    debugPlan = plan;
                } else {
                    debugPlan = null;
                    debugStep = false;
                    mc.player.getInventory().selected = slot;
                    BlockUtils.applyPlacingPlan(plan, config.useShift);
                }
                return;
            }
        }
    }

    private void onRenderWorldLast(RenderWorldLastEvent event) {
        ScriptedBlockPlacerConfig config = ConfigStore.instance.getConfig().scriptedBlockPlacerConfig;
        if (config.enabled && config.debugMode && debugPlan != null) {
            // draw neighbour block
            LineRenderer renderer = RenderUtilities.instance.getLineRenderer();
            renderer.begin(event, false);

            double x1 = debugPlan.neighbour().getX();
            double y1 = debugPlan.neighbour().getY();
            double z1 = debugPlan.neighbour().getZ();
            double x2 = x1 + 1;
            double y2 = y1 + 1;
            double z2 = z1 + 1;
            renderer.cuboid(x1, y1, z1, x2, y2, z2, 1f, 1f, 1f, 1f);
            renderer.end();

            // draw target block
            renderer.begin(event, false);
            RenderSystem.setShaderColor(0.7f, 1f, 0.7f, 1f);

            x1 = debugPlan.destination().getX() + 0.05;
            y1 = debugPlan.destination().getY() + 0.05;
            z1 = debugPlan.destination().getZ() + 0.05;
            x2 = x1 + 0.9;
            y2 = y1 + 0.9;
            z2 = z1 + 0.9;
            renderer.cuboid(x1, y1, z1, x2, y2, z2, 1f, 1f, 1f, 1f);
            renderer.end();

            // draw target point
            renderer.begin(event, false);
            RenderSystem.setShaderColor(1f, 1f, 0.7f, 1f);

            for (Direction direction : Direction.values()) {
                Vec3 p1 = debugPlan.target().relative(direction, 0.1);
                Vec3 p2 = debugPlan.target().relative(direction, -0.1);
                renderer.line(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, 1f, 1f, 1f, 1f);
            }
            renderer.end();

            // reset color
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }
}