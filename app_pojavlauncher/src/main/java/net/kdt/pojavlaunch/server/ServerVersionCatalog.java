package net.kdt.pojavlaunch.server;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ServerVersionCatalog {
    private static final String MOJANG_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String PAPER_PROJECT = "https://fill.papermc.io/v3/projects/paper";
    private static final String FABRIC_GAME = "https://meta.fabricmc.net/v2/versions/game";
    private static final String FABRIC_LOADER = "https://meta.fabricmc.net/v2/versions/loader";

    private ServerVersionCatalog() {}

    public static final class VersionEntry {
        public final String id;
        public final String type;
        public VersionEntry(String id, String type) { this.id = id; this.type = type; }
        @NonNull @Override public String toString() { return id + (type != null ? " (" + type + ")" : ""); }
    }

    public static final class PaperBuild {
        public final int build;
        public final String version;
        public final String downloadUrl;
        public final String sha256;
        public PaperBuild(int build, String version, String downloadUrl, String sha256) {
            this.build = build;
            this.version = version;
            this.downloadUrl = downloadUrl;
            this.sha256 = sha256;
        }
    }

    @NonNull
    public static List<VersionEntry> listVanillaReleases(boolean includeSnapshots) throws Exception {
        JsonObject manifest = JsonParser.parseString(DownloadUtils.downloadString(MOJANG_MANIFEST)).getAsJsonObject();
        List<VersionEntry> out = new ArrayList<>();
        for (JsonElement el : manifest.getAsJsonArray("versions")) {
            JsonObject v = el.getAsJsonObject();
            String type = v.get("type").getAsString();
            if (!includeSnapshots && !"release".equals(type)) continue;
            out.add(new VersionEntry(v.get("id").getAsString(), type));
        }
        return out;
    }

    @NonNull
    public static List<String> listPaperVersions() throws Exception {
        String raw = DownloadUtils.downloadString(PAPER_PROJECT);
        JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
        JsonObject versions = root.getAsJsonObject("versions");
        List<String> out = new ArrayList<>();
        if (versions == null) return out;
        for (Map.Entry<String, JsonElement> family : versions.entrySet()) {
            JsonArray arr = family.getValue().getAsJsonArray();
            for (JsonElement el : arr) {
                String id = el.getAsString();
                if (!out.contains(id)) out.add(id);
            }
        }
        return out;
    }

    @NonNull
    public static PaperBuild latestPaperBuild(String mcVersion) throws Exception {
        String url = PAPER_PROJECT + "/versions/" + mcVersion + "/builds/latest";
        JsonObject root = JsonParser.parseString(DownloadUtils.downloadString(url)).getAsJsonObject();
        if (root.has("ok") && !root.get("ok").getAsBoolean()) {
            throw new Exception(root.has("message") ? root.get("message").getAsString() : "Paper build not found");
        }
        int id = root.get("id").getAsInt();
        JsonObject downloads = root.getAsJsonObject("downloads");
        JsonObject server = downloads.has("server:default")
                ? downloads.getAsJsonObject("server:default") : null;
        if (server == null) {
            for (Map.Entry<String, JsonElement> e : downloads.entrySet()) {
                server = e.getValue().getAsJsonObject();
                break;
            }
        }
        if (server == null) throw new Exception("No Paper server download for " + mcVersion);
        String dl = server.get("url").getAsString();
        String sha = null;
        if (server.has("checksums") && server.getAsJsonObject("checksums").has("sha256"))
            sha = server.getAsJsonObject("checksums").get("sha256").getAsString();
        return new PaperBuild(id, mcVersion, dl, sha);
    }

    @NonNull
    public static List<VersionEntry> listFabricGameVersions(boolean stableOnly) throws Exception {
        JsonArray arr = JsonParser.parseString(DownloadUtils.downloadString(FABRIC_GAME)).getAsJsonArray();
        List<VersionEntry> out = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            boolean stable = o.has("stable") && o.get("stable").getAsBoolean();
            if (stableOnly && !stable) continue;
            out.add(new VersionEntry(o.get("version").getAsString(), stable ? "release" : "snapshot"));
        }
        return out;
    }

    @NonNull
    public static String latestFabricLoader() throws Exception {
        JsonArray arr = JsonParser.parseString(DownloadUtils.downloadString(FABRIC_LOADER)).getAsJsonArray();
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("stable") && o.get("stable").getAsBoolean()) return o.get("version").getAsString();
        }
        if (arr.size() > 0) return arr.get(0).getAsJsonObject().get("version").getAsString();
        throw new Exception("No Fabric loader versions available");
    }

    public static int requiredJavaMajor(String mcVersion) {
        try {
            String[] p = mcVersion.split("\\.");
            int major = Integer.parseInt(p[0]);
            int minor = p.length > 1 ? Integer.parseInt(p[1].replaceAll("[^0-9].*", "")) : 0;
            int patch = 0;
            if (p.length > 2) {
                try { patch = Integer.parseInt(p[2].replaceAll("[^0-9].*", "")); } catch (Exception ignored) {}
            }
            if (major >= 26) return 21;
            if (major >= 1 && minor >= 21) return 21;
            if (major >= 1 && minor == 20 && patch >= 5) return 21;
            if (major >= 1 && minor >= 18) return 17;
            if (major >= 1 && minor >= 17) return 16;
            return 8;
        } catch (Exception e) {
            return 17;
        }
    }
}
