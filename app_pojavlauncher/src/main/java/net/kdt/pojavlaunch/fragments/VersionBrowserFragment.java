package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.server.ClientVersionCatalog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VersionBrowserFragment extends Fragment {
    public static final String TAG = "VersionBrowserFragment";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private EditText mSearch;
    private CheckBox mRelease, mSnapshot, mInstalled;
    private TextView mStatus;
    private ListView mList;
    private List<ClientVersionCatalog.Entry> mAll = new ArrayList<>();

    public VersionBrowserFragment() { super(R.layout.fragment_version_browser); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mSearch = view.findViewById(R.id.vb_search);
        mRelease = view.findViewById(R.id.vb_release);
        mSnapshot = view.findViewById(R.id.vb_snapshot);
        mInstalled = view.findViewById(R.id.vb_installed);
        mStatus = view.findViewById(R.id.vb_status);
        mList = view.findViewById(R.id.vb_list);

        view.findViewById(R.id.vb_refresh).setOnClickListener(v -> load(true));
        view.findViewById(R.id.vb_home).setOnClickListener(v -> Tools.backToMainMenu(requireActivity()));
        mRelease.setOnCheckedChangeListener((b, c) -> applyFilter());
        mSnapshot.setOnCheckedChangeListener((b, c) -> applyFilter());
        mInstalled.setOnCheckedChangeListener((b, c) -> applyFilter());
        mSearch.setOnEditorActionListener((v, actionId, event) -> { applyFilter(); return true; });

        mList.setOnItemClickListener((parent, v, position, id) -> {
            Object item = parent.getItemAtPosition(position);
            if (item != null)
                Toast.makeText(requireContext(),
                        "Version: " + item + "\nUse Home profile spinner / profile editor to install & select.",
                        Toast.LENGTH_LONG).show();
        });

        load(false);
    }

    private void load(boolean force) {
        mStatus.setText(force ? "Refreshing from Mojang…" : "Loading…");
        io.execute(() -> {
            try {
                List<ClientVersionCatalog.Entry> list = ClientVersionCatalog.load(force);
                Tools.runOnUiThread(() -> {
                    mAll = list;
                    mStatus.setText(list.size() + " versions from official manifest");
                    applyFilter();
                });
            } catch (Exception e) {
                Tools.runOnUiThread(() -> mStatus.setText("Failed: " + e.getMessage()));
            }
        });
    }

    private void applyFilter() {
        List<ClientVersionCatalog.Entry> filtered = ClientVersionCatalog.filter(
                mAll,
                mSearch.getText() != null ? mSearch.getText().toString() : "",
                mRelease.isChecked(),
                mSnapshot.isChecked(),
                mInstalled.isChecked());
        List<String> labels = new ArrayList<>();
        for (ClientVersionCatalog.Entry e : filtered) {
            labels.add((e.installed ? "[IN] " : "[  ] ") + e.id + " · " + e.type);
        }
        mList.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, labels));
        mStatus.setText(filtered.size() + " shown · " + mAll.size() + " total");
    }
}
