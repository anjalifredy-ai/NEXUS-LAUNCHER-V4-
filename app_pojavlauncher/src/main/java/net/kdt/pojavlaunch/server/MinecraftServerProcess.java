package net.kdt.pojavlaunch.server;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs server.jar via MultiRT bin/java as a real OS process. */
public class MinecraftServerProcess {
    private static final String TAG = "NexusServerProc";

    public interface LogListener {
        void onLogLine(String line);
        void onProcessExited(int exitCode);
    }

    private final ServerProfile profile;
    private final int heapMb;
    private final String runtimeName;
    private @Nullable Process process;
    private @Nullable OutputStreamWriter stdin;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<LogListener> listeners = new CopyOnWriteArrayList<>();
    private final List<String> logBuffer = new ArrayList<>();
    private static final int LOG_CAP = 500;

    public MinecraftServerProcess(@NonNull ServerProfile profile, int heapMb, @NonNull String runtimeName) {
        this.profile = profile;
        this.heapMb = heapMb;
        this.runtimeName = runtimeName;
    }

    public void addListener(LogListener l) { listeners.add(l); }
    public void removeListener(LogListener l) { listeners.remove(l); }
    public boolean isRunning() { return running.get() && process != null && process.isAlive(); }

    public synchronized List<String> snapshotLogs() { return new ArrayList<>(logBuffer); }

    public synchronized void start() throws Exception {
        if (isRunning()) throw new Exception("Server already running");
        File root = profile.resolveRoot(ServerPaths.serversRoot());
        File jar = profile.serverJar(root);
        if (!jar.isFile()) throw new Exception("Server jar missing. Install first: " + jar.getAbsolutePath());

        File javaBin = ServerPaths.javaBinary(runtimeName);
        Runtime rt = MultiRTUtils.read(runtimeName);
        if (rt == null || !javaBin.isFile()) {
            throw new Exception("Java runtime/binary missing: " + runtimeName + " at " + javaBin.getAbsolutePath()
                    + ". Install JRE 17+ in Runtime Manager.");
        }
        if (!javaBin.canExecute()) //noinspection ResultOfMethodCallIgnored
            javaBin.setExecutable(true);

        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin.getAbsolutePath());
        cmd.add("-Xms" + Math.min(256, heapMb) + "M");
        cmd.add("-Xmx" + heapMb + "M");
        cmd.add("-XX:+UseG1GC");
        cmd.add("-XX:+ParallelRefProcEnabled");
        cmd.add("-XX:MaxGCPauseMillis=200");
        cmd.add("-Djava.awt.headless=true");
        cmd.add("-jar");
        cmd.add(jar.getAbsolutePath());
        cmd.add("nogui");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(root);
        pb.redirectErrorStream(true);
        String ld = System.getenv("LD_LIBRARY_PATH");
        File libDir = new File(javaBin.getParentFile().getParentFile(), "lib");
        String extra = libDir.getAbsolutePath();
        if (ld == null || ld.isEmpty()) pb.environment().put("LD_LIBRARY_PATH", extra);
        else pb.environment().put("LD_LIBRARY_PATH", extra + ":" + ld);

        Log.i(TAG, "Starting: " + cmd);
        process = pb.start();
        stdin = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        running.set(true);
        Thread t = new Thread(this::readLoop, "nexus-server-log");
        t.setDaemon(true);
        t.start();
        Thread waiter = new Thread(() -> {
            try {
                int code = process.waitFor();
                running.set(false);
                emitExit(code);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "nexus-server-wait");
        waiter.setDaemon(true);
        waiter.start();
    }

    public synchronized void sendCommand(@NonNull String command) throws Exception {
        if (!isRunning() || stdin == null) throw new Exception("Server is not running");
        stdin.write(command);
        if (!command.endsWith("\n")) stdin.write("\n");
        stdin.flush();
        appendLog("> " + command.trim());
    }

    public void stopGracefully(long timeoutMs) {
        try {
            if (isRunning()) sendCommand("stop");
        } catch (Exception ignored) {}
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (isRunning() && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        if (isRunning() && process != null) {
            process.destroy();
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            if (process.isAlive()) process.destroyForcibly();
        }
        running.set(false);
    }

    private void readLoop() {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) appendLog(line);
        } catch (Exception e) {
            appendLog("[log reader ended: " + e.getMessage() + "]");
        }
    }

    private synchronized void appendLog(String line) {
        logBuffer.add(line);
        while (logBuffer.size() > LOG_CAP) logBuffer.remove(0);
        for (LogListener l : listeners) {
            try { l.onLogLine(line); } catch (Throwable ignored) {}
        }
    }

    private void emitExit(int code) {
        for (LogListener l : listeners) {
            try { l.onProcessExited(code); } catch (Throwable ignored) {}
        }
    }
}
