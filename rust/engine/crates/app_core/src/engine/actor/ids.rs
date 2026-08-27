macro_rules! monotonic_id {
    ($(#[$meta:meta])* $name:ident) => {
        $(#[$meta])*
        #[derive(Clone, Copy, Debug, Default, Eq, Hash, Ord, PartialEq, PartialOrd)]
        pub struct $name(u64);

        impl $name {
            pub const fn new(value: u64) -> Self {
                Self(value)
            }

            pub const fn get(self) -> u64 {
                self.0
            }
        }
    };
}

monotonic_id!(
    /// Sequence assigned to every message processed by the actor.
    ///
    /// This is deliberately not interchangeable with snapshot revisions or domain
    /// generations:
    ///
    /// ```compile_fail
    /// use panda_engine_core::engine::actor::{MessageSequence, SnapshotRevision};
    /// let revision: SnapshotRevision = MessageSequence::new(1);
    /// ```
    MessageSequence
);

monotonic_id!(
    /// Revision assigned only when a newly published snapshot differs from the
    /// previously published snapshot.
    SnapshotRevision
);

monotonic_id!(
    /// Correlation identifier returned immediately for an accepted command.
    CommandId
);

monotonic_id!(
    /// Correlation identifier for asynchronous internal engine work.
    OperationId
);

monotonic_id!(
    /// Generation invalidated by any account or authenticated-session replacement.
    ///
    /// ```compile_fail
    /// use panda_engine_core::engine::actor::{AccountGeneration, SearchGeneration};
    /// let search: SearchGeneration = AccountGeneration::new(1);
    /// ```
    AccountGeneration
);

monotonic_id!(
    /// Generation invalidated by a superseding search or search-page lineage.
    SearchGeneration
);

monotonic_id!(
    /// Generation invalidated by a superseding playlist read or mutation.
    PlaylistGeneration
);

monotonic_id!(
    /// Generation invalidated by a superseding history read or mutation.
    HistoryGeneration
);

monotonic_id!(
    /// Generation invalidated by a superseding library read or mutation.
    ///
    /// ```compile_fail
    /// use panda_engine_core::engine::actor::{HistoryGeneration, LibraryGeneration};
    /// let library: LibraryGeneration = HistoryGeneration::new(1);
    /// ```
    LibraryGeneration
);

monotonic_id!(
    /// Identity of one logical playback source-resolution attempt.
    PlaybackInstanceId
);
