package com.paodekuai.analyzer;
import com.paodekuai.analyzer.advisor.*;import org.junit.Test;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import static org.junit.Assert.*;
public class AdvisorWorkerTest {
 @Test public void returnsLegalTopKWithoutBlockingCaller() throws Exception {AdvisorWorker w=new AdvisorWorker();CountDownLatch l=new CountDownLatch(1);AtomicReference<AdvisorWorker.Result> got=new AtomicReference<>();long st=System.nanoTime();w.submit(new AdvisorWorker.Snapshot("AKQQTTT987654433",16,null,null,0,0,"k",java.util.List.of()),120,3,r->{got.set(r);l.countDown();});assertTrue((System.nanoTime()-st)/1e6<50);assertTrue(l.await(2,TimeUnit.SECONDS));assertNotNull(got.get());assertFalse(got.get().top.isEmpty());assertTrue(got.get().top.size()<=3);w.close();}
 @Test public void staleRequestIsCancelled() throws Exception {AdvisorWorker w=new AdvisorWorker();AtomicInteger calls=new AtomicInteger();w.submit(new AdvisorWorker.Snapshot("AKQQTTT987654433",16,null,null,0,0,"old",java.util.List.of()),500,3,r->calls.incrementAndGet());w.cancelPending();Thread.sleep(650);assertEquals(0,calls.get());w.close();}
}
