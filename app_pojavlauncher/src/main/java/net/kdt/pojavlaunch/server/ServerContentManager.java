package net.kdt.pojavlaunch.server;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class ServerContentManager {
    private ServerContentManager() {}

    public static final class InstalledFile {
        public final String fileName;
        public final long sizeBytes;
        public final boolean enabled;
        public InstalledFile(String fileName, long sizeBytes, boolean enabled) {
            this.fileName = fileName;
            this.sizeBytes = sizeBytes;
            this.enabled = enabled;
        }
    }

    @NonNull
    public static List<InstalledFile> listMods(@NonNull ServerProfile profile) {
        return listJars(profile.modsDir(profile.resolveRoot(ServerPaths.serversRoot())));
    }

    @NonNull
    public static List<InstalledFile> listPlugins(@NonNull ServerProfile profile) {
        return listJars(profile.pluginsDir(profile.resolveRoot(ServerPaths.serversRoot())));
    }

    private static List<InstalledFile> listJars(File dir) {
        List<InstalledFile> out = new ArrayList<>();
        if (!dir.isDirectory()) return out;
        File[] files = dir.listFiles();
        if (files == null) return out;
        for (File f : files) {
            String n = f.getName();
            if (n.endsWith(".jar")) out.add(new InstalledFile(n, f.length(), true));
            else if (n.endsWith(".jar.disabled")) out.add(new InstalledFile(n, f.length(), false));
        }
        return out;
    }

    public static File installMod(@NonNull ServerProfile profile, @NonNull String url, @NonNull String fileName) throws Exception {
        if (profile.software != ServerProfile.Software.FABRIC) {
            throw new Exception("Fabric mods only apply to Fabric servers (current: " + profile.software + ")");
        }
        return downloadInto(profile.modsDir(profile.resolveRoot(ServerPaths.serversRoot())), url, fileName);
    }

    public static File installPlugin(@NonNull ServerProfile profile, @NonNull String url, @NonNull String fileName) throws Exception {
        if (profile.software != ServerProfile.Software.PAPER) {
            throw new Exception("Plugins only apply to Paper servers (current: " + profile.software + ")");
        }
        return downloadInto(profile.pluginsDir(profile.resolveRoot(ServerPaths.serversRoot())), url, fileName);
    }

    public static void setEnabled(@NonNull File jarOrDisabled, boolean enabled) throws Exception {
        String n = jarOrDisabled.getName();
        File target;
        if (enabled) {
            if (!n.endsWith(".jar.disabled")) return;
            target = new File(jarOrDisabled.getParentFile(), n.substring(0, n.length() - ".disabled".length()));
        } else {
            if (!n.endsWith(".jar")) return;
            target = new File(jarOrDisabled.getParentFile(), n + ".disabled");
        }
        if (!jarOrDisabled.renameTo(target)) throw new Exception("Could not rename " + n);
    }

    public static void delete(@NonNull File f) throws Exception {
        if (!f.delete()) throw new Exception("Could not delete " + f.getName());
    }

    private static File downloadInto(File dir, String url, String fileName) throws Exception {
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new Exception("Unsafe file name");
        }
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Cannot create " + dir);
        File out = new File(dir, fileName);
        DownloadUtils.downloadFile(url, out);
        if (!out.isFile() || out.length() < 10) {
            out.delete();
            throw new Exception("Download failed or empty: " + fileName);
        }
        return out;
    }
}
