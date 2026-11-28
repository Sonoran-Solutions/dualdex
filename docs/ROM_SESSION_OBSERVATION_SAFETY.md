# ROM session observation safety

## Risk demonstrated

`CompanionViewModel.pollVerifiedRomMemory()` previously called multiple coordinator readers, each of which acquired and released the core lock independently. Session setters could clear the StateFlows between a native read and the poller's later publication, allowing that old result to repopulate party, battle, or location state. The private cheat-ROM hash did not identify what the currently loaded core owned, and `stateGeneration` was not coupled to a general ROM binding.

The focused regression blocks a fake party reader, attempts trust revocation while the result is in flight, then releases it. The session setter and publication now serialize; after the setter returns the obsolete party is absent. Synthetic tests also prove an unbound load is playable but cannot admit profile readers, and a same-hash state load advances the generation while retaining the ROM binding.

## Loaded-ROM identity and provenance

`LibretroCoreCoordinator` owns an immutable `LoadedRomSnapshot` under its fair core lock. Its status is `UNLOADED`, `UNBOUND`, or `BOUND`; a bound snapshot contains a normalized SHA-256 and the current core generation. Filename and header data do not participate. Direct `loadRom` calls remain playable but unbound. Only `RomSessionManager` calls the authorized load path, after copying the incoming bytes, hashing them, verifying the installed cache file again, matching detector and identity hashes, and passing that cached path to a successful core load. The cheat hash remains separate.

An old-save flush failure happens before unload and preserves the old binding. Once unload/replacement begins, the old binding is cleared. A failed or throwing new load cannot leave the old identity attached; failures after the mutation boundary clear companion session state. Failures before that boundary leave the old core and session in place.

## Generation, admission, and publication

ROM load/unload, core load/cleanup, state load, SRAM load, and reset advance `stateGeneration`. State and SRAM restoration, and reset, preserve the loaded ROM hash while advancing its generation. ROM/core replacement and cleanup clear the binding. Mutation attempts advance the generation before execution, including failed attempts, so snapshots admitted earlier cannot be reused.

Before a profile-dependent poll, the companion captures its identity, trust decision, and session revision. It then acquires the core lock with the existing 50 ms timeout and revalidates the revision, compatibility decision, bound SHA-256, and loaded status. If the loaded ROM is unbound or its hash does not match the authorized identity, the poll synchronously clears observations when it detects a new core generation, even when the generation watcher is inactive. Interactive battle-state reads use the same core-lock-bound identity admission before invoking the native reader. The complete poll reader batch and its StateFlow publication run under the core lock plus the companion session monitor. An immutable token records loaded SHA-256, core generation, session revision, and the compatibility snapshot. Session setters use the same monitor and advance the revision before clearing observations. Core-generation notifications also clear observations; polling performs a synchronous generation comparison as a fallback when no watcher is running.

The lock order is **core coordinator lock → companion session monitor** during polling. Session setters and the generation watcher take only the session monitor and never acquire the core lock, so there is no reverse edge. ROM switching releases the core transaction before publishing a companion session. Video framebuffer copies and audio-ring draining continue to use their native mutexes and do not acquire the core lock.

The existing 50 ms timeout bounds only how long a poll or interactive read waits to acquire the core lock; it does not bound how long the lock is held after acquisition. The batch uses reentrant readers and can briefly delay frame execution while several native reads and their small transformations complete. It does not hold the core lock across disk I/O, UI callbacks, or rendering work. On timeout, no reader result is published; ordinary same-session transient-read stabilization remains intact. AYN Thor fast-forward/frame-pacing validation is a device check and was not run as part of host validation.

## Session changes and remaining atomicity

ROM identity, trust, profile, party, location, and battle StateFlows are still separate public flows. This change guarantees an obsolete poll cannot publish after invalidation and that readers in one poll use one core generation. It does not promise that an observer combining arbitrary StateFlows sees a single globally atomic app-state transaction during a session setter.

`LiveObservationStabilizer` still retains observations for transient invalid reads within a trusted session. Session setters and core-generation changes clear the observations and reset stabilizers. A result from an obsolete session is not allowed to reset the new session's stabilizers or overwrite its StateFlows. Supported empty-party semantics are unchanged.

## Regression coverage

`LibretroCoreCoordinatorLockTest` covers unbound/loaded/unloaded status, successful and failed authorized loads, same-ROM generation changes after state/SRAM/reset, unload, and cleanup. `RomSessionObservationSafetyTest` covers unbound reader refusal, normal publication after binding, deterministic trust revocation during an in-flight read, same-hash state restoration, synchronous clearing after a mismatched load without the watcher, and interactive battle-read rejection before the native reader. Existing compatibility, H&S, party, battle, and location tests continue to cover their supported-path behavior and denied-ROM gates. ROM-session tests cover successful cache-backed binding and failed replacement behavior.

## Future R.O.W.E. boundary

This contract supplies a ROM-neutral loaded SHA-256, core generation, and session revision for a later separately authorized capability. That work still needs independently verified player storage-layout evidence, verified configuration eligibility, an exact-release metadata provider, a separately denied player-only permission, and an identity-only DTO. No R.O.W.E. profile, hash, decoder, JNI entry point, or production compatibility is added here. The existing `mayReadLiveMemory` and `mayReadBattleState` policies keep their meanings; issue #59 remains open.
