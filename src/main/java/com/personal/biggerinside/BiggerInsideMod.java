package com.personal.biggerinside;

import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(BiggerInsideMod.MOD_ID)
public class BiggerInsideMod {
    public static final String MOD_ID = "biggerinside";
    public static final Logger LOGGER = LogManager.getLogger("BiggerInside");

    public BiggerInsideMod() {
        ModLoadingContext ctx = ModLoadingContext.get();
        ctx.registerConfig(ModConfig.Type.CLIENT, ViewConfig.SPEC);
        // Client-only add-on: it may join servers that do not have it.
        ctx.registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> IExtensionPoint.DisplayTest.IGNORESERVERONLY, (a, b) -> true));
    }
}
