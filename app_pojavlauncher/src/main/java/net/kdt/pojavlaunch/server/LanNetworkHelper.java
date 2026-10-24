package net.kdt.pojavlaunch.server;

import androidx.annotation.Nullable;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.Collections;
import java.util.Enumeration;

public final class LanNetworkHelper {
    private LanNetworkHelper() {}

    @Nullable
    public static String getLanIpv4() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            if (ifs == null) return null;
            for (NetworkInterface nif : Collections.list(ifs)) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String host = addr.getHostAddress();
                        if (host != null && !host.startsWith("169.254.")) return host;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static boolean canConnect(String host, int port, int timeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
