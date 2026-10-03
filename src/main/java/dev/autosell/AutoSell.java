package dev.autosell;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import org.lwjgl.glfw.GLFW;

/** Press G (with no screen open) to toggle sending a command every second. */
public class AutoSell implements ClientModInitializer {
    /** Sent as a chat command, so no leading slash here. */
    public static final String COMMAND = "sellall totem_of_undying";
    private static final int INTERVAL_TICKS = 20; // 20 ticks = 1 second

    private static boolean enabled = false;
    private static boolean lastKeyDown = false;
    private static int ticks = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(AutoSell::tick);
    }

    private static void tick(class_310 mc) {
        if (mc.method_22683() == null) return;

        boolean down = GLFW.glfwGetKey(mc.method_22683().method_4490(), GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS;
        if (down && !lastKeyDown && mc.field_1755 == null && mc.field_1724 != null) {
            enabled = !enabled;
            ticks = INTERVAL_TICKS - 1; // first send on the next tick
            mc.field_1724.method_7353(class_2561.method_43470("Auto Sell: " + (enabled ? "ON" : "OFF")), true);
        }
        lastKeyDown = down;

        if (!enabled) return;
        if (mc.field_1724 == null || mc.method_1562() == null) {
            enabled = false; // left the world
            return;
        }
        if (++ticks >= INTERVAL_TICKS) {
            ticks = 0;
            mc.method_1562().method_45730(COMMAND);
        }
    }
}
