package com.monumentogram.dora.transcript;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.DocumentsContract;

/** Grants only these synthetic test documents, from their owning test APK UID. */
public final class GrantExportReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String target = intent.getStringExtra("target");
        if (!"com.monumentogram.dora.debug".equals(target)
                && !"com.monumentogram.dora.alpha.preview".equals(target)
                && !"com.monumentogram.dora.alpha.pilot".equals(target)
                && !"com.monumentogram.dora.alpha.test".equals(target)) return;
        for (String id : new String[]{"refuse", "full", "success"}) {
            context.grantUriPermission(target, DocumentsContract.buildDocumentUri(
                context.getPackageName() + ".stage12.documents", id),
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
    }
}
