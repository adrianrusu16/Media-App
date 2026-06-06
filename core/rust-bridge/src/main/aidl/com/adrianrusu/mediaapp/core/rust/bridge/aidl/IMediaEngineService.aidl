package com.adrianrusu.mediaapp.core.rust.bridge.aidl;

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand;
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot;
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.IEngineListener;

interface IMediaEngineService {
    EngineSnapshot getSnapshot();
    void dispatch(in EngineCommand command);
    void registerListener(IEngineListener listener);
    void unregisterListener(IEngineListener listener);
}
