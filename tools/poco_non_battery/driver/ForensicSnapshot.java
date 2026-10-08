package com.monumentogram.dora.stage86b.driver;

import android.content.Context;
import android.content.ContextWrapper;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Source-preserving encrypted catalog copy. Never opens the source through SQLite. */
final class ForensicSnapshot {
  private static final long MAX_CATALOG_BYTES = 128L * 1024 * 1024;
  static final class ContextAt extends ContextWrapper {
    private final File root;
    ContextAt(Context base, File root) { super(base); this.root = root; }
    @Override public File getNoBackupFilesDir() { return root; }
    @Override public Context getApplicationContext() { return this; }
  }
  static TreeMap<String,String> hashes(File root) throws Exception {
    TreeMap<String,String> result = new TreeMap<>();
    visit(root, root, result); return result;
  }
  private static void visit(File root, File item, TreeMap<String,String> values) throws Exception {
    StructStat stat = Os.lstat(item.getPath());
    if (OsConstants.S_ISDIR(stat.st_mode)) {
      File[] children = item.listFiles(); RuntimeAccess.check(children != null, "FORENSIC_LIST");
      for (File child : children) visit(root, child, values);
    } else {
      RuntimeAccess.check(OsConstants.S_ISREG(stat.st_mode) && stat.st_nlink == 1, "FORENSIC_UNSAFE_LEAF");
      FileDescriptor fd = Os.open(item.getPath(), OsConstants.O_RDONLY | OsConstants.O_NOFOLLOW, 0);
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[65536];
      try {
        verify(stat, Os.fstat(fd));
        int count; while ((count = Os.read(fd, buffer, 0, buffer.length)) > 0) digest.update(buffer, 0, count);
        verify(stat, Os.lstat(item.getPath()));
      } finally { Arrays.fill(buffer, (byte)0); Os.close(fd); }
      values.put(root.toPath().relativize(item.toPath()).toString(), RuntimeAccess.hex(digest.digest()));
    }
  }
  static void verify(StructStat before, StructStat after) {
    RuntimeAccess.check(before.st_dev == after.st_dev && before.st_ino == after.st_ino
      && before.st_size == after.st_size && before.st_mtime == after.st_mtime
      && before.st_mode == after.st_mode && after.st_nlink == 1, "FORENSIC_SOURCE_CHANGED");
  }
  static ContextAt copy(Context base, File source, File destination) throws Exception {
    RuntimeAccess.check(OsConstants.S_ISDIR(Os.lstat(source.getPath()).st_mode), "FORENSIC_ROOT");
    RuntimeAccess.check(!destination.exists() && !destination.getCanonicalPath().startsWith(source.getCanonicalPath()+File.separator), "FORENSIC_DESTINATION");
    Os.mkdir(destination.getPath(), 0700);
    File vault = new File(destination, "dora-vault-v1"); Os.mkdir(vault.getPath(), 0700);
    File[] files = source.listFiles(); RuntimeAccess.check(files != null, "FORENSIC_LIST");
    int selectors=0,bundles=0,databases=0; long total=0;
    for (File file : files) {
      String name = file.getName();
      if (!(name.equals("selector") || name.equals("vault.bundle")
          || name.matches("journal-[a-zA-Z0-9-]+\\.db(?:-wal)?"))) continue;
      StructStat before = Os.lstat(file.getPath());
      RuntimeAccess.check(OsConstants.S_ISREG(before.st_mode) && before.st_nlink == 1, "FORENSIC_UNSAFE_CATALOG");
      total = Math.addExact(total,before.st_size); RuntimeAccess.check(total <= MAX_CATALOG_BYTES, "FORENSIC_CATALOG_BOUND");
      File target = new File(vault,name);
      FileDescriptor input = Os.open(file.getPath(), OsConstants.O_RDONLY | OsConstants.O_NOFOLLOW, 0);
      try {
        verify(before,Os.fstat(input));
        FileDescriptor output = Os.open(target.getPath(), OsConstants.O_WRONLY | OsConstants.O_CREAT | OsConstants.O_EXCL | OsConstants.O_NOFOLLOW, 0600);
        byte[] buffer = new byte[65536];
        try {
          long copied=0; int count;
          while ((count=Os.read(input,buffer,0,buffer.length))>0) {
            copied+=count; RuntimeAccess.check(copied<=before.st_size,"FORENSIC_COPY_GROWTH");
            int offset=0; while(offset<count) { int n=Os.write(output,buffer,offset,count-offset); RuntimeAccess.check(n>0,"FORENSIC_COPY_WRITE"); offset+=n; }
          }
          RuntimeAccess.check(copied==before.st_size,"FORENSIC_COPY_TRUNCATION"); Os.fsync(output);
        } finally { Arrays.fill(buffer,(byte)0); Os.close(output); }
        verify(before,Os.lstat(file.getPath()));
      } finally { Os.close(input); }
      if(name.equals("selector")) selectors++;
      if(name.equals("vault.bundle")) bundles++;
      if(name.endsWith(".db")) databases++;
    }
    RuntimeAccess.check(selectors==1 && bundles==1 && databases==1,"FORENSIC_CATALOG_SET");
    return new ContextAt(base,destination);
  }
  static String digest(TreeMap<String,String> values) throws Exception {
    StringBuilder text=new StringBuilder();
    for(Map.Entry<String,String> row:values.entrySet()) text.append(row.getKey()).append('=').append(row.getValue()).append('\n');
    return RuntimeAccess.hash(text.toString());
  }
}
