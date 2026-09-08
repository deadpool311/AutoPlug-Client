/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.utils.SteamCMD;
import org.jetbrains.annotations.NotNull;

/**
 * Finds updates for Steam Workshop mods via the Steam Web API, similar to how
 * {@link com.osiris.autoplug.client.tasks.updater.plugins.ResourceFinder}
 * finds updates for regular mods. Only reports an update when the Workshop
 * item was actually updated after the currently cached version, so mods are
 * not re-downloaded on every run.
 */
public class SteamWorkshopUpdateFinder {
    private final UpdaterConfig updaterConfig;
    private final SteamCMD steamCMD;

    public SteamWorkshopUpdateFinder(UpdaterConfig updaterConfig, SteamCMD steamCMD) {
        this.updaterConfig = updaterConfig;
        this.steamCMD = steamCMD;
    }

    public SearchResult find(@NotNull SteamWorkshopMod mod) {
        SearchResult result = new SearchResult(null, SearchResult.Type.UP_TO_DATE, mod.getVersion(), null, "steam-workshop", null, null, false);
        result.mod = mod;

        try {
            SteamCMD.SteamWorkshopItemDetails details = steamCMD.getWorkshopItemDetails(mod.getPublishedId());
            String workshopAppId = resolveWorkshopAppId(details.getConsumerAppId());
            if (workshopAppId == null)
                throw new Exception("Steam Workshop item '" + mod.getPublishedId() + "' did not provide a numeric consumer app-id and server-updater.software is not a numeric fallback.");

            mod.setWorkshopAppId(workshopAppId);
            result.latestVersion = details.getTimeUpdated();
            result.downloadUrl = details.getFileUrl();
            if (hasUpdate(mod, details.getTimeUpdated()))
                result.type = SearchResult.Type.UPDATE_AVAILABLE;
        } catch (Exception e) {
            result.type = SearchResult.Type.API_ERROR;
            result.setException(e);
        }
        return result;
    }

    /**
     * Uses the Workshop item's consumer app-id when Steam provides one. This
     * matters for games such as DayZ, whose dedicated-server app-id differs
     * from the app-id that owns its Workshop. The configured server app-id is
     * retained as a fallback for older or incomplete API responses.
     */
    public String resolveWorkshopAppId(String consumerAppId) {
        if (consumerAppId != null && consumerAppId.matches("\\d+"))
            return consumerAppId;

        String configuredAppId = updaterConfig.server_software.asString();
        if (configuredAppId != null && configuredAppId.matches("\\d+"))
            return configuredAppId;
        return null;
    }

    /**
     * Compares the cached version with the time_updated value of the Workshop item.
     * A missing cached version means the mod was never update-checked before.
     */
    boolean hasUpdate(SteamWorkshopMod mod, String latestTimeUpdated) {
        if (latestTimeUpdated == null || latestTimeUpdated.isEmpty())
            return false;
        String currentVersion = mod.getVersion();
        if (currentVersion == null || currentVersion.isEmpty())
            return true;
        if (latestTimeUpdated.equals(currentVersion))
            return false;
        try {
            return Long.parseLong(latestTimeUpdated) > Long.parseLong(currentVersion);
        } catch (NumberFormatException e) {
            return true;
        }
    }
}
