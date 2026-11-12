package com.personal.biggerinside;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ViewConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue DISABLE_AIT_BOTI;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_LANDED;
    public static final ForgeConfigSpec.BooleanValue ANIMATE;
    public static final ForgeConfigSpec.DoubleValue BRIGHTNESS;
    public static final ForgeConfigSpec.IntValue RANGE;
    public static final ForgeConfigSpec.IntValue MAX_DOORS;
    public static final ForgeConfigSpec.DoubleValue DOOR_WIDTH;
    public static final ForgeConfigSpec.DoubleValue DOOR_HEIGHT;
    public static final ForgeConfigSpec.DoubleValue DOOR_BOTTOM;
    public static final ForgeConfigSpec.DoubleValue PLANE_OFFSET;
    public static final ForgeConfigSpec.DoubleValue FACING_OFFSET;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ENABLED = b.comment("Draw the huge hall in the doorway when a TARDIS door is open.")
                .define("enabled", true);
        DISABLE_AIT_BOTI = b.comment("Switch off AiT's own doorway view while this add-on is running, so the two do not draw on top of each other.")
                .define("disableAitBoti", true);
        REQUIRE_LANDED = b.comment("Only show the hall while the TARDIS is landed (not while it is dematerialising or materialising).")
                .define("onlyWhenLanded", true);
        ANIMATE = b.comment("Animate the hall (pulsing lights and time rotor).")
                .define("animate", true);
        BRIGHTNESS = b.comment("Brightness of the hall, 1.0 is normal.")
                .defineInRange("brightness", 1.0, 0.2, 2.0);
        RANGE = b.comment("How close (in blocks) you have to be to a TARDIS for the hall to be drawn.")
                .defineInRange("range", 24, 4, 96);
        MAX_DOORS = b.comment("Most doors drawn at once (nearest first). Each one costs a little CPU.")
                .defineInRange("maxDoors", 2, 1, 4);
        DOOR_WIDTH = b.comment("Width of the doorway opening in blocks.")
                .defineInRange("doorWidth", 0.8, 0.3, 1.2);
        DOOR_HEIGHT = b.comment("Height of the doorway opening in blocks.")
                .defineInRange("doorHeight", 1.82, 1.0, 2.5);
        DOOR_BOTTOM = b.comment("Height of the bottom of the doorway above the block's floor.")
                .defineInRange("doorBottom", 0.06, -0.5, 1.0);
        PLANE_OFFSET = b.comment("How far from the middle of the block the doorway picture sits, toward the door.",
                        "Raise it if the picture is hidden inside the box, lower it if it floats in front of the door.")
                .defineInRange("planeOffset", 0.48, 0.2, 0.6);
        FACING_OFFSET = b.comment("If the picture shows on the wrong side of the box, set this to 90, 180 or 270 (degrees).")
                .defineInRange("facingOffsetDegrees", 0.0, -360.0, 360.0);
        SPEC = b.build();
    }

    private ViewConfig() {
    }
}
