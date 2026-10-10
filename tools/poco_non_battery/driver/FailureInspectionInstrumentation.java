package com.monumentogram.dora.stage86b.driver;

import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Read-only authenticated catalog inspection after a failed owned long run. No Start/Resume/deletion. */
public final class FailureInspectionInstrumentation extends CampaignInstrumentation {
  @Override public void onStart(){
    try {
      run=args.getString("run","");mode="failure-inspect";
      RuntimeAccess.check(run.equals("lite-failure-inspect-01"),"INSPECTION_RUN_ID");
      r=new RuntimeAccess(this);String apk=args.getString("expectedApkSha256","");
      RuntimeAccess.check(RuntimeAccess.fileHash(new File(r.app.getApplicationInfo().sourceDir)).equals(apk),"APK_IDENTITY");
      directory=new File(r.app.getNoBackupFilesDir(),"stage86b-receipts");receipt=new File(directory,run+".json");
      receiptCreated=receipt.createNewFile();RuntimeAccess.check(receiptCreated,"RUN_EXISTS");
      File priorFile=new File(directory,"dora-long-01.json");
      RuntimeAccess.check(RuntimeAccess.fileHash(priorFile).equals(args.getString("referenceReceiptSha256")),"FAILED_RECEIPT_BINDING");
      JSONObject prior=new JSONObject(new String(java.nio.file.Files.readAllBytes(priorFile.toPath()),StandardCharsets.UTF_8));
      RuntimeAccess.check(prior.getString("run").equals("dora-long-01")&&prior.getString("phase").equals("FAILED")
        &&prior.getString("apkSha256").equals(apk)&&prior.getString("errorCode").equals("LONG_CAPTURE_INTERRUPTED"),"FAILED_REFERENCE_SHAPE");
      String owned=prior.getString("activeCampaignIdentity");
      evidence.put("run",run).put("mode",mode).put("apkSha256",apk).put("referenceRun","dora-long-01")
        .put("referenceReceiptSha256",RuntimeAccess.fileHash(priorFile));
      RuntimeAccess.check(r.quiescent(),"PREEXISTING_CAPTURE");activity();r.open();
      TreeMap<String,String> before=r.inventory();owners=new TreeMap<>(before);
      RuntimeAccess.check(owners.size()==47&&owners.remove(owned)!=null,"FAILED_SOURCE_OR_OWNER_COUNT");
      StringBuilder baseline=new StringBuilder();for(Map.Entry<String,String> e:owners.entrySet())baseline.append(e.getKey()).append('=').append(e.getValue()).append('\n');
      RuntimeAccess.check(RuntimeAccess.hash(baseline.toString()).equals(args.getString("ownerMapSha256")),"OWNER_INVENTORY_BINDING");
      watchdog=new SafetyWatchdog(this);watchdog.arm(120000L);
      Object identity=null;for(Object candidate:r.identities())if(RuntimeAccess.identityHash(candidate).equals(owned)){RuntimeAccess.check(identity==null,"DUPLICATE_SOURCE");identity=candidate;}
      RuntimeAccess.check(identity!=null,"FAILED_SOURCE_MISSING");
      long frames=0;int committed=0,pending=0;boolean seenPending=false;
      List<?> claims=(List<?>)RuntimeAccess.call(r.dao,"claims",RuntimeAccess.field(identity,"assetId"));
      for(Object claim:claims){
        if(Boolean.TRUE.equals(RuntimeAccess.call(claim,"getCommitted"))){
          RuntimeAccess.check(!seenPending&&RuntimeAccess.number(claim,"getFirstFrame")==frames,"CATALOG_PREFIX_NOT_CONTIGUOUS");
          frames+=RuntimeAccess.number(claim,"getFrames");committed++;
        }else{seenPending=true;pending++;}
      }
      Object asset=RuntimeAccess.call(r.dao,"asset",RuntimeAccess.field(identity,"assetId"));
      evidence.put("originalRecordingsPreserved",46).put("ownerMapSha256",RuntimeAccess.hash(baseline.toString()))
        .put("ownedFailedSourceFingerprint",owned).put("catalogCommittedFrames",frames).put("committedClaims",committed)
        .put("pendingClaims",pending).put("catalogFinalized",RuntimeAccess.call(asset,"getFinalized"))
        .put("catalogPrefixContiguous",true).put("authenticatedAudioReadback","NOT_RUN_PENDING_FAILURE_INVESTIGATION")
        .put("sourceRetained",true).put("deletionAttempted",false).put("recordingStarted",false);
      RuntimeAccess.check(r.inventory().equals(before)&&r.quiescent(),"READ_ONLY_INSPECTION_CHANGED_STATE");
      watchdog.healthy();watchdog.close();evidence.put("ownerInventoryUnchanged",true).put("microphoneReleased",true).put("fgsStopped",true);
      r.main(()->RuntimeAccess.call(r.runtime,"lock"));save("COMPLETE");finish(0,new Bundle());
    }catch(Exception failure){
      try{evidence.put("errorClass",failure.getClass().getSimpleName());if(failure.getMessage()!=null&&failure.getMessage().matches("[A-Z0-9_]+"))evidence.put("errorCode",failure.getMessage());if(receiptCreated)save("FAILED");}catch(Exception ignored){}
      try{if(watchdog!=null){watchdog.abort();watchdog.close();}}catch(Exception ignored){}
      finish(1,new Bundle());
    }
  }
}
