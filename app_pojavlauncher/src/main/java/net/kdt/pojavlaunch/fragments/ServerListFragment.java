package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.server.ServerController;
import net.kdt.pojavlaunch.server.ServerProfile;

import java.util.ArrayList;
import java.util.List;

public class ServerListFragment extends Fragment implements ServerController.Listener {
    public static final String TAG = "ServerListFragment";

    private ListView mList;
    private TextView mEmpty;
    private final List<ServerProfile> mProfiles = new ArrayList<>();

    public ServerListFragment() { super(R.layout.fragment_server_list); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mList = view.findViewById(R.id.sl_list);
        mEmpty = view.findViewById(R.id.sl_empty);
        view.findViewById(R.id.sl_create).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ServerManagerFragment.class, ServerManagerFragment.TAG, null));
        view.findViewById(R.id.sl_refresh).setOnClickListener(v -> {
            ServerController.get().reloadCatalog();
            render();
        });
        view.findViewById(R.id.sl_home).setOnClickListener(v -> Tools.backToMainMenu(requireActivity()));
        mList.setOnItemClickListener((p, v, pos, id) ->
                Tools.swapFragment(requireActivity(), ServerManagerFragment.class, ServerManagerFragment.TAG, null));
        ServerController.get().reloadCatalog();
        render();
    }

    @Override public void onStart() { super.onStart(); ServerController.get().addListener(this); }
    @Override public void onStop() { ServerController.get().removeListener(this); super.onStop(); }

    private void render() {
        mProfiles.clear();
        mProfiles.addAll(ServerController.get().getProfiles());
        List<String> labels = new ArrayList<>();
        for (ServerProfile p : mProfiles) {
            String state = p.runtimeState != null ? p.runtimeState.name() : "STOPPED";
            labels.add(p.name + "\n" + p.software.label + " " + p.mcVersion + " · " + state
                    + (p.lastError != null ? " · " + p.lastError : ""));
        }
        mEmpty.setVisibility(mProfiles.isEmpty() ? View.VISIBLE : View.GONE);
        mList.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, labels));
    }

    @Override public void onStateChanged(@NonNull ServerProfile profile) {
        Tools.runOnUiThread(this::render);
    }
    @Override public void onLogLine(@NonNull String line) {}
    @Override public void onCatalogChanged() { Tools.runOnUiThread(this::render); }
    @Override public void onServerReady(@NonNull ServerProfile profile) {
        Tools.runOnUiThread(this::render);
    }
}
