package com.zergatul.cheatutils.modules.scripting;

import com.zergatul.cheatutils.common.Events;
import com.zergatul.cheatutils.common.events.SendChatEvent;
import com.zergatul.cheatutils.configs.ConfigStore;
import com.zergatul.cheatutils.controllers.ScriptController;
import com.zergatul.cheatutils.modules.Module;
import com.zergatul.scripting.DiagnosticMessage;
import com.zergatul.scripting.compiler.CompilationResult;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public class Exec implements Module {

    public static final Exec instance = new Exec();

    private Exec() {
        Events.SendChat.add(this::onSendChat);
    }

    private void onSendChat(SendChatEvent event) {
        if (!ConfigStore.instance.getConfig().execConfig.enabled) {
            return;
        }

        if (event.getMessage().startsWith(".")) {
            try {
                CompilationResult<Runnable> result = ScriptController.instance.compileKeys(event.getMessage().substring(1));
                if (result.program() != null) {
                    result.program().run();
                    systemMessage("OK", 0xFF80FF80);
                } else {
                    for (DiagnosticMessage message : result.diagnostics()) {
                        systemMessage(message.message, 0xFFFF8080);
                    }
                }
            }
            catch (Throwable e) {
                systemMessage(e.getMessage(), 0xFFFF8080);
            }

            event.cancel();
        }
    }

    private void systemMessage(String message, int color) {
        Minecraft.getInstance().getChatListener().handleSystemMessage(Component.literal(message).withStyle(Style.EMPTY.withColor(color)), false);
    }
}