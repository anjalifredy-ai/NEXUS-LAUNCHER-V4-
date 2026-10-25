package net.kdt.pojavlaunch.server;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class ServerRuntimePicker {
    private ServerRuntimePicker() {}

    public static final class Choice {
        public final String name;
        public final int javaMajor;
        public final File javaBinary;
        public Choice(String name, int javaMajor, File javaBinary) {
            this.name = name;
            this.javaMajor = javaMajor;
            this.javaBinary = javaBinary;
        }
    }

    @NonNull
    public static Choice pick(@NonNull ServerProfile profile) throws Exception {
        int required = profile.requiredJavaMajor > 0
                ? profile.requiredJavaMajor
                : ServerVersionCatalog.requiredJavaMajor(profile.mcVersion);
        profile.requiredJavaMajor = required;

        List<Runtime> runtimes = MultiRTUtils.getRuntimes();
        if (runtimes == null || runtimes.isEmpty()) {
            throw new Exception("No Java runtime installed. Open Runtime Manager and install JRE "
                    + required + "+ (Android MultiRT).");
        }

        if (profile.preferredRuntimeName != null && !profile.preferredRuntimeName.isEmpty()) {
            Choice c = fromName(profile.preferredRuntimeName);
            if (c != null && c.javaMajor >= required) return c;
        }

        String exact = MultiRTUtils.getExactJreName(required);
        if (exact != null) {
            Choice c = fromName(exact);
            if (c != null) return c;
        }
        String nearest = MultiRTUtils.getNearestJreName(required);
        if (nearest != null) {
            Choice c = fromName(nearest);
            if (c != null && c.javaMajor >= required) return c;
        }

        Choice best = null;
        for (Runtime r : runtimes) {
            if (r.javaVersion >= required) {
                File bin = ServerPaths.javaBinary(r.name);
                if (bin.isFile()) {
                    if (best == null || r.javaVersion < best.javaMajor) {
                        best = new Choice(r.name, r.javaVersion, bin);
                    }
                }
            }
        }
        if (best != null) return best;

        List<String> names = new ArrayList<>();
        for (Runtime r : runtimes) names.add(r.name + " (Java " + r.javaVersion + ")");
        throw new Exception("Need Java " + required + "+ for MC " + profile.mcVersion
                + ". Installed: " + names);
    }

    @Nullable
    private static Choice fromName(String name) {
        try {
            Runtime r = MultiRTUtils.read(name);
            if (r == null) return null;
            File bin = ServerPaths.javaBinary(name);
            if (!bin.isFile()) return null;
            if (!bin.canExecute()) bin.setExecutable(true);
            return new Choice(name, r.javaVersion, bin);
        } catch (Exception e) {
            return null;
        }
    }
}
