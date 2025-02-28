package com.zergatul.cheatutils.mixins.common;

import com.zergatul.cheatutils.controllers.NetworkPacketsController;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class MixinConnection {

    @Shadow
    private Channel channel;

    @Shadow
    @Final
    private PacketFlow receiving;

    @Inject(at = @At("RETURN"), method = "disconnect(Lnet/minecraft/network/DisconnectionDetails;)V")
    private void onDisconnect(DisconnectionDetails details, CallbackInfo info) {
        if (this.receiving == PacketFlow.CLIENTBOUND) {
            NetworkPacketsController.instance.onDisconnect((Connection) (Object) this);
        }
    }

    @Inject(at = @At("HEAD"), method = "doSendPacket", cancellable = true)
    private void onPacketSend(Packet<?> packet, PacketSendListener listener, boolean flush, CallbackInfo info) {
        if (this.receiving == PacketFlow.CLIENTBOUND) {
            boolean skip = NetworkPacketsController.instance.triggerSend((Connection) (Object) this, packet);
            if (skip) {
                if (flush) {
                    this.channel.flush();
                }
                info.cancel();
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", cancellable = true)
    private void onPacketReceive(ChannelHandlerContext context, Packet<?> packet, CallbackInfo info) {
        if (this.receiving == PacketFlow.CLIENTBOUND) {
            boolean skip = NetworkPacketsController.instance.triggerReceive((Connection) (Object) this, packet);
            if (skip) {
                info.cancel();
            }
        }
    }
}