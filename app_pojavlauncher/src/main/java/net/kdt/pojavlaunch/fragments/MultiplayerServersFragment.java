package net.kdt.pojavlaunch.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.server.SavedServerEntry;
import net.kdt.pojavlaunch.server.SavedServersStore;

import java.util.ArrayList;
import java.util.List;

public class MultiplayerServersFragment extends Fragment {
    public static final String TAG = "MultiplayerServersFragment";

    private EditText mName, mAddress;
    private ListView mList;
    private final List<SavedServerEntry> mEntries = new ArrayList<>();
    private int mSelected = -1;

    public MultiplayerServersFragment() { super(R.layout.fragment_multiplayer_servers); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mName = view.findViewById(R.id.mp_name);
        mAddress = view.findViewById(R.id.mp_address);
        mList = view.findViewById(R.id.mp_list);

        view.findViewById(R.id.mp_btn_add).setOnClickListener(v -> add());
        view.findViewById(R.id.mp_btn_copy).setOnClickListener(v -> copySelected());
        view.findViewById(R.id.mp_btn_delete).setOnClickListener(v -> deleteSelected());
        view.findViewById(R.id.mp_btn_back).setOnClickListener(v -> Tools.backToMainMenu(requireActivity()));

        mList.setOnItemClickListener((p, v, position, id) -> {
            mSelected = position;
            if (position >= 0 && position < mEntries.size()) {
                SavedServerEntry e = mEntries.get(position);
                mName.setText(e.name);
                mAddress.setText(e.port != 25565 ? e.address + ":" + e.port : e.address);
            }
        });

        reload();
    }

    private void reload() {
        mEntries.clear();
        mEntries.addAll(SavedServersStore.load());
        List<String> labels = new ArrayList<>();
        for (SavedServerEntry e : mEntries) {
            String addr = e.port != 25565 ? e.address + ":" + e.port : e.address;
            labels.add(e.name + " — " + addr);
        }
        mList.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, labels));
        mSelected = -1;
    }

    private void add() {
        String name = mName.getText().toString().trim();
        String raw = mAddress.getText().toString().trim();
        if (raw.isEmpty()) { toast("Enter address"); return; }
        SavedServerEntry e = SavedServersStore.createDefault();
        e.name = name.isEmpty() ? raw : name;
        if (raw.contains(":")) {
            int idx = raw.lastIndexOf(':');
            e.address = raw.substring(0, idx).trim();
            try { e.port = Integer.parseInt(raw.substring(idx + 1).trim()); } catch (Exception ignored) {}
        } else {
            e.address = raw;
        }
        mEntries.add(e);
        try {
            SavedServersStore.save(mEntries);
            reload();
            toast("Saved");
        } catch (Exception ex) { toast(ex.getMessage()); }
    }

    private void copySelected() {
        if (mSelected < 0 || mSelected >= mEntries.size()) { toast("Select a server"); return; }
        SavedServerEntry e = mEntries.get(mSelected);
        String addr = e.port != 25565 ? e.address + ":" + e.port : e.address;
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("server", addr));
        toast("Copied: " + addr);
    }

    private void deleteSelected() {
        if (mSelected < 0 || mSelected >= mEntries.size()) { toast("Select a server"); return; }
        mEntries.remove(mSelected);
        try {
            SavedServersStore.save(mEntries);
            reload();
            toast("Deleted");
        } catch (Exception e) { toast(e.getMessage()); }
    }

    private void toast(String m) {
        Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show();
    }
}
