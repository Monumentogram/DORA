package com.monumentogram.dora.stage86b.driver;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Isolated opt-in driver. No fake PCM, auth grant, alternate recorder or battery oracle. */
public class CampaignInstrumentation extends Instrumentation {
  Bundle args;
  RuntimeAccess r;
  File directory,receipt;
  JSONObject evidence=new JSONObject();
  String run,mode;
  JSONObject activeAttempt;
  boolean receiptCreated;
  String invocationToken;
  String startupStep="ARGUMENTS";
  SafetyWatchdog watchdog;
  volatile boolean startPending;
  TreeMap<String,String> owners;
  int expectedOwnerCount=46;
  int configuredOwnerCount(){return args.getString("protectedPolicySha256","").isEmpty()?46:47;}
  void protectedOwnerPolicy()throws Exception {
    String pin=args.getString("protectedPolicySha256","");
    if(pin.isEmpty())return;
    RuntimeAccess.check(pin.matches("[a-f0-9]{64}"),"PROTECTED_POLICY_PIN");
    File policy=new File(r.app.getNoBackupFilesDir(),"stage86-protected-policy.json");
    RuntimeAccess.check(policy.isFile()&&RuntimeAccess.fileHash(policy).equals(pin),"PROTECTED_POLICY_FILE");
    try(InputStream input=r.app.getAssets().open("dora-protected-policy.sha256")) {
      byte[] bytes=new byte[67];int count=0,read;
      while(count<bytes.length&&(read=input.read(bytes,count,bytes.length-count))!=-1)count+=read;
      RuntimeAccess.check(count>=64&&count<=66&&input.read()==-1
        &&new String(bytes,0,count,StandardCharsets.US_ASCII).trim().equals(pin),"PROTECTED_APK_PIN");
    }
    expectedOwnerCount=47;
    int protectedCount=0;
    for(Object identity:r.identities())if(owners.containsKey(RuntimeAccess.identityHash(identity))) {
      RuntimeAccess.check(Boolean.TRUE.equals(RuntimeAccess.call(r.journal,"isProtected",identity)),"PROTECTED_SOURCE_FENCE_MISSING");
      protectedCount++;
    }
    RuntimeAccess.check(protectedCount==47,"PROTECTED_SOURCE_COUNT");
    evidence.put("protectedPolicySha256",pin).put("protectedHistoricalSources",protectedCount);
  }
  @Override public void onCreate(Bundle values){super.onCreate(values);args=values;start();}
  void save(String phase)throws Exception {
    evidence.put("phase",phase).put("updatedElapsedMs",SystemClock.elapsedRealtime());
    File temp=new File(receipt.getPath()+".tmp");
    try(FileOutputStream out=new FileOutputStream(temp)){out.write(evidence.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
    RuntimeAccess.check(temp.renameTo(receipt),"RECEIPT_RENAME");
    Bundle progress=new Bundle();progress.putString("stage86b",phase);sendStatus(2,progress);
  }
  void writeRun(JSONObject row)throws Exception {
    File target=new File(directory,run+"-"+String.format(Locale.ROOT,"%03d",row.getInt("attempt"))+".json");
    RuntimeAccess.check(target.createNewFile(),"ATTEMPT_RECEIPT_EXISTS");
    try(FileOutputStream out=new FileOutputStream(target)){out.write(row.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
  }
  void activity()throws Exception {activity("HOST_LAUNCH_READY",false);}
  void activity(String phase,boolean wake)throws Exception {
    AtomicReference<Activity> ref=new AtomicReference<>();CountDownLatch ready=new CountDownLatch(1);
    Application.ActivityLifecycleCallbacks callbacks=new Application.ActivityLifecycleCallbacks(){
      public void onActivityResumed(Activity a){if(a.getClass().getName().equals("com.monumentogram.dora.MainActivity")){ref.set(a);ready.countDown();}}
      public void onActivityCreated(Activity a,Bundle b){} public void onActivityStarted(Activity a){}
      public void onActivityPaused(Activity a){} public void onActivityStopped(Activity a){}
      public void onActivitySaveInstanceState(Activity a,Bundle b){} public void onActivityDestroyed(Activity a){}
    };
    r.main(()->r.app.registerActivityLifecycleCallbacks(callbacks));save(phase);
    try{
      if(wake)try(ParcelFileDescriptor p=getUiAutomation().executeShellCommand("input keyevent 224")){
        try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(p)){while(in.read()!=-1){}}
      }
      RuntimeAccess.check(ready.await(90,TimeUnit.SECONDS),"HOST_LAUNCH_TIMEOUT");r.activity=ref.get();
    }
    finally{r.main(()->r.app.unregisterActivityLifecycleCallbacks(callbacks));}
  }
  @Override public void onStart(){
    try {
      run=args.getString("run","");mode=args.getString("mode","");
      RuntimeAccess.check(run.matches("[a-z0-9-]{3,48}"),"RUN_ID");
      RuntimeAccess.check(Arrays.asList("preflight","storage","smoke","screen-smoke","lite-screen-smoke","lite-cycles","lite-long","cycles","long","readback-cleanup").contains(mode),"MODE");
      RuntimeAccess.check(Build.VERSION.SDK_INT==34,"POCO_API_PROFILE");
      startupStep="RUNTIME_REFLECTION";r=new RuntimeAccess(this);
      startupStep="APK_VERIFICATION";
      String apk=args.getString("expectedApkSha256","");
      RuntimeAccess.check(apk.matches("[0-9a-f]{64}")&&RuntimeAccess.fileHash(new File(r.app.getApplicationInfo().sourceDir)).equals(apk),"APK_IDENTITY");
      startupStep="RECEIPT_CREATION";directory=new File(r.app.getNoBackupFilesDir(),"stage86b-receipts");
      RuntimeAccess.check(directory.isDirectory()||directory.mkdir(),"RECEIPT_DIRECTORY");
      receipt=new File(directory,run+".json");receiptCreated=receipt.createNewFile();RuntimeAccess.check(receiptCreated,"RUN_EXISTS");
      evidence.put("run",run).put("mode",mode).put("apkSha256",apk).put("pid",android.os.Process.myPid());
      JSONArray inputs=new JSONArray();
      for(android.media.AudioDeviceInfo device:r.app.getSystemService(android.media.AudioManager.class).getDevices(android.media.AudioManager.GET_DEVICES_INPUTS))inputs.put(device.getType());
      evidence.put("inputDeviceTypes",inputs)
        .put("micPermissionGranted",r.app.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED)
        .put("thermalStart",r.app.getSystemService(PowerManager.class).getCurrentThermalStatus())
        .put("batterySaver",r.app.getSystemService(PowerManager.class).isPowerSaveMode());
      RuntimeAccess.check(r.released()&&!Arrays.asList("PREPARING","RECORDING","PAUSED","FINALIZING").contains(r.phase()),"PREEXISTING_CAPTURE");activity();save("AUTH_OPEN_REQUESTED");r.open();
      owners=r.inventory();
      Object cleanupIdentity=null;
      if(mode.equals("readback-cleanup")) {
        String reference=args.getString("referenceRun","");
        RuntimeAccess.check(reference.matches("(?:(?:lite-)?screen-)?smoke-[a-z0-9-]{1,32}"),"CLEANUP_REFERENCE_ID");
        File referenceFile=new File(directory,reference+".json");
        RuntimeAccess.check(referenceFile.isFile()&&referenceFile.length()<2000000,"CLEANUP_REFERENCE_FILE");
        JSONObject prior=new JSONObject(new String(java.nio.file.Files.readAllBytes(referenceFile.toPath()),StandardCharsets.UTF_8));
        RuntimeAccess.check(prior.getString("run").equals(reference)&&Arrays.asList("smoke","screen-smoke","lite-screen-smoke").contains(prior.getString("mode"))
          &&prior.getString("apkSha256").equals(apk)&&prior.getString("phase").equals("FAILED")
          &&prior.getInt("ownerCount")==configuredOwnerCount()&&prior.getString("ownerMapSha256").equals(args.getString("ownerMapSha256"))
          &&prior.optString("protectedPolicySha256","").equals(args.getString("protectedPolicySha256","")),"CLEANUP_REFERENCE_BINDING");
        String owned=prior.getString("activeCampaignIdentity");JSONObject attempt=prior.getJSONObject("activeAttempt");
        RuntimeAccess.check(owned.matches("[0-9a-f]{64}")&&owned.equals(attempt.getString("campaignIdentity"))
          &&attempt.getBoolean("started")&&!attempt.getBoolean("ownerIdentity")&&attempt.getString("assetDisposition").equals("OWNED"),"CLEANUP_OWNERSHIP_RECEIPT");
        RuntimeAccess.check(owners.size()==configuredOwnerCount()+1&&owners.remove(owned)!=null,"CLEANUP_EXACT_ADDITION");
        for(Object candidate:r.identities())if(RuntimeAccess.identityHash(candidate).equals(owned)){
          RuntimeAccess.check(cleanupIdentity==null,"CLEANUP_DUPLICATE_IDENTITY");cleanupIdentity=candidate;
        }
        RuntimeAccess.check(cleanupIdentity!=null,"CLEANUP_SOURCE_MISSING");
        evidence.put("referenceRun",reference).put("referenceReceiptSha256",RuntimeAccess.fileHash(referenceFile)).put("campaignIdentity",owned);
      }
      protectedOwnerPolicy();
      RuntimeAccess.check(owners.size()==expectedOwnerCount,"OWNER_COUNT");
      // Binding uses the exact owner map supplied from the authenticated pre-upgrade inventory.
      StringBuilder ownerText=new StringBuilder();
      for(Map.Entry<String,String> entry:owners.entrySet())ownerText.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
      String ownerHash=RuntimeAccess.hash(ownerText.toString());
      RuntimeAccess.check(ownerHash.equals(args.getString("ownerMapSha256")),"OWNER_INVENTORY_BINDING");
      evidence.put("ownerCount",owners.size()).put("ownerMapSha256",ownerHash);save("OWNER_INVENTORY_VERIFIED");
      if(expectedOwnerCount==47&&mode.equals("lite-screen-smoke"))storage();
      if(mode.startsWith("lite-"))watchdog=new SafetyWatchdog(this);
      if(mode.equals("lite-cycles"))r.main(()->r.activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
      if(cleanupIdentity!=null) {
        String owned=RuntimeAccess.identityHash(cleanupIdentity);String summary=r.inventory().get(owned);
        RuntimeAccess.check(summary!=null&&summary.endsWith("/true"),"CLEANUP_SOURCE_NOT_FINALIZED");
        long frames=Long.parseLong(summary.substring(0,summary.indexOf('/')));
        RuntimeAccess.check(frames>0,"CLEANUP_SOURCE_EMPTY");
        evidence.put("readback",r.readback(cleanupIdentity,frames));save("EXISTING_TEST_SOURCE_READBACK_VERIFIED");
        evidence.put("deletion",r.deleteOwned(cleanupIdentity,owners,owned));save("EXISTING_TEST_SOURCE_DELETED_VERIFIED");
      }
      if(mode.equals("storage"))storage();
      if(Arrays.asList("smoke","screen-smoke","lite-screen-smoke","lite-cycles","lite-long","cycles","long").contains(mode)) {
        int count=mode.equals("cycles")?200:mode.equals("lite-cycles")?60:1;
        int first=Integer.parseInt(args.getString("firstAttempt","1"));
        RuntimeAccess.check(first>=1&&first<=count,"ATTEMPT_RANGE");
        if(first>1)verifyCompletedPrefix(first,apk);
        evidence.put("firstAttempt",first).put("completedAttempts",first-1);
        evidence.put("plannedAttempts",count);save("CAMPAIGN_PREDECLARED");
        for(int number=first;number<=count;number++) {
          JSONObject row=record(number,mode.endsWith("long")||mode.endsWith("screen-smoke"));writeRun(row);activeAttempt=null;
          if(mode.equals("lite-cycles"))RuntimeAccess.check(row.getBoolean("started")&&row.getBoolean("finalized"),"REDUCED_START_FINALIZE_GATE_FAILED");
          evidence.put("completedAttempts",number);save("ATTEMPT_COMPLETE");Thread.sleep(2000);
        }
      }
      RuntimeAccess.check(r.inventory().equals(owners)&&r.released(),"FINAL_PRESERVATION_OR_RELEASE");
      if(mode.equals("lite-cycles"))r.main(()->r.activity.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
      if(watchdog!=null){watchdog.healthy();watchdog.close();evidence.put("watchdog","CLOSED_SAFE");}
      evidence.put("ownerInventoryUnchanged",true).put("microphoneReleased",true).put("fgsStopped",true);
      r.main(()->RuntimeAccess.call(r.runtime,"lock"));save("COMPLETE");finish(0,new Bundle());
    }catch(Exception failure){
      try{if(mode.equals("lite-cycles")&&r.activity!=null)r.main(()->r.activity.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));}catch(Exception ignored){}
      try {
        // Preserve type only: arbitrary exception text could contain paths/identities.
        evidence.put("errorClass",failure.getClass().getSimpleName());
        if(failure instanceof IllegalStateException && failure.getMessage()!=null
            && failure.getMessage().matches("[A-Z0-9_]+"))evidence.put("errorCode",failure.getMessage());
        if(activeAttempt!=null){activeAttempt.put("terminal","FAILED_OR_INCOMPLETE");writeRun(activeAttempt);}
        if(receiptCreated)save("FAILED");
      }catch(Exception ignored){}
      try{if(ownsCurrentCapture()){
        r.main(()->{RuntimeAccess.call(r.controller,"requestStop");RuntimeAccess.call(r.controller,"confirmStop");});r.awaitRelease();
      }}catch(Exception ignored){}
      try{if(watchdog!=null){watchdog.abort();watchdog.close();}}catch(Exception ignored){}
      Bundle terminal=new Bundle();terminal.putString("startupStep",startupStep);
      terminal.putString("errorClass",failure.getClass().getSimpleName());
      if(failure.getCause()!=null)terminal.putString("causeClass",failure.getCause().getClass().getSimpleName());
      if(failure instanceof IllegalStateException&&failure.getMessage()!=null&&failure.getMessage().matches("[A-Z0-9_]+"))terminal.putString("errorCode",failure.getMessage());
      finish(1,terminal);
    }
  }
  void verifyCompletedPrefix(int first,String apk)throws Exception {
    RuntimeAccess.check(mode.equals("lite-cycles"),"CONTINUATION_MODE");
    String priorRun=args.getString("continuationOf","");
    RuntimeAccess.check(priorRun.equals("lite-cycles-01"),"CONTINUATION_ID");
    File source=new File(directory,priorRun+".json");
    RuntimeAccess.check(RuntimeAccess.fileHash(source).equals(args.getString("continuationReceiptSha256")),"CONTINUATION_RECEIPT_SHA");
    JSONObject prior=new JSONObject(new String(java.nio.file.Files.readAllBytes(source.toPath()),StandardCharsets.UTF_8));
    RuntimeAccess.check(prior.getString("phase").equals("FAILED")&&prior.getString("errorCode").equals("WATCHDOG_ABORTED")
      &&prior.getInt("completedAttempts")==first-1&&prior.getInt("currentAttempt")==first-1
      &&prior.getString("apkSha256").equals(apk)&&prior.getString("ownerMapSha256").equals(evidence.getString("ownerMapSha256")),"CONTINUATION_BOUNDARY");
    RuntimeAccess.check(!new File(directory,priorRun+"-"+String.format(Locale.ROOT,"%03d",first)+".json").exists(),"CONTINUATION_ALREADY_ATTEMPTED");
    HashSet<String> identities=new HashSet<>();
    for(int number=1;number<first;number++) {
      File file=new File(directory,priorRun+"-"+String.format(Locale.ROOT,"%03d",number)+".json");
      JSONObject row=new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
      long frames=row.getLong("readbackFrames");
      RuntimeAccess.check(row.getInt("attempt")==number&&row.getBoolean("started")&&row.getBoolean("finalized")
        &&!row.getBoolean("wholeRecordingLost")&&!row.getBoolean("ownerIdentity")&&frames>=80000
        &&row.getLong("admittedFrames")==frames&&row.getLong("durableFrames")==frames
        &&row.getLong("unexplainedGaps")==0&&row.getLong("unexplainedDuplicates")==0&&row.getLong("corruptSegments")==0
        &&row.getLong("readErrors")==0&&row.getBoolean("microphoneReleased")&&row.getBoolean("fgsStopped")
        &&row.getJSONObject("deletion").getBoolean("productDeletionCompleted")
        &&row.getJSONObject("deletion").getInt("ownerRecordingsPreserved")==expectedOwnerCount
        &&identities.add(row.getString("campaignIdentity")),"CONTINUATION_PREFIX_NOT_ACCEPTABLE");
    }
    evidence.put("continuationOf",priorRun).put("continuationReceiptSha256",RuntimeAccess.fileHash(source));
  }
  boolean ownsCurrentCapture()throws Exception {
    if(r==null||owners==null||invocationToken==null||!invocationToken.equals(RuntimeAccess.call(r.controller,"getActionToken")))return false;
    Object current=RuntimeAccess.field(r.controller,"session");
    if(current==null)current=RuntimeAccess.field(r.controller,"access");
    if(current==null)return false;
    String identity=RuntimeAccess.identityHash(RuntimeAccess.call(current,"getIdentity"));
    return !owners.containsKey(identity)&&evidence.optString("invocationTokenFingerprint").equals(RuntimeAccess.hash(invocationToken));
  }
  boolean ownsPendingOrCurrentStart()throws Exception {
    if(r==null||owners==null||invocationToken==null||!invocationToken.equals(RuntimeAccess.call(r.controller,"getActionToken")))return false;
    if(!evidence.optString("invocationTokenFingerprint").equals(RuntimeAccess.hash(invocationToken)))return false;
    Object current=RuntimeAccess.field(r.controller,"session");
    if(current==null)current=RuntimeAccess.field(r.controller,"access");
    return current==null||!owners.containsKey(RuntimeAccess.identityHash(RuntimeAccess.call(current,"getIdentity")));
  }
  void storage()throws Exception {
    Object original=RuntimeAccess.field(r.controller,"freeStorageBytes");
    long admittedBefore=RuntimeAccess.number(r.capture,"getAdmittedFrames");
    long diagnosticStartNanos=System.nanoTime();
    try {
      Object low=r.function(0,unused->100000000L);RuntimeAccess.setField(r.controller,"freeStorageBytes",low);
      RuntimeAccess.check(!Boolean.TRUE.equals(RuntimeAccess.call(RuntimeAccess.call(r.controller,"storageBudget"),"getCanStart")),"LOW_STORAGE_BUDGET");
      r.main(()->RuntimeAccess.call(r.controller,"start",r.activity,null));
      long deadline=SystemClock.elapsedRealtime()+10000;
      while(!r.phase().equals("INTERRUPTED")&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(50);
      RuntimeAccess.check(r.phase().equals("INTERRUPTED"),"LOW_STORAGE_START_NOT_REJECTED");
      Object view=RuntimeAccess.call(RuntimeAccess.call(r.controller,"getState"),"getValue");
      RuntimeAccess.check(RuntimeAccess.call(view,"getFailure").toString().equals("STORAGE_FULL"),"LOW_STORAGE_WRONG_ACTION");
      RuntimeAccess.check(r.released()&&r.inventory().equals(owners),"LOW_STORAGE_MUTATED_DATA");
      RuntimeAccess.check(RuntimeAccess.number(r.capture,"getAdmittedFrames")==admittedBefore,"LOW_STORAGE_ADMITTED_PCM");
      evidence.put("injectedFreeBytes",100000000L).put("requiredBytes",141777216L)
        .put("unsafeStartRejected",true).put("noPartialAsset",true).put("ownerInventoryUnchanged",true);
      if(expectedOwnerCount==47)terminalReceiptSmoke(diagnosticStartNanos);
      save("LOW_STORAGE_VERIFIED");
    }finally{RuntimeAccess.setField(r.controller,"freeStorageBytes",original);}
  }
  void terminalReceiptSmoke(long started)throws Exception {
    File target=new File(r.app.getNoBackupFilesDir(),"recording-terminal-v1.txt");
    long deadline=SystemClock.elapsedRealtime()+10000;String text="";
    Object diagnostics=RuntimeAccess.field(r.controller,"terminalDiagnostics");
    do {
      if(target.isFile()&&target.length()>0&&target.length()<=4096)
        text=new String(java.nio.file.Files.readAllBytes(target.toPath()),StandardCharsets.UTF_8);
      if(text.equals(RuntimeAccess.call(diagnostics,"dump")))break;
      Thread.sleep(50);
    }while(SystemClock.elapsedRealtime()<deadline);
    RuntimeAccess.check(text.equals(RuntimeAccess.call(diagnostics,"dump"))
      &&text.matches("[A-Za-z0-9_= -]{1,4096}"),"TERMINAL_RECEIPT_ASYNC_WRITE");
    TerminalReceiptCheck.requireNoCapture(text,started);
    evidence.put("terminalDiagnosticInjection","CONTROLLED_STORAGE_PREFLIGHT_NO_AUDIO")
      .put("terminalDiagnosticReceipt",text).put("terminalDiagnosticAsyncFileVerified",true);
  }
  JSONObject record(int attempt,boolean longRun)throws Exception {
    if(watchdog!=null){watchdog.healthy();watchdog.arm(mode.equals("lite-long")?5400000L:240000L);}
    JSONObject row=new JSONObject().put("attempt",attempt).put("preconditionValid",false)
      .put("started",false).put("finalized",false).put("wholeRecordingLost",JSONObject.NULL)
      .put("run",longRun?run.toUpperCase(Locale.ROOT):run).put("pid",android.os.Process.myPid());
    activeAttempt=row;
    RuntimeAccess.check(r.released()&&r.inventory().equals(owners),"ATTEMPT_PRECONDITION");
    RuntimeAccess.check(r.app.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED,"MIC_PERMISSION");
    RuntimeAccess.check(Boolean.TRUE.equals(RuntimeAccess.call(RuntimeAccess.call(r.controller,"storageBudget"),"getCanStart")),"STORAGE_PRECONDITION");
    row.put("preconditionValid",true);evidence.put("activeAttempt",row);
    evidence.put("currentAttempt",attempt).put("attemptStartElapsedMs",SystemClock.elapsedRealtime());save("START_ATTEMPT_COMMITTED");
    long admittedBefore=RuntimeAccess.number(r.capture,"getAdmittedFrames"),readsBefore=RuntimeAccess.number(r.capture,"getReadErrors"),shortBefore=RuntimeAccess.number(r.capture,"getShortReads");
    File vaultRoot=new File(r.app.getNoBackupFilesDir(),"dora-vault-v1");
    TreeMap<String,long[]> before=Telemetry.storage(vaultRoot);
    row.put("startingFreeBytes",RuntimeAccess.number(r.controller,"availableBytes"));
    try(Telemetry telemetry=new Telemetry(r)) {
      row.put("startRequestedElapsedMs",SystemClock.elapsedRealtime());
      invocationToken=null;
      startPending=true;
      try{r.main(()->{
        if(watchdog!=null)watchdog.healthy();
        Object previous=RuntimeAccess.call(r.controller,"getActionToken");
        RuntimeAccess.call(r.controller,"start",r.activity,null);
        Object selected=RuntimeAccess.call(r.controller,"getActionToken");
        RuntimeAccess.check(selected!=null&&!selected.equals(previous),"START_IGNORED_NO_OWNERSHIP");
        invocationToken=(String)selected;
      });}finally{startPending=false;}
      evidence.put("invocationTokenFingerprint",RuntimeAccess.hash(invocationToken));save("PRODUCT_START_REQUESTED");
      try {
        r.awaitPhase("RECORDING",120000);
      } catch(IllegalStateException failure) {
        // A handled failure with no created asset remains in the fixed denominator.
        // Anything ambiguous retains the partial receipt and halts; never invent loss=0.
        if(!r.phase().equals("INTERRUPTED"))throw failure;
        r.awaitRelease();r.open();
        if(!r.inventory().equals(owners)||RuntimeAccess.number(r.capture,"getAdmittedFrames")!=admittedBefore)throw failure;
        Object view=RuntimeAccess.call(RuntimeAccess.call(r.controller,"getState"),"getValue");
        Object reason=RuntimeAccess.call(view,"getFailure");
        RuntimeAccess.check(reason!=null,"UNCLASSIFIED_START_FAILURE");
        row.put("handledStartFailure",reason.toString()).put("assetDisposition","NO_ASSET_VERIFIED")
          .put("campaignIdentity",JSONObject.NULL).put("ownerIdentity",false).put("wholeRecordingLost",false)
          .put("microphoneReleased",true).put("fgsStopped",true).put("readbackFrames",0);
        return row;
      }
      long confirmed=SystemClock.elapsedRealtime();row.put("started",true).put("startConfirmedElapsedMs",confirmed);
      Object identity=RuntimeAccess.call(RuntimeAccess.field(r.controller,"session"),"getIdentity");
      String owned=RuntimeAccess.identityHash(identity);RuntimeAccess.check(!owners.containsKey(owned),"OWNER_IDENTITY_COLLISION");
      row.put("campaignIdentity",owned).put("ownerIdentity",false).put("assetDisposition","OWNED");
      evidence.put("activeCampaignIdentity",owned);save("REAL_CAPTURE_STARTED");
      JSONArray samples=new JSONArray();
      if(longRun) {
        save("SCREEN_OFF_REQUESTED");
        try(ParcelFileDescriptor p=getUiAutomation().executeShellCommand("input keyevent 223")){
          try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(p)){while(in.read()!=-1){}}
        }
        long deadline=SystemClock.elapsedRealtime()+10000;
        while(telemetry.power.isInteractive()&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(50);
        telemetry.drain();RuntimeAccess.check(!telemetry.power.isInteractive(),"SCREEN_OFF_FAILED");
        JSONObject first=telemetry.sample();long start=first.getLong("elapsedMs");samples.put(first);
        row.put("screenOffStartMs",start);save("SCREEN_OFF_WINDOW_STARTED");
        long next=start+30000;
        long minimumScreenOffMs=mode.endsWith("screen-smoke")?30000L:3600000L;
        row.put("minimumScreenOffMs",minimumScreenOffMs);
        while(SystemClock.elapsedRealtime()<start+minimumScreenOffMs) {
          Thread.sleep(Math.min(1000,Math.max(1,next-SystemClock.elapsedRealtime())));
          RuntimeAccess.check(r.phase().equals("RECORDING"),"LONG_CAPTURE_INTERRUPTED");
          if(watchdog!=null)watchdog.healthy();
          if(SystemClock.elapsedRealtime()>=next){samples.put(telemetry.sample());next+=30000;
            evidence.put("screenOffSamples",samples).put("screenThermalEvents",telemetry.events());save("SCREEN_OFF_WINDOW_RUNNING");}
        }
        telemetry.drain();JSONObject last=telemetry.sample();
        if(last.getLong("elapsedMs")>samples.getJSONObject(samples.length()-1).getLong("elapsedMs"))samples.put(last);
        row.put("screenOffEndMs",last.getLong("elapsedMs")).put("samples",samples).put("events",telemetry.events()).put("callbackCoverage",true);
        save("FULL_SCREEN_OFF_WINDOW_FINISHED");
      } else {
        Thread.sleep(mode.equals("lite-cycles")?5000:3000);
        if(mode.equals("lite-cycles")) {
          long frameDeadline=SystemClock.elapsedRealtime()+5000;
          while(RuntimeAccess.number(r.capture,"getAdmittedFrames")-admittedBefore<80000&&SystemClock.elapsedRealtime()<frameDeadline){
            watchdog.healthy();RuntimeAccess.check(r.phase().equals("RECORDING"),"SHORT_CAPTURE_INTERRUPTED");Thread.sleep(20);
          }
          RuntimeAccess.check(RuntimeAccess.number(r.capture,"getAdmittedFrames")-admittedBefore>=80000,"SHORT_CAPTURE_FRAME_TARGET_NOT_REACHED");
        }
        samples.put(telemetry.sample());row.put("samples",samples);
      }
      String diagnostics=RuntimeAccess.call(r.controller,"diagnosticSummary").toString();
      row.put("beforeStopDiagnostics",diagnostics);
      java.util.regex.Matcher inference=java.util.regex.Pattern.compile("(?:^|\\s)vadInference=(\\d+)").matcher(diagnostics);
      RuntimeAccess.check(inference.find(),"VAD_INFERENCE_COUNTER_MISSING");
      row.put("vadInference",Long.parseLong(inference.group(1)));
      RuntimeAccess.check(row.getLong("vadInference")>0,"VAD_NO_INFERENCE_OBSERVED");
      row.put("vadProof",r.vadProof());
      row.put("vadRuntime","PINNED_SHERPA_SILERO");
      row.put("stopRequestedElapsedMs",SystemClock.elapsedRealtime());
      r.main(()->{RuntimeAccess.call(r.controller,"requestStop");RuntimeAccess.call(r.controller,"confirmStop");});
      r.awaitPhase("SAVED",120000);r.awaitRelease();row.put("stopCompletedElapsedMs",SystemClock.elapsedRealtime());
      telemetry.close();
      row.put("callbackRegisteredMs",telemetry.registeredMs).put("callbackBarrierMs",telemetry.barrierMs)
        .put("callbackUnregisteredMs",telemetry.unregisteredMs).put("finalEventsDrained",true).put("events",telemetry.events());
      int nativeSession=samples.getJSONObject(0).getInt("nativeSession");boolean absent=true;
      for(android.media.AudioRecordingConfiguration configuration:r.app.getSystemService(android.media.AudioManager.class).getActiveRecordingConfigurations())
        if(configuration.getClientAudioSessionId()==nativeSession)absent=false;
      row.put("nativeSessionAbsent",absent);
      long frames=RuntimeAccess.number(r.state(),"getFrames");
      row.put("admittedFrames",RuntimeAccess.number(r.capture,"getAdmittedFrames")-admittedBefore)
        .put("durableFrames",RuntimeAccess.number(r.state(),"getDurableFrames"))
        .put("readErrors",RuntimeAccess.number(r.capture,"getReadErrors")-readsBefore)
        .put("shortReads",RuntimeAccess.number(r.capture,"getShortReads")-shortBefore)
        .put("afterStopDiagnostics",RuntimeAccess.call(r.controller,"diagnosticSummary"));
      // Foreground authentication cannot run while the Activity is paused by screen-off.
      // The full observer window and callback barrier have ended before intentional wake.
      if(longRun) {
        activity("POST_WINDOW_HOST_LAUNCH_READY",true);
      }
      r.open();JSONObject readback=r.readback(identity,frames);
      for(Iterator<String> keys=readback.keys();keys.hasNext();){String key=keys.next();row.put(key,readback.get(key));}
      RuntimeAccess.check(row.getLong("admittedFrames")==frames&&row.getLong("durableFrames")==frames
        &&row.getLong("readbackFrames")==frames&&row.getLong("readErrors")==0,"CANONICAL_COUNTER_OR_READ_ERROR");
      JSONObject growth=Telemetry.growth(before,Telemetry.storage(vaultRoot));
      for(Iterator<String> keys=growth.keys();keys.hasNext();){String key=keys.next();row.put(key,growth.get(key));}
      row.put("endingFreeBytes",RuntimeAccess.number(r.controller,"availableBytes"))
        .put("finalized",true).put("wholeRecordingLost",false).put("microphoneReleased",r.released()).put("fgsStopped",!r.fgs());
      evidence.put("currentReadback",row);save("FULL_READBACK_REDUCED");
      if(watchdog!=null)watchdog.healthy();
      row.put("deletion",r.deleteOwned(identity,owners,owned));return row;
    }
  }
}
