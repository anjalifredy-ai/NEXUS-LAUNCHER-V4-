package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
        mOverlay = view.findViewById(R.id.search_mod_overlay);
        mOverlayTopCache = getResources().getDimensionPixelSize(R.dimen.fragment_padding_medium);
        mSearchEditText = view.findViewById(R.id.search_mod_edittext);
        mFilterButton = view.findViewById(R.id.search_mod_filter);
        mRecyclerview = view.findViewById(R.id.mod_list_view);
        mSearchProgressBar = view.findViewById(R.id.search_mod_progressbar);
        mStatusTextView = view.findViewById(R.id.search_mod_status);
        mDefaultTextColor = mStatusTextView.getTextColors();

        mModItemAdapter = new ModItemAdapter(mSearchFilters, modpackApi, this);
        mRecyclerview.setLayoutManager(new LinearLayoutManager(view.getContext()));
        mRecyclerview.setAdapter(mModItemAdapter);
        mRecyclerview.addOnScrollListener(mOverlayPositionListener);

        view.findViewById(R.id.search_mod_button).setOnClickListener(v -> applySearch());
        mFilterButton.setOnClickListener(v -> openFilterDialog());

        applySearch();
    }

    private void applySearch() {
        CharSequence name = mSearchEditText.getText();
        mSearchFilters.name = name == null ? "" : name.toString();
        mModItemAdapter.performSearchQuery(mSearchFilters);
    }

    private void openFilterDialog() {
        VersionSelectorDialog.open(requireActivity(), false, (id, snapshot) -> {
            mSearchFilters.mcVersion = id;
            applySearch();
        });
    }

    @Override
    public void onSearchFinished() {
        mSearchProgressBar.setVisibility(View.GONE);
        mStatusTextView.setTextColor(mDefaultTextColor);
        mStatusTextView.setText(R.string.modpack_search_result_count);
    }

    @Override
    public void onSearchError(int error) {
        mSearchProgressBar.setVisibility(View.GONE);
        mStatusTextView.setTextColor(Color.RED);
        mStatusTextView.setText(error);
    }

    @Override
    public void onSearchStarted() {
        mSearchProgressBar.setVisibility(View.VISIBLE);
    }
}
