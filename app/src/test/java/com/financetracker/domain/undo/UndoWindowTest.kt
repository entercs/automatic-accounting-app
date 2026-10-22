package com.financetracker.domain.undo

import org.junit.Assert.*
import org.junit.Test

class UndoWindowTest {
    @Test fun timeAwayFromTheScreenStillExpiresTheOpportunity() {
        var now = 1000L
        val undo = UndoWindow<String>(4000) { now }
        val item = undo.offer("bill")
        now = 7000 // No UI and no snackbar callbacks ran while away.
        assertNull(undo.peek()); assertNull(undo.consume(item.token))
    }
    @Test fun expiryUsesTheOriginalDeadlineAndIsNotRestartedByReading() {
        var now = 1000L
        val undo = UndoWindow<String>(4000) { now }
        val item = undo.offer("bill")
        now = 4999; assertEquals(item, undo.peek())
        now = 5000; assertNull(undo.peek())
    }
    @Test fun anUndoCanOnlyBeConsumedOnce() {
        val undo = UndoWindow<String>(4000) { 0 }
        val item = undo.offer("bill")
        assertEquals("bill", undo.consume(item.token)); assertNull(undo.consume(item.token))
    }
    @Test fun newDeletionReplacesPreviousOneAndOldActionCannotConsumeIt() {
        val undo = UndoWindow<String>(4000) { 0 }
        val first = undo.offer("first"); val second = undo.offer("second")
        assertNull(undo.consume(first.token)); assertEquals(second, undo.peek())
        assertEquals("second", undo.consume(second.token))
    }
    @Test fun staleDismissCannotRemoveANewerDeletion() {
        val undo = UndoWindow<String>(4000) { 0 }
        val first = undo.offer("first"); val second = undo.offer("second")
        assertFalse(undo.dismiss(first.token)); assertEquals(second, undo.peek())
    }
    @Test fun repeatedDeletionOfTheSameValueHasADifferentToken() {
        val undo = UndoWindow<String>(4000) { 0 }
        assertNotEquals(undo.offer("same").token, undo.offer("same").token)
    }
    @Test fun dismissalImmediatelyRemovesUndo() {
        val undo = UndoWindow<String>(4000) { 0 }
        val item = undo.offer("bill")
        assertTrue(undo.dismiss(item.token)); assertNull(undo.peek()); assertNull(undo.consume(item.token))
    }
}
