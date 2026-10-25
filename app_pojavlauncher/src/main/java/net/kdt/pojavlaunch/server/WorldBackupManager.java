package net.kdt.pojavlaunch.server;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class WorldBackupManager {
    private WorldBackupManager() {}

    public static final class BackupEntry {
        public final String name;
        public final File file;
        public final long sizeBytes;
        public final long modifiedMs;
        public BackupEntry(String name, File file, long sizeBytes, long modifiedMs) {
            this.name = name; this.file = file; this.sizeBytes = sizeBytes; this.modifiedMs = modifiedMs;
        }
    }

    @NonNull
    public static List<BackupEntry> list(@NonNull ServerProfile profile) {
        File dir = profile.backupsDir(profile.resolveRoot(ServerPaths.serversRoot()));
        List<BackupEntry> out = new ArrayList<>();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".zip"));
        if (files != null) for (File f : files) out.add(new BackupEntry(f.getName(), f, f.length(), f.lastModified()));
        return out;
    }

    @NonNull
    public static File create(@NonNull ServerProfile profile) throws Exception {
        File root = profile.resolveRoot(ServerPaths.serversRoot());
        File world = profile.worldDir(root);
        if (!world.isDirectory()) throw new Exception("No world directory yet");
        File backups = profile.backupsDir(root);
        if (!backups.exists() && !backups.mkdirs()) throw new Exception("Cannot create backups directory");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File zip = new File(backups, "world_" + stamp + ".zip");
        zipDir(world, world.getName(), zip);
        return zip;
    }

    public static void restore(@NonNull ServerProfile profile, @NonNull File backupZip) throws Exception {
        if (ServerController.get().isRunning()
                && profile.id.equals(ServerController.get().getActiveProfileId())) {
            throw new Exception("Stop the server before restoring a backup");
        }
        File root = profile.resolveRoot(ServerPaths.serversRoot());
        File world = profile.worldDir(root);
        if (world.exists()) {
            File aside = new File(root, "world_pre_restore_" + System.currentTimeMillis());
            if (!world.renameTo(aside)) throw new Exception("Could not move existing world aside");
        }
        unzipTo(backupZip, root);
    }

    private static void zipDir(File source, String baseName, File zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            addRecursive(source, baseName, zos);
        }
    }

    private static void addRecursive(File file, String entryName, ZipOutputStream zos) throws IOException {
        if (file.isDirectory()) {
            File[] kids = file.listFiles();
            if (kids == null || kids.length == 0) {
                zos.putNextEntry(new ZipEntry(entryName + "/"));
                zos.closeEntry();
                return;
            }
            for (File k : kids) {
                if ("session.lock".equals(k.getName())) continue;
                addRecursive(k, entryName + "/" + k.getName(), zos);
            }
        } else {
            zos.putNextEntry(new ZipEntry(entryName));
            try (FileInputStream in = new FileInputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
            }
            zos.closeEntry();
        }
    }

    private static void unzipTo(File zipFile, File destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                File out = new File(destDir, entry.getName());
                String destPath = destDir.getCanonicalPath();
                String outPath = out.getCanonicalPath();
                if (!outPath.startsWith(destPath + File.separator) && !outPath.equals(destPath))
                    throw new IOException("Zip entry escapes destination: " + entry.getName());
                if (entry.isDirectory()) out.mkdirs();
                else {
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        int n;
                        while ((n = zis.read(buf)) > 0) fos.write(buf, 0, n);
                    }
                }
                zis.closeEntry();
            }
        }
    }
}
