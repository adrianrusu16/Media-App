#[cfg(target_os = "android")]
mod platform {
    use std::ffi::{CString, c_char};

    #[link(name = "android")]
    unsafe extern "C" {
        fn ATrace_isEnabled() -> bool;
        fn ATrace_beginSection(section_name: *const c_char);
        fn ATrace_endSection();
        fn ATrace_setCounter(counter_name: *const c_char, counter_value: i64);
    }

    #[must_use]
    pub(crate) struct TraceSection {
        active: bool,
    }

    impl Drop for TraceSection {
        fn drop(&mut self) {
            if self.active {
                unsafe { ATrace_endSection() };
            }
        }
    }

    pub(crate) fn section(name: &'static str) -> TraceSection {
        if !unsafe { ATrace_isEnabled() } {
            return TraceSection { active: false };
        }
        let Ok(name) = CString::new(name) else {
            return TraceSection { active: false };
        };
        unsafe { ATrace_beginSection(name.as_ptr()) };
        TraceSection { active: true }
    }

    pub(crate) fn counter(name: &'static str, value: i64) {
        if !unsafe { ATrace_isEnabled() } {
            return;
        }
        if let Ok(name) = CString::new(name) {
            unsafe { ATrace_setCounter(name.as_ptr(), value) };
        }
    }
}

#[cfg(not(target_os = "android"))]
mod platform {
    #[must_use]
    pub(crate) struct TraceSection;

    pub(crate) fn section(_name: &'static str) -> TraceSection {
        TraceSection
    }

    pub(crate) fn counter(_name: &'static str, _value: i64) {}
}

pub(crate) use platform::{counter, section};
