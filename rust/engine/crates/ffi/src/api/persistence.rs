use crate::PandaEngine;

#[unsafe(no_mangle)]
pub unsafe extern "C" fn panda_engine_restore(engine: *mut PandaEngine) -> bool {
    let engine = unsafe { engine.as_mut() };
    if let Some(engine) = engine {
        engine.engine.with_engine(|e| e.restore().unwrap_or(false))
    } else {
        false
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn panda_engine_save(engine: *const PandaEngine) -> bool {
    let engine = unsafe { engine.as_ref() };
    if let Some(engine) = engine {
        engine.engine.with_engine(|e| e.save().is_ok())
    } else {
        false
    }
}