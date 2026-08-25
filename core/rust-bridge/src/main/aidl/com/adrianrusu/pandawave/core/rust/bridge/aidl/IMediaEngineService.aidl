package com.adrianrusu.pandawave.core.rust.bridge.aidl;

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand;
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult;
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem;
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryPage;
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryItem;
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineLibraryItem;
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistItem;
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistTrackItem;
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistReconciliation;
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect;
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent;
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot;
import com.adrianrusu.pandawave.core.rust.bridge.aidl.IEngineListener;
import com.adrianrusu.pandawave.core.rust.bridge.engine.EngineDispatchResult;

import java.util.List;

interface IMediaEngineService {
    EngineAuthOperationResult registerPassword(String email, in byte[] password);
    EngineAuthOperationResult resendVerification(String email);
    EngineAuthOperationResult verifyEmail(in byte[] verificationToken, String deviceLabel);
    EngineAuthOperationResult loginPassword(String email, in byte[] password, String deviceLabel);
    EngineAuthOperationResult logout();
    EngineSnapshot getSnapshot();
    EngineCatalogItem getBrowseResult(int index);
    EngineCatalogItem getSearchResult(int index);
    EngineHistoryItem getHistoryEntry(int index);
    EngineHistoryPage getHistoryPage(int offset, int limit, long generation);
    int getEffectCount();
    EngineEffect getEffect(int index);
    EngineDispatchResult dispatch(in EngineCommand command);
    EngineDispatchResult dispatchPlatformEvent(in EnginePlatformEvent event);
    void registerListener(IEngineListener listener);
    void unregisterListener(IEngineListener listener);
    EngineLibraryItem getSavedTrack(int index);
    List<EngineLibraryItem> getSavedTracksPage(int offset, int limit);
    EngineLibraryItem getLikedTrack(int index);
    List<EngineLibraryItem> getLikedTracksPage(int offset, int limit);
    String getPendingLibraryTrackId(int index);
    EnginePlaylistItem getPlaylist(int index);
    List<EnginePlaylistItem> getPlaylistsPage(int offset, int limit);
    EnginePlaylistTrackItem getPlaylistTrack(int index);
    List<EnginePlaylistTrackItem> getPlaylistTracksPage(int offset, int limit);
    String getSelectedPlaylistId();
    EnginePlaylistReconciliation getPlaylistReconciliation();
    EngineCatalogItem getDiscoveryResult(int index);
    List<EngineCatalogItem> getDiscoveryResultsPage(int offset, int limit);
    EngineCatalogItem getForYouResult(int index);
    List<EngineCatalogItem> getForYouResultsPage(int offset, int limit);
    EngineCatalogItem getRecommendationResult(int index);
    List<EngineCatalogItem> getRecommendationResultsPage(int offset, int limit);
    String getProfilePreferenceValue(String key);
}
