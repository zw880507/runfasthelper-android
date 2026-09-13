package com.paodekuai.analyzer.tracker;

import java.util.*;

public final class GameTracker {
    public static final class State {
        public final String ownHand;public final int[] cardsLeft;public final RuleEngine.Play currentTarget;public final Integer targetOwner;public final int currentPlayer,consecutivePasses;public final Integer winner;
        State(String h,int[] left,RuleEngine.Play t,Integer owner,int cp,int passes,Integer winner){ownHand=h;cardsLeft=left;currentTarget=t;targetOwner=owner;currentPlayer=cp;consecutivePasses=passes;this.winner=winner;}
    }
    public static final class Hist {public final int player;public final RuleEngine.Play targetBefore,action;Hist(int p,RuleEngine.Play t,RuleEngine.Play a){player=p;targetBefore=t;action=a;}}
    private State state;private final List<Hist> history=new ArrayList<>();
    public GameTracker(String myHand,int opponentCards,int firstPlayer){state=new State(RuleEngine.sortText(myHand),new int[]{myHand.length(),opponentCards},null,null,firstPlayer,0,null);}
    public State state(){return state;}public List<Hist> history(){return Collections.unmodifiableList(history);}
    public State observe(int player,String raw){if(state.winner!=null)throw new IllegalStateException("game already finished");if(player!=state.currentPlayer)throw new IllegalArgumentException("out-of-turn event: expected player "+state.currentPlayer+", got "+player);String s=normalize(raw);RuleEngine.Play target=state.currentTarget,play=s==null?null:RuleEngine.classify(s);if(s!=null&&play==null)throw new IllegalArgumentException("illegal/unrecognized play: "+s);
        if(play==null){if(target==null)throw new IllegalArgumentException("cannot pass while leading");if(player==0&&!RuleEngine.legalResponses(state.ownHand,target).isEmpty())throw new IllegalArgumentException("mandatory-play violation: my hand can beat target");}
        else {if(target!=null){Integer c=RuleEngine.compare(play,target);if(c==null||c!=1)throw new IllegalArgumentException("play does not beat current target");}if(player==0&&!RuleEngine.contains(state.ownHand,play.cards))throw new IllegalArgumentException("my hand does not contain "+play.cards);}
        history.add(new Hist(player,target,play));int[] left=state.cardsLeft.clone();String own=state.ownHand;
        if(play!=null){left[player]-=play.length();if(left[player]<0)throw new IllegalArgumentException("played more cards than player has");if(player==0)own=RuleEngine.subtract(own,play.cards);if(left[player]==0){state=new State(own,left,play,player,player,0,player);return state;}state=new State(own,left,play,player,(player+1)%2,0,null);return state;}
        int np=state.consecutivePasses+1;if(np>=1){if(state.targetOwner==null)throw new IllegalStateException("target owner missing");int leader=state.targetOwner;state=new State(own,left,null,null,leader,0,null);return state;}state=new State(own,left,target,state.targetOwner,(player+1)%2,np,null);return state;
    }
    private static String normalize(String a){if(a==null)return null;String s=a.trim().toUpperCase(Locale.ROOT);if(s.isEmpty()||s.equals("PASS")||s.equals("P")||s.equals("过")||s.equals("不要"))return null;return s;}
}
