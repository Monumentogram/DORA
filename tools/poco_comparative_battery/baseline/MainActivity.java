package com.monumentogram.dora.stage86.baseline;
import android.app.Activity;
import android.os.Bundle;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.*;

public final class MainActivity extends Activity {
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(24,80,24,24);
    TextView info=new TextView(this);info.setText("Instrumentation only\nMIC / 16000 Hz / mono / PCM16\nBounded read/discard; no saved audio\nAutomatic stop at 15 minutes");layout.addView(info);
    Button start=new Button(this);start.setText("Start baseline");start.setOnClickListener(v->{
      if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);
      else startForegroundService(new Intent(this,CaptureService.class));
    });layout.addView(start);
    Button stop=new Button(this);stop.setText("Stop baseline");stop.setOnClickListener(v->startService(new Intent(this,CaptureService.class).setAction("STOP")));layout.addView(stop);
    setContentView(layout);
  }
  @Override public void onRequestPermissionsResult(int r,String[] p,int[] g) {
    super.onRequestPermissionsResult(r,p,g);
    if(r==1&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startForegroundService(new Intent(this,CaptureService.class));
  }
}
