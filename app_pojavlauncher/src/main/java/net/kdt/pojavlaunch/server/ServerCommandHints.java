package net.kdt.pojavlaunch.server;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

public final class ServerCommandHints {
    private ServerCommandHints() {}

    @NonNull
    public static List<String> forSoftware(ServerProfile.Software soft) {
        if (soft == ServerProfile.Software.PAPER) {
            return Arrays.asList(
                    "list", "say Hello", "save-all", "stop", "whitelist list",
                    "paper version", "timings report", "plugins"
            );
        }
        if (soft == ServerProfile.Software.FABRIC) {
            return Arrays.asList("list", "say Hello", "save-all", "stop", "whitelist list");
        }
        return Arrays.asList(
                "list", "say Hello", "save-all", "stop", "whitelist list", "time query daytime"
        );
    }
}
