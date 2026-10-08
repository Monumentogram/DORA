package com.monumentogram.dora.stage86b.driver;

import android.content.Context;
import android.os.*;
import android.system.Os;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** No normal runtime open, original-audio acquire, recovery, capture or cleanup route. */
public final class ForensicCatalogInstrumentation extends CampaignInstrumentation {
  private Object snapshotVault;
  private File originalRoot;
  private TreeMap<String,String> originalMap;
  private final java.util.concurrent.atomic.AtomicInteger deniedOriginalOpens=new java.util.concurrent.atomic.AtomicInteger();
  private Object originalOpenGuard;
  @Override public void onStart() {
    boolean complete=false;
    try {
      r=new RuntimeAccess(this); run=args.getString("run","");
      RuntimeAccess.check(run.matches("forensic-[a-z0-9-]{3,32}"),"FORENSIC_RUN");
      RuntimeAccess.check(r.quiescent(),"FORENSIC_NOT_QUIESCENT");
      Object coordinator=RuntimeAccess.field(r.runtime,"coordinator");
      RuntimeAccess.check(RuntimeAccess.field(coordinator,"current")==null,"FORENSIC_ORIGINAL_ALREADY_OPEN");
      originalOpenGuard=r.function(2,a->{deniedOriginalOpens.incrementAndGet();throw new IllegalStateException("FORENSIC_ORIGINAL_OPEN_DENIED");});
      RuntimeAccess.setField(coordinator,"openVault",originalOpenGuard);
      String apk=RuntimeAccess.fileHash(new File(r.app.getApplicationInfo().sourceDir));
      RuntimeAccess.check(apk.equals(args.getString("expectedApkSha256")),"FORENSIC_APK");
      directory=new File(r.app.getNoBackupFilesDir(),"stage86b-receipts");
      RuntimeAccess.check(directory.isDirectory()||directory.mkdir(),"FORENSIC_RECEIPT_DIRECTORY");
      receipt=new File(directory,run+".json"); receiptCreated=receipt.createNewFile();
      RuntimeAccess.check(receiptCreated,"FORENSIC_RUN_EXISTS");
      evidence.put("run",run).put("apkSha256",apk).put("recordingStarted",false);
      if(args.getString("mode","").equals("snapshot-self-test")) {
        selfTest(); complete=true; return;
      }
      RuntimeAccess.check(Arrays.asList("catalog-snapshot","catalog-prefix").contains(args.getString("mode","")),"FORENSIC_MODE");
      File original=new File(r.app.getNoBackupFilesDir(),"dora-vault-v1");
      TreeMap<String,String> before=ForensicSnapshot.hashes(original);
      originalRoot=original;originalMap=before;
      RuntimeAccess.check(ForensicSnapshot.digest(before).equals(args.getString("sourceMapSha256")),"FORENSIC_PRESERVATION_BINDING");
      File failed=new File(directory,"dora-long-01.json");
      RuntimeAccess.check(RuntimeAccess.fileHash(failed).equals(args.getString("referenceReceiptSha256")),"FORENSIC_FAILURE_BINDING");
      File copy=new File(r.app.getNoBackupFilesDir(),run+"-catalog");
      Context context=ForensicSnapshot.copy(r.app,original,copy);
      RuntimeAccess.check(ForensicSnapshot.hashes(original).equals(before),"FORENSIC_COPY_CHANGED_SOURCE");
      activity();
      AtomicReference<Object> unlocked=new AtomicReference<>(); CountDownLatch ready=new CountDownLatch(1);
      Object callback=r.function(1,a->{unlocked.set(a[0]);ready.countDown();return r.unit();});
      r.main(()->RuntimeAccess.call(r.runtime,"requestRecordingUiUnlock",r.activity,callback));
      RuntimeAccess.check(ready.await(60,TimeUnit.SECONDS)&&Boolean.TRUE.equals(unlocked.get()),"FORENSIC_AUTH_UNAVAILABLE");
      Object authorization=RuntimeAccess.call(RuntimeAccess.field(r.runtime,"appLock"),"captureAuthorization");
      Object gate=r.function(0,a->{RuntimeAccess.call(authorization,"requireActive");return r.unit();});
      Class<?> vaultClass=Class.forName("com.monumentogram.dora.audio.persistence.EncryptedAudioVault",true,r.loader);
      Object companion=vaultClass.getField("Companion").get(null);
      Object opened=RuntimeAccess.call(companion,"openExisting",context,gate);
      RuntimeAccess.check(opened.getClass().getSimpleName().equals("Value"),"FORENSIC_SNAPSHOT_OPEN");
      snapshotVault=RuntimeAccess.call(opened,"getValue");
      r.journal=RuntimeAccess.field(snapshotVault,"journal"); r.dao=RuntimeAccess.field(r.journal,"dao");
      TreeMap<String,String> inventory=r.inventory();
      String target=args.getString("targetFingerprint","");
      RuntimeAccess.check(target.matches("[a-f0-9]{64}")&&inventory.size()==47&&inventory.remove(target)!=null,"FORENSIC_TARGET_INVENTORY");
      RuntimeAccess.check(ForensicSnapshot.digest(inventory).equals(args.getString("ownerMapSha256")),"FORENSIC_ORIGINAL46_BINDING");
      Object identity=null;
      for(Object candidate:r.identities()) if(RuntimeAccess.identityHash(candidate).equals(target)) {
        RuntimeAccess.check(identity==null,"FORENSIC_DUPLICATE_TARGET"); identity=candidate;
      }
      RuntimeAccess.check(identity!=null,"FORENSIC_MISSING_TARGET");
      Object catalog=RuntimeAccess.call(r.journal,"getCatalog");
      Object lease=RuntimeAccess.call(catalog,"tryAcquire",identity);
      RuntimeAccess.check(lease instanceof AutoCloseable,"FORENSIC_SNAPSHOT_LEASE");
      try(AutoCloseable held=(AutoCloseable)lease) {
        Object asset=RuntimeAccess.call(catalog,"load",identity);
        RuntimeAccess.check(asset!=null,"FORENSIC_CATALOG_REJECTED");
        List<?> segments=(List<?>)RuntimeAccess.call(asset,"getSegments");
        long frames=0; int ordinal=0; Set<String> runs=new HashSet<>(); JSONArray ranges=new JSONArray();
        for(Object segment:segments) {
          Object id=RuntimeAccess.call(segment,"getIdentity");
          long first=RuntimeAccess.number(id,"getFirstFrame"),count=RuntimeAccess.number(segment,"getFrames");
          RuntimeAccess.check(first==frames&&count>0&&count<=80000,"FORENSIC_CATALOG_RANGE");
          RuntimeAccess.check(RuntimeAccess.number(id,"getOrdinal")==ordinal&&runs.add((String)RuntimeAccess.field(id,"unitId")),"FORENSIC_CATALOG_ORDER");
          ranges.put(new JSONObject().put("ordinal",ordinal++).put("firstFrame",first).put("frames",count)); frames=Math.addExact(frames,count);
        }
        Object pending=RuntimeAccess.call(asset,"getPending");
        evidence.put("targetFingerprint",target).put("catalogClaimedFrames",frames).put("committedStorageUnits",segments.size())
          .put("ranges",ranges).put("pendingIntent",pending==null?"NONE":pending.getClass().getSimpleName())
          .put("finalized",RuntimeAccess.call(asset,"getFinalization")!=null)
          .put("authenticatedReadableFrames",JSONObject.NULL).put("pcmInspection","NOT_RUN")
          .put("catalogValidation","SNAPSHOT_AUTHENTICATED_SELECT_ONLY")
          .put("original46Count",inventory.size()).put("original46MapSha256",ForensicSnapshot.digest(inventory));
        JSONArray metadata=new JSONArray();String after="";
        for(int page=0;page<100;page++) {
          List<?> rows=(List<?>)RuntimeAccess.call(r.journal,"segmentationPage",identity,after);
          if(rows.isEmpty())break;
          for(Object row:rows) {
            String kind=RuntimeAccess.call(row,"getKind").toString();
            JSONObject value=new JSONObject().put("kind",kind).put("firstFrame",RuntimeAccess.number(row,"getFirstFrame"))
              .put("endFrame",RuntimeAccess.number(row,"getEndFrame"));
            if(kind.startsWith("TECHNICAL_"))value.put("reason",RuntimeAccess.call(row,"getReason"))
              .put("overlapFirstFrame",RuntimeAccess.call(row,"getOverlapFirstFrame"));
            metadata.put(value);after=(String)RuntimeAccess.call(row,"getKey");
          }
          RuntimeAccess.check(page<99,"FORENSIC_METADATA_BOUND");
        }
        evidence.put("segmentationMetadata",metadata);
        if(args.getString("mode").equals("catalog-prefix")) {
          ForensicPrefix prefix=new ForensicPrefix(r,snapshotVault,original,authorization);
          long readable=0;int verified=0;String status="ALL_COMMITTED_UNITS_AUTHENTICATED";
          for(Object segment:segments) {
            try {readable=Math.addExact(readable,prefix.verify(segment));verified++;}
            catch(Exception rejected) {
              status="STOPPED_AT_FIRST_UNVERIFIED_UNIT";evidence.put("firstUnverifiedOrdinal",verified);
              Throwable reason=rejected;for(int depth=0;depth<8&&reason.getCause()!=null;depth++)reason=reason.getCause();
              String code=reason.getMessage();evidence.put("prefixFailureClass",reason.getClass().getSimpleName());
              if(code!=null&&code.matches("[A-Z0-9_]+"))evidence.put("prefixFailureCode",code);
              break;
            }
          }
          evidence.put("authenticatedReadableFrames",readable).put("authenticatedStorageUnits",verified)
            .put("pcmInspection",status).put("ownedPcmBuffersCleared",prefix.verifiedBuffers)
            .put("mutationCallsDenied",prefix.deniedMutations).put("providerInternalMemoryZeroingClaimed",false);
        }
      }
      RuntimeAccess.call(snapshotVault,"close");snapshotVault=null;
      RuntimeAccess.check(ForensicSnapshot.hashes(original).equals(before)&&r.quiescent(),"FORENSIC_SOURCE_CHANGED");
      evidence.put("sourceFileCount",before.size()).put("sourceMapSha256",ForensicSnapshot.digest(before))
        .put("sourceFilesUnchanged",true).put("micOff",true).put("fgsAbsent",true)
        .put("snapshotOnlyDatabaseOpen",true).put("normalRecoveryInvoked",false);
      RuntimeAccess.check(deniedOriginalOpens.get()==0,"FORENSIC_ORIGINAL_OPEN_ATTEMPTED");
      complete=true;
    } catch(Exception failure) {
      try {
        evidence.put("errorClass",failure.getClass().getSimpleName());
        String code=failure.getMessage();if(code!=null&&code.matches("[A-Z0-9_]+"))evidence.put("errorCode",code);
      } catch(Exception ignored){}
    } finally {
      try { if(snapshotVault!=null)RuntimeAccess.call(snapshotVault,"close"); }
      catch(Exception closeFailure) {complete=false;try{evidence.put("snapshotCloseFailed",true);}catch(Exception ignored){}}
      try {
        evidence.put("originalVaultOpenGuardInstalled",originalOpenGuard!=null).put("originalVaultOpenAttemptsDenied",deniedOriginalOpens.get());
        if(originalMap!=null) {
          boolean same=ForensicSnapshot.hashes(originalRoot).equals(originalMap);
          boolean quiet=r.quiescent();
          evidence.put("preservation",same&&quiet?"VERIFIED":"CHANGED_OR_NOT_QUIESCENT")
            .put("sourceFilesUnchanged",same).put("micOffAndFgsAbsent",quiet);
          if(!same||!quiet)complete=false;
        }
      } catch(Exception preservationFailure) {complete=false;try{evidence.put("preservation","UNAVAILABLE");}catch(Exception ignored){}}
      try {if(receiptCreated)save(complete?"COMPLETE":"FAILED");}catch(Exception ignored){complete=false;}
      // Guard remains installed until instrumentation process exit, including Activity callbacks.
      finish(complete?0:1,new Bundle());
    }
  }
  private void selfTest() throws Exception {
    RuntimeAccess.check(Build.FINGERPRINT.contains("generic")||Build.PRODUCT.contains("sdk"),"FORENSIC_SYNTHETIC_EMULATOR_ONLY");
    boolean denied=false;try{RuntimeAccess.call(originalOpenGuard,"invoke",null,null);}catch(Exception expected){denied=true;}
    RuntimeAccess.check(denied&&deniedOriginalOpens.get()==1,"FORENSIC_ORIGINAL_OPEN_GUARD_TEST");
    evidence.put("originalOpenCallbackRejectedBeforeVaultAccess",true);
    File root=new File(r.app.getNoBackupFilesDir(),run+"-synthetic");Os.mkdir(root.getPath(),0700);
    File source=new File(root,"source");Os.mkdir(source.getPath(),0700);
    for(String name:Arrays.asList("selector","vault.bundle","journal-synthetic.db","journal-synthetic.db-wal","excluded-audio-canary"))
      try(FileOutputStream out=new FileOutputStream(new File(source,name))) {out.write(("SYNTHETIC_"+name).getBytes(StandardCharsets.US_ASCII));}
    TreeMap<String,String> before=ForensicSnapshot.hashes(source);
    File destination=new File(root,"copy");Context context=ForensicSnapshot.copy(r.app,source,destination);
    RuntimeAccess.check(context.getApplicationContext()==context&&context.getNoBackupFilesDir().equals(destination),"FORENSIC_CONTEXT_ESCAPE");
    RuntimeAccess.check(!new File(destination,"dora-vault-v1/excluded-audio-canary").exists(),"FORENSIC_AUDIO_COPY");
    try(FileOutputStream out=new FileOutputStream(new File(destination,"dora-vault-v1/journal-synthetic.db"))) {out.write(7);}
    RuntimeAccess.check(ForensicSnapshot.hashes(source).equals(before),"FORENSIC_MUTATION_REACHED_SOURCE");
    boolean collision=false;try {ForensicSnapshot.copy(r.app,source,destination);}catch(IllegalStateException expected){collision=true;}
    RuntimeAccess.check(collision,"FORENSIC_OVERWRITE_ALLOWED");
    File bundle=new File(source,"vault.bundle");File saved=new File(root,"bundle.saved");RuntimeAccess.check(bundle.renameTo(saved),"FORENSIC_FIXTURE");
    Os.symlink(saved.getPath(),bundle.getPath());
    boolean symlink=false;try {ForensicSnapshot.copy(r.app,source,new File(root,"unsafe-copy"));}catch(IllegalStateException expected){symlink=true;}
    RuntimeAccess.check(symlink,"FORENSIC_SYMLINK_ALLOWED");
    evidence.put("syntheticChecks",5).put("sourceCopyImmutable",true).put("snapshotContextIsolated",true)
      .put("audioExcluded",true).put("destinationCollisionRejected",true).put("symlinkRejected",true)
      .put("ownerVaultAccessed",false).put("ownerPcmDecrypted",false);
    encryptedSelfTest(root);
  }
  public static final class SyntheticAuthorization {
    int calls; int revokeAt=Integer.MAX_VALUE;
    public void requireActive() {RuntimeAccess.check(++calls<revokeAt,"SYNTHETIC_AUTH_REVOKED");}
  }
  private void encryptedSelfTest(File root) throws Exception {
    File synthetic=new File(root,"encrypted-synthetic");Os.mkdir(synthetic.getPath(),0700);
    Context context=new ForensicSnapshot.ContextAt(r.app,synthetic);
    Class<?> vaultClass=Class.forName("com.monumentogram.dora.audio.persistence.EncryptedAudioVault",true,r.loader);
    Object companion=vaultClass.getField("Companion").get(null), gate=r.function(0,a->r.unit());
    Object originalVault=RuntimeAccess.call(RuntimeAccess.call(companion,"createNew",context,gate),"getValue");
    Class<?> identityType=Class.forName("com.monumentogram.dora.audio.AudioIdentity",true,r.loader);
    Constructor<?> identityConstructor=identityType.getDeclaredConstructor(String.class,String.class,String.class);identityConstructor.setAccessible(true);
    Object identity=identityConstructor.newInstance(UUID.randomUUID().toString(),UUID.randomUUID().toString(),UUID.randomUUID().toString());
    Class<?> unitType=Class.forName("com.monumentogram.dora.audio.AudioStorageUnitIdentity",true,r.loader);
    String unitId=UUID.randomUUID().toString();
    Object unit=unitType.getConstructor(identityType,String.class,int.class,long.class,String.class,long.class,long.class)
      .newInstance(identity,unitId,0,0L,unitId,0L,0L);
    Object writer=RuntimeAccess.call(originalVault,"getWriter");
    RuntimeAccess.check(RuntimeAccess.call(writer,"create",identity).getClass().getSimpleName().equals("Value"),"SYNTHETIC_CREATE");
    Object format=Class.forName("com.monumentogram.dora.audio.AudioFormat",true,r.loader).getConstructor(String.class,int.class,int.class).newInstance("PCM_S16LE",16000,1);
    byte[] pcm=new byte[1600];Arrays.fill(pcm,(byte)37);
    try {RuntimeAccess.check(RuntimeAccess.call(writer,"append",unit,format,pcm).getClass().getSimpleName().equals("Value"),"SYNTHETIC_APPEND");}
    finally {Arrays.fill(pcm,(byte)0);RuntimeAccess.call(originalVault,"close");}
    File original=new File(synthetic,"dora-vault-v1");TreeMap<String,String> before=ForensicSnapshot.hashes(original);
    Context copy=ForensicSnapshot.copy(r.app,original,new File(root,"encrypted-copy"));
    Object vault=RuntimeAccess.call(RuntimeAccess.call(companion,"openExisting",copy,gate),"getValue");
    try {
      Object journal=RuntimeAccess.field(vault,"journal"),catalog=RuntimeAccess.call(journal,"getCatalog");
      try(AutoCloseable lease=(AutoCloseable)RuntimeAccess.call(catalog,"tryAcquire",identity)) {
        Object asset=RuntimeAccess.call(catalog,"load",identity);
        List<?> segments=(List<?>)RuntimeAccess.call(asset,"getSegments");RuntimeAccess.check(segments.size()==1,"SYNTHETIC_CATALOG");
        SyntheticAuthorization authorization=new SyntheticAuthorization();
        ForensicPrefix prefix=new ForensicPrefix(r,vault,original,authorization);
        RuntimeAccess.check(prefix.verify(segments.get(0))==800&&prefix.verifiedBuffers==1&&prefix.deniedMutations==0,"SYNTHETIC_PREFIX");
        RuntimeAccess.check(ForensicSnapshot.hashes(original).equals(before),"SYNTHETIC_READ_MUTATION");
        authorization.revokeAt=authorization.calls+3;
        boolean revoked=false;try {prefix.verify(segments.get(0));}catch(Exception expected){revoked=true;}
        RuntimeAccess.check(revoked&&prefix.verifiedBuffers==2,"SYNTHETIC_REVOCATION_CLEANUP");
        RuntimeAccess.check(ForensicSnapshot.hashes(original).equals(before),"SYNTHETIC_FAILURE_MUTATION");
        authorization.revokeAt=Integer.MAX_VALUE;
        prefix.syntheticWriteGuardCheck();
        List<String> ciphertexts=new ArrayList<>();for(String path:before.keySet())if(path.contains("/units/"))ciphertexts.add(path);
        RuntimeAccess.check(ciphertexts.size()==1,"SYNTHETIC_CIPHERTEXT_COUNT");
        File ciphertext=new File(original,ciphertexts.get(0));
        int first;
        try(RandomAccessFile file=new RandomAccessFile(ciphertext,"rw")) {first=file.read();file.seek(0);file.write(first^1);}
        TreeMap<String,String> corrupted=ForensicSnapshot.hashes(original);
        boolean rejected=false;try {prefix.verify(segments.get(0));}catch(Exception expected){rejected=true;}
        RuntimeAccess.check(rejected&&prefix.verifiedBuffers==2&&ForensicSnapshot.hashes(original).equals(corrupted),"SYNTHETIC_CORRUPTION_NOT_READ_ONLY");
        try(RandomAccessFile file=new RandomAccessFile(ciphertext,"rw")) {file.write(first);}
        RuntimeAccess.check(ForensicSnapshot.hashes(original).equals(before),"SYNTHETIC_RESTORE");
        evidence.put("authenticatedSyntheticFrames",800).put("rawDecryptOwnedBuffersCleared",prefix.verifiedBuffers)
          .put("sourceUnchangedAfterAuthenticatedRead",true).put("sourceUnchangedAfterRevocation",true)
          .put("syntheticPcmDecrypted",true).put("corruptCiphertextRejectedWithoutMutation",true)
          .put("writeGuardsRejectBeforeOsCall",true).put("providerInternalMemoryZeroingClaimed",false).put("syntheticChecks",9);
      }
    } finally {RuntimeAccess.call(vault,"close");}
  }
}
