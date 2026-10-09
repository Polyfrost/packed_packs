package io.github.fishstiz.packed_packs;

import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pl.tomgirl.pylon.Platform;

import java.io.File;
import java.nio.file.Path;

public final class PackedPacks {
    public static final String MOD_ID = "packed_packs";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private PackedPacks() {
    }

    public static Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
    }

    public static void open(File file) {
        Platform.CURRENT.open(file.toURI().toString());
    }
}
