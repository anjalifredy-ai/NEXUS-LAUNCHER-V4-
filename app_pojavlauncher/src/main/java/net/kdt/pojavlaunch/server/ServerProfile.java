package net.kdt.pojavlaunch.server;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.UUID;

public class ServerProfile {
    public enum Software {
        VANILLA("Vanilla"),
        PAPER("Paper");
        public final String label;
        Software(String label) { this.label = label; }
    }

    public enum State {
        STOPPED, INSTALLING, STARTING, RUNNING, STOPPING, CRASHED
    }

    public String id = UUID.randomUUID().toString();
    public String name = "My Server";
    public Software software = Software.VANILLA;
    public String mcVersion = "1.20.4";
    public int port = 25565;
    public int maxPlayers = 5;
    public int viewDistance = 8;
    public int simulationDistance = 6;
    public int heapMb = 512;
    public boolean eulaAccepted = false;
    public boolean publicAccessEnabled = false;
    public String directoryName = "server_" + System.currentTimeMillis();
    public transient State runtimeState = State.STOPPED;
    public transient @Nullable String lastError;

    @NonNull
    public File resolveRoot(File serversRoot) {
        return new File(serversRoot, directoryName);
    }

    public File serverJar(File root) {
        return new File(root, software == Software.PAPER ? "paper.jar" : "server.jar");
    }

    public File propertiesFile(File root) {
        return new File(root, "server.properties");
    }

    public File eulaFile(File root) {
        return new File(root, "eula.txt");
    }

    public File worldDir(File root) {
        return new File(root, "world");
    }
}
