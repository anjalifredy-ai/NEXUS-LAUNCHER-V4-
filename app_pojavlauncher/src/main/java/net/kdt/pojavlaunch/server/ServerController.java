package net.kdt.pojavlaunch.server;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ServerController {
    private static final String TAG = "NexusServerCtrl";
    private static final ServerController INSTANCE = new ServerController();

    public interface Listener {
        void onStateChanged(@NonNull ServerProfile profile);
        void onLogLine(@NonNull String line);
        void onCatalogChanged();
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final PublicAccessManager publicAccess = new PublicAccessManager();
    private @Nullable MinecraftServerProcess activeProcess;
    private @Nullable String activeProfileId;
    private List<ServerProfile> profiles = new ArrayList<>();

    private ServerController() {}
    public static ServerController get() { return INSTANCE; }

    public void addListener(Listener l) { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public synchronized void reloadCatalog() {
        profiles = ServerCatalog.load();
        for (Listener l : listeners) l.onCatalogChanged();
    }

    public synchronized List<ServerProfile> getProfiles() { return new ArrayList<>(profiles); }

    public synchronized @Nullable ServerProfile find(String id) {
        for (ServerProfile p : profiles) if (p.id.equals(id)) return p;
        return null;
    }

    public synchronized void upsert(ServerProfile p) throws Exception {
        boolean found = false;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id.equals(p.id)) { profiles.set(i, p); found = true; break; }
        }
        if (!found) profiles.add(p);
        ServerCatalog.save(profiles);
        for (Listener l : listeners) l.onCatalogChanged();
    }

    public synchronized void delete(String id) throws Exception {
        if (id.equals(activeProfileId) && activeProcess != null && activeProcess.isRunning()) {
            throw new Exception("Stop the server before deleting it");
        }
        ServerProfile victim = find(id);
        profiles.removeIf(p -> p.id.equals(id));
        ServerCatalog.save(profiles);
        if (victim != null) deleteTree(victim.resolveRoot(ServerPaths.serversRoot()));
        for (Listener l : listeners) l.onCatalogChanged();
    }

    public PublicAccessManager publicAccess() { return publicAccess; }
    public @Nullable String getActiveProfileId() { return activeProfileId; }
    public boolean isRunning() { return activeProcess != null && activeProcess.isRunning(); }

    public void installAsync(@NonNull Context ctx, @NonNull String profileId) {
        io.execute(() -> {
            ServerProfile p = find(profileId);
            if (p == null) return;
            try {
                p.runtimeState = ServerProfile.State.INSTALLING;
                notifyState(p);
                ServerInstaller.install(p, new ServerInstaller.Progress() {
                    @Override public void onStatus(String message) { emitLog(message); }
                    @Override public void onProgress(long current, long total) {}
                });
                p.runtimeState = ServerProfile.State.STOPPED;
                p.lastError = null;
                upsert(p);
                notifyState(p);
                emitLog("Install complete.");
            } catch (Exception e) {
                Log.e(TAG, "install failed", e);
                p.runtimeState = ServerProfile.State.CRASHED;
                p.lastError = e.getMessage();
                notifyState(p);
                emitLog("Install error: " + e.getMessage());
            }
        });
    }

    public void startAsync(@NonNull Context ctx, @NonNull String profileId) {
        io.execute(() -> {
            try { startBlocking(ctx, profileId); }
            catch (Exception e) {
                Log.e(TAG, "start failed", e);
                ServerProfile p = find(profileId);
                if (p != null) {
                    p.runtimeState = ServerProfile.State.CRASHED;
                    p.lastError = e.getMessage();
                    notifyState(p);
                }
                emitLog("Start error: " + e.getMessage());
            }
        });
    }

    private void startBlocking(Context ctx, String profileId) throws Exception {
        if (isRunning()) throw new Exception("A server is already running. Stop it first.");
        ServerProfile p = find(profileId);
        if (p == null) throw new Exception("Unknown profile");
        if (!p.eulaAccepted) throw new Exception("Accept the Minecraft EULA first");

        AdaptiveRamManager.Snapshot ram = AdaptiveRamManager.inspect(ctx);
        int heap = AdaptiveRamManager.clampHeap(p.heapMb, ram.maxSafeHeapMb);
        if (ram.availMemMb < 400) {
            throw new Exception("Available memory too low (" + ram.availMemMb + " MB).");
        }
        if (ram.freeStorageMb >= 0 && ram.freeStorageMb < 512) {
            throw new Exception("Free storage too low (" + ram.freeStorageMb + " MB).");
        }

        if (!p.serverJar(p.resolveRoot(ServerPaths.serversRoot())).isFile()) {
            p.runtimeState = ServerProfile.State.INSTALLING;
            notifyState(p);
            ServerInstaller.install(p, msg -> emitLog(msg));
        }
        ServerInstaller.ensureProperties(p);

        String runtimeName = pickRuntimeName();
        p.runtimeState = ServerProfile.State.STARTING;
        notifyState(p);

        MinecraftServerProcess proc = new MinecraftServerProcess(p, heap, runtimeName);
        proc.addListener(new MinecraftServerProcess.LogListener() {
            @Override public void onLogLine(String line) { emitLog(line); }
            @Override public void onProcessExited(int exitCode) {
                p.runtimeState = exitCode == 0 ? ServerProfile.State.STOPPED : ServerProfile.State.CRASHED;
                if (exitCode != 0) p.lastError = "Process exited with code " + exitCode;
                activeProcess = null;
                activeProfileId = null;
                notifyState(p);
                try {
                    Intent stop = new Intent(ctx, ServerHostService.class);
                    stop.setAction(ServerHostService.ACTION_STOP);
                    ctx.startService(stop);
                } catch (Throwable ignored) {}
            }
        });
        proc.start();
        activeProcess = proc;
        activeProfileId = p.id;
        p.runtimeState = ServerProfile.State.RUNNING;
        p.lastError = null;
        notifyState(p);

        Intent fg = new Intent(ctx, ServerHostService.class);
        fg.setAction(ServerHostService.ACTION_START);
        fg.putExtra(ServerHostService.EXTRA_PROFILE_ID, p.id);
        fg.putExtra(ServerHostService.EXTRA_PORT, p.port);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(fg);
        else ctx.startService(fg);
    }

    public void stopAsync(@NonNull Context ctx) {
        io.execute(() -> {
            MinecraftServerProcess proc = activeProcess;
            ServerProfile p = activeProfileId != null ? find(activeProfileId) : null;
            if (p != null) { p.runtimeState = ServerProfile.State.STOPPING; notifyState(p); }
            if (proc != null) proc.stopGracefully(20000);
            activeProcess = null;
            activeProfileId = null;
            if (p != null) { p.runtimeState = ServerProfile.State.STOPPED; notifyState(p); }
            try {
                Intent stop = new Intent(ctx, ServerHostService.class);
                stop.setAction(ServerHostService.ACTION_STOP);
                ctx.startService(stop);
            } catch (Throwable ignored) {}
        });
    }

    public void sendCommand(String cmd) throws Exception {
        if (activeProcess == null || !activeProcess.isRunning()) throw new Exception("Server not running");
        activeProcess.sendCommand(cmd);
    }

    private String pickRuntimeName() throws Exception {
        List<Runtime> runtimes = MultiRTUtils.getRuntimes();
        if (runtimes == null || runtimes.isEmpty()) {
            throw new Exception("No Java runtime installed. Install JRE 17+ in Runtime Manager.");
        }
        String name = MultiRTUtils.getNearestJreName(17);
        if (name == null) name = MultiRTUtils.getNearestJreName(8);
        if (name == null) name = runtimes.get(0).name;
        return name;
    }

    private void notifyState(ServerProfile p) {
        for (Listener l : listeners) {
            try { l.onStateChanged(p); } catch (Throwable ignored) {}
        }
    }

    private void emitLog(String line) {
        for (Listener l : listeners) {
            try { l.onLogLine(line); } catch (Throwable ignored) {}
        }
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }
}
