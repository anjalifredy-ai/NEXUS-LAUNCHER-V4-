package net.kdt.pojavlaunch.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class ServerCatalog {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<ServerProfile>>(){}.getType();

    private ServerCatalog() {}

    public static synchronized List<ServerProfile> load() {
        File f = ServerPaths.profilesJson();
        if (!f.isFile()) return new ArrayList<>();
        try (FileReader r = new FileReader(f)) {
            List<ServerProfile> list = GSON.fromJson(r, LIST_TYPE);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static synchronized void save(List<ServerProfile> profiles) throws Exception {
        File f = ServerPaths.profilesJson();
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        try (FileWriter w = new FileWriter(f)) {
            GSON.toJson(profiles, LIST_TYPE, w);
        }
    }
}
