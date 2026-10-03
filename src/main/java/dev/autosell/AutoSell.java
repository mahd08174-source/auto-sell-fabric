package dev.autosell;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_9779;
import org.lwjgl.glfw.GLFW;

/**
 * Press G (with no screen open) to toggle the cycle:
 * wait 5 min -> /home 1 -> wait 6 s -> /sellall x10 (1 s apart) -> /home 3 -> repeat.
 * A small HUD panel shows the cycle count, current status and time until the next cycle.
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
    private static int cycles = 0; // fully finished cycles since toggled on

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(AutoSell::tick);
        HudElementRegistry.addLast(class_2960.method_60655("auto-sell", "timer"), AutoSell::renderHud);
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
            cycles = 0;
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
                    cycles++;
                }
            }
        }
    }

    /** Ticks until the next cycle starts (the next /home 1). */
    private static int ticksUntilNextCycle() {
        int remaining = switch (state) {
            case IDLE -> IDLE_TICKS - ticks;
            case ARRIVING -> (ARRIVE_TICKS - ticks) + SELL_COUNT * SELL_INTERVAL_TICKS + IDLE_TICKS;
            case SELLING -> Math.max(0, SELL_INTERVAL_TICKS - ticks)
                    + (SELL_COUNT - sells - 1) * SELL_INTERVAL_TICKS + SELL_INTERVAL_TICKS + IDLE_TICKS;
            case RETURNING -> (SELL_INTERVAL_TICKS - ticks) + IDLE_TICKS;
        };
        return Math.max(0, remaining);
    }

    private static String clock(int tickCount) {
        int total = (tickCount + TPS - 1) / TPS; // round up so it hits 0:00 right at the start
        return (total / 60) + ":" + String.format("%02d", total % 60);
    }

    private static String statusText() {
        return switch (state) {
            case IDLE -> "Waiting at home 3";
            case ARRIVING -> "At home 1 (" + ((ARRIVE_TICKS - ticks + TPS - 1) / TPS) + "s)";
            case SELLING -> "Selling " + sells + "/" + SELL_COUNT;
            case RETURNING -> "Returning to home 3";
        };
    }

    private static void renderHud(class_332 ctx, class_9779 tickCounter) {
        class_310 mc = class_310.method_1551();
        if (!enabled || mc.field_1724 == null || mc.field_1690.field_1842) return;

        class_327 tr = mc.field_1772;
        String title = "AUTO SELL";
        String[] keys = {"Cycles", "Status", "Next cycle"};
        String[] values = {String.valueOf(cycles), statusText(), clock(ticksUntilNextCycle())};

        int pad = 6;
        int row = 11;
        int width = tr.method_1727(title) + pad * 2;
        for (int i = 0; i < keys.length; i++) {
            width = Math.max(width, tr.method_1727(keys[i]) + 14 + tr.method_1727(values[i]) + pad * 2);
        }
        width = Math.max(width, 120);
        int headerH = row + 5;
        int height = headerH + keys.length * row + pad;
        int x = 6, y = 6;

        int blue = 0xFF3D8BFF;
        ctx.method_25294(x - 1, y - 1, x + width + 1, y + height + 1, 0x8C3D8BFF); // outline
        ctx.method_25294(x, y, x + width, y + height, 0xD9101320);                  // body
        ctx.method_25294(x, y, x + width, y + headerH, 0xF00B0D16);                 // header
        ctx.method_25294(x, y, x + width, y + 1, blue);                             // top line
        ctx.method_25294(x + 3, y + headerH, x + width - 3, y + headerH + 1, blue); // separator

        ctx.method_51433(tr, title, x + (width - tr.method_1727(title)) / 2, y + 4, 0xFFE6E8EF, false);

        int ry = y + headerH + 3;
        for (int i = 0; i < keys.length; i++) {
            ctx.method_51433(tr, keys[i], x + pad, ry, 0xFF8D93A8, false);
            ctx.method_51433(tr, values[i], x + width - pad - tr.method_1727(values[i]), ry, 0xFFE6E8EF, false);
            ry += row;
        }
    }
}
