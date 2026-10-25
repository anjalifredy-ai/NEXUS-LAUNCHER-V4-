package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InstalledModsFragment extends Fragment {
    public static final String TAG = "InstalledModsFragment";

    private TextView mHeader;
    private ListView mList;
    private final List<String> mNames = new ArrayList<>();
    private final List<Boolean> mDisabled = new ArrayList<>();
    private File mModsDir;
    private File mDisabledDir;

    public InstalledModsFragment() {
        super(R.layout.fragment_installed_mods);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mHeader = view.findViewById(R.id.installed_mods_header);
        mList = view.findViewById(R.id.installed_mods_list);
        Button refresh = view.findViewById(R.id.installed_mods_refresh);
        Button browse = view.findViewById(R.id.installed_mods_browse);
        Button back = view.findViewById(R.id.installed_mods_back);

        if (refresh != null) refresh.setOnClickListener(v -> reload());
        if (browse != null) browse.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ContentLibraryFragment.class, ContentLibraryFragment.TAG,
                        ContentLibraryFragment.newInstance("mod").getArguments()));
        if (back != null) back.setOnClickListener(v -> Tools.backToMainMenu(requireActivity()));

        mList.setOnItemLongClickListener((parent, v, position, id) -> {
            if (position < 0 || position >= mNames.size() || mModsDir == null) return true;
            String name = mNames.get(position);
            boolean wasDisabled = mDisabled.get(position);
            try {
                if (wasDisabled) {
                    File src = new File(mDisabledDir, name);
                    File dst = new File(mModsDir, name);
                    if (dst.exists()) throw new Exception("Target already exists: " + name);
                    if (!src.renameTo(dst)) throw new Exception("Enable failed");
                    toast("Enabled: " + name + " (restart game to apply)");
                } else {
                    if (mDisabledDir == null) throw new Exception("No disabled dir");
                    if (!mDisabledDir.exists() && !mDisabledDir.mkdirs())
                        throw new Exception("Cannot create disabled/");
                    File src = new File(mModsDir, name);
                    File dst = new File(mDisabledDir, name);
                    if (dst.exists()) throw new Exception("Already disabled: " + name);
                    if (!src.renameTo(dst)) throw new Exception("Disable failed");
                    toast("Disabled: " + name + " (restart game to apply)");
                }
                reload();
            } catch (Exception e) {
                toast(e.getMessage());
            }
            return true;
        });

        reload();
    }

    private void reload() {
        mModsDir = resolveModsDir();
        mDisabledDir = mModsDir != null ? new File(mModsDir, "disabled") : null;
        mNames.clear();
        mDisabled.clear();
        if (mModsDir != null && mModsDir.isDirectory()) {
            File[] files = mModsDir.listFiles((dir, name) -> {
                String n = name.toLowerCase();
                return n.endsWith(".jar") && !new File(dir, name).isDirectory();
            });
            if (files != null) {
                Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (File f : files) {
                    mNames.add(f.getName());
                    mDisabled.add(false);
                }
            }
        }
        if (mDisabledDir != null && mDisabledDir.isDirectory()) {
            File[] files = mDisabledDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
            if (files != null) {
                Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (File f : files) {
                    mNames.add(f.getName());
                    mDisabled.add(true);
                }
            }
        }
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < mNames.size(); i++) {
            labels.add((mDisabled.get(i) ? "[OFF] " : "[ON]  ") + mNames.get(i));
        }
        if (mHeader != null) {
            String path = mModsDir != null ? mModsDir.getAbsolutePath() : "(no profile)";
            mHeader.setText("Installed mods (" + mNames.size() + ")\n" + path
                    + "\nLong-press toggle · mods/disabled/ · restart game after change");
        }
        if (mList != null) {
            mList.setAdapter(new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_list_item_1, labels));
        }
    }

    private File resolveModsDir() {
        try {
            String currentProfile = LauncherPreferences.DEFAULT_PREF.getString(
                    LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
            if (!Tools.isValidString(currentProfile)) return new File(Tools.DIR_GAME_NEW, "mods");
            LauncherProfiles.load();
            MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(currentProfile);
            if (profile == null) return new File(Tools.DIR_GAME_NEW, "mods");
            File game = Tools.getGameDirPath(profile);
            File mods = new File(game, "mods");
            if (!mods.exists()) mods.mkdirs();
            return mods;
        } catch (Exception e) {
            return new File(Tools.DIR_GAME_NEW, "mods");
        }
    }

    private void toast(String m) {
        Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show();
    }
}
