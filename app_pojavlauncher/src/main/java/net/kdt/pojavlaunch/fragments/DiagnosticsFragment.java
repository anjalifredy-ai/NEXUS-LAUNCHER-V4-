package net.kdt.pojavlaunch.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.server.NexusDiagnostics;

import java.io.File;

public class DiagnosticsFragment extends Fragment {
    public static final String TAG = "DiagnosticsFragment";

    private TextView mBody;
    private String mReport = "";

    public DiagnosticsFragment() { super(R.layout.fragment_diagnostics); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mBody = view.findViewById(R.id.diag_body);
        view.findViewById(R.id.diag_refresh).setOnClickListener(v -> refresh());
        view.findViewById(R.id.diag_copy).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("nexus-diag", mReport));
            Toast.makeText(requireContext(), "Copied", Toast.LENGTH_SHORT).show();
        });
        view.findViewById(R.id.diag_export).setOnClickListener(v -> {
            try {
                File f = NexusDiagnostics.exportToFile(requireContext());
                Toast.makeText(requireContext(), "Exported: " + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        view.findViewById(R.id.diag_home).setOnClickListener(v -> Tools.backToMainMenu(requireActivity()));
        refresh();
    }

    private void refresh() {
        mReport = NexusDiagnostics.buildReport(requireContext());
        mBody.setText(mReport);
    }
}
