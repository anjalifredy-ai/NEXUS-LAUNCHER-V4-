package net.kdt.pojavlaunch.modloaders.modpacks.api;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModrinthIndex;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.zip.ZipFile;

public class ModrinthApi implements ApiHandler.Api, ModpackApi {
    private final ApiHandler mApiHandler;
    public ModrinthApi(){
        mApiHandler = new ApiHandler("https://api.modrinth.com/v2");
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        HashMap<String, Object> params = new HashMap<>();

        String offset;
        if(previousPageResult != null)
            offset = String.valueOf(previousPageResult.previousOffset + previousPageResult.results.length);
        else
            offset = "0";

        // List of parameters for facets - Nexus: support mod, modpack, resourcepack, shader, plugin
        StringBuilder facetString = new StringBuilder();
        facetString.append("[");
        facetString.append(String.format("[\"project_type:%s\"]", searchFilters.resolvedProjectType()));
        if(searchFilters.mcVersion != null && !searchFilters.mcVersion.isEmpty())
            facetString.append(String.format(",[\"versions:%s\"]", searchFilters.mcVersion));
        facetString.append("]");

        params.put("facets", facetString.toString());
        params.put("query", searchFilters.name);
        params.put("limit", 50);
        params.put("index", "relevance");
        params.put("offset", offset);

        JsonObject response = mApiHandler.get("search", params, JsonObject.class);
        if(response == null) return null;
        JsonArray responseHits = response.getAsJsonArray("hits");
        if(responseHits == null) return null;

        SearchResult searchResult = new SearchResult();
        searchResult.previousOffset = Integer.parseInt(offset);
        searchResult.totalResultCount = response.get("total_hits").getAsInt();
        searchResult.results = new ModItem[responseHits.size()];

        for(int i=0; i<responseHits.size(); ++i){
            JsonObject hit = responseHits.get(i).getAsJsonObject();
            ModItem item = new ModItem(
                    Constants.SOURCE_MODRINTH,
                    hit.get("project_type").getAsString().equals("modpack"),
                    hit.get("project_id").getAsString(),
                    hit.get("title").getAsString(),
                    hit.get("description").getAsString(),
                    hit.get("icon_url").getAsString()
            );
            searchResult.results[i] = item;
        }
        return searchResult;
    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        JsonArray response = mApiHandler.get("project/" + item.id + "/version", JsonArray.class);
        if(response == null) return null;
        int responseSize = response.size();

        String[] names = new String[responseSize];
        String[] mcVersions = new String[responseSize];
        String[] urls = new String[responseSize];
        String[] hashes = new String[responseSize];

        for(int i=0; i<responseSize; ++i){
            JsonObject version = response.get(i).getAsJsonObject();
            names[i] = version.get("name").getAsString();
            JsonArray gameVersions = version.getAsJsonArray("game_versions");
            mcVersions[i] = gameVersions.size() > 0 ? gameVersions.get(0).getAsString() : "";
            JsonArray files = version.getAsJsonArray("files");
            if(files.size() > 0){
                JsonObject file0 = files.get(0).getAsJsonObject();
                urls[i] = file0.get("url").getAsString();
                JsonObject hashesObj = file0.getAsJsonObject("hashes");
                hashes[i] = hashesObj != null && hashesObj.has("sha1") ? hashesObj.get("sha1").getAsString() : "";
            } else {
                urls[i] = "";
                hashes[i] = "";
            }
        }
        return new ModDetail(item, names, mcVersions, urls, hashes);
    }

    @Override
    public void installMod(ModDetail modDetail, int selectedVersion, File instanceDestination) throws Exception {
        String downloadUrl = modDetail.versionUrls[selectedVersion];
        String hash = modDetail.versionHashes[selectedVersion];
        String fileName = new File(downloadUrl).getName();
        if (fileName.isEmpty() || !fileName.contains(".")) fileName = "download.jar";

        File destination = new File(instanceDestination, fileName);
        ProgressKeeper.submitProgress(net.kdt.pojavlaunch.progresskeeper.ProgressLayout.INSTALL_MODPACK, 0, R.string.modpack_download_downloading_mods);
        Tools.downloadFileMonitored(downloadUrl, destination, new byte[8192], (curr, total) ->
                ProgressKeeper.submitProgress(net.kdt.pojavlaunch.progresskeeper.ProgressLayout.INSTALL_MODPACK,
                        total > 0 ? (int)(curr * 100 / total) : 0, R.string.modpack_download_downloading_mods));

        if (fileName.endsWith(".mrpack")) {
            try (ZipFile modpackZipFile = new ZipFile(destination)) {
                ModrinthIndex index = Tools.GLOBAL_GSON.fromJson(
                        Tools.read(ZipUtils.getEntryStream(modpackZipFile, "modrinth.index.json")),
                        ModrinthIndex.class);
                ModDownloader modDownloader = new ModDownloader(instanceDestination, index);
                modDownloader.awaitFinish(new NotificationDownloadListener(
                        R.string.modpack_download_downloading_mods, net.kdt.pojavlaunch.progresskeeper.ProgressLayout.INSTALL_MODPACK));
                ProgressKeeper.submitProgress(net.kdt.pojavlaunch.progresskeeper.ProgressLayout.INSTALL_MODPACK, 0, R.string.modpack_download_applying_overrides, 1, 2);
                ZipUtils.zipExtract(modpackZipFile, "overrides/", instanceDestination);
                ProgressKeeper.submitProgress(net.kdt.pojavlaunch.progresskeeper.ProgressLayout.INSTALL_MODPACK, 50, R.string.modpack_download_applying_overrides, 2, 2);
                ZipUtils.zipExtract(modpackZipFile, "client-overrides/", instanceDestination);
            }
            destination.delete();
        }
        ProgressKeeper.submitProgress(net.kdt.pojavlaunch.progresskeeper.ProgressLayout.INSTALL_MODPACK, 100, R.string.modpack_download_downloading_mods);
    }
}
