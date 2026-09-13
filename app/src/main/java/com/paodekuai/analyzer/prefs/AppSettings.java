package com.paodekuai.analyzer.prefs;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppSettings {
    private static final String P="pdk_settings";
    public final boolean autoDetect, overlayEnabled, advisorEnabled, keepAudit;
    public final int searchBudgetMs, topK, firstPlayer;

    public AppSettings(boolean autoDetect, boolean overlayEnabled, boolean advisorEnabled,
                       boolean keepAudit, int searchBudgetMs, int topK, int firstPlayer) {
        this.autoDetect=autoDetect; this.overlayEnabled=overlayEnabled; this.advisorEnabled=advisorEnabled;
        this.keepAudit=keepAudit; this.searchBudgetMs=searchBudgetMs; this.topK=topK; this.firstPlayer=firstPlayer;
    }

    public static AppSettings load(Context c){
        SharedPreferences s=c.getSharedPreferences(P,Context.MODE_PRIVATE);
        return new AppSettings(s.getBoolean("auto",true),s.getBoolean("overlay",true),
                s.getBoolean("advisor",true),s.getBoolean("audit",true),
                clamp(s.getInt("budget",500),100,3000),clamp(s.getInt("topk",3),1,5),
                clamp(s.getInt("first",-1),-1,1));
    }
    public void save(Context c){c.getSharedPreferences(P,Context.MODE_PRIVATE).edit()
            .putBoolean("auto",autoDetect).putBoolean("overlay",overlayEnabled)
            .putBoolean("advisor",advisorEnabled).putBoolean("audit",keepAudit)
            .putInt("budget",searchBudgetMs).putInt("topk",topK).putInt("first",firstPlayer).apply();}
    private static int clamp(int x,int a,int b){return Math.max(a,Math.min(b,x));}
}
