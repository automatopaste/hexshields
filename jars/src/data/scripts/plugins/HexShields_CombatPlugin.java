package data.scripts.plugins;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import data.scripts.shaders.HexShields_ShieldGraphicRenderer;

public class HexShields_CombatPlugin extends BaseEveryFrameCombatPlugin {
    @Override
    public void init(CombatEngineAPI engine) {
        engine.addLayeredRenderingPlugin(new HexShields_ShieldGraphicRenderer());
    }
}
