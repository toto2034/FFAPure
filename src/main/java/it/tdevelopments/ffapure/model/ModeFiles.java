package it.tdevelopments.ffapure.model;

import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ModeFiles {

    private final File folder;
    private final File modeFile;
    private final File kitsFile;
    private final File guiFile;
    private final File premadeKitsFile;
    private final YamlConfiguration modeConfig;
    private final YamlConfiguration kitsConfig;
    private final YamlConfiguration guiConfig;
    private final YamlConfiguration premadeKitsConfig;

    public ModeFiles(
        File folder,
        File modeFile,
        File kitsFile,
        File guiFile,
        File premadeKitsFile,
        YamlConfiguration modeConfig,
        YamlConfiguration kitsConfig,
        YamlConfiguration guiConfig,
        YamlConfiguration premadeKitsConfig
    ) {
        this.folder = folder;
        this.modeFile = modeFile;
        this.kitsFile = kitsFile;
        this.guiFile = guiFile;
        this.premadeKitsFile = premadeKitsFile;
        this.modeConfig = modeConfig;
        this.kitsConfig = kitsConfig;
        this.guiConfig = guiConfig;
        this.premadeKitsConfig = premadeKitsConfig;
    }

    public File getFolder() {
        return folder;
    }

    public File getModeFile() {
        return modeFile;
    }

    public File getKitsFile() {
        return kitsFile;
    }

    public File getGuiFile() {
        return guiFile;
    }

    public YamlConfiguration getModeConfig() {
        return modeConfig;
    }

    public YamlConfiguration getKitsConfig() {
        return kitsConfig;
    }

    public YamlConfiguration getGuiConfig() {
        return guiConfig;
    }

    public File getPremadeKitsFile() {
        return premadeKitsFile;
    }

    public YamlConfiguration getPremadeKitsConfig() {
        return premadeKitsConfig;
    }
}
