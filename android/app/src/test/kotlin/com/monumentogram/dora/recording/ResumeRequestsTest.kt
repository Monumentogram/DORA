package com.monumentogram.dora.recording

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeRequestsTest {
    @Test
    fun duplicateUiOrNotificationResumeHasOneWinner() {
        val requests = ResumeRequests()
        val owner = Any()
        assertNotNull(requests.begin(owner))
        assertNull(requests.begin(owner))
    }

    @Test
    fun pauseOrStopCancelsPendingResumeBeforeNativeStart() {
        val requests = ResumeRequests()
        val owner = Any()
        val request = requireNotNull(requests.begin(owner))
        requests.cancel()
        assertFalse(requests.isCurrent(request, owner))
    }

    @Test
    fun lateCompletionCannotClearNewResume() {
        val requests = ResumeRequests()
        val owner = Any()
        val old = requireNotNull(requests.begin(owner))
        requests.cancel()
        val next = requireNotNull(requests.begin(owner))
        assertFalse(requests.complete(old))
        assertTrue(requests.isCurrent(next, owner))
    }

    @Test
    fun exactServiceAccessOwnerMustStillMatch() {
        val requests = ResumeRequests()
        val request = requireNotNull(requests.begin(Any()))
        assertFalse(requests.isCurrent(request, Any()))
    }

    @Test
    fun activityRecreationDoesNotReplaceProcessOwnedRequest() {
        val requests = ResumeRequests()
        val serviceOwner = Any()
        val request = requireNotNull(requests.begin(serviceOwner))
        val recreatedUiReference = requests
        assertNull(recreatedUiReference.begin(serviceOwner))
        assertTrue(recreatedUiReference.isCurrent(request, serviceOwner))
        assertTrue(requests.complete(request))
        assertNotNull(requests.begin(serviceOwner))
    }
}
