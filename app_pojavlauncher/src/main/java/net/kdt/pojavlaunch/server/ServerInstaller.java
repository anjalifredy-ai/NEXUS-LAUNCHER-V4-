package net.kdt.pojavlaunch.server;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class ServerInstaller {
    private static final String TAG = "NexusServerInstall";
    private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    public interface Progress {
        void onStatus(String message);
        void onProgress(long current, long total);
    }

    private ServerInstaller() {}

    public static void install(@NonNull ServerProfile profile, @Nullable Progress progress) throws Exception {
        File root = profile.resolveRoot(ServerPaths.serversRoot());
        if (!root.exists() && !root.mkdirs()) {
            throw new Exception("Cannot create server directory: " + root.getAbsolutePath());
        }
        if (!profile.eulaAccepted) {
            throw new Exception("EULA must be accepted before install/start");
        }
        writeEula(profile.eulaFile(root));
        ensureProperties(profile);
        if (profile.software == ServerProfile.Software.VANILLA) {
            installVanilla(profile, root, progress);
        } else if (profile.software == ServerProfile.Software.PAPER) {
            installPaper(profile, root, progress);
        } else {
            throw new Exception("Unsupported server software");
        }
    }

    private static void installVanilla(ServerProfile profile, File root, Progress progress) throws Exception {
        if (progress != null) progress.onStatus("Fetching Mojang version manifest");
        String manifestJson = DownloadUtils.downloadString(MANIFEST);
        JsonObject manifest = JsonParser.parseString(manifestJson).getAsJsonObject();
        String versionUrl = null;
        JsonArray versions = manifest.getAsJsonArray("versions");
        for (JsonElement el : versions) {
            JsonObject v = el.getAsJsonObject();
            if (profile.mcVersion.equals(v.get("id").getAsString())) {
                versionUrl = v.get("url").getAsString();
                break;
            }
        }
        if (versionUrl == null) {
            throw new Exception("Minecraft version not found in Mojang manifest: " + profile.mcVersion);
        }
        if (progress != null) progress.onStatus("Fetching version metadata");
        JsonObject versionJson = JsonParser.parseString(DownloadUtils.downloadString(versionUrl)).getAsJsonObject();
        JsonObject downloads = versionJson.getAsJsonObject("downloads");
        if (downloads == null || !downloads.has("server")) {
            throw new Exception("No official server jar for " + profile.mcVersion);
        }
        JsonObject server = downloads.getAsJsonObject("server");
        String url = server.get("url").getAsString();
        String sha1 = server.has("sha1") ? server.get("sha1").getAsString() : null;
        File jar = profile.serverJar(root);
        if (progress != null) progress.onStatus("Downloading Vanilla server.jar");
        DownloadUtils.downloadFile(url, jar);
        if (sha1 != null && !sha1.isEmpty()) {
            String actual = hashFile(jar, "SHA-1");
            if (!sha1.equalsIgnoreCase(actual)) {
                jar.delete();
                throw new Exception("SHA-1 mismatch for server.jar");
            }
        }
        Log.i(TAG, "Installed vanilla server to " + jar.getAbsolutePath());
    }

    private static void installPaper(ServerProfile profile, File root, Progress progress) throws Exception {
        String ver = profile.mcVersion;
        if (progress != null) progress.onStatus("Querying PaperMC builds for " + ver);
        String buildsUrl = "https://api.papermc.io/v2/projects/paper/versions/" + ver + "/builds";
        JsonObject buildsJson;
        try {
            buildsJson = JsonParser.parseString(DownloadUtils.downloadString(buildsUrl)).getAsJsonObject();
        } catch (Exception e) {
            throw new Exception("Paper has no builds for " + ver + " or API unreachable: " + e.getMessage());
        }
        JsonArray builds = buildsJson.getAsJsonArray("builds");
        if (builds == null || builds.size() == 0) {
            throw new Exception("No Paper builds for " + ver);
        }
        JsonObject latest = builds.get(builds.size() - 1).getAsJsonObject();
        int build = latest.get("build").getAsInt();
        JsonObject downloads = latest.getAsJsonObject("downloads");
        JsonObject application = downloads.getAsJsonObject("application");
        String fileName = application.get("name").getAsString();
        String sha256 = application.has("sha256") ? application.get("sha256").getAsString() : null;
        String url = "https://api.papermc.io/v2/projects/paper/versions/" + ver + "/builds/" + build + "/downloads/" + fileName;
        File jar = profile.serverJar(root);
        if (progress != null) progress.onStatus("Downloading Paper build " + build);
        DownloadUtils.downloadFile(url, jar);
        if (sha256 != null) {
            String actual = hashFile(jar, "SHA-256");
            if (!sha256.equalsIgnoreCase(actual)) {
                jar.delete();
                throw new Exception("SHA-256 mismatch for paper.jar");
            }
        }
        Log.i(TAG, "Installed Paper build " + build);
    }

    public static void writeEula(File eula) throws Exception {
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(eula), StandardCharsets.UTF_8)) {
            w.write("# By changing the setting below to TRUE you are indicating your agreement to Mojang EULA.\n");
            w.write("eula=true\n");
        }
    }

    public static void ensureProperties(ServerProfile profile) throws Exception {
        File root = profile.resolveRoot(ServerPaths.serversRoot());
        File props = profile.propertiesFile(root);
        if (!root.exists()) root.mkdirs();
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(props), StandardCharsets.UTF_8)) {
            w.write("server-port=" + profile.port + "\n");
            w.write("max-players=" + profile.maxPlayers + "\n");
            w.write("view-distance=" + profile.viewDistance + "\n");
            w.write("simulation-distance=" + profile.simulationDistance + "\n");
            w.write("online-mode=false\n");
            w.write("spawn-protection=0\n");
            w.write("motd=Nexus Hosted Server\n");
            w.write("enable-status=true\n");
            w.write("white-list=false\n");
            w.write("enforce-secure-profile=false\n");
            w.write("sync-chunk-writes=true\n");
            w.write("max-tick-time=60000\n");
        }
    }

    private static String hashFile(File f, String algo) throws Exception {
        MessageDigest md = MessageDigest.getInstance(algo);
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        byte[] dig = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : dig) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
