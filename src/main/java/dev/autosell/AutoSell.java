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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Press G (with no screen open) to toggle the cycle:
 * wait 5 min -> /home 1 -> wait 6 s -> /sellall x30 (0.5 s apart) -> /home 3 -> repeat.
 * While on, the sneak key is held down. A small HUD panel shows the cycle count,
 * current status and time until the next cycle, and a red pulsing panel appears
 * whenever another player is within 100 blocks.
 */
public class AutoSell implements ClientModInitializer {
    // commands are sent without the leading slash
    private static final String HOME_SELL = "home 1";
    private static final String HOME_BACK = "home 3";
    private static final String SELL = "sellall totem_of_undying";

    private static final int TPS = 20;
    private static final int IDLE_TICKS = 5 * 60 * TPS;   // 5 minutes at home 3 between runs
    private static final int ARRIVE_TICKS = 6 * TPS;      // wait after /home 1
    private static final int SELL_INTERVAL_TICKS = TPS / 2; // 0.5 seconds between sells
    private static final int SELL_COUNT = 30;

    private static final double ALERT_RADIUS = 100.0;     // blocks
    private static final int MAX_LISTED = 5;              // players listed in the alert panel

    private enum State { IDLE, ARRIVING, SELLING, RETURNING }

    private record Nearby(class_2561 name, int distance) {}

    private static boolean enabled = false;
    private static boolean lastKeyDown = false;
    private static State state = State.IDLE;
    private static int ticks = 0;
    private static int sells = 0;
    private static int cycles = 0; // fully finished cycles since toggled on
    private static List<Nearby> nearby = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        // sneak is applied at the START of the tick so the player's input sees it this tick
        ClientTickEvents.START_CLIENT_TICK.register(AutoSell::holdSneak);
        ClientTickEvents.END_CLIENT_TICK.register(AutoSell::tick);
        HudElementRegistry.addLast(class_2960.method_60655("auto-sell", "timer"), AutoSell::renderHud);
    }

    private static void holdSneak(class_310 mc) {
        if (enabled && mc.field_1724 != null) {
            mc.field_1690.field_1832.method_23481(true);
        }
    }

    private static void releaseSneak(class_310 mc) {
        mc.field_1690.field_1832.method_23481(false);
    }

    private static void reset() {
        state = State.IDLE;
        ticks = 0;
        sells = 0;
        nearby = new ArrayList<>();
    }

    private static void send(class_310 mc, String command) {
        mc.method_1562().method_45730(command);
    }

    /** Collects other players within ALERT_RADIUS, closest first. */
    private static void scanPlayers(class_310 mc) {
        List<Nearby> found = new ArrayList<>();
        if (mc.field_1687 != null && mc.field_1724 != null) {
            for (var p : mc.field_1687.method_18456()) {
                if (p == mc.field_1724) continue;
                float d = mc.field_1724.method_5739(p);
                if (d <= ALERT_RADIUS) found.add(new Nearby(p.method_5477(), Math.round(d)));
            }
        }
        found.sort(Comparator.comparingInt(Nearby::distance));
        nearby = found;
    }

    private static void tick(class_310 mc) {
        if (mc.method_22683() == null) return;

        boolean down = GLFW.glfwGetKey(mc.method_22683().method_4490(), GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS;
        if (down && !lastKeyDown && mc.field_1755 == null && mc.field_1724 != null) {
            enabled = !enabled;
            reset();
            cycles = 0;
            if (!enabled) releaseSneak(mc);
            mc.field_1724.method_7353(class_2561.method_43470(
                    enabled ? "Auto Sell: ON (first run in 5:00)" : "Auto Sell: OFF"), true);
        }
        lastKeyDown = down;

        if (!enabled) return;
        if (mc.field_1724 == null || mc.method_1562() == null) {
            enabled = false; // left the world
            reset();
            releaseSneak(mc);
            return;
        }

        scanPlayers(mc);

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

        renderAlert(ctx, tr, x, y + height + 6, pad, row, headerH);
    }

    /** Red pulsing panel listing nearby players; only drawn while someone is in range. */
    private static void renderAlert(class_332 ctx, class_327 tr, int x, int y, int pad, int row, int headerH) {
        List<Nearby> list = nearby;
        if (list.isEmpty()) return;

        int shown = Math.min(list.size(), MAX_LISTED);
        int extra = list.size() - shown;
        String title = "PLAYER NEARBY";
        String moreText = "+" + extra + " more";

        int width = tr.method_1727(title) + pad * 2;
        for (int i = 0; i < shown; i++) {
            Nearby n = list.get(i);
            width = Math.max(width, tr.method_27525(n.name()) + 14 + tr.method_1727(n.distance() + "m") + pad * 2);
        }
        width = Math.max(width, 120);
        int rows = shown + (extra > 0 ? 1 : 0);
        int height = headerH + rows * row + pad;

        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 150.0);
        int outlineAlpha = 0x80 + (int) (0x7F * pulse);
        int red = 0xFF4040;

        ctx.method_25294(x - 1, y - 1, x + width + 1, y + height + 1, (outlineAlpha << 24) | red); // pulsing outline
        ctx.method_25294(x, y, x + width, y + height, 0xD9200C0C);                                   // body
        ctx.method_25294(x, y, x + width, y + headerH, 0xF0140808);                                  // header
        ctx.method_25294(x, y, x + width, y + 1, 0xFFFF4040);                                        // top line
        ctx.method_25294(x + 3, y + headerH, x + width - 3, y + headerH + 1, 0xFFFF4040);            // separator

        int titleColor = (0xC0 + (int) (0x3F * pulse)) << 24 | 0xFF5555;
        ctx.method_51433(tr, title, x + (width - tr.method_1727(title)) / 2, y + 4, titleColor, false);

        int ry = y + headerH + 3;
        for (int i = 0; i < shown; i++) {
            Nearby n = list.get(i);
            String dist = n.distance() + "m";
            ctx.method_51439(tr, n.name(), x + pad, ry, 0xFFE6E8EF, false);
            ctx.method_51433(tr, dist, x + width - pad - tr.method_1727(dist), ry, 0xFFFF8080, false);
            ry += row;
        }
        if (extra > 0) {
            ctx.method_51433(tr, moreText, x + pad, ry, 0xFF8D93A8, false);
        }
    }
}
