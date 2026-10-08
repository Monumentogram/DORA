package com.monumentogram.dora.stage86b.driver;

import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;

/** Private random challenge, never key export. Existing aliases are only opened, never created. */
final class ProtectedKeyProofs {
  private static final byte[] AAD="DORA/protected-key-preservation/v1".getBytes(StandardCharsets.US_ASCII);
  static JSONObject capture(JSONObject states) throws Exception {
    KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
    JSONObject result=new JSONObject();SecureRandom random=new SecureRandom();
    for(Iterator<String> names=states.keys();names.hasNext();) {
      String name=names.next();Key key=store.getKey(name,null);
      RuntimeAccess.check(key instanceof SecretKey&&key.getAlgorithm().equals("AES"),"PROTECTED_KEY_PROOF_TYPE");
      byte[] plaintext=new byte[32];random.nextBytes(plaintext);
      try {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key);cipher.updateAAD(AAD);
        byte[] ciphertext=cipher.doFinal(plaintext);
        result.put(name,new JSONObject().put("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
          .put("ciphertext",Base64.encodeToString(ciphertext,Base64.NO_WRAP))
          .put("plaintextSha256",RuntimeAccess.hex(MessageDigest.getInstance("SHA-256").digest(plaintext))));
      } finally {Arrays.fill(plaintext,(byte)0);}
    }
    return result;
  }
  static int verify(JSONObject proofs) throws Exception {
    KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);int count=0;
    for(Iterator<String> names=proofs.keys();names.hasNext();) {
      String name=names.next();JSONObject row=proofs.getJSONObject(name);
      RuntimeAccess.check(store.containsAlias(name),"PROTECTED_KEY_PROOF_MISSING");
      Key key=store.getKey(name,null);
      RuntimeAccess.check(key instanceof SecretKey&&key.getAlgorithm().equals("AES"),"PROTECTED_KEY_PROOF_TYPE");
      byte[] iv=Base64.decode(row.getString("iv"),Base64.NO_WRAP),ciphertext=Base64.decode(row.getString("ciphertext"),Base64.NO_WRAP);
      RuntimeAccess.check(iv.length==12&&ciphertext.length==48,"PROTECTED_KEY_PROOF_SHAPE");
      Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));cipher.updateAAD(AAD);
      byte[] plaintext=cipher.doFinal(ciphertext);
      try {
        RuntimeAccess.check(plaintext.length==32&&RuntimeAccess.hex(MessageDigest.getInstance("SHA-256").digest(plaintext))
          .equals(row.getString("plaintextSha256")),"PROTECTED_KEY_PROOF_CHANGED");count++;
      } finally {Arrays.fill(plaintext,(byte)0);}
    }
    return count;
  }
}
