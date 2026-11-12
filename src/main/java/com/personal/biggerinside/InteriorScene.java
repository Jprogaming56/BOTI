package com.personal.biggerinside;

/**
 * A tiny CPU ray caster that draws a huge hall in the style of AiT's Renaissance interior
 * (sand-stone block walls with pale blue windows, a sand and slate tiled floor, a hanging lantern cube,
 * a white hexagonal console with a glass time rotor, and a police box far away) as seen through a doorway.
 * It has no Minecraft dependencies: it just fills an int[] with ABGR pixels.
 *
 * Coordinates are door-local: x across the doorway (0 = middle), y up from the doorway's bottom edge,
 * z out of the doorway toward the viewer (the hall is at z below 0). The eye is at (ex, ey, ez) with ez above 0,
 * so moving around outside makes the hall shift with real parallax.
 */
public final class InteriorScene {
    public static final int W = 80;
    public static final int H = 176;

    private static final double RW = 15.0;   // half width of the hall
    private static final double RH = 16.0;   // ceiling height
    private static final double RD = 46.0;   // depth of the hall
    private static final double INF = 1e18;

    private static final double CON_Z = -17.0;

    private InteriorScene() {
    }

    /**
     * @param out  array of W*H pixels, ABGR (as NativeImage wants), row 0 is the top
     * @param hw   half width of the doorway in blocks
     * @param dh   height of the doorway in blocks
     * @param time animation time in seconds
     */
    public static void render(int[] out, double ex, double ey, double ez, double hw, double dh, double time) {
        double[] c = new double[3];
        if (ez < 0.05) ez = 0.05;
        for (int iy = 0; iy < H; iy++) {
            double v = dh * (1.0 - (iy + 0.5) / H);
            for (int ix = 0; ix < W; ix++) {
                double u = -hw + (ix + 0.5) / W * 2.0 * hw;
                trace(c, u, v, ex, ey, ez, time);
                int r = clamp8(c[0]);
                int g = clamp8(c[1]);
                int b = clamp8(c[2]);
                out[iy * W + ix] = 0xFF000000 | (b << 16) | (g << 8) | r;
            }
        }
    }

    private static int clamp8(double x) {
        int i = (int) (x * 255.0 + 0.5);
        return i < 0 ? 0 : Math.min(i, 255);
    }

    private static double hash(int i, int j) {
        int h = i * 374761393 + j * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535.0;
    }

    private static void set(double[] col, double r, double g, double b) {
        col[0] = r;
        col[1] = g;
        col[2] = b;
    }

    private static void trace(double[] col, double px, double py, double ex, double ey, double ez, double time) {
        double dx = px - ex, dy = py - ey, dz = -ez;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= len;
        dy /= len;
        dz /= len;

        // --- the hall's shell ---
        double tx = dx > 1e-9 ? (RW - px) / dx : dx < -1e-9 ? (-RW - px) / dx : INF;
        double ty = dy > 1e-9 ? (RH - py) / dy : dy < -1e-9 ? (-py) / dy : INF;
        double tz = (-RD) / dz;
        double t = Math.min(tx, Math.min(ty, tz));
        int kind; // 1 side wall, 2 floor, 3 ceiling, 4 back wall
        if (t == tx) kind = 1;
        else if (t == ty) kind = dy < 0 ? 2 : 3;
        else kind = 4;

        double hx = px + dx * t, hy = py + dy * t, hz = dz * t;
        switch (kind) {
            case 2 -> floor(col, hx, hz);
            case 3 -> ceiling(col, hx, hz);
            case 1 -> wall(col, hz, hy, 1.0);
            default -> wall(col, hx, hy, 0.92);
        }

        double bestT = t;
        double[] oc = new double[4];

        // --- the little police box at the far end ---
        if (box(oc, px, py, dx, dy, dz, 5.5, 1.1, -RD + 1.4, 0.55, 1.1, 0.55, bestT)) {
            bestT = oc[3];
            double yy = py + dy * bestT;
            set(col, 0.07, 0.13, 0.30);
            if (yy > 1.9) set(col, 0.95, 0.95, 0.80);
            else if (Math.abs(yy - 1.55) < 0.12) set(col, 0.9, 0.9, 0.9);
        }

        // --- the hanging lantern cube ---
        if (box(oc, px, py, dx, dy, dz, 0.0, 11.0, CON_Z - 1.0, 1.7, 1.7, 1.7, bestT)) {
            bestT = oc[3];
            double ax = px + dx * bestT, ay = py + dy * bestT, az = dz * bestT;
            set(col, 0.88, 0.86, 0.76);
            double a, b;
            int axis = (int) oc[0];
            if (axis == 1) {
                a = ax;
                b = az - (CON_Z - 1.0);
            } else if (axis == 0) {
                a = az - (CON_Z - 1.0);
                b = ay - 11.0;
            } else {
                a = ax;
                b = ay - 11.0;
            }
            if (axis != 1) {
                // 2x2 lit windows on the sides
                double fa = a / 1.7, fb = b / 1.7;
                if (Math.abs(Math.abs(fa) - 0.5) < 0.22 && Math.abs(Math.abs(fb) - 0.5) < 0.22) {
                    set(col, 1.0, 0.93, 0.62);
                }
            } else if (oc[1] < 0) {
                set(col, 1.0, 0.96, 0.8);            // glowing underside
            }
        }

        // --- the console: white hex platform, console body, glass time rotor ---
        if (cylinder(oc, px, py, dx, dy, dz, 0, CON_Z, 4.4, 0, 0.35, bestT)) {
            bestT = oc[2];
            set(col, 0.92, 0.93, 0.94);
        }
        if (cylinder(oc, px, py, dx, dy, dz, 0, CON_Z, 2.3, 0.35, 1.35, bestT)) {
            bestT = oc[2];
            double hxp = px + dx * bestT, hzp = dz * bestT - CON_Z;
            boolean top = oc[0] > 0.5;
            if (top) {
                double d = Math.hypot(hxp, hzp);
                set(col, 0.82, 0.84, 0.86);
                // little glowing controls dotted across the top
                double ang = Math.atan2(hzp, hxp);
                double cell = Math.floor((ang + Math.PI) / (Math.PI / 9.0));
                double fr = ((ang + Math.PI) / (Math.PI / 9.0)) - cell;
                if (d > 0.9 && d < 2.0 && fr > 0.35 && fr < 0.65 && (int) (d * 2.2) % 2 == 0) {
                    double p = 0.5 + 0.5 * Math.sin(time * 3.0 + cell);
                    set(col, 0.95, 0.7 + 0.25 * p, 0.25 + 0.2 * p);
                }
            } else {
                set(col, 0.94, 0.95, 0.96);
            }
        }
        if (cylinder(oc, px, py, dx, dy, dz, 0, CON_Z, 0.7, 1.35, 5.6, bestT)) {
            bestT = oc[2];
            double yy = py + dy * bestT;
            double bands = 0.5 + 0.5 * Math.sin(yy * 3.5 - time * 3.5);
            double pulse = 0.7 + 0.3 * Math.sin(time * 1.6);
            double g = (0.55 + 0.45 * bands) * pulse;
            set(col, 0.45 + 0.4 * g, 0.85 + 0.15 * g, 0.78 + 0.2 * g);
        }
        if (cylinder(oc, px, py, dx, dy, dz, 0, CON_Z, 0.95, 5.6, 6.2, bestT)) {
            bestT = oc[2];
            set(col, 0.78, 0.8, 0.82);
        }

        // --- soft cream haze so far things melt away ---
        double f = 1.0 - Math.exp(-bestT * 0.020);
        col[0] += (0.93 - col[0]) * f * 0.5;
        col[1] += (0.89 - col[1]) * f * 0.5;
        col[2] += (0.76 - col[2]) * f * 0.5;
    }

    // ------------------------------------------------------------------ shell colours

    /** Sand-stone blocks with recessed pale blue windows. a: along the wall, b: height. */
    private static void wall(double[] col, double a, double b, double light) {
        double bs = 1.25;                               // block size
        int i = (int) Math.floor(a / bs);
        int j = (int) Math.floor(b / bs);
        double fa = a / bs - i, fb = b / bs - j;
        double h = hash(i, j);
        double up = 0.78 + 0.22 * Math.min(1.0, b / 12.0);
        double shade = (0.70 + 0.30 * h) * up * light;

        // blocks stick out and recess; darken the lower and right edges
        double edge = 1.0;
        if (fa < 0.06 || fb < 0.08) edge = 0.78;
        else if (fa > 0.94 || fb > 0.92) edge = 1.06;

        boolean window = (j % 3 == 1) && h > 0.42 && b > 1.5 && fa > 0.12 && fa < 0.88 && fb > 0.15 && fb < 0.85;
        if (window) {
            double w = 0.85 + 0.15 * hash(j, i);
            set(col, 0.60 * w, 0.76 * w, 0.90 * w);
            return;
        }
        set(col, 0.80 * shade * edge, 0.72 * shade * edge, 0.54 * shade * edge);
        if (b < 0.9) {                                  // darker plinth
            col[0] *= 0.78;
            col[1] *= 0.78;
            col[2] *= 0.78;
        }
    }

    private static void floor(double[] col, double x, double z) {
        double cell = 2.0;
        int i = (int) Math.floor(x / cell);
        int j = (int) Math.floor(z / cell);
        double fx = x / cell - i, fz = z / cell - j;
        // light sand with dark slate tiles set into it in a checker pattern
        boolean slate = ((i + j) & 1) == 0 && fx > 0.12 && fx < 0.88 && fz > 0.12 && fz < 0.88;
        if (slate) {
            double k = 0.9 + 0.2 * hash(i, j);
            set(col, 0.44 * k, 0.43 * k, 0.40 * k);
        } else {
            double k = 0.95 + 0.1 * hash(j, i);
            set(col, 0.86 * k, 0.78 * k, 0.60 * k);
        }
        // light spilling around the console
        double dc = Math.hypot(x, z - CON_Z);
        if (dc < 10.0) {
            double k = 1.0 - dc / 10.0;
            k *= k * 0.30;
            col[0] += 0.5 * k;
            col[1] += 0.5 * k;
            col[2] += 0.4 * k;
        }
    }

    private static void ceiling(double[] col, double x, double z) {
        int i = (int) Math.floor(x / 1.25);
        int j = (int) Math.floor(z / 1.25);
        double h = hash(i, j);
        set(col, 0.66 + 0.12 * h, 0.60 + 0.1 * h, 0.46 + 0.08 * h);
        // glowing skylight panels, like the lit patches high in the Renaissance hall
        double mx = ((x % 10.0) + 10.0) % 10.0;
        double mz = ((z % 12.0) + 12.0) % 12.0;
        if (mx > 3.5 && mx < 6.5 && mz > 4.0 && mz < 8.0) set(col, 0.95, 0.97, 1.0);
    }

    // ------------------------------------------------------------------ primitives

    /**
     * Axis aligned box. On a hit: oc[0] = axis of the face hit (0 x, 1 y, 2 z), oc[1] = sign of the ray
     * direction on that axis, oc[3] = distance. Only returns true when closer than maxT.
     */
    private static boolean box(double[] oc, double px, double py, double dx, double dy, double dz,
                               double cx, double cy, double cz, double hx, double hy, double hz, double maxT) {
        double tmin = -INF, tmax = INF;
        int axis = 0;
        double[] o = {px - cx, py - cy, 0 - cz};
        double[] d = {dx, dy, dz};
        double[] h = {hx, hy, hz};
        for (int k = 0; k < 3; k++) {
            if (Math.abs(d[k]) < 1e-9) {
                if (Math.abs(o[k]) > h[k]) return false;
            } else {
                double t1 = (-h[k] - o[k]) / d[k];
                double t2 = (h[k] - o[k]) / d[k];
                double near = Math.min(t1, t2), far = Math.max(t1, t2);
                if (near > tmin) {
                    tmin = near;
                    axis = k;
                }
                if (far < tmax) tmax = far;
                if (tmin > tmax) return false;
            }
        }
        if (tmin <= 0 || tmin >= maxT) return false;
        oc[0] = axis;
        oc[1] = Math.signum(d[axis]);
        oc[3] = tmin;
        return true;
    }

    /**
     * Ray vs. vertical capped cylinder (top cap only). On a hit: oc[0] = 1 if the cap was hit (else 0),
     * oc[2] = distance. Only returns true when the hit is closer than maxT.
     */
    private static boolean cylinder(double[] oc, double px, double py, double dx, double dy, double dz,
                                    double cx, double cz, double r, double y0, double y1, double maxT) {
        double best = maxT;
        boolean got = false;
        double ox = px - cx;
        double oz = 0 - cz;
        double a = dx * dx + dz * dz;
        if (a > 1e-9) {
            double b = 2 * (ox * dx + oz * dz);
            double c = ox * ox + oz * oz - r * r;
            double disc = b * b - 4 * a * c;
            if (disc > 0) {
                double t = (-b - Math.sqrt(disc)) / (2 * a);
                if (t > 0 && t < best) {
                    double y = py + dy * t;
                    if (y >= y0 && y <= y1) {
                        best = t;
                        oc[0] = 0;
                        got = true;
                    }
                }
            }
        }
        if (dy < -1e-9) {
            double t = (y1 - py) / dy;
            if (t > 0 && t < best) {
                double hx = px + dx * t - cx;
                double hz = dz * t - cz;
                if (hx * hx + hz * hz <= r * r) {
                    best = t;
                    oc[0] = 1;
                    got = true;
                }
            }
        }
        if (got) oc[2] = best;
        return got;
    }
}
