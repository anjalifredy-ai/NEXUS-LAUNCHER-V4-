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

public class InstalledPacksFragment extends Fragment {
    public static final String TAG = "InstalledPacksFragment";
    public static final String ARG_KIND = "kind";

    private String mKind = "resourcepacks";
    private TextView mHeader;
    private ListView mList;
    private final List<String> mNames = new ArrayList<>();
    private File mDir;

    public static InstalledPacksFragment newInstance(String kind) {
        InstalledPacksFragment f = new InstalledPacksFragment();
        Bundle b = new Bundle();
        b.putString(ARG_KIND, kind);
        f.setArguments(b);
        return f;
    }

    public InstalledPacksFragment() { super(R.layout.fragment_installed_packs); }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle a = getArguments();
        if (a != null) mKind = a.getString(ARG_KIND, "resourcepacks");
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mHeader = view.findViewById(R.id.installed_packs_header);
        mList = view.findViewById(R.id.installed_packs_list);
        Button refresh = view.findViewById(R.id.installed_packs_refresh);
        Button browse = view.findViewById(R.id.installed_packs_browse);
        Button back = view.findViewById(R.id.installed_packs_back);

        if (refresh != null) refresh.setOnClickListener(v -> reload());
        if (browse != null) browse.setOnClickListener(v -> {
            String mode = "shaderpacks".equals(mKind) ? "shader" : "resourcepack";
            Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG,
                    SearchModFragment.newInstance(mode).getArguments());
        });
        if (back != null) back.setOnClickListener(v -> Tools.backToMainMenu(requireActivity()));

        mList.setOnItemLongClickListener((parent, v, position, id) -> {
            if (position < 0 || position >= mNames.size() || mDir == null) return true;
            File f = new File(mDir, mNames.get(position));
            if (!f.exists()) return true;
            try {
                if (f.isDirectory()) deleteTree(f);
                else if (!f.delete()) throw new Exception("delete failed");
                toast("Removed");
                reload();
            } catch (Exception e) { toast(e.getMessage()); }
            return true;
        });

        reload();
    }

    private void reload() {
        mDir = resolveDir();
        mNames.clear();
        if (mDir != null && mDir.isDirectory()) {
            File[] files = mDir.listFiles();
            if (files != null) {
                Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (File f : files) {
                    String n = f.getName();
                    if (n.startsWith(".")) continue;
                    mNames.add(n);
                }
            }
        }
        if (mHeader != null) {
            mHeader.setText(mKind + " (" + mNames.size() + ")\n"
                    + (mDir != null ? mDir.getAbsolutePath() : "")
                    + "\nLong-press to delete");
        }
        if (mList != null) {
            mList.setAdapter(new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_list_item_1, mNames));
        }
    }

    private File resolveDir() {
        try {
            String currentProfile = LauncherPreferences.DEFAULT_PREF.getString(
                    LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
            File game;
            if (!Tools.isValidString(currentProfile)) game = new File(Tools.DIR_GAME_NEW);
            else {
                LauncherProfiles.load();
                MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(currentProfile);
                game = profile == null ? new File(Tools.DIR_GAME_NEW) : Tools.getGameDirPath(profile);
            }
            File dir = new File(game, mKind);
            if (!dir.exists()) dir.mkdirs();
            return dir;
        } catch (Exception e) {
            return new File(Tools.DIR_GAME_NEW, mKind);
        }
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    private void toast(String m) {
        Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show();
    }
}
