package com.monumentogram.dora.stage86.observer;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;

/** Local test-only observer: no microphone, network, wake lock or power-policy changes. */
public final class ProbeInstrumentation extends Instrumentation {
  private String id;
  private boolean smoke;
  private BatteryManager battery;
  private PowerManager power;
  @Override public void onCreate(Bundle args) {
    super.onCreate(args);
    id=args.getString("probeId", "");
    smoke="true".equals(args.getString("smoke"));
    if(!id.matches("[ABC]-[0-9]{2}|SMOKE-[0-9]{2}")) {
      finish(1,new Bundle()); return;
    }
    start();
  }
  private JSONObject snapshot() throws Exception {
    JSONObject o=new JSONObject();
    o.put("elapsedRealtimeMs",SystemClock.elapsedRealtime());
    int[] props={1,2,3,4,5};
    String[] names={"chargeCounterMicroAh","currentNowMicroA","currentAverageMicroA","capacityPercent","energyNanoWh"};
    for(int i=0;i<props.length;i++) {
      long value=battery.getLongProperty(props[i]);
      o.put(names[i],value);o.put(names[i]+"Supported",value!=Long.MIN_VALUE);
    }
    o.put("interactive",power.isInteractive());
    o.put("thermalStatus",power.getCurrentThermalStatus());
    Intent state=getTargetContext().registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    if(state==null||!state.hasExtra(BatteryManager.EXTRA_PLUGGED)) throw new IllegalStateException("Missing power state");
    int plugged=state.getIntExtra(BatteryManager.EXTRA_PLUGGED,-1);
    o.put("pluggedMask",plugged);
    o.put("AC powered",(plugged&1)!=0);o.put("USB powered",(plugged&2)!=0);
    o.put("Wireless powered",(plugged&4)!=0);o.put("Dock powered",(plugged&8)!=0);
    for(String key:new String[]{"status","voltage","temperature"}) {
      if(!state.hasExtra(key))throw new IllegalStateException("Missing battery field");
      o.put(key,state.getIntExtra(key,-1));
    }
    o.put("probeId",id);o.put("observer","ANDROID_INSTRUMENTATION_V2");
    o.put("snapshotEndMs",SystemClock.elapsedRealtime());return o;
  }
  @Override public void onStart() {
    Context context=getTargetContext();
    battery=context.getSystemService(BatteryManager.class);power=context.getSystemService(PowerManager.class);
    File file=new File(context.getFilesDir(),id+".jsonl");
    int code=1;
    try {
      if(!file.createNewFile())throw new IllegalStateException("Refuse overwrite");
      try(FileOutputStream stream=new FileOutputStream(file);PrintWriter out=new PrintWriter(stream)) {
        long deadline=SystemClock.elapsedRealtime()+1800000L,start=-1,next=0;int samples=0;
        boolean valid=true,done=false;
        while(SystemClock.elapsedRealtime()<deadline) {
          JSONObject row=snapshot();long now=row.getLong("elapsedRealtimeMs");
          boolean unplugged=row.getInt("pluggedMask")==0,off=!row.getBoolean("interactive");
          if(smoke) {
            row.put("phase","USB_SMOKE_DIAGNOSTIC_ONLY");out.println(row);out.flush();stream.getFD().sync();
            if(++samples>=3){done=true;code=0;break;}SystemClock.sleep(2000);continue;
          }
          if(start<0) {
            if(unplugged&&off){start=now;next=start;}
            else {row.put("phase","WAITING_FOR_PHYSICAL_UNPLUG_AND_SCREEN_OFF");out.println(row);out.flush();stream.getFD().sync();SystemClock.sleep(5000);continue;}
          }
          valid=valid&&unplugged&&off;
          row.put("phase","MEASURING");row.put("windowStartMs",start);row.put("sampleDeltaMs",now-start);
          row.put("batteryOnly",unplugged);row.put("screenOff",off);
          out.println(row);out.flush();stream.getFD().sync();samples++;
          if(!unplugged||!off||now-start>=600000L) {
            JSONObject end=new JSONObject();end.put("phase","END");end.put("probeId",id);
            end.put("actualSampleSpanMs",now-start);end.put("samples",samples);
            end.put("screenOffUnpluggedObserved",valid);end.put("tenMinuteWindowComplete",now-start>=600000L);
            end.put("wakeLockHeldByProbe",false);out.println(end);out.flush();stream.getFD().sync();done=true;code=0;break;
          }
          next+=15000L;SystemClock.sleep(Math.max(1,next-SystemClock.elapsedRealtime()));
        }
        if(!done){out.println("{\"phase\":\"TIMEOUT_WAITING_FOR_OWNER\"}");out.flush();stream.getFD().sync();}
      }
    } catch(Exception e) {
      Bundle result=new Bundle();result.putString("errorClass",e.getClass().getSimpleName());finish(1,result);return;
    }
    finish(code,new Bundle());
  }
}
