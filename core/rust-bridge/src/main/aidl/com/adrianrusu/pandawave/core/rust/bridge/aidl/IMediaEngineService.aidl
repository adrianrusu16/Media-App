package com.adrianrusu.pandawave.core.rust.bridge.aidl;

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand;
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult;
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem;
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryPage;
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
    List<EngineCatalogItem> getBrowseResultsPage(int offset, int limit);
    List<EngineCatalogItem> getSearchResultsPage(int offset, int limit);
    EngineHistoryPage getHistoryPage(int offset, int limit, long generation);
    int getEffectCount();
    EngineEffect getEffect(int index);
    EngineDispatchResult dispatch(in EngineCommand command);
    EngineDispatchResult dispatchPlatformEvent(in EnginePlatformEvent event);
    void registerListener(IEngineListener listener);
    void unregisterListener(IEngineListener listener);
    List<EngineLibraryItem> getSavedTracksPage(int offset, int limit);
    List<EngineLibraryItem> getLikedTracksPage(int offset, int limit);
    List<String> getPendingLibraryTrackIdsPage(int offset, int limit);
    List<EnginePlaylistItem> getPlaylistsPage(int offset, int limit);
    List<EnginePlaylistTrackItem> getPlaylistTracksPage(int offset, int limit);
    String getSelectedPlaylistId();
    EnginePlaylistReconciliation getPlaylistReconciliation();
    List<EngineCatalogItem> getDiscoveryResultsPage(int offset, int limit);
    List<EngineCatalogItem> getForYouResultsPage(int offset, int limit);
    List<EngineCatalogItem> getRecommendationResultsPage(int offset, int limit);
    String getProfilePreferenceValue(String key);
}
