package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.server.ServerController;

public class NexusHubFragment extends Fragment {
    public static final String TAG = "NexusHubFragment";

    public NexusHubFragment() {
        super(R.layout.fragment_nexus_hub);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        TextView status = view.findViewById(R.id.nexus_hub_status);
        String serverStatus = ServerController.get().isRunning()
                ? (ServerController.get().isReady() ? "Server: READY" : "Server: RUNNING")
                : "Server: stopped";
        status.setText(serverStatus + "\nNexus Launcher V4");

        setNav(view, R.id.hub_btn_versions, () ->
                Tools.swapFragment(requireActivity(), VersionBrowserFragment.class, VersionBrowserFragment.TAG, null));
        setNav(view, R.id.hub_btn_downloads, () ->
                Tools.swapFragment(requireActivity(), DownloadsFragment.class, DownloadsFragment.TAG, null));
        setNav(view, R.id.hub_btn_mods, () ->
                Tools.swapFragment(requireActivity(), ContentLibraryFragment.class, ContentLibraryFragment.TAG,
                        ContentLibraryFragment.newInstance("mod").getArguments()));
        setNav(view, R.id.hub_btn_installed_mods, () ->
                Tools.swapFragment(requireActivity(), ContentLibraryFragment.class, ContentLibraryFragment.TAG,
                        ContentLibraryFragment.newInstance("mod").getArguments()));
        setNav(view, R.id.hub_btn_modpacks, () ->
                Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG,
                        SearchModFragment.newInstance("modpack").getArguments()));
        setNav(view, R.id.hub_btn_resourcepacks, () ->
                Tools.swapFragment(requireActivity(), ContentLibraryFragment.class, ContentLibraryFragment.TAG,
                        ContentLibraryFragment.newInstance("resourcepack").getArguments()));
        setNav(view, R.id.hub_btn_installed_rp, () ->
                Tools.swapFragment(requireActivity(), ContentLibraryFragment.class, ContentLibraryFragment.TAG,
                        ContentLibraryFragment.newInstance("resourcepack").getArguments()));
        setNav(view, R.id.hub_btn_shaders, () ->
                Tools.swapFragment(requireActivity(), ContentLibraryFragment.class, ContentLibraryFragment.TAG,
                        ContentLibraryFragment.newInstance("shader").getArguments()));
        setNav(view, R.id.hub_btn_installed_shaders, () ->
                Tools.swapFragment(requireActivity(), ContentLibraryFragment.class, ContentLibraryFragment.TAG,
                        ContentLibraryFragment.newInstance("shader").getArguments()));
        setNav(view, R.id.hub_btn_server_list, () ->
                Tools.swapFragment(requireActivity(), ServerListFragment.class, ServerListFragment.TAG, null));
        setNav(view, R.id.hub_btn_servers, () ->
                Tools.swapFragment(requireActivity(), ServerManagerFragment.class, ServerManagerFragment.TAG, null));
        setNav(view, R.id.hub_btn_multiplayer, () ->
                Tools.swapFragment(requireActivity(), MultiplayerServersFragment.class, MultiplayerServersFragment.TAG, null));
        setNav(view, R.id.hub_btn_plugins, () ->
                Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG,
                        SearchModFragment.newInstance("plugin").getArguments()));
        setNav(view, R.id.hub_btn_perf, () ->
                Tools.swapFragment(requireActivity(), PerformanceLabFragment.class, PerformanceLabFragment.TAG, null));
        setNav(view, R.id.hub_btn_diag, () ->
                Tools.swapFragment(requireActivity(), DiagnosticsFragment.class, DiagnosticsFragment.TAG, null));
        setNav(view, R.id.hub_btn_profiles, () ->
                Tools.swapFragment(requireActivity(), ProfilesFragment.class, ProfilesFragment.TAG, null));
        setNav(view, R.id.hub_btn_accounts, () ->
                Tools.swapFragment(requireActivity(), SelectAuthFragment.class, SelectAuthFragment.TAG, null));
        setNav(view, R.id.hub_btn_voice, () ->
                Tools.swapFragment(requireActivity(), FriendsVoiceFragment.class, FriendsVoiceFragment.TAG, null));
        setNav(view, R.id.hub_btn_settings, () ->
                Tools.swapFragment(requireActivity(), LauncherPreferenceFragment.class, "SETTINGS_FRAGMENT", null));
        setNav(view, R.id.hub_btn_home, () -> Tools.backToMainMenu(requireActivity()));
    }

    private void setNav(View root, int id, Runnable action) {
        View v = root.findViewById(id);
        if (v != null) v.setOnClickListener(x -> action.run());
    }
}
