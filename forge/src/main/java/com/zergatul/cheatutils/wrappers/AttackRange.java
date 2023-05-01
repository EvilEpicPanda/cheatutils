package com.zergatul.cheatutils.wrappers;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public class AttackRange {

    public static double get() {
        return Minecraft.getInstance().player.getAttackRange();
    }

    public static boolean canHit(Entity entity) {
        return Minecraft.getInstance().player.canHit(entity, 0);
    }
}