package com.dualdex.emulator

import android.os.SystemClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Locks in the synchronization contract of [LibretroCoreCoordinator] that the AYN Thor
 * fast-forward regression depended on.
 *
 * High-frequency framebuffer / audio-ring snapshots are protected natively by g_video_mutex and
 * g_audio_mutex. They must NOT queue behind the frame lock, because `stepFrame()` holds that lock
 * for the whole duration of retro_run and the render thread's snapshot request is an untimed
 * tryLock(): under fast-forward it silently failed and the top screen dropped updates.
 *
 * Core mutation, stepFrame and the protected memory readers must still be mutually exclusive.
 */
class LibretroCoreCoordinatorLockTest {

    private class RecordingBridge : LibretroCoreBridge {
        val stepped = java.util.concurrent.atomic.AtomicInteger(0)
        override fun stepFrame(): Boolean {
            stepped.incrementAndGet()
            return true
        }
    }

    /**
     * Records whether an exclusive mutation was in progress at the moment the bridge's
     * stepFrame() body ran. That body executes while the coordinator lock is held, so it is the
     * only place where overlap can be observed reliably: stepFrame() returns *after* unlocking.
     */
    private class OverlapDetectingBridge(
        private val inMutation: AtomicBoolean,
        private val overlapping: AtomicBoolean
    ) : LibretroCoreBridge {
        val stepped = java.util.concurrent.atomic.AtomicInteger(0)
        override fun stepFrame(): Boolean {
            if (inMutation.get()) overlapping.set(true)
            stepped.incrementAndGet()
            return true
        }
    }

    @Test
    fun `snapshot reads do not queue behind the frame lock`() {
        val coordinator = LibretroCoreCoordinator()
        val bridge = RecordingBridge()
        coordinator.bridge = bridge

        // Hold the frame lock on another thread, exactly as a long retro_run frame would.
        val holding = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holdDurationMs = 300L
        val holder = Thread {
            coordinator.executeExclusive {
                holding.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
        }
        holder.start()
        assertTrue("frame lock was never acquired", holding.await(5, TimeUnit.SECONDS))
        assertTrue("core lock should be held for this test", coordinator.isExclusiveLocked)

        // Both snapshot paths must reach the native layer immediately while the core lock is
        // held. In a host JVM the native library is absent, so the call completes by throwing
        // UnsatisfiedLinkError -- which still proves it did not wait for the core lock.
        val startedNs = SystemClock.elapsedRealtime()
        val errors = mutableListOf<Throwable>()
        try {
            coordinator.getVideoFrame(ByteBuffer.allocateDirect(512 * 512 * 4), IntArray(4))
        } catch (t: Throwable) {
            errors.add(t)
        }
        try {
            coordinator.getAudioSamples(ShortArray(1024))
        } catch (t: Throwable) {
            errors.add(t)
        }
        val elapsedMs = SystemClock.elapsedRealtime() - startedNs

        release.countDown()
        holder.join(5000)

        assertTrue(
            "snapshot reads must not wait for the core lock; took ${elapsedMs}ms while the " +
                "lock was held for ${holdDurationMs}ms",
            elapsedMs < holdDurationMs / 3
        )
        // Absent the native library the calls must fail, never silently block or spin.
        assertEquals("both snapshot calls should have reached native", 2, errors.size)
        assertTrue(
            "unexpected failure: ${errors.first()}",
            errors.all { it is UnsatisfiedLinkError }
        )
    }

    @Test
    fun `stepFrame and core mutation remain mutually exclusive`() {
        val coordinator = LibretroCoreCoordinator()
        val inMutation = AtomicBoolean(false)
        val overlapping = AtomicBoolean(false)
        val bridge = OverlapDetectingBridge(inMutation, overlapping)
        coordinator.bridge = bridge

        val iterations = 200
        // Makes the mutation hold the lock long enough to be hit by a concurrent stepFrame.
        val mutationHoldNanos = 200_000L

        val mutator = Thread {
            repeat(iterations) {
                coordinator.executeExclusive(2000L) {
                    inMutation.set(true)
                    Thread.sleep(0, mutationHoldNanos.toInt())
                    inMutation.set(false)
                }
            }
        }
        val stepper = Thread {
            repeat(iterations) {
                coordinator.stepFrame()
            }
        }

        mutator.start()
        stepper.start()
        mutator.join(30_000)
        stepper.join(30_000)

        assertFalse("stepFrame ran inside an exclusive core mutation", overlapping.get())
        assertEquals(iterations, bridge.stepped.get())
    }

    @Test
    fun `exclusive operations remain reentrant on one thread`() {
        val coordinator = LibretroCoreCoordinator()
        val sawInner = AtomicBoolean(false)
        coordinator.executeExclusive {
            coordinator.executeExclusive {
                sawInner.set(true)
            }
        }
        assertTrue("nested exclusive block did not run", sawInner.get())
    }

    @Test
    fun `exclusive timeout throws instead of silently proceeding`() {
        val coordinator = LibretroCoreCoordinator()
        val acquired = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = Thread {
            coordinator.executeExclusive {
                acquired.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
        }
        holder.start()
        assertTrue(acquired.await(5, TimeUnit.SECONDS))

        val error = try {
            coordinator.executeExclusive(50L) { }
            null
        } catch (e: IllegalStateException) {
            e
        } finally {
            release.countDown()
            holder.join(5000)
        }

        assertNotNull("a timed-out exclusive operation must not proceed", error)
    }
}
