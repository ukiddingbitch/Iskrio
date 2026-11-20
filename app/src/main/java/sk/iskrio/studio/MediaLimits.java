package sk.iskrio.studio;
final class MediaLimits {
 static final long VIDEO_BYTES=1024L*1024*1024, VIDEO_DURATION_MS=600000, IMAGE_BYTES=40L*1024*1024;
 static void video(long bytes,long duration){if(bytes<1||bytes>VIDEO_BYTES)throw new IllegalArgumentException("Videos must be no larger than 1 GB.");if(duration<1||duration>VIDEO_DURATION_MS)throw new IllegalArgumentException("Videos must be no longer than 10 minutes.");}
}
