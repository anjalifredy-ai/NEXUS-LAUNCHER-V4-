package net.kdt.pojavlaunch.server;

import net.kdt.pojavlaunch.Tools;

import java.io.File;

public final class ServerPaths {
    private ServerPaths() {}

    public static File serversRoot() {
        File root = new File(Tools.DIR_GAME_HOME, "nexus_servers");
        if (!root.exists()) //noinspection ResultOfMethodCallIgnored
            root.mkdirs();
        return root;
    }

    public static File profilesJson() {
        return new File(serversRoot(), "servers_index.json");
    }

    public static File javaBinary(String runtimeName) {
        return new File(Tools.MULTIRT_HOME + "/" + runtimeName + "/bin/java");
    }

    public static boolean isSafeDirectoryName(String name) {
        if (name == null || name.isEmpty()) return false;
        if (name.contains("..") || name.contains("/") || name.contains("\\")) return false;
        if (name.startsWith(".")) return false;
        return name.matches("[A-Za-z0-9_\\-.]{1,64}");
    }
}
