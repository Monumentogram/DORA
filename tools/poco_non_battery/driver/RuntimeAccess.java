package com.monumentogram.dora.stage86b.driver;

import android.app.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Test-only reflection; all mutations use actual product control/authorization boundaries. */
final class RuntimeAccess {
  final Instrumentation instrumentation;
  final Application app;
  final ClassLoader loader;
  final Object runtime, controller, capture;
  Object session, vault, journal, dao;
  Activity activity;
  RuntimeAccess(Instrumentation i) throws Exception {
    instrumentation=i; app=(Application)i.getTargetContext().getApplicationContext();
    // Instrumentation.start may race Application.onCreate during bindApplication.
    // A main-looper barrier completes that lifecycle before reading lateinit product owners.
    i.runOnMainSync(()->{});
    loader=app.getClassLoader(); runtime=call(app,"getAudioRuntime");
    controller=call(app,"getRecording"); capture=field(controller,"capture");
  }
  static void check(boolean ok,String reason) {
    if(!ok) throw new IllegalStateException(reason);
  }
  static Object call(Object target,String name,Object...args) throws Exception {
    List<Method> matches=new ArrayList<>();
    for(Method m:target.getClass().getMethods())
      if(m.getName().equals(name)&&m.getParameterCount()==args.length) matches.add(m);
    check(matches.size()==1,"METHOD_SHAPE_"+name);
    Method m=matches.get(0); m.setAccessible(true); return m.invoke(target,args);
  }
  static Object field(Object target,String name) throws Exception {
    Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);
  }
  static void setField(Object target,String name,Object value) throws Exception {
    Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);
  }
  static long number(Object target,String method) throws Exception {
    return ((Number)call(target,method)).longValue();
  }
  static String hash(String value) throws Exception {
    return hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }
  static String hex(byte[] bytes) {
    StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();
  }
  static String fileHash(File file) throws Exception {
    MessageDigest d=MessageDigest.getInstance("SHA-256");
    try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}
    return hex(d.digest());
  }
  static String identityHash(Object identity) throws Exception {
    return hash(field(identity,"recordingId")+"/"+field(identity,"assetId")+"/"+field(identity,"sessionId"));
  }
  interface Checked {void run() throws Exception;}
  void main(Checked action) throws Exception {
    AtomicReference<Exception> error=new AtomicReference<>();
    instrumentation.runOnMainSync(()->{try{action.run();}catch(Exception e){error.set(e);}});
    if(error.get()!=null)throw error.get();
  }
  Object unit() throws Exception {return Class.forName("kotlin.Unit",true,loader).getField("INSTANCE").get(null);}
  interface Invocation {Object invoke(Object[] args) throws Exception;}
  Object function(int arity,Invocation action) throws Exception {
    Class<?> fn=Class.forName("kotlin.jvm.functions.Function"+arity,true,loader);
    return Proxy.newProxyInstance(loader,new Class<?>[]{fn},(p,m,args)->{
      if(m.getName().equals("invoke"))return action.invoke(args);
      if(m.getName().equals("toString"))return "ContentFreeAcceptanceCallback";
      if(m.getName().equals("hashCode"))return System.identityHashCode(p);
      if(m.getName().equals("equals"))return p==args[0];return null;
    });
  }
  void open() throws Exception {
    Class<?> cls=Class.forName("com.monumentogram.dora.audio.AudioOpenMode",true,loader);
    @SuppressWarnings({"rawtypes","unchecked"}) Object mode=Enum.valueOf((Class)cls,"OPEN_EXISTING");
    AtomicReference<Object> answer=new AtomicReference<>();CountDownLatch latch=new CountDownLatch(1);
    Object callback=function(1,args->{answer.set(args[0]);latch.countDown();return unit();});
    main(()->call(runtime,"requestOpen",activity,mode,callback));
    check(latch.await(120,TimeUnit.SECONDS),"AUTH_OPEN_TIMEOUT");
    check(answer.get().getClass().getSimpleName().equals("Available"),"AUTH_OPEN_UNAVAILABLE");
    session=call(answer.get(),"getSession");vault=field(session,"vault");journal=field(vault,"journal");dao=field(journal,"dao");
  }
  List<Object> identities() throws Exception {
    List<Object> all=new ArrayList<>();String after="";
    for(int i=0;i<50;i++) {
      List<?> page=(List<?>)call(journal,"recordingCandidates",after);
      if(page.isEmpty())return all;
      for(Object identity:page)check(((String)field(identity,"assetId")).compareTo(after)>0,"CATALOG_ORDER");
      all.addAll(page);after=(String)field(page.get(page.size()-1),"assetId");
    }
    throw new IllegalStateException("CATALOG_BOUND");
  }
  TreeMap<String,String> inventory() throws Exception {
    TreeMap<String,String> result=new TreeMap<>();
    for(Object identity:identities()) {
      Object id=field(identity,"assetId"),asset=call(dao,"asset",id);long frames=0;
      for(Object claim:(List<?>)call(dao,"claims",id))if(Boolean.TRUE.equals(call(claim,"getCommitted")))frames+=number(claim,"getFrames");
      check(call(dao,"tombstone",id)==null,"OWNER_TOMBSTONE");
      check(result.put(identityHash(identity),frames+"/"+call(asset,"getFinalized"))==null,"DUPLICATE_IDENTITY");
    }
    return result;
  }
  Object state() throws Exception {return call(call(call(controller,"getState"),"getValue"),"getRecording");}
  String phase() throws Exception {return call(state(),"getPhase").toString();}
  boolean live() throws Exception {return Boolean.TRUE.equals(call(capture,"getHasLiveThread"));}
  boolean fgs() {
    ActivityManager am=app.getSystemService(ActivityManager.class);
    for(ActivityManager.RunningServiceInfo s:am.getRunningServices(100))
      if(s.service.getClassName().equals("com.monumentogram.dora.recording.ProductRecordingService"))return s.foreground;
    return false;
  }
  boolean released() throws Exception {return !live() && field(capture,"recorder")==null && !fgs();}
  boolean quiescent()throws Exception {
    return released()&&call(controller,"getActionToken")==null
      &&!((java.util.concurrent.atomic.AtomicBoolean)field(controller,"starting")).get()
      &&!Arrays.asList("PREPARING","RECORDING","PAUSED","FINALIZING").contains(phase());
  }
  void awaitPhase(String wanted,long timeout) throws Exception {
    long deadline=android.os.SystemClock.elapsedRealtime()+timeout;
    while(android.os.SystemClock.elapsedRealtime()<deadline) {
      if(phase().equals(wanted))return;
      if(phase().equals("INTERRUPTED"))throw new IllegalStateException("CAPTURE_INTERRUPTED");
      Thread.sleep(50);
    }
    throw new IllegalStateException("PHASE_TIMEOUT_"+wanted);
  }
  void awaitRelease() throws Exception {
    long deadline=android.os.SystemClock.elapsedRealtime()+30000;
    while(android.os.SystemClock.elapsedRealtime()<deadline){if(released())return;Thread.sleep(50);}
    throw new IllegalStateException("RELEASE_TIMEOUT");
  }
  JSONObject readback(Object identity,long expected) throws Exception {
    Object originals=call(session,"getOriginals");
    Object source=call(call(call(originals,"acquire",identity),"getValue"),"getReference");
    check(number(source,"getFrames")==expected,"SOURCE_FRAME_MISMATCH");
    final long[] frames={0},blocks={0};
    Object consume=function(2,args->{
      long first=((Number)args[0]).longValue();byte[] pcm=(byte[])args[1];
      check(first==frames[0]&&pcm.length>0&&pcm.length%2==0,"CANONICAL_GAP_DUPLICATE");
      frames[0]+=pcm.length/2;blocks[0]++;return unit();
    });
    Object extracted=call(call(originals,"extract",source,consume),"getValue");
    check(extracted.getClass().getSimpleName().equals("Available")&&frames[0]==expected,"FULL_READBACK_FAILED");
    Object recording=call(call(call(call(session,"getLogicalRecordings"),"read",source),"getValue"),"getRecording");
    check(source.equals(call(recording,"getOriginalAudioReference")),"LOGICAL_SOURCE_CHANGED");
    check(field(identity,"recordingId").equals(field(call(recording,"getAuthorizationUnitId"),"recordingId")),"AUTHORIZATION_UNIT_CHANGED");
    List<?> chunks=(List<?>)call(recording,"getTechnicalChunks");JSONArray list=new JSONArray();
    long end=0;String epoch=null;Set<String> chunkIds=new HashSet<>();int caps=0;
    for(Object chunk:chunks) {
      long first=number(chunk,"getCanonicalFirstFrame"),last=number(chunk,"getCanonicalEndFrame"),processing=number(chunk,"getProcessingFirstFrame");
      String open=call(chunk,"getOpenReason").toString(),close=call(chunk,"getCloseReason").toString(),currentEpoch=call(chunk,"getCaptureEpochId").toString();
      check(first==end&&last>first&&chunkIds.add(call(chunk,"getChunkId").toString()),"CHUNK_COVERAGE");
      check(source.equals(call(chunk,"getSourceAudioReference")),"CHUNK_SOURCE_CHANGED");
      if(close.equals("CAP")){check(last-first==9600000L,"CAP_FRAME_MISMATCH");caps++;}
      if(open.equals("CAP"))check(first-processing==32000&&currentEpoch.equals(epoch),"CAP_OVERLAP_OR_RESTART");
      else check(processing==first,"UNEXPECTED_OVERLAP");
      for(long frame:new long[]{first,last-1})check(chunk.equals(call(call(recording,"lookup",frame),"getCanonicalOwner")),"REVERSE_LOOKUP");
      check(numberWithArg(chunk,"originalFrame",0L)==processing,"SOURCE_MAPPING");
      list.put(new JSONObject().put("first",first).put("end",last).put("processingFirst",processing)
        .put("open",open).put("close",close).put("epochFingerprint",hash(currentEpoch))
        .put("chunkFingerprint",hash(call(chunk,"getChunkId").toString())).put("profileSha256",call(chunk,"getProfileSha256")));
      end=last;epoch=currentEpoch;
    }
    check(end==expected,"CHUNK_TOTAL");
    return new JSONObject().put("readbackFrames",frames[0]).put("authenticatedBlocks",blocks[0])
      .put("sourceFingerprint",hash(identityHash(identity)+"/"+number(source,"getVersion")+"/"+call(source,"getDigest")+"/"+expected))
      .put("sourceVersion",number(source,"getVersion")).put("chunks",list).put("capCount",caps)
      .put("authorizationUnits",1).put("semanticCount",((List<?>)call(recording,"getSemanticSegments")).size())
      .put("degradedObservations",((List<?>)call(recording,"getDegradedObservations")).size())
      .put("unexplainedGaps",0).put("unexplainedDuplicates",0).put("corruptSegments",0);
  }
  static long numberWithArg(Object o,String method,Object arg)throws Exception{return ((Number)call(o,method,arg)).longValue();}
  JSONObject vadProof()throws Exception {
    Object segmentation=field(controller,"segmentation");check(segmentation!=null,"VAD_SEGMENTATION_MISSING");
    ExecutorService worker=(ExecutorService)field(segmentation,"worker");
    return worker.submit(()->{
      Object observer=field(segmentation,"observer"),engine=field(observer,"engine");
      check(engine!=null,"VAD_ENGINE_MISSING");
      Object binding=field(engine,"binding"),instance=field(binding,"instance"),profile=field(engine,"profile");
      Object version=Class.forName("com.k2fsa.sherpa.onnx.VersionInfo",true,loader).getMethod("getVersion").invoke(null);
      boolean named=false,apk=false;
      try(BufferedReader reader=new BufferedReader(new FileReader("/proc/self/maps"))){String line;
        while((line=reader.readLine())!=null){
          if(line.contains("libsherpa-onnx-jni.so"))named=true;
          if(line.contains(app.getApplicationInfo().sourceDir)&&line.matches("^[^ ]+ +[^ ]*x[^ ]* .*"))apk=true;
        }
      }
      JSONObject proof=new JSONObject().put("factoryClass",field(controller,"vadFactory").getClass().getName())
        .put("engineClass",engine.getClass().getName()).put("bindingClass",binding.getClass().getName())
        .put("nativeInstanceClass",instance.getClass().getName()).put("nativeVersion",version)
        .put("profileModelSha256",call(profile,"getModelSha256")).put("namedLibraryMapping",named).put("executableApkMapping",apk);
      check(proof.getString("factoryClass").equals("com.monumentogram.dora.vad.sherpa.SherpaEngineFactory")
        &&proof.getString("engineClass").equals("com.monumentogram.dora.vad.sherpa.SherpaVadEngine")
        &&proof.getString("bindingClass").equals("com.monumentogram.dora.vad.sherpa.ReflectiveSherpaBinding")
        &&proof.getString("nativeInstanceClass").equals("com.k2fsa.sherpa.onnx.Vad")&&version.equals("1.13.8")
        &&proof.getString("profileModelSha256").equals("1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3"),"PRIVATE_VAD_IMPLEMENTATION_MISMATCH");
      return proof;
    }).get(5,TimeUnit.SECONDS);
  }
  JSONObject deleteOwned(Object identity,TreeMap<String,String> protectedInventory,String owned) throws Exception {
    check(identityHash(identity).equals(owned)&&!protectedInventory.containsKey(owned),"OWNER_DELETE_FENCE");
    TreeMap<String,String> current=inventory();current.remove(owned);
    check(current.equals(protectedInventory),"OWNER_INVENTORY_CHANGED_BEFORE_DELETE");
    AtomicReference<Object> answer=new AtomicReference<>();CountDownLatch done=new CountDownLatch(1);
    Object callback=function(1,args->{answer.set(args[0]);done.countDown();return unit();});
    main(()->call(runtime,"requestAudioDeletion",activity,session,identity,callback));
    main(()->{
      Object pending=field(runtime,"pending");check(pending!=null,"PRODUCT_DELETE_DIALOG_MISSING");
      AlertDialog dialog=(AlertDialog)call(pending,"getDialog");
      check(dialog.isShowing(),"PRODUCT_DELETE_DIALOG_NOT_VISIBLE");
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    });
    check(done.await(120,TimeUnit.SECONDS),"PRODUCT_DELETE_TIMEOUT");
    check(answer.get().getClass().getSimpleName().equals("Value"),"PRODUCT_DELETE_FAILED");
    check(call(call(vault,"sourceState",identity),"getValue").getClass().getSimpleName().equals("UserDeleted"),"DELETION_STATE");
    AutoCloseable lease=(AutoCloseable)call(call(journal,"getCatalog"),"tryAcquire",identity);
    check(lease!=null,"DELETION_VERIFICATION_LEASE");int count=0;JSONObject kinds=new JSONObject();
    try {
      for(Object step:(List<?>)call(call(journal,"loadDeletion",identity),"getSteps")) {
        Object target=call(step,"getTarget");
        check(Boolean.TRUE.equals(call(step,"getCompleted"))&&Boolean.TRUE.equals(call(field(vault,"deletionStorage"),"verifiedAbsent",target)),"DELETION_ABSENCE");
        String kind=call(target,"getKind").toString();kinds.put(kind,kinds.optInt(kind)+1);count++;
      }
    } finally {lease.close();}
    check(count>0&&inventory().equals(protectedInventory),"OWNER_INVENTORY_CHANGED_AFTER_DELETE");
    return new JSONObject().put("productDeletionCompleted",true).put("verifiedAbsentTargets",count)
      .put("absenceCategories",kinds).put("ownerRecordingsPreserved",protectedInventory.size());
  }
}
