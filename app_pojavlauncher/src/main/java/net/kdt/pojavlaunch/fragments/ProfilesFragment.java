package net.kdt.pojavlaunch.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
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
import java.util.List;
import java.util.Map;

public class ProfilesFragment extends Fragment {
    public static final String TAG = "ProfilesFragment";

    private TextView mHeader;
    private ListView mList;
    private final List<String> mKeys = new ArrayList<>();
    private final List<MinecraftProfile> mProfiles = new ArrayList<>();

    public ProfilesFragment() { super(R.layout.fragment_profiles_nexus); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mHeader = view.findViewById(R.id.pf_header);
        mList = view.findViewById(R.id.pf_list);
        view.findViewById(R.id.pf_refresh).setOnClickListener(v -> refresh());
        view.findViewById(R.id.pf_create).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ProfileTypeSelectFragment.class, ProfileTypeSelectFragment.TAG, null));
        view.findViewById(R.id.pf_home).setOnClickListener(v -> Tools.backToMainMenu(requireActivity()));
        view.findViewById(R.id.pf_mods).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ContentLibraryFragment.class, ContentLibraryFragment.TAG,
                        ContentLibraryFragment.newInstance("mod").getArguments()));

        mList.setOnItemClickListener((p, v, pos, id) -> selectProfile(pos));
        mList.setOnItemLongClickListener((p, v, pos, id) -> {
            showActions(pos);
            return true;
        });
        refresh();
    }

    private void refresh() {
        mKeys.clear();
        mProfiles.clear();
        try {
            LauncherProfiles.load();
            Map<String, MinecraftProfile> map = LauncherProfiles.mainProfileJson.profiles;
            String current = LauncherPreferences.DEFAULT_PREF.getString(
                    LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
            List<String> labels = new ArrayList<>();
            for (Map.Entry<String, MinecraftProfile> e : map.entrySet()) {
                MinecraftProfile pr = e.getValue();
                if (pr == null) continue;
                mKeys.add(e.getKey());
                mProfiles.add(pr);
                boolean sel = e.getKey().equals(current);
                String name = Tools.isValidString(pr.name) ? pr.name : "(unnamed)";
                String ver = Tools.isValidString(pr.lastVersionId) ? pr.lastVersionId : "?";
                int mods = countMods(pr);
                labels.add((sel ? "★ " : "  ") + name + "\n" + ver
                        + " · mods " + mods
                        + (Tools.isValidString(pr.lastUsed) ? " · last " + pr.lastUsed : ""));
            }
            mHeader.setText(map.size() + " profiles · tap = select · long-press = actions");
            mList.setAdapter(new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_list_item_1, labels));
        } catch (Exception e) {
            mHeader.setText("Failed to load profiles: " + e.getMessage());
        }
    }

    private int countMods(MinecraftProfile pr) {
        try {
            File game = Tools.getGameDirPath(pr);
            File mods = new File(game, "mods");
            if (!mods.isDirectory()) return 0;
            File[] files = mods.listFiles((d, n) -> n.toLowerCase().endsWith(".jar"));
            return files != null ? files.length : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private void selectProfile(int pos) {
        if (pos < 0 || pos >= mKeys.size()) return;
        String key = mKeys.get(pos);
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, key)
                .apply();
        toast("Selected: " + (Tools.isValidString(mProfiles.get(pos).name)
                ? mProfiles.get(pos).name : key));
        refresh();
    }

    private void showActions(int pos) {
        if (pos < 0 || pos >= mKeys.size()) return;
        final String key = mKeys.get(pos);
        final MinecraftProfile pr = mProfiles.get(pos);
        String title = Tools.isValidString(pr.name) ? pr.name : key;
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setItems(new String[]{
                        "Select", "Open mods", "Open resource packs", "Open shaders",
                        "Duplicate", "Delete"
                }, (d, which) -> {
                    switch (which) {
                        case 0: selectProfile(pos); break;
                        case 1:
                            selectProfile(pos);
                            Tools.swapFragment(requireActivity(), ContentLibraryFragment.class,
                                    ContentLibraryFragment.TAG,
                                    ContentLibraryFragment.newInstance("mod").getArguments());
                            break;
                        case 2:
                            selectProfile(pos);
                            Tools.swapFragment(requireActivity(), ContentLibraryFragment.class,
                                    ContentLibraryFragment.TAG,
                                    ContentLibraryFragment.newInstance("resourcepack").getArguments());
                            break;
                        case 3:
                            selectProfile(pos);
                            Tools.swapFragment(requireActivity(), ContentLibraryFragment.class,
                                    ContentLibraryFragment.TAG,
                                    ContentLibraryFragment.newInstance("shader").getArguments());
                            break;
                        case 4: duplicateProfile(key, pr); break;
                        case 5: confirmDelete(key, title); break;
                    }
                })
                .show();
    }

    private void duplicateProfile(String key, MinecraftProfile pr) {
        try {
            MinecraftProfile copy = new MinecraftProfile(pr);
            copy.name = (Tools.isValidString(pr.name) ? pr.name : "Profile") + " (copy)";
            LauncherProfiles.load();
            LauncherProfiles.insertMinecraftProfile(copy);
            LauncherProfiles.write();
            toast("Duplicated");
            refresh();
        } catch (Exception e) {
            toast(e.getMessage());
        }
    }

    private void confirmDelete(String key, String title) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete profile?")
                .setMessage("Remove \"" + title + "\" from the profile list. Custom gameDir worlds are not auto-deleted.")
                .setPositiveButton("Delete", (d, w) -> {
                    try {
                        LauncherProfiles.load();
                        LauncherProfiles.mainProfileJson.profiles.remove(key);
                        String current = LauncherPreferences.DEFAULT_PREF.getString(
                                LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
                        if (key.equals(current) && !LauncherProfiles.mainProfileJson.profiles.isEmpty()) {
                            String first = LauncherProfiles.mainProfileJson.profiles.keySet().iterator().next();
                            LauncherPreferences.DEFAULT_PREF.edit()
                                    .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, first)
                                    .apply();
                        }
                        LauncherProfiles.write();
                        toast("Deleted");
                        refresh();
                    } catch (Exception e) {
                        toast(e.getMessage());
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void toast(String m) {
        Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show();
    }
}
