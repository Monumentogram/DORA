package com.monumentogram.dora.transcript;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.ProxyFileDescriptorCallback;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsProvider;
import android.system.ErrnoException;
import android.system.OsConstants;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Test APK process deliberately uses only platform classes, without target APK Kotlin runtime. */
public final class FaultDocumentsProvider extends DocumentsProvider {
    private final HandlerThread thread = new HandlerThread("stage12-provider");
    private final java.util.concurrent.atomic.AtomicInteger fullWrites = new java.util.concurrent.atomic.AtomicInteger();
    @Override public boolean onCreate() { thread.start(); return true; }
    @Override public Cursor queryRoots(String[] projection) {
        return new MatrixCursor(projection == null ? new String[0] : projection);
    }
    @Override public Cursor queryDocument(String id, String[] projection) {
        String[] columns = projection == null ? new String[]{Document.COLUMN_DOCUMENT_ID} : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if ("enospc_writes".equals(column)) row.add(fullWrites.get());
            else row.add(Document.COLUMN_DOCUMENT_ID.equals(column) ? id : null);
        }
        return cursor;
    }
    @Override public Cursor queryChildDocuments(String parent, String[] projection, String sort) {
        return new MatrixCursor(projection == null ? new String[0] : projection);
    }
    @Override public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        if ("refuse".equals(id)) throw new FileNotFoundException("Synthetic provider unavailable");
        if ("full".equals(id)) {
            try {
                return getContext().getSystemService(StorageManager.class).openProxyFileDescriptor(
                    ParcelFileDescriptor.MODE_WRITE_ONLY, new ProxyFileDescriptorCallback() {
                        @Override public long onGetSize() { return 0L; }
                        @Override public int onWrite(long offset, int size, byte[] data) throws ErrnoException {
                            fullWrites.incrementAndGet();
                            throw new ErrnoException("write", OsConstants.ENOSPC);
                        }
                        @Override public void onFsync() throws ErrnoException {
                            throw new ErrnoException("fsync", OsConstants.ENOSPC);
                        }
                        @Override public void onRelease() {}
                    }, new Handler(thread.getLooper()));
            } catch (IOException error) { throw new FileNotFoundException("Proxy unavailable"); }
        }
        if (!"success".equals(id)) throw new FileNotFoundException("Unknown fixture");
        return ParcelFileDescriptor.open(new File(getContext().getCacheDir(), "stage12-export.txt"),
            ParcelFileDescriptor.parseMode(mode));
    }
}
