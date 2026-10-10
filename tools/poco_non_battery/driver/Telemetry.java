package com.monumentogram.dora.stage86b.driver;

import android.content.*;
import android.media.*;
import android.os.*;
import android.system.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Monotonic callbacks plus bounded content-free process samples. No battery measurement. */
final class Telemetry implements AutoCloseable {
  final RuntimeAccess r;
  final PowerManager power;
  final AudioManager audio;
  final AudioManager.AudioRecordingCallback recordingCallback;
  final AudioManager.AudioPlaybackCallback playbackCallback;
  final AudioManager.OnModeChangedListener modeCallback;
  final JSONArray events=new JSONArray();
  final BroadcastReceiver screen;
  final PowerManager.OnThermalStatusChangedListener thermal;
  boolean registered;
  final HandlerThread callbacks=new HandlerThread("Dora acceptance telemetry");
  final Handler handler;
  long registeredMs,barrierMs,unregisteredMs;
  Telemetry(RuntimeAccess runtime)throws Exception {
    r=runtime;power=r.app.getSystemService(PowerManager.class);audio=r.app.getSystemService(AudioManager.class);
    callbacks.start();handler=new Handler(callbacks.getLooper());
    screen=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
      if(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(i.getAction())){
        try{detail("POWER_SAVE",new JSONObject().put("enabled",power.isPowerSaveMode()));}
        catch(JSONException e){throw new IllegalStateException("POWER_EVENT_ENCODING",e);}
      }else event(Intent.ACTION_SCREEN_ON.equals(i.getAction())?"SCREEN_ON":"SCREEN_OFF",null);
    }};
    thermal=value->event("THERMAL",value);
    recordingCallback=new AudioManager.AudioRecordingCallback(){
      public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configurations){
        try{detail("AUDIO_CONFIG",new JSONObject().put("configurations",configurations(configurations)));}
        catch(Exception e){throw new IllegalStateException("ROUTE_EVENT_ENCODING",e);}
      }
    };
    playbackCallback=new AudioManager.AudioPlaybackCallback(){
      public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configurations){
        try{detail("PLAYBACK",new JSONObject().put("active",playbackActive(configurations)));}
        catch(Exception e){throw new IllegalStateException("PLAYBACK_EVENT_ENCODING",e);}
      }
    };
    modeCallback=value->{
      try{detail("AUDIO_MODE",new JSONObject().put("mode",value));}
      catch(Exception e){throw new IllegalStateException("MODE_EVENT_ENCODING",e);}
    };
    r.main(()->{
      IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_SCREEN_OFF);
      filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
      r.app.registerReceiver(screen,filter,null,handler,Context.RECEIVER_NOT_EXPORTED);
      audio.registerAudioRecordingCallback(recordingCallback,handler);
      audio.registerAudioPlaybackCallback(playbackCallback,handler);
      audio.addOnModeChangedListener(command->handler.post(command),modeCallback);
      power.addThermalStatusListener(command->handler.post(command),thermal);registered=true;
      registeredMs=SystemClock.elapsedRealtime();
    });
  }
  synchronized void event(String kind,Integer value) {
    try {
      JSONObject event=new JSONObject().put("kind",kind).put("elapsedMs",SystemClock.elapsedRealtime()).put("sequence",events.length());
      if(value!=null)event.put("thermal",value);
      events.put(event);
    } catch(JSONException e){throw new IllegalStateException("TELEMETRY_EVENT_ENCODING",e);}
  }
  synchronized void detail(String kind,JSONObject event)throws JSONException {
    RuntimeAccess.check(events.length()<10000,"EVENT_BOUND_EXCEEDED");
    events.put(event.put("kind",kind).put("elapsedMs",SystemClock.elapsedRealtime()).put("sequence",events.length()));
  }
  static boolean playbackActive(List<AudioPlaybackConfiguration> configurations){
    // Public AudioManager supplies active configurations; no hidden isActive API.
    // Conservatively reject any returned playback configuration, without owner/app details.
    return !configurations.isEmpty();
  }
  static JSONArray configurations(List<AudioRecordingConfiguration> configurations)throws Exception {
    JSONArray routes=new JSONArray();
    for(AudioRecordingConfiguration c:configurations){
      AudioDeviceInfo device=c.getAudioDevice();
      routes.put(new JSONObject().put("deviceType",device==null?JSONObject.NULL:device.getType())
        .put("session",c.getClientAudioSessionId()).put("silenced",c.isClientSilenced()).put("sampleRate",c.getClientFormat().getSampleRate())
        .put("channels",c.getClientFormat().getChannelCount()).put("encoding",c.getClientFormat().getEncoding()));
    }
    return routes;
  }
  synchronized JSONArray events()throws Exception {return new JSONArray(events.toString());}
  void drain()throws Exception {
    CountDownLatch done=new CountDownLatch(1);RuntimeAccess.check(handler.post(done::countDown),"CALLBACK_HANDLER_STOPPED");
    RuntimeAccess.check(done.await(5,TimeUnit.SECONDS),"CALLBACK_BARRIER_TIMEOUT");
  }
  JSONObject sample()throws Exception {
    Debug.MemoryInfo memory=new Debug.MemoryInfo();Debug.getMemoryInfo(memory);
    Runtime java=Runtime.getRuntime();JSONObject result=new JSONObject();
    result.put("elapsedMs",SystemClock.elapsedRealtime()).put("pid",android.os.Process.myPid())
      .put("interactive",power.isInteractive()).put("thermal",power.getCurrentThermalStatus())
      .put("batterySaver",power.isPowerSaveMode()).put("audioMode",audio.getMode())
      .put("playbackActive",playbackActive(audio.getActivePlaybackConfigurations()))
      .put("fgs",r.fgs()).put("microphone",r.live()&&Boolean.TRUE.equals(RuntimeAccess.call(r.capture,"getHealthy")))
      .put("pssKiB",memory.getTotalPss()).put("nativeHeapBytes",Debug.getNativeHeapAllocatedSize())
      .put("javaHeapBytes",java.totalMemory()-java.freeMemory()).put("cpuTimeMs",android.os.Process.getElapsedCpuTime())
      .put("threads",count(new File("/proc/self/task"))).put("fds",count(new File("/proc/self/fd")))
      .put("rssKiB",rss()).put("diagnostics",RuntimeAccess.call(r.controller,"diagnosticSummary"));
    result.put("audioConfigurations",configurations(audio.getActiveRecordingConfigurations()));
    Object microphone=RuntimeAccess.field(r.capture,"recorder");
    AudioRecord nativeRecord=microphone==null?null:(AudioRecord)RuntimeAccess.field(microphone,"record");
    result.put("nativeRecording",nativeRecord!=null&&nativeRecord.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING)
      .put("nativeSession",nativeRecord==null?JSONObject.NULL:nativeRecord.getAudioSessionId());
    int servicePid=0;
    for(android.app.ActivityManager.RunningServiceInfo service:r.app.getSystemService(android.app.ActivityManager.class).getRunningServices(100))
      if(service.service.getClassName().equals("com.monumentogram.dora.recording.ProductRecordingService")&&service.foreground)servicePid=service.pid;
    android.content.pm.ServiceInfo declared=r.app.getPackageManager().getServiceInfo(new ComponentName(r.app.getPackageName(),"com.monumentogram.dora.recording.ProductRecordingService"),0);
    result.put("fgsPid",servicePid).put("microphoneFgsType",(declared.getForegroundServiceType()&android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)!=0);
    return result;
  }
  static int count(File dir){String[] entries=dir.list();RuntimeAccess.check(entries!=null,"PROC_COUNT_UNAVAILABLE");return entries.length;}
  static Object rss()throws Exception {
    try(BufferedReader reader=new BufferedReader(new FileReader("/proc/self/status"))){
      String line;while((line=reader.readLine())!=null)if(line.startsWith("VmRSS:"))return Long.parseLong(line.trim().split("\\s+")[1]);
    }
    return JSONObject.NULL;
  }
  static TreeMap<String,long[]> storage(File root)throws Exception {
    RuntimeAccess.check(root.isDirectory(),"VAULT_ROOT_MISSING");
    TreeMap<String,long[]> files=new TreeMap<>();List<File> pending=new ArrayList<>();pending.add(root);
    String base=root.getCanonicalPath()+File.separator;int visited=0;
    while(!pending.isEmpty()) {
      File file=pending.remove(pending.size()-1);String canonical=file.getCanonicalPath();
      RuntimeAccess.check(file.equals(root)||canonical.startsWith(base),"STORAGE_PATH_ESCAPE");
      StructStat stat=Os.lstat(file.getPath());
      RuntimeAccess.check(!OsConstants.S_ISLNK(stat.st_mode),"STORAGE_SYMLINK");
      files.put(file.equals(root)?"VAULT_ROOT":RuntimeAccess.hash(canonical.substring(base.length())),new long[]{stat.st_size,Math.multiplyExact(stat.st_blocks,512L)});
      if(file.isDirectory()){
        File[] children=file.listFiles();RuntimeAccess.check(children!=null,"STORAGE_LIST_FAILED");pending.addAll(Arrays.asList(children));
      }else if(!file.isFile())throw new IllegalStateException("STORAGE_SPECIAL_FILE");
      RuntimeAccess.check(++visited<=100000,"STORAGE_BOUND");
    }
    return files;
  }
  static JSONObject growth(TreeMap<String,long[]> before,TreeMap<String,long[]> after)throws Exception {
    long logical=0,allocated=0;
    for(Map.Entry<String,long[]> entry:after.entrySet()) {
      long[] prior=before.getOrDefault(entry.getKey(),new long[]{0,0});
      logical=Math.addExact(logical,Math.max(0,entry.getValue()[0]-prior[0]));
      allocated=Math.addExact(allocated,Math.max(0,entry.getValue()[1]-prior[1]));
    }
    return new JSONObject().put("logicalPositiveGrowthBytes",logical).put("allocatedPositiveGrowthBytes",allocated)
      .put("attributedBytes",Math.max(logical,allocated)).put("beforeFileCount",before.size()).put("afterFileCount",after.size())
      .put("storageScope","VAULT_POSITIVE_FILE_GROWTH_MAX_LOGICAL_ALLOCATED");
  }
  public void close()throws Exception {
    if(registered){
      drain();
      power.removeThermalStatusListener(thermal);r.app.unregisterReceiver(screen);registered=false;
      audio.unregisterAudioRecordingCallback(recordingCallback);audio.unregisterAudioPlaybackCallback(playbackCallback);
      audio.removeOnModeChangedListener(modeCallback);
      unregisteredMs=SystemClock.elapsedRealtime();drain();barrierMs=SystemClock.elapsedRealtime();
    }
    callbacks.quitSafely();callbacks.join(5000);RuntimeAccess.check(!callbacks.isAlive(),"CALLBACK_THREAD_NOT_RELEASED");
  }
}
