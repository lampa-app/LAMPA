package top.rootu.lampa.channels

import org.junit.Assert.*
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.Executor

class PluginChannelTest {
    private val payload = """{"id":"book","title":"Example","items":[{"id":1399,"source":"tmdb","type":"tv","name":"Example show","poster_path":"/poster.jpg"}]}"""

    @Test fun parsesNormalCardIntoIsolatedNamespace() {
        val request = requireNotNull(PluginChannelRequest.parse(payload))
        assertEquals("plugin:book", request.name)
        assertEquals("Example", request.title)
        assertEquals("1399", request.items.single().id)
        assertEquals("tv", request.items.single().type)
        assertEquals("/poster.jpg", request.items.single().poster_path)
    }

    @Test fun acceptsEmptyListButRejectsInvalidInput() {
        assertTrue(requireNotNull(PluginChannelRequest.parse("""{"id":"x","title":"X","items":[]}""")).items.isEmpty())
        val invalid = listOf(null, "{}", "[]", payload + " {}", payload.replace("book", "plugin:book"),
            payload.replace("Example\"", "\\n\""), payload.replace("\"items\":[", "\"items\":{} ,\"other\":["),
            payload.replace("\"tv\"", "\"intent\""), payload.replace("\"name\":\"Example show\"", "\"name\":{}"),
            payload.replace("1399", "{}"), " ".repeat(1_048_577))
        invalid.forEach { assertNull(PluginChannelRequest.parse(it)) }
    }

    @Test fun filtersMissingIdentityButNeverTurnsBadListIntoClear() {
        val mixed = """{"id":"x","title":"X","items":[{"id":"missing-title","type":"tv"},{"name":"missing-id","type":"tv"},{"id":"ok","name":"Good","type":"tv"}]}"""
        assertEquals(listOf("ok"), requireNotNull(PluginChannelRequest.parse(mixed)).items.map { it.id })
        assertNull(PluginChannelRequest.parse(mixed.replace(",{\"id\":\"ok\",\"name\":\"Good\",\"type\":\"tv\"}", "")))
    }

    @Test fun doesNotForwardExecutableFields() {
        val request = requireNotNull(PluginChannelRequest.parse(payload.replace("\"source\"", "\"intent\":\"intent:evil\",\"javascript\":\"evil()\",\"source\"")))
        assertEquals("tmdb", request.items.single().source)
        assertEquals("1399", request.items.single().id)
    }

    @Test fun acceptsIdBoundaryAndRejectsOversizedOrWrongScalarFields() {
        assertNotNull(PluginChannelRequest.parse(payload.replace("book", "a".repeat(64))))
        assertNull(PluginChannelRequest.parse(payload.replace("book", "a".repeat(65))))
        listOf("Book", "", "../book/", "книга").forEach {
            assertNull(PluginChannelRequest.parse(payload.replace("book", it)))
        }
        assertNull(PluginChannelRequest.parse(payload.replace("1399", "true")))
        assertNull(PluginChannelRequest.parse(payload.replace("\"source\":\"tmdb\"", "\"source\":{}")))
        assertNull(PluginChannelRequest.parse(payload.replace("\"poster_path\":\"/poster.jpg\"", "\"img\":\"javascript:evil()\"")))
    }

    @Test fun queueRejectionReturnsFalse() {
        val executor = Executor { throw java.util.concurrent.RejectedExecutionException() }
        val publisher = PluginChannelPublisher(executor, { true }, { fail("must not write") }, {})
        assertFalse(publisher.publish(payload))
        assertFalse(publisher.clear("book"))
    }

    @Test fun rejectsDeeplyNestedJsonWithoutCrashingBridge() {
        val nested = "[".repeat(10000) + "0" + "]".repeat(10000)
        assertNull(PluginChannelRequest.parse("""{"id":"x","title":"X","items":[],"extra":$nested}"""))
    }

    @Test fun rejectsCardIdentifiersThatCouldEscapeLampaNavigation() {
        listOf("x'evil()", "x\\\\evil", "x\\n", "x\\u0000", "x\"evil").forEach { unsafe ->
            val encoded = com.google.gson.Gson().toJson(unsafe)
            assertNull(PluginChannelRequest.parse(payload.replace("1399", encoded)))
            assertNull(PluginChannelRequest.parse(payload.replace("\"tmdb\"", encoded)))
        }
        assertNotNull(PluginChannelRequest.parse(payload.replace("1399", "\"KP_1227897\"")))
        assertNotNull(PluginChannelRequest.parse(payload.replace("1399", "\"0a88d69f-6f33-49aa-91db-ee6e0c3fdff1\"")))
    }

    @Test fun providerDisappearingDoesNotWriteButQueueRecovers() {
        val executor = DeferredExecutor()
        var available = true
        val written = mutableListOf<String>()
        var failures = 0
        val publisher = PluginChannelPublisher(executor, { available }, { written.add(it.name) }, { failures++ })
        assertTrue(publisher.publish(payload))
        available = false
        executor.drain()
        assertTrue(written.isEmpty())
        assertEquals(1, failures)
        available = true
        assertTrue(publisher.clear("book"))
        executor.drain()
        assertEquals(listOf("plugin:book"), written)
    }

    @Test fun drainsUpdatesAndClearInFifoOrderWithoutDroppingLatest() {
        val executor = DeferredExecutor()
        val result = mutableListOf<String>()
        val publisher = PluginChannelPublisher(executor, { true }, { request ->
            result.add("${request.name}:${request.title ?: "clear"}:${request.items.size}")
        }, {})
        assertTrue(publisher.publish(payload))
        assertTrue(publisher.clear("book"))
        assertTrue(publisher.publish(payload.replace("Example\"", "Latest\"")))
        assertTrue(result.isEmpty())
        executor.drain()
        assertEquals(listOf("plugin:book:Example:1", "plugin:book:clear:0", "plugin:book:Latest:1"), result)
    }

    @Test fun rejectsUnavailableProviderAndInvalidClearWithoutScheduling() {
        val executor = DeferredExecutor()
        val publisher = PluginChannelPublisher(executor, { false }, { fail("must not write") }, {})
        assertFalse(publisher.publish(payload))
        assertFalse(publisher.clear("book"))
        assertFalse(publisher.clear("plugin:book"))
        assertEquals(0, executor.size)
    }

    @Test fun failedWriteDoesNotPreventLaterClear() {
        val executor = DeferredExecutor()
        val result = mutableListOf<String>()
        var failures = 0
        val publisher = PluginChannelPublisher(executor, { true }, { request ->
            if (request.title != null) throw IllegalStateException("private provider error")
            result.add(request.name)
        }, { failures++ })
        assertTrue(publisher.publish(payload))
        assertTrue(publisher.clear("book"))
        executor.drain()
        assertEquals(1, failures)
        assertEquals(listOf("plugin:book"), result)
    }

    private class DeferredExecutor : Executor {
        private val pending = ArrayDeque<Runnable>()
        val size get() = pending.size
        override fun execute(command: Runnable) { pending.add(command) }
        fun drain() { while (pending.isNotEmpty()) pending.removeFirst().run() }
    }
}
