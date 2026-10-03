package dev.autosell;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import org.lwjgl.glfw.GLFW;

/**
 * Press G (with no screen open) to toggle the cycle:
 * wait 5 min -> /home 1 -> wait 6 s -> /sellall x10 (1 s apart) -> /home 3 -> repeat.
 */
public class AutoSell implements ClientModInitializer {
    // commands are sent without the leading slash
    private static final String HOME_SELL = "home 1";
    private static final String HOME_BACK = "home 3";
    private static final String SELL = "sellall totem_of_undying";

    private static final int TPS = 20;
    private static final int IDLE_TICKS = 5 * 60 * TPS;   // 5 minutes at home 3 between runs
    private static final int ARRIVE_TICKS = 6 * TPS;      // wait after /home 1
    private static final int SELL_INTERVAL_TICKS = TPS;   // 1 second between sells
    private static final int SELL_COUNT = 10;

    private enum State { IDLE, ARRIVING, SELLING, RETURNING }

    private static boolean enabled = false;
    private static boolean lastKeyDown = false;
    private static State state = State.IDLE;
    private static int ticks = 0;
    private static int sells = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(AutoSell::tick);
    }

    private static void reset() {
        state = State.IDLE;
        ticks = 0;
        sells = 0;
    }

    private static void send(class_310 mc, String command) {
        mc.method_1562().method_45730(command);
    }

    private static void tick(class_310 mc) {
        if (mc.method_22683() == null) return;

        boolean down = GLFW.glfwGetKey(mc.method_22683().method_4490(), GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS;
        if (down && !lastKeyDown && mc.field_1755 == null && mc.field_1724 != null) {
            enabled = !enabled;
            reset();
            mc.field_1724.method_7353(class_2561.method_43470(
                    enabled ? "Auto Sell: ON (first run in 5:00)" : "Auto Sell: OFF"), true);
        }
        lastKeyDown = down;

        if (!enabled) return;
        if (mc.field_1724 == null || mc.method_1562() == null) {
            enabled = false; // left the world
            reset();
            return;
        }

        ticks++;
        switch (state) {
            case IDLE -> {
                if (ticks >= IDLE_TICKS) {
                    send(mc, HOME_SELL);
                    state = State.ARRIVING;
                    ticks = 0;
                }
            }
            case ARRIVING -> {
                if (ticks >= ARRIVE_TICKS) {
                    state = State.SELLING;
                    sells = 0;
                    ticks = SELL_INTERVAL_TICKS; // first sell right away
                }
            }
            case SELLING -> {
                if (ticks >= SELL_INTERVAL_TICKS) {
                    send(mc, SELL);
                    sells++;
                    ticks = 0;
                    if (sells >= SELL_COUNT) state = State.RETURNING;
                }
            }
            case RETURNING -> {
                if (ticks >= SELL_INTERVAL_TICKS) { // let the last sell finish first
                    send(mc, HOME_BACK);
                    state = State.IDLE;
                    ticks = 0;
                }
            }
        }
    }
}
