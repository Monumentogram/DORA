package com.monumentogram.dora.stage86b.driver;

import android.system.OsConstants;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;

/** Exact one-microfile verifier. Never invokes recover/reconcile/acquire or retains PCM. */
final class ForensicPrefix {
  private final RuntimeAccess r;
  private final Object reader,source,crypto,confirmation;
  private final Object authorization;
  private final Object guardedOs;
  int deniedMutations;
  int verifiedBuffers;
  ForensicPrefix(RuntimeAccess runtime,Object vault,File original,Object authorization) throws Exception {
    r=runtime;this.authorization=authorization;
    reader=RuntimeAccess.field(RuntimeAccess.field(vault,"bridge"),"reader");
    source=RuntimeAccess.field(reader,"source"); crypto=RuntimeAccess.field(reader,"crypto");
    confirmation=RuntimeAccess.field(reader,"confirmationController");
    Set<Object> delegates=Collections.newSetFromMap(new IdentityHashMap<>());
    for(Field f:source.getClass().getDeclaredFields()) {
      f.setAccessible(true);Object value=f.get(source);
      if(value!=null&&value.getClass().getName().equals("com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryReconciliationSource")) delegates.add(value);
    }
    RuntimeAccess.check(delegates.size()==1,"FORENSIC_SOURCE_SHAPE");
    String prefix="com.monumentogram.dora.poc.recovery.storage.";
    Class<?> osType=Class.forName(prefix+"RecoveryReconciliationOs",true,r.loader);
    Class<?> actual=Class.forName(prefix+"AndroidRecoveryReconciliationOs",true,r.loader);
    Field instance=actual.getDeclaredField("INSTANCE");instance.setAccessible(true);Object os=instance.get(null);
    Object readOnly=Proxy.newProxyInstance(r.loader,new Class<?>[]{osType},(p,m,args)->{
      String name=m.getName();
      if(!Arrays.asList("lstat","list","open","fstat","read","close").contains(name)) {deniedMutations++;throw new IllegalStateException("FORENSIC_WRITE_DENIED");}
      if(name.equals("open")) {
        int flags=(Integer)args[1];
        if((flags&(OsConstants.O_WRONLY|OsConstants.O_RDWR|OsConstants.O_CREAT|OsConstants.O_TRUNC|OsConstants.O_APPEND))!=0) {deniedMutations++;throw new IllegalStateException("FORENSIC_WRITE_OPEN_DENIED");}
      }
      m.setAccessible(true);return m.invoke(os,args);
    });
    guardedOs=readOnly;
    Class<?> storage=Class.forName(prefix+"AndroidOsRecoveryReconciliationStorage",true,r.loader);
    Constructor<?> constructor=storage.getDeclaredConstructor(File.class,osType);constructor.setAccessible(true);
    RuntimeAccess.setField(delegates.iterator().next(),"storage",constructor.newInstance(original,readOnly));
    // Defensive: even accidental future controller use has no quarantine executor.
    RuntimeAccess.setField(reader,"quarantineController",null);
  }
  void syntheticWriteGuardCheck() throws Exception {
    int before=deniedMutations;
    try {invoke(guardedOs,"mkdir","SYNTHETIC_MUST_NOT_BE_USED",0700);}catch(Exception expected){}
    try {invoke(guardedOs,"open","SYNTHETIC_MUST_NOT_BE_USED",OsConstants.O_WRONLY);}catch(Exception expected){}
    RuntimeAccess.check(deniedMutations==before+2,"FORENSIC_WRITE_GUARD_TEST");
  }
  static Object invoke(Object target,String base,Object... args) throws Exception {
    List<Method> found=new ArrayList<>();
    for(Method m:target.getClass().getDeclaredMethods())
      if((m.getName().equals(base)||m.getName().startsWith(base+"-"))&&m.getParameterCount()==args.length) found.add(m);
    RuntimeAccess.check(found.size()==1,"FORENSIC_METHOD_SHAPE");
    Method method=found.get(0);method.setAccessible(true);return method.invoke(target,args);
  }
  private static Object get(Object row,String name) throws Exception {return invoke(row,"get"+name);}
  private static long number(Object row,String name) throws Exception {return ((Number)get(row,name)).longValue();}
  private Object companion(String name) throws Exception {return Class.forName(name,true,r.loader).getField("Companion").get(null);}
  private byte[] artifact(Object run,Object row,String name,String size,String digest,String context) throws Exception {
    Class<?> kind=Class.forName("com.monumentogram.dora.poc.recovery.candidate.RecoveryArtifactContext",true,r.loader);
    @SuppressWarnings({"rawtypes","unchecked"}) Object value=Enum.valueOf((Class)kind,context);
    Object loaded=invoke(source,"loadArtifact",run,get(row,name),value);
    RuntimeAccess.check(loaded!=null&&number(loaded,"Size")==number(row,size)&&get(loaded,"Sha256").equals(get(row,digest)),"FORENSIC_ARTIFACT_IDENTITY");
    return (byte[])invoke(loaded,"snapshot"); // Ciphertext only.
  }
  long verify(Object segment) throws Exception {
    RuntimeAccess.call(authorization,"requireActive");
    long frames=RuntimeAccess.number(segment,"getFrames");RuntimeAccess.check(frames>0&&frames<=80000,"FORENSIC_FRAME_BOUND");
    Object identity=RuntimeAccess.call(segment,"getIdentity");String runText=(String)RuntimeAccess.field(identity,"unitId");
    Object run=RuntimeAccess.call(companion("com.monumentogram.dora.poc.recovery.contract.RunId"),"fromCanonicalString",runText);
    Object confirmed=invoke(confirmation,"evaluate",invoke(source,"loadConfirmation",run));
    RuntimeAccess.check(confirmed.getClass().getSimpleName().equals("Validated"),"FORENSIC_KEY_CONFIRMATION");
    RuntimeAccess.check(((List<?>)invoke(source,"loadPendingQuarantine",run)).isEmpty(),"FORENSIC_PENDING_QUARANTINE");
    Object candidate=invoke(source,"loadCandidate",run);
    List<?> units=(List<?>)get(candidate,"Units"),publications=(List<?>)get(candidate,"Publications");
    RuntimeAccess.check(units.size()==1&&publications.size()==1,"FORENSIC_UNIT_CARDINALITY");
    RuntimeAccess.check(invoke(reader,"maximalValidUnits",units,runText).equals(units),"FORENSIC_UNIT_STRUCTURE");
    Object unit=units.get(0),publication=publications.get(0);
    Object zero=RuntimeAccess.call(companion("com.monumentogram.dora.poc.recovery.contract.Sha256Value"),"getZERO");
    RuntimeAccess.check(number(publication,"Generation")==1&&get(publication,"PreviousPublicationCiphertextSha256").equals(zero)
      &&number(publication,"CommittedEndExclusive")==frames*2&&number(unit,"PlaintextStartInclusive")==0
      &&number(unit,"PlaintextEndExclusive")==frames*2,"FORENSIC_PUBLICATION_RANGE");
    RuntimeAccess.check(get(publication,"PublicationSha256").equals(RuntimeAccess.call(segment,"getManifestDigest")),"FORENSIC_CATALOG_MANIFEST_BINDING");
    byte[] envelope=artifact(run,publication,"KeyEnvelopeRelativeName","KeyEnvelopeBytes","KeyEnvelopeSha256","MANIFEST_KEY_ENVELOPE");
    byte[] ciphertext=artifact(run,publication,"PublicationRelativeName","PublicationBytes","PublicationSha256","MANIFEST_CIPHERTEXT");
    Object outcome=invoke(crypto,"authenticateManifest",run,publication,zero,envelope,ciphertext);
    RuntimeAccess.check(outcome.getClass().getSimpleName().equals("Authenticated"),"FORENSIC_MANIFEST_AUTHENTICATION");
    Object manifest=get(outcome,"Manifest");
    RuntimeAccess.check(get(manifest,"Candidate").toString().equals("MICROFILE")&&get(manifest,"RunId").equals(run)
      &&number(manifest,"Generation")==1&&get(manifest,"PreviousManifestCiphertextSha256").equals(zero)
      &&number(manifest,"CommittedEndExclusive")==frames*2
      &&get(manifest,"Entries").equals(Collections.singletonList(invoke(reader,"manifestEntry",unit))),"FORENSIC_MANIFEST_SEMANTICS");
    envelope=artifact(run,unit,"KeyEnvelopeRelativeName","KeyEnvelopeBytes","KeyEnvelopeSha256","UNIT_KEY_ENVELOPE");
    ciphertext=artifact(run,unit,"CiphertextRelativeName","CiphertextBytes","CiphertextSha256","UNIT_CIPHERTEXT");
    RuntimeAccess.call(authorization,"requireActive");
    // Raw admitted decrypt returns its sole DORA-owned PCM array, avoiding copying outcomes.
    byte[] pcm=(byte[])invoke(crypto,"authenticateUnitRaw",run,unit,zero,envelope,ciphertext);
    try {
      RuntimeAccess.check(pcm.length==frames*2,"FORENSIC_AUTHENTICATED_LENGTH");
      RuntimeAccess.call(authorization,"requireActive"); return frames;
    } finally { Arrays.fill(pcm,(byte)0);verifiedBuffers++; }
  }
}
