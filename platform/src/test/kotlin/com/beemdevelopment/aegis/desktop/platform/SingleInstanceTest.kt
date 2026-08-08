package com.beemdevelopment.aegis.desktop.platform

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class SingleInstanceTest {
    private lateinit var dir: Path
    private lateinit var lockFile: Path
    private val instances = mutableListOf<SingleInstance>()

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("aegis-single-instance-test")
        lockFile = dir.resolve("aegis.lock")
    }

    @After
    fun tearDown() {
        instances.forEach { it.release() }
        Files.walk(dir).sorted(Comparator.reverseOrder()).forEach {
            runCatching { Files.deleteIfExists(it) }
        }
    }

    private fun newInstance(): SingleInstance =
        SingleInstance(lockFile).also { instances.add(it) }

    @Test
    fun secondInstanceCannotAcquireTheLock() {
        assertTrue(newInstance().acquire())
        assertFalse("A second instance must not open the same vault", newInstance().acquire())
    }

    @Test
    fun theLockIsReleasedOnExit() {
        val first = newInstance()
        assertTrue(first.acquire())
        first.release()
        assertTrue(newInstance().acquire())
    }

    @Test
    fun aLinkFromASecondLaunchReachesTheRunningInstance() {
        val first = newInstance()
        assertTrue(first.acquire())

        val received = ArrayBlockingQueue<Optional>(1)
        first.onActivationRequested { received.offer(Optional(it)) }

        val second = newInstance()
        assertFalse(second.acquire())
        second.signalExistingInstance("otpauth://totp/Example:alice?secret=JBSWY3DPEHPK3PXP")

        val result = received.poll(5, TimeUnit.SECONDS)
        assertEquals(
            "otpauth://totp/Example:alice?secret=JBSWY3DPEHPK3PXP",
            result?.value,
        )
    }

    @Test
    fun aPlainActivationCarriesNoPayload() {
        val first = newInstance()
        assertTrue(first.acquire())

        val received = ArrayBlockingQueue<Optional>(1)
        first.onActivationRequested { received.offer(Optional(it)) }

        val second = newInstance()
        assertFalse(second.acquire())
        second.signalExistingInstance()

        assertNull(received.poll(5, TimeUnit.SECONDS)?.value)
    }

    /**
     * A payload with a newline would be read as a second message, so it is dropped rather than
     * letting an attacker-supplied link inject one.
     */
    @Test
    fun aPayloadWithANewlineIsDropped() {
        val first = newInstance()
        assertTrue(first.acquire())

        val received = ArrayBlockingQueue<Optional>(1)
        first.onActivationRequested { received.offer(Optional(it)) }

        val second = newInstance()
        assertFalse(second.acquire())
        second.signalExistingInstance("otpauth://totp/a?secret=AA\naegis-activate evil")

        assertNull(received.poll(5, TimeUnit.SECONDS)?.value)
    }

    /** ArrayBlockingQueue rejects nulls, so the nullable payload is boxed. */
    private class Optional(val value: String?)
}
