package net.kdt.pojavlaunch.modloaders.modpacks.models;

import org.jetbrains.annotations.Nullable;

public class SearchFilters {
    public boolean isModpack;
    @Nullable public String projectType;
    public String name;
    @Nullable public String mcVersion;

    public String resolvedProjectType() {
        if (projectType != null && !projectType.isEmpty()) return projectType;
        return isModpack ? "modpack" : "mod";
    }
}
