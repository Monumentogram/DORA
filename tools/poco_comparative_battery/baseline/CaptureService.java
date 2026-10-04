package com.monumentogram.dora.stage86.baseline;
import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.os.*;
import org.json.JSONObject;
import java.io.FileOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Isolated test-only comparator. No audio persistence, VAD, model or network. */
public final class CaptureService extends Service {
  private volatile boolean running;
  @Override public IBinder onBind(Intent i) {return null;}
  @Override public int onStartCommand(Intent i,int flags,int id) {
    if(i!=null&&"STOP".equals(i.getAction())) {running=false;stopSelf();return START_NOT_STICKY;}
    if(running)return START_NOT_STICKY;
    NotificationManager nm=getSystemService(NotificationManager.class);
    nm.createNotificationChannel(new NotificationChannel("recording","Baseline microphone",NotificationManager.IMPORTANCE_LOW));
    PendingIntent stop=PendingIntent.getService(this,0,new Intent(this,CaptureService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    Notification n=new Notification.Builder(this,"recording").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Battery baseline: microphone active").setContentText("PCM is discarded; automatic stop within 15 minutes").setOngoing(true).addAction(new Notification.Action.Builder(null,"Stop",stop).build()).build();
    startForeground(86,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
    running=true;new Thread(this::capture,"Baseline AudioRecord").start();return START_NOT_STICKY;
  }
  private void capture() {
    AudioRecord rec=null;byte[] pcm=new byte[1600];long frames=0,shorts=0,errors=0,start=0,end=0;String outcome="NOT_STARTED";int route=-1;
    try {
      android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
      int min=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
      if(min<=0)throw new IllegalStateException("configuration unavailable");
      rec=new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC).setAudioFormat(new AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(Math.max(min*4,32000)).build();
      if(rec.getState()!=AudioRecord.STATE_INITIALIZED||rec.getSampleRate()!=16000||rec.getChannelCount()!=1||rec.getAudioFormat()!=AudioFormat.ENCODING_PCM_16BIT)throw new IllegalStateException("wrong format");
      rec.startRecording();start=SystemClock.elapsedRealtime();
      if(rec.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IllegalStateException("not recording");
      while(running&&SystemClock.elapsedRealtime()-start<900000L) {
        int count=rec.read(pcm,0,pcm.length,AudioRecord.READ_BLOCKING);
        if(count<=0||(count%2)!=0) {errors++;outcome="READ_ERROR";break;}
        if(count<pcm.length)shorts++;
        frames+=count/2;
      }
      if(errors==0)outcome="STOPPED";
      if(rec.getRoutedDevice()!=null)route=rec.getRoutedDevice().getType();
    } catch(Exception e) {outcome=e.getClass().getSimpleName();}
    finally {
      end=SystemClock.elapsedRealtime();running=false;Arrays.fill(pcm,(byte)0);
      if(rec!=null) {try{rec.stop();}catch(Exception ignored){}rec.release();}
      try {
        JSONObject receipt=new JSONObject();receipt.put("mode","MINIMAL_AUDIO_RECORD_FGS_BASELINE");receipt.put("sampleRate",16000);receipt.put("channels",1);receipt.put("encoding","PCM_S16LE");receipt.put("startElapsedMs",start);receipt.put("endElapsedMs",end);receipt.put("framesReadAndDiscarded",frames);receipt.put("shortReads",shorts);receipt.put("readErrors",errors);receipt.put("routeType",route);receipt.put("outcome",outcome);receipt.put("audioSaved",false);
        File file=getFileStreamPath("run-"+start+"-"+end+".json");
        if(!file.createNewFile())throw new IllegalStateException("attempt receipt already exists");
        try(FileOutputStream out=new FileOutputStream(file)){out.write(receipt.toString().getBytes(StandardCharsets.UTF_8));}
      } catch(Exception ignored) {android.util.Log.e("Baseline","content-free receipt unavailable");}
      stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
  }
  @Override public void onDestroy(){running=false;super.onDestroy();}
}
