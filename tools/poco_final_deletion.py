"""Stage 8.6A narrowly admits worker-owned Room invalidation, preserving historical bytes."""
PRODUCTION = 'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/journal/RoomAudioJournal.kt'
TEST = 'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/persistence/EncryptedAudioVaultDeletionFailureTest.kt'
OVERRIDES = {PRODUCTION, TEST}
EXECUTOR = '''                        // All journal DAOs are synchronous and operations own the sole encrypted
                        // connection. Finish Room invalidation on that same worker: an async
                        // refresh
                        // can otherwise hold Room's close barrier while waiting for a connection
                        // retained by a failed endTransaction on the thread trying to close it.
                        // Async DAO/observer admission requires revisiting this ownership contract.
                        .setQueryExecutor { command ->
                            check(Looper.myLooper() != Looper.getMainLooper()) {
                                "Journal work requires a worker thread"
                            }
                            command.run()
                        }
'''
TEST_ADDITIONS = (
    '                    val invalidation = RoomInvalidationProbe(vault)\n',
    '                    invalidation.assertRefreshOwnedByCaller(transactionRetained = false)\n',
    '                    invalidation.assertRefreshOwnedByCaller(transactionRetained = !after)\n',
)


def normalize(path, current, original):
    current, original = (raw.replace(b'\r\n', b'\n') for raw in (current, original))
    if path == PRODUCTION:
        additions = ('import android.os.Looper\n', EXECUTOR)
    elif path == TEST:
        additions = TEST_ADDITIONS
    else:
        raise ValueError('Unapproved final-deletion override')
    for addition in additions:
        encoded = addition.encode()
        if current.count(encoded) != 1:
            raise ValueError('Final-deletion exact ownership addition missing or changed')
        current = current.replace(encoded, b'', 1)
    if current != original:
        raise ValueError('Final-deletion change weakened or altered historical implementation/tests')
    return original
