package ru.kdev.hotswap;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.annotation.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;

@Plugin(name = "HotSwap", version = "1.0")
public class HotswapPlugin extends JavaPlugin {

    public static HotswapPlugin INSTANCE;

    private Path watchDir;
    private final Set<JarFile> waitToUpdate = new HashSet<>();
    private boolean canUpdate = true;

    @Override
    public void onEnable() {
        INSTANCE = this;

        saveDefaultConfig();

        watchDir = Paths.get(getConfig().getString("watchdir").replace("%dataFolder%", getDataFolder().getAbsolutePath()));

        if (!Files.exists(watchDir)) {
            try {
                Files.createDirectory(watchDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (getConfig().getBoolean("autoLoad")) {
            for (org.bukkit.plugin.Plugin plugin : Bukkit.getPluginManager().loadPlugins(watchDir.toFile())) {
                Bukkit.getPluginManager().enablePlugin(plugin);
            }
        }

        new WatchThread().start();
    }

    public Path getWatchDir() {
        return watchDir;
    }

    public boolean isCanUpdate() {
        return canUpdate;
    }

    public Set<JarFile> getWaitToUpdate() {
        return waitToUpdate;
    }

    public void setCanUpdate(boolean canUpdate) {
        this.canUpdate = canUpdate;

        if (canUpdate)
            Hotswap.swapAllAndClear(waitToUpdate);
    }
}
