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
        if (!ServerPaths.isSafeDirectoryName(profile.directoryName))
            throw new Exception("Unsafe server directory name");
        File root = profile.resolveRoot(ServerPaths.serversRoot());
        if (!root.exists() && !root.mkdirs())
            throw new Exception("Cannot create server directory: " + root.getAbsolutePath());
        if (!profile.eulaAccepted)
            throw new Exception("EULA must be accepted before install/start");
        profile.requiredJavaMajor = ServerVersionCatalog.requiredJavaMajor(profile.mcVersion);
        writeEula(profile.eulaFile(root));
        ServerPropertiesEditor.writeCore(profile);

        if (profile.software == ServerProfile.Software.VANILLA) installVanilla(profile, root, progress);
        else if (profile.software == ServerProfile.Software.PAPER) installPaper(profile, root, progress);
        else if (profile.software == ServerProfile.Software.FABRIC) installFabric(profile, root, progress);
        else throw new Exception("Unsupported server software");

        profile.modsDir(root).mkdirs();
        profile.pluginsDir(root).mkdirs();
        profile.backupsDir(root).mkdirs();
    }

    private static void installVanilla(ServerProfile profile, File root, Progress progress) throws Exception {
        if (progress != null) progress.onStatus("Fetching Mojang version manifest");
        JsonObject manifest = JsonParser.parseString(DownloadUtils.downloadString(MANIFEST)).getAsJsonObject();
        String versionUrl = null;
        for (JsonElement el : manifest.getAsJsonArray("versions")) {
            JsonObject v = el.getAsJsonObject();
            if (profile.mcVersion.equals(v.get("id").getAsString())) {
                versionUrl = v.get("url").getAsString();
                break;
            }
        }
        if (versionUrl == null) throw new Exception("Minecraft version not found: " + profile.mcVersion);
        if (progress != null) progress.onStatus("Fetching version metadata");
        JsonObject versionJson = JsonParser.parseString(DownloadUtils.downloadString(versionUrl)).getAsJsonObject();
        JsonObject downloads = versionJson.getAsJsonObject("downloads");
        if (downloads == null || !downloads.has("server"))
            throw new Exception("No official server jar for " + profile.mcVersion);
        JsonObject server = downloads.getAsJsonObject("server");
        String url = server.get("url").getAsString();
        String sha1 = server.has("sha1") ? server.get("sha1").getAsString() : null;
        File jar = profile.serverJar(root);
        if (progress != null) progress.onStatus("Downloading Vanilla server.jar");
        DownloadUtils.downloadFile(url, jar);
        if (sha1 != null && !sha1.isEmpty()) {
            if (!sha1.equalsIgnoreCase(hashFile(jar, "SHA-1"))) {
                jar.delete();
                throw new Exception("SHA-1 mismatch for server.jar");
            }
        }
        profile.softwareBuild = "";
        Log.i(TAG, "Installed vanilla server");
    }

    private static void installPaper(ServerProfile profile, File root, Progress progress) throws Exception {
        String ver = profile.mcVersion;
        if (progress != null) progress.onStatus("Querying PaperMC builds for " + ver);
        String buildsUrl = "https://api.papermc.io/v2/projects/paper/versions/" + ver + "/builds";
        JsonObject buildsJson;
        try {
            buildsJson = JsonParser.parseString(DownloadUtils.downloadString(buildsUrl)).getAsJsonObject();
        } catch (Exception e) {
            throw new Exception("Paper unavailable for " + ver + ": " + e.getMessage());
        }
        JsonArray builds = buildsJson.getAsJsonArray("builds");
        if (builds == null || builds.size() == 0) throw new Exception("No Paper builds for " + ver);
        JsonObject chosen = builds.get(builds.size() - 1).getAsJsonObject();
        if (profile.softwareBuild != null && !profile.softwareBuild.isEmpty()) {
            int want = Integer.parseInt(profile.softwareBuild);
            chosen = null;
            for (JsonElement el : builds) {
                JsonObject b = el.getAsJsonObject();
                if (b.get("build").getAsInt() == want) { chosen = b; break; }
            }
            if (chosen == null) throw new Exception("Paper build " + want + " not found");
        }
        int build = chosen.get("build").getAsInt();
        JsonObject application = chosen.getAsJsonObject("downloads").getAsJsonObject("application");
        String fileName = application.get("name").getAsString();
        String sha256 = application.has("sha256") ? application.get("sha256").getAsString() : null;
        String url = "https://api.papermc.io/v2/projects/paper/versions/" + ver + "/builds/" + build + "/downloads/" + fileName;
        File jar = profile.serverJar(root);
        if (progress != null) progress.onStatus("Downloading Paper build " + build);
        DownloadUtils.downloadFile(url, jar);
        if (sha256 != null && !sha256.equalsIgnoreCase(hashFile(jar, "SHA-256"))) {
            jar.delete();
            throw new Exception("SHA-256 mismatch for paper.jar");
        }
        profile.softwareBuild = String.valueOf(build);
        Log.i(TAG, "Installed Paper build " + build);
    }

    private static void installFabric(ServerProfile profile, File root, Progress progress) throws Exception {
        String game = profile.mcVersion;
        String loader = profile.softwareBuild;
        if (loader == null || loader.isEmpty()) {
            if (progress != null) progress.onStatus("Resolving latest Fabric loader");
            loader = ServerVersionCatalog.latestFabricLoader();
            profile.softwareBuild = loader;
        }
        String installerVersion = "1.0.1";
        try {
            JsonArray arr = JsonParser.parseString(
                    DownloadUtils.downloadString("https://meta.fabricmc.net/v2/versions/installer")).getAsJsonArray();
            if (arr.size() > 0) installerVersion = arr.get(0).getAsJsonObject().get("version").getAsString();
        } catch (Exception ignored) {}
        String url = "https://meta.fabricmc.net/v2/versions/loader/"
                + game + "/" + loader + "/" + installerVersion + "/server/jar";
        File jar = profile.serverJar(root);
        if (progress != null) progress.onStatus("Downloading Fabric server (loader " + loader + ")");
        try {
            DownloadUtils.downloadFile(url, jar);
        } catch (Exception e) {
            throw new Exception("Fabric server download failed for " + game + " / " + loader + ": " + e.getMessage());
        }
        if (!jar.isFile() || jar.length() < 1000) {
            jar.delete();
            throw new Exception("Fabric server jar missing or too small");
        }
        Log.i(TAG, "Installed Fabric server loader " + loader);
    }

    public static void writeEula(File eula) throws Exception {
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(eula), StandardCharsets.UTF_8)) {
            w.write("# Mojang EULA accepted via Nexus Launcher\n");
            w.write("eula=true\n");
        }
    }

    public static boolean isInstalled(ServerProfile profile) {
        File jar = profile.serverJar(profile.resolveRoot(ServerPaths.serversRoot()));
        return jar.isFile() && jar.length() > 1000;
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
