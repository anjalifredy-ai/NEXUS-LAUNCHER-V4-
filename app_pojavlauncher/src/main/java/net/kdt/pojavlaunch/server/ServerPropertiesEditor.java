package net.kdt.pojavlaunch.server;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ServerPropertiesEditor {
    private ServerPropertiesEditor() {}

    @NonNull
    public static Map<String, String> load(File propsFile) throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        if (!propsFile.isFile()) return map;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(propsFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                map.put(line.substring(0, eq), line.substring(eq + 1));
            }
        }
        return map;
    }

    public static void save(File propsFile, Map<String, String> map) throws Exception {
        File parent = propsFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(propsFile), StandardCharsets.UTF_8)) {
            w.write("# Nexus Launcher managed server.properties\n");
            for (Map.Entry<String, String> e : map.entrySet()) {
                w.write(e.getKey());
                w.write('=');
                w.write(e.getValue() != null ? e.getValue() : "");
                w.write('\n');
            }
        }
    }

    public static void writeCore(@NonNull ServerProfile profile) throws Exception {
        File root = profile.resolveRoot(ServerPaths.serversRoot());
        File props = profile.propertiesFile(root);
        Map<String, String> map = load(props);
        map.put("server-port", String.valueOf(profile.port));
        map.put("server-ip", profile.bindAddress != null ? profile.bindAddress : "0.0.0.0");
        map.put("max-players", String.valueOf(profile.maxPlayers));
        map.put("view-distance", String.valueOf(profile.viewDistance));
        map.put("simulation-distance", String.valueOf(profile.simulationDistance));
        map.put("online-mode", profile.isOnlineMode() ? "true" : "false");
        map.put("white-list", profile.whitelist ? "true" : "false");
        map.put("enforce-whitelist", profile.whitelist ? "true" : "false");
        map.put("motd", "Nexus Hosted Server");
        map.put("enable-status", "true");
        map.put("spawn-protection", "0");
        map.put("sync-chunk-writes", "true");
        map.put("max-tick-time", "60000");
        map.put("enforce-secure-profile", profile.isOnlineMode() ? "true" : "false");
        save(props, map);
    }

    public static void applyAuthMode(@NonNull ServerProfile profile) throws Exception {
        writeCore(profile);
    }
}
