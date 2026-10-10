"""Execute the actual Java receipt oracle with valid and adversarial inputs."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class TerminalReceiptTest(unittest.TestCase):
    def test_real_java_oracle_rejects_audio_stale_missing_and_duplicate_evidence(self):
        javac = shutil.which('javac')
        if not javac and os.environ.get('JAVA_HOME'):
            javac = str(Path(os.environ['JAVA_HOME']) / 'bin/javac.exe')
        self.assertTrue(javac, 'JDK is required for the diagnostic oracle test')
        java = str(Path(javac).with_name('java.exe' if os.name == 'nt' else 'java'))
        source = Path(__file__).with_name('driver') / 'TerminalReceiptCheck.java'
        harness = '''package com.monumentogram.dora.stage86b.driver;
public final class OracleTest {
  public static void main(String[] ignored) {
    String valid="terminal_version=1 failure=STORAGE_FULL persistenceFailure=NONE operation=PREPARATION "
      +"phase=IDLE observedNanos=100 generation=0 admittedFrames=0 durableFrames=0 outstandingFrames=0 "
      +"pendingWriterUnits=0 lastCompletedAppendDurationNanos=0 canonicalQueueHighWater=0 "
      +"captureAttempted=false microphoneThreadAlive=false servicePresent=false nativeEventNanos=UNKNOWN "
      +"nativeGeneration=UNKNOWN nativeAdmittedFrames=UNKNOWN nativeDurableFrames=UNKNOWN "
      +"nativeOutstandingBlocks=UNKNOWN writerFailureOperation=UNKNOWN vadQueueHighWater=UNKNOWN";
    TerminalReceiptCheck.requireNoCapture(valid,100);
    String[] invalid={valid.replace("admittedFrames=0","admittedFrames=160"),
      valid.replace("durableFrames=0","durableFrames=160"),
      valid.replace("outstandingFrames=0","outstandingFrames=1"),
      valid.replace("pendingWriterUnits=0","pendingWriterUnits=1"),
      valid.replace("observedNanos=100","observedNanos=99"),
      valid.replace("nativeGeneration=UNKNOWN","nativeGeneration=1"),
      valid.replace("servicePresent=false","servicePresent=true"),
      valid.replace("admittedFrames=0 ",""),valid+" admittedFrames=0"};
    for(String receipt:invalid) {
      boolean rejected=false;
      try{TerminalReceiptCheck.requireNoCapture(receipt,100);}catch(IllegalStateException expected){rejected=true;}
      if(!rejected)throw new AssertionError("Invalid diagnostic receipt accepted");
    }
    System.out.println("PASS 1 valid / 9 rejected receipts");
  }
}'''
        with tempfile.TemporaryDirectory(prefix='dora-terminal-oracle-') as directory:
            root = Path(directory)
            test = root / 'OracleTest.java'
            test.write_text(harness, encoding='utf-8')
            subprocess.run([javac, '-d', directory, str(source), str(test)], check=True, capture_output=True)
            result = subprocess.run([java, '-cp', directory,
                                     'com.monumentogram.dora.stage86b.driver.OracleTest'],
                                    check=True, capture_output=True, text=True)
            self.assertIn('PASS 1 valid / 9 rejected receipts', result.stdout)


if __name__ == '__main__':
    unittest.main()
