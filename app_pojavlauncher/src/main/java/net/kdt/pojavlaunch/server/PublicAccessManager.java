package net.kdt.pojavlaunch.server;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Public internet exposure for friends outside LAN.
 * Direct inbound ports are almost never available on mobile CGNAT.
 * A real relay must be configured; we never embed private API secrets or invent hostnames.
 */
public class PublicAccessManager {
    public enum Status {
        DISABLED,
        NEEDS_RELAY,
        CONNECTING,
        ONLINE,
        ERROR
    }

    public static final class Snapshot {
        public Status status = Status.DISABLED;
        public @Nullable String publicHost;
        public int publicPort = -1;
        public @Nullable String message;
    }

    private final Snapshot snapshot = new Snapshot();

    @NonNull
    public synchronized Snapshot getSnapshot() {
        Snapshot c = new Snapshot();
        c.status = snapshot.status;
        c.publicHost = snapshot.publicHost;
        c.publicPort = snapshot.publicPort;
        c.message = snapshot.message;
        return c;
    }

    public synchronized void disable() {
        snapshot.status = Status.DISABLED;
        snapshot.publicHost = null;
        snapshot.publicPort = -1;
        snapshot.message = "Public access disabled. LAN only.";
    }

    public synchronized Snapshot requestPublic(@NonNull String lanIp, int lanPort) {
        snapshot.status = Status.NEEDS_RELAY;
        snapshot.publicHost = null;
        snapshot.publicPort = -1;
        snapshot.message = "Direct port-forward is usually blocked by mobile CGNAT. "
                + "Install a relay agent (e.g. playit.gg) on this device and link it later. "
                + "LAN address remains " + lanIp + ":" + lanPort + " for same-Wi-Fi players.";
        return getSnapshot();
    }
}
