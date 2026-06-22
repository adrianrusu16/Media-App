package com.adrianrusu.pandawave.core.rust.bridge.aidl;

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand;
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem;
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect;
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent;
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot;
import com.adrianrusu.pandawave.core.rust.bridge.aidl.IEngineListener;

interface IMediaEngineService {
    EngineSnapshot getSnapshot();
    EngineCatalogItem getBrowseResult(int index);
    EngineCatalogItem getSearchResult(int index);
    int getEffectCount();
    EngineEffect getEffect(int index);
    void dispatch(in EngineCommand command);
    void dispatchPlatformEvent(in EnginePlatformEvent event);
    void registerListener(IEngineListener listener);
    void unregisterListener(IEngineListener listener);
}
