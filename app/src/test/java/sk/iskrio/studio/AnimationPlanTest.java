package sk.iskrio.studio;
import org.junit.Test;
import static org.junit.Assert.*;
public class AnimationPlanTest {
 @Test public void completeVideoDurationIsAutomatic(){AnimationPlan p=new AnimationPlan(1234,29.97);assertEquals(1234,p.durationMs);assertEquals(30,p.fps);assertEquals(38,p.frames);}
 @Test public void smoothShortHighFrameRateVideo(){AnimationPlan p=new AnimationPlan(1000,60);assertEquals(50,p.fps);assertEquals(50,p.frames);}
 @Test public void frameBudgetPreservesDuration(){AnimationPlan p=new AnimationPlan(30000,60);assertEquals(30000,p.durationMs);assertEquals(30,p.fps);assertEquals(900,p.frames);}
 @Test public void neverSilentlyTruncates(){assertThrows(IllegalArgumentException.class,()->new AnimationPlan(30001,30));assertThrows(IllegalArgumentException.class,()->new AnimationPlan(0,30));}
 @Test public void missingFrameRateHasSafeDefault(){assertEquals(30,new AnimationPlan(1000,Double.NaN).fps);assertEquals(1,new AnimationPlan(1,30).frames);}
}
