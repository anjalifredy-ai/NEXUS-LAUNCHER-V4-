package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

public class ContentLibraryFragment extends Fragment {
    public static final String TAG = "ContentLibraryFragment";
    public static final String ARG_KIND = "lib_kind";

    private String mKind = "mod";
    private boolean mBrowse = true;

    public static ContentLibraryFragment newInstance(String kind) {
        ContentLibraryFragment f = new ContentLibraryFragment();
        Bundle b = new Bundle();
        b.putString(ARG_KIND, kind);
        f.setArguments(b);
        return f;
    }

    public ContentLibraryFragment() {
        super(R.layout.fragment_content_library);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle a = getArguments();
        if (a != null) mKind = a.getString(ARG_KIND, "mod");
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        TextView title = view.findViewById(R.id.lib_title);
        TextView sub = view.findViewById(R.id.lib_subtitle);
        Button tabBrowse = view.findViewById(R.id.lib_tab_browse);
        Button tabInstalled = view.findViewById(R.id.lib_tab_installed);
        Button home = view.findViewById(R.id.lib_btn_home);

        title.setText(titleFor(mKind));
        sub.setText(profileLine());

        tabBrowse.setOnClickListener(v -> {
            mBrowse = true;
            styleTabs(tabBrowse, tabInstalled);
            showChild();
        });
        tabInstalled.setOnClickListener(v -> {
            mBrowse = false;
            styleTabs(tabBrowse, tabInstalled);
            showChild();
        });
        home.setOnClickListener(v -> Tools.backToMainMenu(requireActivity()));

        styleTabs(tabBrowse, tabInstalled);
        showChild();
    }

    private void styleTabs(Button browse, Button installed) {
        browse.setTextColor(getResources().getColor(mBrowse ? R.color.nexus_accent : R.color.nexus_text_secondary));
        installed.setTextColor(getResources().getColor(!mBrowse ? R.color.nexus_accent : R.color.nexus_text_secondary));
    }

    private void showChild() {
        Fragment child;
        if (mBrowse) {
            child = SearchModFragment.newInstance(searchMode(mKind));
        } else if ("mod".equals(mKind)) {
            child = new InstalledModsFragment();
        } else if ("shader".equals(mKind)) {
            child = InstalledPacksFragment.newInstance("shaderpacks");
        } else {
            child = InstalledPacksFragment.newInstance("resourcepacks");
        }
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.lib_content, child)
                .commitAllowingStateLoss();
    }

    private static String titleFor(String kind) {
        if ("shader".equals(kind)) return "Shaders";
        if ("resourcepack".equals(kind)) return "Resource Packs";
        return "Mods Library";
    }

    private static String searchMode(String kind) {
        if ("shader".equals(kind)) return "shader";
        if ("resourcepack".equals(kind)) return "resourcepack";
        return "mod";
    }

    private String profileLine() {
        try {
            String id = LauncherPreferences.DEFAULT_PREF.getString(
                    LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
            if (!Tools.isValidString(id)) return "Profile: default";
            LauncherProfiles.load();
            MinecraftProfile p = LauncherProfiles.mainProfileJson.profiles.get(id);
            if (p == null) return "Profile: " + id;
            String name = p.name != null ? p.name : id;
            String ver = p.lastVersionId != null ? p.lastVersionId : "?";
            return "Profile: " + name + " · " + ver;
        } catch (Exception e) {
            return "Profile: —";
        }
    }
}
