package me.alpha432.oyvey.features.modules.player;
import me.alpha432.oyvey.features.modules.Module;
public class FastPlaceModule extends Module {
    public FastPlaceModule() {
        super("FastUse", "Makes you place/use items faster", Category.PLAYER);
    }
    @Override
    public void onTick() {
        if (nullCheck()) return;
        mc.rightClickDelay = 0;
    }
}
