package sk.iskrio.studio;

/** Derives a bounded playback duration from packet presentation timestamps. */
final class VideoTimeline {
    long first=Long.MAX_VALUE,last=-1,previous=-1;
    int samples;
    void add(long timeUs){
        if(timeUs<0)return;
        if(++samples>100000)throw new IllegalArgumentException("This video has too many samples to import safely.");
        first=Math.min(first,timeUs);
        if(timeUs>last){previous=last;last=timeUs;}else if(timeUs<last&&timeUs>previous)previous=timeUs;
        if(last-first>=600000000L)throw new IllegalArgumentException("This video is longer than the 10-minute video import limit. Choose a shorter video.");
    }
    long durationUs(long declaredDurationUs,double frameRate){
        if(samples==0||last<0)throw new IllegalArgumentException("This file contains no readable video frames.");
        long span=last-first;
        long tail=previous>=first&&previous<last?last-previous:Math.round(1000000.0/(Double.isFinite(frameRate)&&frameRate>0?frameRate:30));
        tail=Math.max(1,Math.min(tail,600000000L));
        long duration=declaredDurationUs>span&&declaredDurationUs<=600000000L?declaredDurationUs:span+tail;
        if(duration>600000000L)throw new IllegalArgumentException("This video is longer than the 10-minute video import limit. Choose a shorter video.");
        return duration;
    }
}
