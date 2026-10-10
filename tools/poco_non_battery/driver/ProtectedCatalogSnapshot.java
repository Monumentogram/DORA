package com.monumentogram.dora.stage86b.driver;

import android.content.Context;
import android.database.Cursor;
import android.security.keystore.KeyInfo;
import android.util.Base64;
import java.io.File;
import java.lang.reflect.*;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;

/** Private metadata only. Direct read-only SQLCipher on a new isolated copy, never Room. */
final class ProtectedCatalogSnapshot {
  private static final Set<String> TABLES = new TreeSet<>(Arrays.asList(
    "audio_segmentation", "original_audio_reference", "vault_binding", "audio_asset",
    "physical_source", "unit_claim", "audio_intent", "bootstrap", "manifest", "microfile",
    "finalization_source", "quarantine_intent", "deletion_tombstone", "deletion_target",
    "room_master_table"));
  private static final int MAX_ROWS = 100000, MAX_BYTES = 32 * 1024 * 1024;
  private static final String KEY_PREFIX = "com.monumentogram.dora.audio.persistence.keys.";

  static Object instance(RuntimeAccess r, String name) throws Exception {
    Field f=Class.forName(name,true,r.loader).getDeclaredField("INSTANCE"); f.setAccessible(true); return f.get(null);
  }
  private static Object construct(RuntimeAccess r,String name,Class<?>[] types,Object...args) throws Exception {
    Constructor<?> c=Class.forName(name,true,r.loader).getDeclaredConstructor(types); c.setAccessible(true); return c.newInstance(args);
  }
  static JSONObject collect(RuntimeAccess r, Context copied, Object authorization) throws Exception {
    return collect(r,copied,authorization,false);
  }
  static JSONObject collect(RuntimeAccess r, Context copied, Object authorization,boolean writeCanary) throws Exception {
    if(writeCanary)RuntimeAccess.check(android.os.Build.FINGERPRINT.contains("generic")||android.os.Build.PRODUCT.contains("sdk"),"PROTECTED_CANARY_EMULATOR_ONLY");
    RuntimeAccess.check(copied instanceof ForensicSnapshot.ContextAt,"PROTECTED_COPY_REQUIRED");
    RuntimeAccess.call(authorization,"requireActive");
    Class<?> io=Class.forName(KEY_PREFIX+"VaultFileIo",true,r.loader);
    Class<?> keyIo=Class.forName(KEY_PREFIX+"VaultKeystoreIo",true,r.loader);
    Object storage=construct(r,KEY_PREFIX+"AndroidVaultBundleStorage",new Class<?>[]{Context.class,io},copied,
      instance(r,KEY_PREFIX+"AndroidVaultFileIo"));
    Object backend=construct(r,KEY_PREFIX+"AndroidVaultKeyBackend",new Class<?>[]{Context.class,keyIo},copied,
      instance(r,KEY_PREFIX+"AndroidVaultKeystoreIo"));
    Object store=construct(r,KEY_PREFIX+"VaultSecretStore",new Class<?>[]{
      Class.forName(KEY_PREFIX+"VaultBundleStorage",true,r.loader),Class.forName(KEY_PREFIX+"VaultKeyBackend",true,r.loader)},storage,backend);
    Object opened=RuntimeAccess.call(store,"openExisting");
    RuntimeAccess.check(opened.getClass().getSimpleName().equals("Available"),"PROTECTED_ROOT_AUTHENTICATION");
    Object secrets=RuntimeAccess.call(opened,"getValue");
    File vault=(File)RuntimeAccess.call(storage,"getVaultDirectory");
    File file=new File(vault,"journal-"+RuntimeAccess.call(secrets,"getDatabaseObjectSelector")+".db");
    RuntimeAccess.check(file.isFile()&&file.getCanonicalFile().getParentFile().equals(vault.getCanonicalFile()),"PROTECTED_COPY_DATABASE");
    Object callback=r.function(1,args->{
      RuntimeAccess.call(authorization,"requireActive");
      byte[] secret=(byte[])args[0];
      // Initialize the admitted native library/logger only. No helper is created or opened.
      Object factory=construct(r,"com.monumentogram.dora.audio.persistence.database.SqlCipherJournalHelperFactory",
        new Class<?>[]{Context.class,File.class,byte[].class},copied,file,secret);
      Object db=null;
      try {
        Class<?> type=Class.forName("net.zetetic.database.sqlcipher.SQLiteDatabase",true,r.loader);
        Class<?> cursorFactory=Class.forName(type.getName()+"$CursorFactory",true,r.loader);
        Class<?> hook=Class.forName("net.zetetic.database.sqlcipher.SQLiteDatabaseHook",true,r.loader);
        Method open=type.getMethod("openDatabase",String.class,byte[].class,cursorFactory,int.class,hook);
        db=open.invoke(null,file.getCanonicalPath(),secret,null,type.getField("OPEN_READONLY").getInt(null),null);
        RuntimeAccess.check(Boolean.TRUE.equals(RuntimeAccess.call(db,"isReadOnly")),"PROTECTED_NOT_READONLY");
        // Reuse the admitted DDL/FK verifier; it only executes SELECT/foreign_key_check.
        RuntimeAccess.call(instance(r,"com.monumentogram.dora.audio.persistence.journal.JournalSchemaVerifier"),"verify",db,3);
        JSONObject result=read(db,authorization);
        if(writeCanary) {
          boolean denied=false;
          try {db.getClass().getMethod("execSQL",String.class).invoke(db,"DELETE FROM vault_binding");}
          catch(InvocationTargetException expected){denied=true;}
          RuntimeAccess.check(denied&&read(db,authorization).toString().equals(result.toString()),"PROTECTED_READONLY_WRITE_CANARY");
        }
        JSONObject binding=result.getJSONObject("tables").getJSONObject("vault_binding");
        JSONArray columns=binding.getJSONArray("columns"),rows=binding.getJSONArray("rows");
        RuntimeAccess.check(columns.toString().equals("[\"singleton\",\"ownerId\",\"vaultId\"]"),"PROTECTED_BINDING_COLUMNS");
        RuntimeAccess.check(rows.length()==1,"PROTECTED_BINDING_COUNT");
        RuntimeAccess.check(rows.getJSONArray(0).getLong(0)==1,"PROTECTED_BINDING_SINGLETON");
        for(int i=0;i<columns.length();i++) {
          String c=columns.getString(i);
          if(c.equals("ownerId"))RuntimeAccess.check(rows.getJSONArray(0).getString(i).equals(RuntimeAccess.call(secrets,"getOwnerId")),"PROTECTED_OWNER_BINDING");
          if(c.equals("vaultId"))RuntimeAccess.check(rows.getJSONArray(0).getString(i).equals(RuntimeAccess.call(secrets,"getVaultId")),"PROTECTED_VAULT_BINDING");
        }
        RuntimeAccess.call(authorization,"requireActive"); return result;
      } finally {if(db!=null)RuntimeAccess.call(db,"close");RuntimeAccess.call(factory,"close");}
    });
    JSONObject result=(JSONObject)RuntimeAccess.call(secrets,"borrowDatabaseSecret",callback);
    result.put("keys",keys());
    RuntimeAccess.call(authorization,"requireActive");
    RuntimeAccess.check(result.toString().length()<=MAX_BYTES,"PROTECTED_OUTPUT_BOUND");
    return result;
  }
  private static Cursor query(Object db,String sql) throws Exception {
    // Do not use ambiguous reflection by arity: SQLite exposes multiple one-argument query overloads.
    return (Cursor)db.getClass().getMethod("query",String.class).invoke(db,sql);
  }
  static JSONObject read(Object db,Object authorization) throws Exception {
    int version;
    try(Cursor c=query(db,"PRAGMA user_version")) {RuntimeAccess.check(c.moveToFirst(),"PROTECTED_VERSION_MISSING");version=c.getInt(0);}
    RuntimeAccess.check(version==3,"PROTECTED_SCHEMA_VERSION");
    try(Cursor c=query(db,"PRAGMA cipher_integrity_check")) {RuntimeAccess.check(!c.moveToFirst(),"PROTECTED_CIPHER_INTEGRITY");}
    try(Cursor c=query(db,"PRAGMA integrity_check")) {
      RuntimeAccess.check(c.moveToFirst()&&c.getString(0).equals("ok")&&!c.moveToNext(),"PROTECTED_SQL_INTEGRITY");
    }
    try(Cursor c=query(db,"PRAGMA foreign_key_check")) {RuntimeAccess.check(!c.moveToFirst(),"PROTECTED_FOREIGN_KEY_INTEGRITY");}
    JSONArray schema=new JSONArray();Set<String> found=new TreeSet<>();
    try(Cursor c=query(db,"SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY type,name,tbl_name")) {
      while(c.moveToNext()) {
        JSONArray row=new JSONArray();for(int i=0;i<4;i++)row.put(c.isNull(i)?JSONObject.NULL:c.getString(i));schema.put(row);
        if(c.getString(0).equals("table"))found.add(c.getString(1));
      }
    }
    RuntimeAccess.check(found.equals(TABLES),"PROTECTED_TABLE_INVENTORY_MISSING_"+String.join("_",difference(TABLES,found)).toUpperCase(Locale.ROOT));
    JSONObject tables=new JSONObject();int total=0;
    for(String table:TABLES) {
      RuntimeAccess.call(authorization,"requireActive");
      JSONArray rows=new JSONArray(),columns=new JSONArray();
      try(Cursor c=query(db,"SELECT * FROM \""+table+"\"")) {
        for(String name:c.getColumnNames())columns.put(name);
        ArrayList<String> sorted=new ArrayList<>();
        while(c.moveToNext()) {
          RuntimeAccess.check(++total<=MAX_ROWS,"PROTECTED_ROW_BOUND");
          JSONArray row=new JSONArray();
          for(int i=0;i<c.getColumnCount();i++) {
            switch(c.getType(i)) {
              case Cursor.FIELD_TYPE_NULL: row.put(JSONObject.NULL);break;
              case Cursor.FIELD_TYPE_INTEGER: row.put(c.getLong(i));break;
              case Cursor.FIELD_TYPE_STRING: row.put(c.getString(i));break;
              default: throw new IllegalStateException("PROTECTED_UNADMITTED_SQL_TYPE");
            }
          }
          sorted.add(row.toString());
        }
        Collections.sort(sorted);for(String row:sorted)rows.put(new JSONArray(row));
      }
      tables.put(table,new JSONObject().put("columns",columns).put("rows",rows));
    }
    return new JSONObject().put("format","DORA_PRIVATE_CATALOG_V1").put("schemaVersion",version)
      .put("schema",schema).put("tables",tables);
  }
  private static Set<String> difference(Set<String> first,Set<String> second) {
    Set<String> result=new TreeSet<>(first);result.removeAll(second);return result;
  }
  static JSONObject keys() throws Exception {
    KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
    List<String> names=Collections.list(store.aliases());Collections.sort(names);
    RuntimeAccess.check(names.size()<=MAX_ROWS,"PROTECTED_KEY_BOUND");JSONObject result=new JSONObject();
    for(String name:names) {
      Key key=store.getKey(name,null);
      RuntimeAccess.check(key instanceof SecretKey,"PROTECTED_UNSUPPORTED_KEY_TYPE");
      KeyInfo info=(KeyInfo)SecretKeyFactory.getInstance(key.getAlgorithm(),"AndroidKeyStore").getKeySpec((SecretKey)key,KeyInfo.class);
      Date created=store.getCreationDate(name);
      RuntimeAccess.check(created!=null,"PROTECTED_KEY_DATE_UNAVAILABLE");
      result.put(name,new JSONObject().put("created",created.getTime()).put("algorithm",key.getAlgorithm())
        .put("keySize",info.getKeySize()).put("purposes",info.getPurposes()).put("origin",info.getOrigin())
        .put("userAuthenticationRequired",info.isUserAuthenticationRequired()).put("hardware",info.isInsideSecureHardware())
        .put("blockModes",new JSONArray(Arrays.asList(info.getBlockModes())))
        .put("encryptionPaddings",new JSONArray(Arrays.asList(info.getEncryptionPaddings()))));
    }
    return result;
  }
}
