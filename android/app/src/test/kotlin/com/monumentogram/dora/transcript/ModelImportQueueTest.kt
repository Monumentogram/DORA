package com.monumentogram.dora.transcript

import java.util.ArrayDeque
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelImportQueueTest {
    @Test
    fun restoredRegistryResultCanImportInANewOwner() {
        val worker = ArrayDeque<Runnable>()
        val states = mutableListOf<ModelImportStatus>()
        val queue =
            ModelImportQueue(Executor { worker.add(it) }, Executor { it.run() }, states::add)
        queue.completeSelection { true }
        worker.removeFirst().run()
        assertEquals(ModelImportStatus.INSTALLED, states.last())
    }

    @Test
    fun pickerResultCanImportAfterThePresentationIsDetached() {
        val worker = ArrayDeque<Runnable>()
        val states = mutableListOf<ModelImportStatus>()
        val queue =
            ModelImportQueue(Executor { worker.add(it) }, Executor { it.run() }, states::add)
        assertTrue(queue.beginSelection())
        // The owner is independent of the authorization-scoped dialog; no pause/dispose action.
        var imports = 0
        queue.completeSelection {
            imports++
            true
        }
        assertEquals(0, imports)
        assertEquals(ModelImportStatus.IMPORTING, states.last())
        worker.removeFirst().run()
        assertEquals(1, imports)
        assertEquals(ModelImportStatus.INSTALLED, states.last())
    }

    @Test
    fun busyPickerAndImporterCannotQueueAnotherImport() {
        val worker = ArrayDeque<Runnable>()
        val queue = ModelImportQueue(Executor { worker.add(it) }, Executor { it.run() }) {}
        assertTrue(queue.beginSelection())
        assertFalse(queue.beginSelection())
        queue.completeSelection { true }
        assertFalse(queue.beginSelection())
        queue.completeSelection { error("Duplicate selection must not run") }
        assertEquals(1, worker.size)
        worker.removeFirst().run()
        assertTrue(queue.beginSelection())
    }

    @Test
    fun cancelledPickerDoesNotOpenOrScheduleAnyInput() {
        val worker = ArrayDeque<Runnable>()
        val states = mutableListOf<ModelImportStatus>()
        val queue =
            ModelImportQueue(Executor { worker.add(it) }, Executor { it.run() }, states::add)
        assertTrue(queue.beginSelection())
        queue.completeSelection(null)
        assertTrue(worker.isEmpty())
        assertEquals(ModelImportStatus.CANCELLED, states.last())
    }

    @Test
    fun ownerDestructionPreventsQueuedModelReadsAndLateDelivery() {
        val worker = ArrayDeque<Runnable>()
        val states = mutableListOf<ModelImportStatus>()
        val queue =
            ModelImportQueue(Executor { worker.add(it) }, Executor { it.run() }, states::add)
        queue.beginSelection()
        queue.completeSelection { error("Closed owner must not open model input") }
        queue.close()
        val delivered = states.size
        worker.removeFirst().run()
        assertEquals(delivered, states.size)
        assertFalse(queue.beginSelection())
    }
}
