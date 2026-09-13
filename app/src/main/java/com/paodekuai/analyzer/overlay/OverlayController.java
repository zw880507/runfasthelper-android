package com.paodekuai.analyzer.overlay;

import android.content.*;import android.graphics.*;import android.provider.Settings;import android.view.*;import android.widget.*;import com.paodekuai.analyzer.service.CaptureService;

/** Small top-left overlay deliberately outside the calibrated hand/table ROIs. */
public final class OverlayController {
    private final Context c; private final WindowManager wm; private LinearLayout root; private TextView title,body,debug; private boolean expanded=true;
    private String lastStatus="等待牌局",lastDetail="",lastDebug="phase=INITIALIZING";
    public OverlayController(Context c){this.c=c;wm=(WindowManager)c.getSystemService(Context.WINDOW_SERVICE);}
    public boolean available(){return Settings.canDrawOverlays(c);}
    public void show(){if(root!=null||!available())return;root=new LinearLayout(c);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(10),dp(7),dp(10),dp(7));root.setBackground(roundBg());
        title=new TextView(c);title.setText("● PDK · 启动中");title.setTextColor(Color.WHITE);title.setTextSize(13);title.setTypeface(null,1);root.addView(title);
        body=new TextView(c);body.setTextColor(Color.WHITE);body.setTextSize(12);body.setText("等待牌局");body.setPadding(0,dp(4),0,0);root.addView(body);
        debug=new TextView(c);debug.setTextColor(0xFFE0E0E0);debug.setTextSize(10);debug.setTypeface(android.graphics.Typeface.MONOSPACE);debug.setText("phase=INITIALIZING");debug.setPadding(0,dp(5),0,0);root.addView(debug);
        LinearLayout actions=new LinearLayout(c);Button min=new Button(c);min.setText("－");min.setMinWidth(0);min.setOnClickListener(v->toggle());Button stop=new Button(c);stop.setText("停止");stop.setOnClickListener(v->{Intent i=new Intent(c,CaptureService.class);i.setAction(CaptureService.ACTION_STOP);c.startService(i);});actions.addView(min,new LinearLayout.LayoutParams(dp(52),dp(40)));actions.addView(stop,new LinearLayout.LayoutParams(dp(74),dp(40)));root.addView(actions);
        int type=WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;WindowManager.LayoutParams lp=new WindowManager.LayoutParams(dp(315),WindowManager.LayoutParams.WRAP_CONTENT,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);lp.gravity=Gravity.TOP|Gravity.START;lp.x=dp(8);lp.y=dp(38);wm.addView(root,lp);render();}
    public void status(String s,String detail){lastStatus=s==null?"":s;lastDetail=detail==null?"":detail;render();}
    public void debug(String text){lastDebug=text==null?"":text;render();}
    public void advice(String target,String ownLeft,int oppLeft,String text){status("轮到你","目标："+(target==null?"自由出牌":target)+"   你:"+ownLeft+" 对手:"+oppLeft+"\n"+text);}
    public void hide(){if(root!=null){try{wm.removeView(root);}catch(Exception ignored){}root=null;}}
    private void render(){if(root==null)return;root.post(()->{if(title!=null)title.setText("● PDK · "+lastStatus);if(body!=null)body.setText(lastDetail);if(debug!=null)debug.setText(lastDebug);});}
    private void toggle(){expanded=!expanded;if(body!=null)body.setVisibility(expanded?View.VISIBLE:View.GONE);if(debug!=null)debug.setVisibility(expanded?View.VISIBLE:View.GONE);}
    private android.graphics.drawable.GradientDrawable roundBg(){android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setColor(0xDD202124);g.setCornerRadius(dp(12));return g;}
    private int dp(int x){return (int)(x*c.getResources().getDisplayMetrics().density+.5f);}
}
