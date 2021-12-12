package ru.kdev.hotswap;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.PluginClassLoader;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class Hotswap {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private Hotswap() {
        throw new UnsupportedOperationException("Can't create instance of utility class.");
    }

    public static void swapAll(Collection<JarFile> jarFiles) {
        jarFiles.forEach(Hotswap::swap);
    }

    public static void swapAllAndClear(Collection<JarFile> jarFiles) {
        swapAll(jarFiles);
        jarFiles.clear();
    }

    public static void swap(JarFile jarFile) {
        PluginManager pluginManager = Bukkit.getPluginManager();
        Optional<JarEntry> optionalPluginYml = pluginYmlOf(jarFile);

        optionalPluginYml.ifPresent(pluginYml -> {
            try {
                PluginDescriptionFile pluginDescriptionFile = descriptionOf(jarFile, pluginYml);
                Optional<Plugin> optionalPlugin = Optional.ofNullable(pluginManager.getPlugin(pluginDescriptionFile.getName()));

                optionalPlugin.ifPresent(plugin -> unloadPlugin(plugin, pluginManager));

                Plugin plugin = loadPlugin(jarFile, pluginManager);

                try {
                    pluginManager.enablePlugin(plugin);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    unloadPlugin(plugin, pluginManager);
                }
            } catch (IOException | InvalidDescriptionException e) {
                e.printStackTrace();
            }
        });
    }

    private static Plugin loadPlugin(JarFile jarFile, PluginManager pluginManager) {
        try {
            System.out.println(jarFile.getName());
            return pluginManager.loadPlugin(new File(jarFile.getName()));
        } catch (InvalidPluginException | InvalidDescriptionException e) {
            e.printStackTrace();
            return null;
        }
    }

    static Optional<JarEntry> pluginYmlOf(JarFile jarFile) {
        return jarFile.stream().filter(x -> x.getName().equals("plugin.yml")).findFirst();
    }

    static PluginDescriptionFile descriptionOf(JarFile jarFile, JarEntry pluginYml) throws IOException, InvalidDescriptionException {
        return new PluginDescriptionFile(jarFile.getInputStream(pluginYml));
    }

    static Plugin pluginOf(PluginDescriptionFile descriptionFile) {
        return Bukkit.getPluginManager().getPlugin(descriptionFile.getName());
    }

    static void unloadPlugin(Plugin plugin, PluginManager pluginManager) {
        clearCommandsByPlugin(plugin);
        Field classLoaderField;
        try {
            classLoaderField = JavaPlugin.class.getDeclaredField("classLoader");
            classLoaderField.setAccessible(true);

            ClassLoader classLoader = (ClassLoader) LOOKUP.unreflectGetter(classLoaderField).invoke(plugin);

            pluginManager.disablePlugin(plugin);

            if (classLoader instanceof PluginClassLoader) {
                ((PluginClassLoader) classLoader).close();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static void clearCommandsByPlugin(Plugin plugin) {
        CommandMap commandMap = Bukkit.getCommandMap();
        Map<String, Command> knownCommands = commandMap.getKnownCommands();

        Set<String> toRemoveCommands = new HashSet<>();

        for (Map.Entry<String, Command> entry : knownCommands.entrySet()) {
            Command command = entry.getValue();

            if (command instanceof PluginIdentifiableCommand) {
                PluginIdentifiableCommand identifiableCommand = (PluginIdentifiableCommand) command;

                if (identifiableCommand.getPlugin().getName().equals(plugin.getName()))
                    command.unregister(commandMap);

                toRemoveCommands.add(command.getLabel());
            }
        }

        toRemoveCommands.forEach(knownCommands::remove);
    }
}
