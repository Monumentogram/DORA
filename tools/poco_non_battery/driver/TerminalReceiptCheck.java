package com.monumentogram.dora.stage86b.driver;

import java.util.*;

/** Pure content-free oracle for the pre-capture storage injection. */
final class TerminalReceiptCheck {
  static void requireNoCapture(String text,long started) {
    if(!text.matches("[A-Za-z0-9_= -]{1,4096}"))fail();
    Map<String,String> values=new TreeMap<>();
    for(String token:text.split(" ")) {
      String[] part=token.split("=",-1);
      if(part.length!=2||values.put(part[0],part[1])!=null)fail();
    }
    expect(values,"terminal_version","1");expect(values,"failure","STORAGE_FULL");
    expect(values,"operation","PREPARATION");expect(values,"persistenceFailure","NONE");
    for(String field:Arrays.asList("captureAttempted","microphoneThreadAlive","servicePresent"))expect(values,field,"false");
    for(String field:Arrays.asList("generation","admittedFrames","durableFrames","outstandingFrames",
        "pendingWriterUnits","lastCompletedAppendDurationNanos","canonicalQueueHighWater"))expect(values,field,"0");
    for(String field:Arrays.asList("nativeEventNanos","nativeGeneration","nativeAdmittedFrames",
        "nativeDurableFrames","nativeOutstandingBlocks","writerFailureOperation","vadQueueHighWater"))expect(values,field,"UNKNOWN");
    try{if(Long.parseLong(values.get("observedNanos"))<started)fail();}
    catch(NumberFormatException failure){fail();}
  }
  private static void expect(Map<String,String> values,String field,String value){if(!value.equals(values.get(field)))fail();}
  private static void fail(){throw new IllegalStateException("TERMINAL_RECEIPT_NO_CAPTURE_ORACLE");}
}
