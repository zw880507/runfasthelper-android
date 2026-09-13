package com.paodekuai.analyzer.audit;
import android.content.Context;import java.io.*;import java.text.SimpleDateFormat;import java.util.*;
public final class AuditLogger implements Closeable {
    private final PrintWriter w; private final File file;
    public AuditLogger(Context c,boolean enabled) throws IOException {if(!enabled){w=null;file=null;return;}File d=new File(c.getExternalFilesDir(null),"audit");d.mkdirs();file=new File(d,"run_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".log");w=new PrintWriter(new BufferedWriter(new FileWriter(file,true)));}
    public synchronized void log(String x){if(w!=null){w.println(System.currentTimeMillis()+"\t"+x);w.flush();}}
    public File file(){return file;} public void close(){if(w!=null)w.close();}
}
