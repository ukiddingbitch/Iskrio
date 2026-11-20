package sk.iskrio.studio;

/** Automatic video sampling: preserve the entire accepted source interval and its duration. */
final class AnimationPlan {
    final long durationMs;
    final int fps,frames;
    AnimationPlan(long durationMs,double sourceFps){
        if(durationMs<1)throw new IllegalArgumentException("Could not determine this video's duration. Try a different file.");
        if(durationMs>30000)throw new IllegalArgumentException("This video is longer than the 30-second GIF import limit. Choose a shorter video.");
        this.durationMs=durationMs;
        int preferred=Double.isFinite(sourceFps)&&sourceFps>0?(int)Math.round(Math.min(50,sourceFps)):30;
        fps=Math.max(1,Math.min(Math.max(30,preferred),(int)(900000/durationMs)));
        frames=Math.max(1,(int)Math.ceil(durationMs*fps/1000.0));
    }
}
