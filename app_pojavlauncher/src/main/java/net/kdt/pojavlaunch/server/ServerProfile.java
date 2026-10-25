package net.kdt.pojavlaunch.server;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ServerProfile {
    public enum Software {
        VANILLA("Vanilla"), PAPER("Paper"), FABRIC("Fabric");
        public final String label;
        Software(String label) { this.label = label; }
    }
    public enum State { STOPPED, INSTALLING, STARTING, RUNNING, STOPPING, CRASHED }
    public enum AuthMode { ONLINE, OFFLINE }
    public enum RamPreset { AUTO, LOW, BALANCED, PERFORMANCE }

    public String id = UUID.randomUUID().toString();
    public String name = "My Server";
    public Software software = Software.VANILLA;
    public String mcVersion = "1.20.4";
    public String softwareBuild = "";
    public int port = 25565;
    public String bindAddress = "0.0.0.0";
    public int maxPlayers = 5;
    public int viewDistance = 8;
    public int simulationDistance = 6;
    public int heapMb = 512;
    public RamPreset ramPreset = RamPreset.AUTO;
    public String jvmArgs = "";
    public AuthMode authMode = AuthMode.OFFLINE;
    public boolean eulaAccepted = false;
    public boolean publicAccessEnabled = false;
    public boolean whitelist = false;
    public String directoryName = "server_" + System.currentTimeMillis();
    public String preferredRuntimeName = "";
    public int requiredJavaMajor = 17;
    public long lastStartMs = 0;
    public long lastStopMs = 0;
    public long lastSuccessfulStartMs = 0;
    public String lastCrashSummary = "";
    public List<String> installedMods = new ArrayList<>();
    public List<String> installedPlugins = new ArrayList<>();
    public transient State runtimeState = State.STOPPED;
    public transient @Nullable String lastError;
    public transient long uptimeStartMs = 0;

    @NonNull
    public File resolveRoot(File serversRoot) { return new File(serversRoot, directoryName); }
    public File serverJar(File root) {
        if (software == Software.PAPER) return new File(root, "paper.jar");
        if (software == Software.FABRIC) return new File(root, "fabric-server-launch.jar");
        return new File(root, "server.jar");
    }
    public File propertiesFile(File root) { return new File(root, "server.properties"); }
    public File eulaFile(File root) { return new File(root, "eula.txt"); }
    public File worldDir(File root) { return new File(root, "world"); }
    public File modsDir(File root) { return new File(root, "mods"); }
    public File pluginsDir(File root) { return new File(root, "plugins"); }
    public File logsDir(File root) { return new File(root, "logs"); }
    public File backupsDir(File root) { return new File(root, "nexus_backups"); }
    public boolean isOnlineMode() { return authMode == AuthMode.ONLINE; }
}
