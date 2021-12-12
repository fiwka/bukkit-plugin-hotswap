package ru.kdev.hotswap;

import org.bukkit.Bukkit;
import org.bukkit.plugin.InvalidDescriptionException;

import java.io.IOException;
import java.nio.file.*;
import java.util.jar.JarFile;

import static java.nio.file.StandardWatchEventKinds.*;

public class WatchThread extends Thread {

    private final Path pathToWatch;

    WatchThread() {
        super("Bukkit HotSwap Plugin watch thread");
        pathToWatch = HotswapPlugin.INSTANCE.getWatchDir();
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            pathToWatch.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);

            WatchKey key;

            long lastModified = 0;

            while ((key = watchService.take()) != null) {
                Path watchable = (Path) key.watchable();

                for (WatchEvent<?> event : key.pollEvents()) {
                    JarFile[] buffer = new JarFile[1];
                    Path context = watchable.resolve((Path) event.context());

                    if (tryGetValidJarFile(context, buffer)) {
                        if (event.kind() == ENTRY_DELETE) {
                            Hotswap.pluginYmlOf(buffer[0]).ifPresent(pluginYml -> {
                                try {
                                    Hotswap.unloadPlugin(Hotswap.pluginOf(Hotswap.descriptionOf(buffer[0], pluginYml)), Bukkit.getPluginManager());
                                } catch (IOException | InvalidDescriptionException e) {
                                    e.printStackTrace();
                                }
                            });
                        } else {
                            long contextLastModified = context.toFile().lastModified();

                            if (!(event.kind() == ENTRY_MODIFY && contextLastModified - lastModified > 1000))
                                continue;

                            lastModified = contextLastModified;

                            if (HotswapPlugin.INSTANCE.isCanUpdate())
                                Hotswap.swap(buffer[0]);
                            else
                                HotswapPlugin.INSTANCE.getWaitToUpdate().add(buffer[0]);
                        }
                    }

                    if (buffer[0] != null)
                        buffer[0].close();
                }

                key.reset();
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private boolean tryGetValidJarFile(Path path, JarFile[] buffer) {
        try {
            JarFile jarFile = new JarFile(path.toFile());
            buffer[0] = jarFile;
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
