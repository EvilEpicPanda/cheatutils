package com.zergatul.cheatutils.webui;

import com.zergatul.cheatutils.configs.ConfigStore;
import com.zergatul.cheatutils.modules.scripting.StatusOverlay;
import com.zergatul.cheatutils.controllers.ScriptController;
import com.zergatul.scripting.compiler.CompilationResult;

public class StatusOverlayCodeApi extends CodeApiBase {

    @Override
    public String getRoute() {
        return "status-overlay-code";
    }

    @Override
    protected CompilationResult<Runnable> compile(String code) {
        return ScriptController.instance.compileOverlay(code);
    }

    @Override
    protected void setCode(String code) {
        ConfigStore.instance.getConfig().statusOverlayConfig.code = code;
    }

    @Override
    protected void setProgram(Runnable program) {
        StatusOverlay.instance.setScript(program);
    }
}