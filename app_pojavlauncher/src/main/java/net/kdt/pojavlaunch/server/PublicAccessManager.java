package net.kdt.pojavlaunch.server;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class PublicAccessManager {
    public enum Status {
        DISABLED, CHECKING, DIRECT_UNLIKELY, NEEDS_RELAY, CONNECTING, ONLINE, ERROR
    }

    public static final class Snapshot {
        public Status status = Status.DISABLED;
        public @Nullable String publicHost;
        public int publicPort = -1;
        public @Nullable String externalIp;
        public @Nullable String message;
        public boolean offlineModeWarning;
    }

    private final Snapshot snapshot = new Snapshot();

    @NonNull
    public synchronized Snapshot getSnapshot() {
        Snapshot c = new Snapshot();
        c.status = snapshot.status;
        c.publicHost = snapshot.publicHost;
        c.publicPort = snapshot.publicPort;
        c.externalIp = snapshot.externalIp;
        c.message = snapshot.message;
        c.offlineModeWarning = snapshot.offlineModeWarning;
        return c;
    }

    public synchronized void disable() {
        snapshot.status = Status.DISABLED;
        snapshot.publicHost = null;
        snapshot.publicPort = -1;
        snapshot.message = "Public access disabled. LAN only.";
        snapshot.offlineModeWarning = false;
    }

    public synchronized Snapshot requestPublic(@NonNull String lanIp, int lanPort, boolean offlineMode) {
        snapshot.status = Status.CHECKING;
        snapshot.offlineModeWarning = offlineMode;
        snapshot.publicHost = null;
        snapshot.publicPort = -1;
        String ext = null;
        try { ext = fetchExternalIp(); snapshot.externalIp = ext; }
        catch (Exception e) { snapshot.externalIp = null; }
        snapshot.status = Status.NEEDS_RELAY;
        StringBuilder msg = new StringBuilder();
        msg.append("Local server listening on ").append(lanIp).append(':').append(lanPort).append(". ");
        if (ext != null) {
            msg.append("External IP appears to be ").append(ext)
                    .append(" — this does NOT mean inbound Minecraft ports are open (CGNAT common on mobile). ");
        } else {
            msg.append("Could not detect external IP. ");
        }
        msg.append("For friends outside Wi-Fi, use a TCP relay agent (playit.gg / Cloudflare Tunnel) ")
                .append("and set the hostname:port it provides. Nexus keeps the game server local.");
        if (offlineMode) {
            msg.append(" WARNING: offline-mode ON — enable whitelist before public exposure.");
        }
        snapshot.message = msg.toString();
        return getSnapshot();
    }

    /** User-supplied relay address after external agent is configured. */
    public synchronized Snapshot setVerifiedRelay(@NonNull String host, int port) {
        if (host.isEmpty() || host.contains(" ")) {
            snapshot.status = Status.ERROR;
            snapshot.message = "Invalid relay hostname";
            return getSnapshot();
        }
        snapshot.status = Status.ONLINE;
        snapshot.publicHost = host;
        snapshot.publicPort = port;
        snapshot.message = "Relay set by user: " + host + ":" + port + " — verify friends can join.";
        return getSnapshot();
    }

    @Nullable
    private static String fetchExternalIp() {
        String[] endpoints = { "https://api.ipify.org", "https://ifconfig.me/ip" };
        for (String ep : endpoints) {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(ep).openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(4000);
                c.setRequestProperty("User-Agent", "NexusLauncher");
                try (Scanner sc = new Scanner(c.getInputStream()).useDelimiter("\\A")) {
                    if (sc.hasNext()) {
                        String ip = sc.next().trim();
                        if (ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) return ip;
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}
