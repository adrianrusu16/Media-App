package com.adrianrusu.mediaapp.core.rust.bridge.aidl;

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent;
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot;

interface IEngineListener {
    oneway void onSnapshotChanged(in EngineSnapshot snapshot);
    oneway void onEngineEvent(in EngineEvent event);
}
