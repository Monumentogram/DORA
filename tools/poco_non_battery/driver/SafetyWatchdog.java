package com.monumentogram.dora.stage86b.driver;

import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.*;

/** Independent device thread. Never deletes data or grants authority. */
final class SafetyWatchdog implements AutoCloseable {
  final CampaignInstrumentation owner;
  final Thread thread;
  final File heartbeat,abort,receipt;
  volatile boolean closed;
  volatile String failure;
  volatile long deadline;
  long changed=SystemClock.elapsedRealtime();
  String previous="";
  SafetyWatchdog(CampaignInstrumentation owner) {
    this.owner=owner;
    heartbeat=new File(owner.directory,owner.run+".heartbeat");
    abort=new File(owner.directory,owner.run+".abort");
    receipt=new File(owner.directory,owner.run+"-watchdog.json");
    deadline=SystemClock.elapsedRealtime()+120000;
    thread=new Thread(this::watch,"Dora acceptance safety watchdog");thread.start();
  }
  void arm(long budgetMs){deadline=SystemClock.elapsedRealtime()+budgetMs;}
  void abort(){if(failure==null)failure="DRIVER_ABORT";}
  void healthy(){RuntimeAccess.check(failure==null,"WATCHDOG_ABORTED");}
  void write(String state)throws Exception {
    JSONObject row=new JSONObject().put("run",owner.run).put("state",state)
      .put("elapsedMs",SystemClock.elapsedRealtime()).put("reason",failure==null?JSONObject.NULL:failure)
      .put("microphoneReleased",owner.r.released()).put("pendingStartCancelled",!owner.startPending&&owner.r.quiescent());
    try(FileOutputStream out=new FileOutputStream(receipt)){out.write(row.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
  }
  void watch(){
    try {
      write("ARMED");
      while(!closed) {
        long now=SystemClock.elapsedRealtime();
        if(heartbeat.isFile()) {
          RuntimeAccess.check(heartbeat.length()<128,"HEARTBEAT_SHAPE");
          String value=new String(Files.readAllBytes(heartbeat.toPath()),StandardCharsets.UTF_8).trim();
          RuntimeAccess.check(value.matches("[0-9]{1,18}"),"HEARTBEAT_SHAPE");
          if(!value.equals(previous)){previous=value;changed=now;}
        }
        if(abort.exists())failure="HOST_ABORT";
        else if(now-changed>30000)failure="HOST_HEARTBEAT_EXPIRED";
        else if(now>deadline)failure="HARD_DEADLINE";
        else if(owner.r.app.getSystemService(PowerManager.class).getCurrentThermalStatus()>=3)failure="THERMAL_SEVERE";
        else if(RuntimeAccess.number(owner.r.controller,"availableBytes")<16777216L)failure="STORAGE_RESERVE_EXHAUSTED";
        else if(owner.r.phase().equals("RECORDING")&&!owner.r.fgs())failure="RECORDING_FGS_MISSING";
        if(failure!=null)break;
        Thread.sleep(500);
      }
    }catch(Exception problem){failure="WATCHDOG_OBSERVATION_FAILED";}
    if(failure!=null) {
      try{write("ABORT_REQUESTED");}catch(Exception ignored){}
      try {
        long stopDeadline=SystemClock.elapsedRealtime()+60000;
        while(SystemClock.elapsedRealtime()<stopDeadline) {
          owner.r.main(()->{
            if(owner.ownsPendingOrCurrentStart()){
              RuntimeAccess.call(owner.r.controller,"requestStop");RuntimeAccess.call(owner.r.controller,"confirmStop");
            }
          });
          if(!owner.startPending&&owner.r.quiescent()){write("STOP_CONFIRMED");return;}
          Thread.sleep(250);
        }
        write("DEVICE_SAFE_STATE_UNCONFIRMED");
      }catch(Exception ignored){try{write("DEVICE_SAFE_STATE_UNCONFIRMED");}catch(Exception unavailable){}}
    }
  }
  public void close()throws Exception {
    // Never retire the independent device observer while a Start can still acquire the mic.
    try{if(owner.startPending||!owner.r.quiescent())abort();}
    catch(Exception unavailable){abort();}
    closed=true;thread.join(65000);
    RuntimeAccess.check(!thread.isAlive(),"WATCHDOG_TERMINATION_UNCONFIRMED");
    if(failure==null){RuntimeAccess.check(!owner.startPending&&owner.r.quiescent(),"WATCHDOG_SAFE_STATE_UNCONFIRMED");write("CLOSED_SAFE");}
  }
}
