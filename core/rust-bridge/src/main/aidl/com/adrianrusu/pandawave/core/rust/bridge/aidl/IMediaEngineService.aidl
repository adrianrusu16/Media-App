package com.adrianrusu.pandawave.core.rust.bridge.aidl;

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand;
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult;
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem;
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
    int getEffectCount();
    EngineEffect getEffect(int index);
    EngineDispatchResult dispatch(in EngineCommand command);
    EngineDispatchResult dispatchPlatformEvent(in EnginePlatformEvent event);
    void registerListener(IEngineListener listener);
    void unregisterListener(IEngineListener listener);
    EngineLibraryItem getSavedTrack(int index);
    EngineLibraryItem getLikedTrack(int index);
    String getPendingLibraryTrackId(int index);
    EnginePlaylistItem getPlaylist(int index);
    EnginePlaylistTrackItem getPlaylistTrack(int index);
    String getSelectedPlaylistId();
    EnginePlaylistReconciliation getPlaylistReconciliation();
    EngineCatalogItem getDiscoveryResult(int index);
    EngineCatalogItem getForYouResult(int index);
    EngineCatalogItem getRecommendationResult(int index);
    String getProfilePreferenceValue(String key);
}
