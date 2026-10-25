package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.math.MathUtils;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.modloaders.modpacks.ModItemAdapter;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;

public class SearchModFragment extends Fragment implements ModItemAdapter.SearchResultCallback {

    public static final String TAG = "SearchModFragment";
    public static final String ARG_MODE = "nexus_search_mode";

    public static SearchModFragment newInstance(String mode) {
        SearchModFragment f = new SearchModFragment();
        Bundle b = new Bundle();
        b.putString(ARG_MODE, mode != null ? mode : "modpack");
        f.setArguments(b);
        return f;
    }

    private View mOverlay;
    private float mOverlayTopCache;

    private final RecyclerView.OnScrollListener mOverlayPositionListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            mOverlay.setY(MathUtils.clamp(mOverlay.getY() - dy, -mOverlay.getHeight(), mOverlayTopCache));
        }
    };

    private EditText mSearchEditText;
    private ImageButton mFilterButton;
    private RecyclerView mRecyclerview;
    private ModItemAdapter mModItemAdapter;
    private ProgressBar mSearchProgressBar;
    private TextView mStatusTextView;
    private ColorStateList mDefaultTextColor;

    private ModpackApi modpackApi;
    private final SearchFilters mSearchFilters;

    public SearchModFragment(){
        super(R.layout.fragment_mod_search);
        mSearchFilters = new SearchFilters();
        mSearchFilters.isModpack = true;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        String mode = args != null ? args.getString(ARG_MODE, "modpack") : "modpack";
        mSearchFilters.projectType = mode;
        mSearchFilters.isModpack = "modpack".equals(mode);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        modpackApi = new CommonApi(context.getString(R.string.curseforge_api_key));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mModItemAdapter = new ModItemAdapter(getResources(), modpackApi, this);
        ProgressKeeper.addTaskCountListener(mModItemAdapter);

        mOverlayTopCache = getResources().getDimensionPixelSize(R.dimen.fragment_padding_medium);
        mOverlay = view.findViewById(R.id.search_mod_overlay);
        mSearchEditText = view.findViewById(R.id.search_mod_edittext);
        mSearchProgressBar = view.findViewById(R.id.search_mod_progressbar);
        mRecyclerview = view.findViewById(R.id.search_mod_list);
        mStatusTextView = view.findViewById(R.id.search_mod_status_text);
        mFilterButton = view.findViewById(R.id.search_mod_filter);
        mDefaultTextColor = mStatusTextView.getTextColors();

        mRecyclerview.setLayoutManager(new LinearLayoutManager(view.getContext()));
        mRecyclerview.setAdapter(mModItemAdapter);
        mRecyclerview.addOnScrollListener(mOverlayPositionListener);

        View searchBtn = view.findViewById(R.id.search_mod_button);
        if (searchBtn != null) searchBtn.setOnClickListener(v -> applySearch());
        if (mSearchEditText != null) {
            mSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
                applySearch();
                return true;
            });
        }
        mFilterButton.setOnClickListener(v -> openFilterDialog());
        applySearch();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ProgressKeeper.removeTaskCountListener(mModItemAdapter);
        if (mRecyclerview != null) mRecyclerview.removeOnScrollListener(mOverlayPositionListener);
    }

    private void applySearch() {
        CharSequence name = mSearchEditText.getText();
        mSearchFilters.name = name == null ? "" : name.toString();
        mModItemAdapter.performSearchQuery(mSearchFilters);
    }

    private void openFilterDialog() {
        try {
            AlertDialog dialog = new AlertDialog.Builder(requireContext())
                    .setView(R.layout.dialog_mod_filter)
                    .setPositiveButton(android.R.string.ok, (d, w) -> applySearch())
                    .create();
            dialog.show();
            TextView selected = dialog.findViewById(R.id.search_mod_selected_mc_version_textview);
            Button selectVersion = dialog.findViewById(R.id.search_mod_mc_version_button);
            if (selected != null && mSearchFilters.mcVersion != null) selected.setText(mSearchFilters.mcVersion);
            if (selectVersion != null) selectVersion.setOnClickListener(v ->
                    VersionSelectorDialog.open(requireActivity(), false, (id, snapshot) -> {
                        mSearchFilters.mcVersion = id;
                        if (selected != null) selected.setText(id);
                    }));
        } catch (Throwable t) {
            VersionSelectorDialog.open(requireActivity(), false, (id, snapshot) -> {
                mSearchFilters.mcVersion = id;
                applySearch();
            });
        }
    }

    @Override
    public void onSearchFinished() {
        if (mSearchProgressBar != null) mSearchProgressBar.setVisibility(View.GONE);
        if (mStatusTextView != null) {
            mStatusTextView.setTextColor(mDefaultTextColor);
            mStatusTextView.setText("");
        }
    }

    @Override
    public void onSearchError(int error) {
        if (mSearchProgressBar != null) mSearchProgressBar.setVisibility(View.GONE);
        if (mStatusTextView != null) {
            mStatusTextView.setTextColor(Color.RED);
            if (error == ModItemAdapter.SearchResultCallback.ERROR_NO_RESULTS)
                mStatusTextView.setText("No results");
            else mStatusTextView.setText("Search error");
        }
    }
}
