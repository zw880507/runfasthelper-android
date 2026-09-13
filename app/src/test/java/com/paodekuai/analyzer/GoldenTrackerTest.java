package com.paodekuai.analyzer;
import com.paodekuai.analyzer.tracker.*;import org.junit.Test;import static org.junit.Assert.*;
public class GoldenTrackerTest {
 private void run(String hand,int first,String[] ev,int win,int a,int b){GameTracker t=new GameTracker(hand,16,first);for(String x:ev){String[] q=x.split(":",2);t.observe(Integer.parseInt(q[0]),q[1]);}assertEquals(Integer.valueOf(win),t.state().winner);assertArrayEquals(new int[]{a,b},t.state().cardsLeft);}
 @Test public void fourGoldenRounds(){
  run("AAKQQJ9998876554",1,new String[]{"1:T987654","0:PASS","1:5","0:K","1:2","0:PASS","1:7","0:J","1:K","0:A","1:3333","0:PASS","1:Q"},1,13,0);
  run("2AQJTTT886644333",1,new String[]{"1:44","0:66","1:99","0:TT","1:KK","0:PASS","1:7","0:A","1:5555","0:PASS","1:AKQJT"},1,11,0);
  run("JJJT998876654433",1,new String[]{"1:77743","0:JJJ75","1:PASS","0:4433","1:AAKK","0:PASS","1:8","0:T","1:J","0:PASS","1:55","0:66","1:TT","0:PASS","1:2"},1,4,0);
  run("AKQQTTT987654433",1,new String[]{"1:99","0:QQ","1:KK","0:PASS","1:66653","0:TTT43","1:PASS","0:9876543","1:PASS","0:A","1:PASS","0:K"},0,0,7);
 }
}
