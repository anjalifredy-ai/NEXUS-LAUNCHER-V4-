package net.kdt.pojavlaunch.server;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class MinecraftServerProcess {
    private static final String TAG = "NexusServerProc";

    public interface LogListener {
        void onLogLine(String line);
        void onProcessExited(int exitCode);
        void onServerReady();
    }

    private final ServerProfile profile;
    private final int heapMb;
    private final String javaBinaryPath;
    private final String extraJvmArgs;
    private @Nullable Process process;
    private @Nullable OutputStreamWriter stdin;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<LogListener> listeners = new CopyOnWriteArrayList<>();
    private final List<String> logBuffer = new ArrayList<>();
    private static final int LOG_CAP = 400;
    private final AtomicReference<String> lastCrashHint = new AtomicReference<>(null);

    public MinecraftServerProcess(@NonNull ServerProfile profile, int heapMb,
                                  @NonNull String javaBinaryPath, @Nullable String extraJvmArgs) {
        this.profile = profile;
        this.heapMb = heapMb;
        this.javaBinaryPath = javaBinaryPath;
        this.extraJvmArgs = extraJvmArgs != null ? extraJvmArgs : "";
    }

    public void addListener(LogListener l) { listeners.add(l); }
    public void removeListener(LogListener l) { listeners.remove(l); }
    public boolean isRunning() { return running.get() && process != null && process.isAlive(); }
    public boolean isReady() { return ready.get() && isRunning(); }
    @Nullable public String getLastCrashHint() { return lastCrashHint.get(); }
    public synchronized List<String> snapshotLogs() { return new ArrayList<>(logBuffer); }

    public synchronized void start() throws Exception {
        if (isRunning()) throw new Exception("Server already running");
        File root = profile.resolveRoot(ServerPaths.serversRoot());
        File jar = profile.serverJar(root);
        if (!jar.isFile()) throw new Exception("Server jar missing. Install first: " + jar.getAbsolutePath());
        File javaBin = new File(javaBinaryPath);
        if (!javaBin.isFile()) throw new Exception("java binary missing: " + javaBinaryPath);
        if (!javaBin.canExecute()) javaBin.setExecutable(true);

        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin.getAbsolutePath());
        cmd.add("-Xms" + Math.min(256, heapMb) + "M");
        cmd.add("-Xmx" + heapMb + "M");
        cmd.add("-XX:+UseG1GC");
        cmd.add("-XX:+ParallelRefProcEnabled");
        cmd.add("-XX:MaxGCPauseMillis=200");
        cmd.add("-Djava.awt.headless=true");
        if (!extraJvmArgs.trim().isEmpty()) {
            for (String a : extraJvmArgs.trim().split("\\s+")) {
                if (!a.isEmpty() && !a.startsWith("-Xmx") && !a.startsWith("-Xms")) cmd.add(a);
            }
        }
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
        ready.set(false);
        lastCrashHint.set(null);
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
                ready.set(false);
                for (LogListener l : listeners) {
                    try { l.onProcessExited(code); } catch (Throwable ignored) {}
                }
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
        try { if (isRunning()) sendCommand("stop"); } catch (Exception ignored) {}
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
        ready.set(false);
    }

    private void readLoop() {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                appendLog(line);
                detectSignals(line);
            }
        } catch (Exception e) {
            appendLog("[log reader ended: " + e.getMessage() + "]");
        }
    }

    private void detectSignals(String line) {
        String lower = line.toLowerCase();
        if (!ready.get() && (lower.contains("done (") || lower.contains("done!")
                || (lower.contains("for help") && lower.contains("type")))) {
            ready.set(true);
            for (LogListener l : listeners) {
                try { l.onServerReady(); } catch (Throwable ignored) {}
            }
        }
        if (lower.contains("outofmemory") || lower.contains("java.lang.outofmemoryerror"))
            lastCrashHint.set("OutOfMemoryError — lower heap or view-distance");
        else if (lower.contains("address already in use") || lower.contains("bind exception"))
            lastCrashHint.set("Port bind failed — change port or stop other server");
        else if (lower.contains("unsupportedclassversionerror"))
            lastCrashHint.set("Wrong Java version — install a newer MultiRT JRE");
    }

    private synchronized void appendLog(String line) {
        logBuffer.add(line);
        while (logBuffer.size() > LOG_CAP) logBuffer.remove(0);
        for (LogListener l : listeners) {
            try { l.onLogLine(line); } catch (Throwable ignored) {}
        }
    }
}
