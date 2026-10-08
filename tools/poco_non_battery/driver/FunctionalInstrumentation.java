package com.monumentogram.dora.stage86b.driver;

import android.app.*;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Bounded real-product smokes, run only after the main fixed campaign. */
public final class FunctionalInstrumentation extends CampaignInstrumentation {
  @Override public void onStart() {
    try {
      run=args.getString("run","");mode=args.getString("mode","");
      RuntimeAccess.check(run.matches("lite-functional-[a-z-]+-[0-9]{2}"),"FUNCTIONAL_RUN_ID");
      RuntimeAccess.check(Arrays.asList("notification","permission","storage-ui","recovery-seed","recovery-resume").contains(mode),"FUNCTIONAL_MODE");
      RuntimeAccess.check(Build.VERSION.SDK_INT==34,"POCO_API_PROFILE");
      r=new RuntimeAccess(this);String apk=args.getString("expectedApkSha256","");
      RuntimeAccess.check(RuntimeAccess.fileHash(new File(r.app.getApplicationInfo().sourceDir)).equals(apk),"APK_IDENTITY");
      directory=new File(r.app.getNoBackupFilesDir(),"stage86b-receipts");receipt=new File(directory,run+".json");
      receiptCreated=receipt.createNewFile();RuntimeAccess.check(receiptCreated,"RUN_EXISTS");
      evidence.put("run",run).put("mode",mode).put("apkSha256",apk).put("pid",android.os.Process.myPid());
      RuntimeAccess.check(r.quiescent(),"PREEXISTING_CAPTURE");activity();r.open();owners=r.inventory();
      Object recoveredIdentity=null;long prefix=0;
      if(mode.equals("recovery-resume")) {
        String reference=args.getString("referenceRun","");
        RuntimeAccess.check(reference.equals("lite-functional-recovery-seed-01"),"RECOVERY_REFERENCE");
        File priorFile=new File(directory,reference+".json");
        RuntimeAccess.check(RuntimeAccess.fileHash(priorFile).equals(args.getString("referenceReceiptSha256")),"RECOVERY_REFERENCE_HASH");
        JSONObject prior=new JSONObject(new String(java.nio.file.Files.readAllBytes(priorFile.toPath()),StandardCharsets.UTF_8));
        RuntimeAccess.check(prior.getString("phase").equals("RECOVERY_KILL_READY")&&prior.getString("apkSha256").equals(apk)
          &&prior.getString("ownerMapSha256").equals(args.getString("ownerMapSha256")),"RECOVERY_REFERENCE_BINDING");
        String owned=prior.getString("activeCampaignIdentity");
        RuntimeAccess.check(owners.size()==47&&owners.remove(owned)!=null,"RECOVERY_EXACT_ADDITION");
        Object recovery=findRecovery(owned);recoveredIdentity=RuntimeAccess.call(recovery,"getIdentity");
        prefix=RuntimeAccess.number(recovery,"getRecoveredFrames");
        RuntimeAccess.check(Boolean.TRUE.equals(RuntimeAccess.call(recovery,"getCanResume"))
          &&RuntimeAccess.call(recovery,"getCompletionState").toString().equals("RECOVERABLE_PARTIAL")
          &&prefix>=prior.getLong("durableFramesBeforeKill")&&prefix>0,"RECOVERY_PREFIX_OR_STATE");
        Thread.sleep(2000);RuntimeAccess.check(r.released(),"RECOVERY_AUTO_MICROPHONE");
        evidence.put("referenceRun",reference).put("referenceReceiptSha256",RuntimeAccess.fileHash(priorFile))
          .put("recoveredFrames",prefix).put("noAutoMicrophone",true).put("recoveryEntry","RECOVERABLE_PARTIAL");
      }
      RuntimeAccess.check(owners.size()==46,"OWNER_COUNT");StringBuilder ownerText=new StringBuilder();
      for(Map.Entry<String,String> e:owners.entrySet())ownerText.append(e.getKey()).append('=').append(e.getValue()).append('\n');
      RuntimeAccess.check(RuntimeAccess.hash(ownerText.toString()).equals(args.getString("ownerMapSha256")),"OWNER_INVENTORY_BINDING");
      evidence.put("ownerCount",46).put("ownerMapSha256",RuntimeAccess.hash(ownerText.toString()));save("OWNER_INVENTORY_VERIFIED");
      watchdog=new SafetyWatchdog(this);watchdog.arm(360000L);
      if(mode.equals("storage-ui"))storageUi();
      else if(mode.equals("permission")){permission();recording(null,0);evidence.put("retryAfterPermissionRestored",true);}
      else recording(recoveredIdentity,prefix);
      RuntimeAccess.check(r.inventory().equals(owners)&&r.quiescent(),"FINAL_PRESERVATION_OR_RELEASE");
      watchdog.healthy();watchdog.close();
      evidence.put("ownerInventoryUnchanged",true).put("microphoneReleased",true).put("fgsStopped",true);
      r.main(()->RuntimeAccess.call(r.runtime,"lock"));save("COMPLETE");finish(0,new Bundle());
    } catch(Exception failure) {
      try {
        evidence.put("errorClass",failure.getClass().getSimpleName());
        if(failure instanceof IllegalStateException&&failure.getMessage()!=null&&failure.getMessage().matches("[A-Z0-9_]+"))evidence.put("errorCode",failure.getMessage());
        if(receiptCreated)save("FAILED");
      }catch(Exception ignored){}
      try{if(watchdog!=null){watchdog.abort();watchdog.close();}}catch(Exception ignored){}
      finish(1,new Bundle());
    }
  }
  Object findRecovery(String owned)throws Exception {
    String after="";
    for(int page=0;page<4;page++) {
      AtomicReference<Object> answer=new AtomicReference<>();CountDownLatch done=new CountDownLatch(1);
      Object callback=r.function(1,values->{answer.set(values[0]);done.countDown();return r.unit();});String cursor=after;
      r.main(()->RuntimeAccess.call(r.runtime,"requestRecordingRecovery",r.activity,cursor,callback));
      RuntimeAccess.check(done.await(120,TimeUnit.SECONDS),"RECOVERY_PAGE_TIMEOUT");
      RuntimeAccess.check(answer.get().getClass().getSimpleName().equals("Value"),"RECOVERY_PAGE_FAILED");
      List<?> entries=(List<?>)RuntimeAccess.call(answer.get(),"getValue");
      for(Object entry:entries){Object identity=RuntimeAccess.call(entry,"getIdentity");
        if(identity!=null&&RuntimeAccess.identityHash(identity).equals(owned))return entry;
      }
      if(entries.size()<20)break;
      after=RuntimeAccess.call(entries.get(entries.size()-1),"getCursor").toString();
    }
    throw new IllegalStateException("RECOVERY_ENTRY_MISSING");
  }
  void requestStart(Object identity)throws Exception {
    startPending=true;invocationToken=null;save("START_ATTEMPT_COMMITTED");
    try{r.main(()->{watchdog.healthy();RuntimeAccess.call(r.controller,"start",r.activity,identity);
      invocationToken=(String)RuntimeAccess.call(r.controller,"getActionToken");RuntimeAccess.check(invocationToken!=null,"START_NO_TOKEN");
    });}finally{startPending=false;}
    evidence.put("invocationTokenFingerprint",RuntimeAccess.hash(invocationToken));save("PRODUCT_START_REQUESTED");
  }
  void recording(Object recoveredIdentity,long prefix)throws Exception {
    long before=RuntimeAccess.number(r.capture,"getAdmittedFrames");requestStart(recoveredIdentity);r.awaitPhase("RECORDING",120000);
    Object identity=RuntimeAccess.call(RuntimeAccess.field(r.controller,"session"),"getIdentity");
    String owned=RuntimeAccess.identityHash(identity);RuntimeAccess.check(!owners.containsKey(owned),"OWNER_COLLISION");
    if(recoveredIdentity!=null)RuntimeAccess.check(identity.equals(recoveredIdentity),"RECOVERY_IDENTITY_CHANGED");
    evidence.put("activeCampaignIdentity",owned).put("started",true);save("REAL_CAPTURE_STARTED");
    try(Telemetry telemetry=new Telemetry(r)) {
      Thread.sleep(5000);watchdog.healthy();evidence.put("vadProof",r.vadProof()).put("diagnostics",RuntimeAccess.call(r.controller,"diagnosticSummary"))
        .put("captureSample",telemetry.sample());
      if(mode.equals("recovery-seed")) {
        // A five-second transport unit is asynchronous: wait for its real commit,
        // never infer durability from elapsed capture time or stop/seal it artificially.
        long commitDeadline=SystemClock.elapsedRealtime()+30000;
        while(RuntimeAccess.number(r.state(),"getDurableFrames")==0&&SystemClock.elapsedRealtime()<commitDeadline){
          watchdog.healthy();RuntimeAccess.check(r.phase().equals("RECORDING"),"RECOVERY_CAPTURE_INTERRUPTED");Thread.sleep(50);
        }
        long durable=RuntimeAccess.number(r.state(),"getDurableFrames");RuntimeAccess.check(durable>0,"RECOVERY_NO_DURABLE_PREFIX");
        evidence.put("durableFramesBeforeKill",durable).put("admittedBeforeKill",RuntimeAccess.number(r.capture,"getAdmittedFrames")-before)
          .put("sampleBeforeKill",telemetry.sample());save("RECOVERY_KILL_READY");
        long deadline=SystemClock.elapsedRealtime()+60000;
        while(SystemClock.elapsedRealtime()<deadline){watchdog.healthy();Thread.sleep(100);}
        throw new IllegalStateException("RECOVERY_HOST_KILL_TIMEOUT");
      }
      if(mode.equals("notification")) {
        r.main(()->r.activity.moveTaskToBack(true));shell("input keyevent 223");
        long deadline=SystemClock.elapsedRealtime()+5000;
        while(telemetry.power.isInteractive()&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(50);
        RuntimeAccess.check(!telemetry.power.isInteractive(),"BACKGROUND_SCREEN_NOT_OFF");
        long backgroundFrames=RuntimeAccess.number(r.capture,"getAdmittedFrames");Thread.sleep(3000);
        RuntimeAccess.check(RuntimeAccess.number(r.capture,"getAdmittedFrames")>backgroundFrames&&r.fgs(),"BACKGROUND_CAPTURE_STOPPED");
        evidence.put("backgroundScreenOffSample",telemetry.sample());
        notificationAction(0);r.awaitPhase("PAUSED",30000);
        deadline=SystemClock.elapsedRealtime()+10000;
        while(r.live()&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(50);
        RuntimeAccess.check(!r.live()&&RuntimeAccess.field(r.capture,"recorder")==null,"NOTIFICATION_PAUSE_MIC_LIVE");
        evidence.put("notificationPause",true);
        activity("POST_WINDOW_HOST_LAUNCH_READY",true);r.main(()->RuntimeAccess.call(r.controller,"resume",r.activity));
        r.awaitPhase("RECORDING",120000);Thread.sleep(3000);
        evidence.put("explicitProductResume",true);notificationAction(1);
        deadline=SystemClock.elapsedRealtime()+10000;
        while(!Boolean.TRUE.equals(RuntimeAccess.call(r.state(),"getStopConfirmation"))&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(50);
        RuntimeAccess.check(Boolean.TRUE.equals(RuntimeAccess.call(r.state(),"getStopConfirmation")),"NOTIFICATION_STOP_CONFIRMATION_MISSING");
        evidence.put("notificationStopRequested",true);
      }else {
        r.main(()->RuntimeAccess.call(r.controller,"requestStop"));
      }
      r.main(()->RuntimeAccess.call(r.controller,"confirmStop"));r.awaitPhase("SAVED",120000);r.awaitRelease();
      long frames=RuntimeAccess.number(r.state(),"getFrames");
      RuntimeAccess.check(frames==prefix+RuntimeAccess.number(r.capture,"getAdmittedFrames")-before
        &&frames==RuntimeAccess.number(r.state(),"getDurableFrames")&&RuntimeAccess.number(r.capture,"getReadErrors")==0,"FUNCTIONAL_COUNTER_MISMATCH");
      evidence.put("finalFrames",frames).put("durableFrames",RuntimeAccess.number(r.state(),"getDurableFrames"))
        .put("admittedNewFrames",RuntimeAccess.number(r.capture,"getAdmittedFrames")-before).put("prefixFrames",prefix)
        .put("readErrors",RuntimeAccess.number(r.capture,"getReadErrors"))
        .put("afterStopDiagnostics",RuntimeAccess.call(r.controller,"diagnosticSummary"));
      r.open();evidence.put("readback",r.readback(identity,frames));save("FULL_READBACK_REDUCED");
      evidence.put("deletion",r.deleteOwned(identity,owners,owned)).put("finalized",true).put("wholeRecordingLost",false);
    }
  }
  void permission()throws Exception {
    Object original=RuntimeAccess.field(r.capture,"permitted");long before=RuntimeAccess.number(r.capture,"getAdmittedFrames");
    try {
      RuntimeAccess.setField(r.capture,"permitted",r.function(0,unused->false));requestStart(null);
      long deadline=SystemClock.elapsedRealtime()+120000;
      while(!r.phase().equals("INTERRUPTED")&&SystemClock.elapsedRealtime()<deadline){watchdog.healthy();Thread.sleep(50);}
      RuntimeAccess.check(r.phase().equals("INTERRUPTED"),"DENIED_START_NOT_HANDLED");r.awaitRelease();
      Object state=RuntimeAccess.call(RuntimeAccess.call(r.controller,"getState"),"getValue");
      RuntimeAccess.check(RuntimeAccess.call(state,"getFailure").toString().equals("PERMISSION_DENIED"),"DENIED_START_WRONG_ERROR");
      RuntimeAccess.check(RuntimeAccess.number(r.capture,"getAdmittedFrames")==before,"DENIED_START_ADMITTED_PCM");
      Object failedSession=RuntimeAccess.field(r.controller,"session");
      Object failedIdentity=failedSession==null?null:RuntimeAccess.call(failedSession,"getIdentity");
      r.open();TreeMap<String,String> current=r.inventory();
      for(String owner:owners.keySet())RuntimeAccess.check(owners.get(owner).equals(current.remove(owner)),"DENIAL_OWNER_CHANGED");
      RuntimeAccess.check(current.size()<=1,"DENIAL_UNEXPECTED_ASSETS");
      if(!current.isEmpty()) {
        String owned=current.firstKey();RuntimeAccess.check(current.get(owned).startsWith("0/"),"DENIAL_UNEXPECTED_AUDIO");
        RuntimeAccess.check(failedIdentity!=null&&RuntimeAccess.identityHash(failedIdentity).equals(owned)
          &&!owners.containsKey(owned),"DENIAL_OWNERSHIP_UNPROVEN");
        Object identity=failedIdentity;
        // No AudioRecord was constructed: the injected real permission boundary rejects
        // before native creation. Authenticate the catalog and prove no storage-unit
        // claim (including uncommitted claims) exists before deleting this empty shell.
        RuntimeAccess.check(((List<?>)RuntimeAccess.call(r.dao,"claims",RuntimeAccess.field(identity,"assetId"))).isEmpty(),"DENIAL_STORAGE_UNIT_EXISTS");
        evidence.put("deniedStartAssetDisposition","OWNED_EMPTY_LOGICAL_SHELL_NO_PCM_NO_STORAGE_UNITS")
          .put("deniedStartReadback","NOT_APPLICABLE_NO_AUDIO_UNITS")
          .put("deniedStartDeletion",r.deleteOwned(identity,owners,owned));
      }
      evidence.put("permissionDeniedHandled",true).put("admittedPcmFrames",0).put("permissionSettingsChanged",false);
    }finally{RuntimeAccess.setField(r.capture,"permitted",original);}
  }
  void storageUi()throws Exception {
    Object original=RuntimeAccess.field(r.controller,"freeStorageBytes");
    try {
      uiClick("Открыть экран записи");
      RuntimeAccess.check(uiFind("Резерв учитывается при запуске; место заранее не выделяется.",10000)!=null,"STORAGE_UI_RESERVE_EXPLANATION_MISSING");
      RuntimeAccess.check(uiFind("Для часа записи нужно 125 МБ и резерв на завершение 16 MiB: всего не менее 142 МБ.",5000)!=null,"STORAGE_UI_REQUIRED_BUDGET_MISSING");
      RuntimeAccess.setField(r.controller,"freeStorageBytes",r.function(0,unused->100000000L));
      uiClick("Обновить проверку места");
      RuntimeAccess.check(uiFind("Доступно на устройстве: 100 МБ",5000)!=null,"STORAGE_UI_FREE_VALUE_MISSING");
      RuntimeAccess.check(uiFind("Освободите место и обновите проверку. Сохранённые записи останутся доступны.",5000)!=null,"STORAGE_UI_ACTION_MISSING");
      evidence.put("storageUiRequiredBudgetVisible",true).put("storageUiInjectedFreeBytesVisible",100000000L)
        .put("storageUiRefreshWorked",true).put("storageUiActionVisible",true).put("physicalStorageFilled",false);
      storage();
    }finally {
      RuntimeAccess.setField(r.controller,"freeStorageBytes",original);
      uiClick("Обновить проверку места");
      RuntimeAccess.check(Boolean.TRUE.equals(RuntimeAccess.call(RuntimeAccess.call(r.controller,"storageBudget"),"getCanStart")),"STORAGE_SUPPLIER_NOT_RESTORED");
      evidence.put("storageSupplierRestored",true);
    }
  }
  android.view.accessibility.AccessibilityNodeInfo uiFind(String exact,long timeout)throws Exception {
    long deadline=SystemClock.elapsedRealtime()+timeout;
    do {
      watchdog.healthy();android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
      if(root!=null&&r.app.getPackageName().contentEquals(root.getPackageName()==null?"":root.getPackageName())) {
        android.view.accessibility.AccessibilityNodeInfo found=null;
        for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(exact))
          if(node.isVisibleToUser()&&node.getWindowId()==root.getWindowId()
            &&r.app.getPackageName().contentEquals(node.getPackageName()==null?"":node.getPackageName())
            &&(exact.contentEquals(node.getText()==null?"":node.getText())
              ||exact.contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))) {
            RuntimeAccess.check(found==null,"PRODUCT_UI_EXACT_MATCH_AMBIGUOUS");found=node;
          }
        if(found!=null)return found;
      }
      Thread.sleep(100);
    }while(SystemClock.elapsedRealtime()<deadline);
    return null;
  }
  void uiClick(String exact)throws Exception {
    android.view.accessibility.AccessibilityNodeInfo node=uiFind(exact,10000);
    RuntimeAccess.check(node!=null,"EXPECTED_PRODUCT_UI_ACTION_MISSING");
    int window=node.getWindowId();
    for(int depth=0;node!=null&&depth<4;depth++,node=node.getParent()) {
      RuntimeAccess.check(node.getWindowId()==window&&r.app.getPackageName().contentEquals(
        node.getPackageName()==null?"":node.getPackageName()),"PRODUCT_UI_ACTION_SCOPE_CHANGED");
      if(node.isClickable()){RuntimeAccess.check(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK),"PRODUCT_UI_CLICK_FAILED");return;}
    }
    throw new IllegalStateException("PRODUCT_UI_ACTION_NOT_CLICKABLE");
  }
  void notificationAction(int index)throws Exception {
    for(android.service.notification.StatusBarNotification n:r.app.getSystemService(NotificationManager.class).getActiveNotifications())
      if(n.getId()==8301){Notification.Action[] actions=n.getNotification().actions;
        RuntimeAccess.check(actions!=null&&actions.length==2,"NOTIFICATION_ACTIONS_MISSING");actions[index].actionIntent.send();return;
      }
    throw new IllegalStateException("NOTIFICATION_MISSING");
  }
  void shell(String command)throws Exception {
    try(ParcelFileDescriptor p=getUiAutomation().executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(p)){while(in.read()!=-1){}}
  }
}
