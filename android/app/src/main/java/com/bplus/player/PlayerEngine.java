package com.bplus.player;

import android.content.*;
import android.net.Uri;
import android.view.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.*;
import androidx.media3.ui.*;
import java.util.*;

/** B+ adapter over stock Media3, with no bundled native decoder. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class PlayerEngine {
  public final AudioMeter meter = new AudioMeter();
  final Context context;
  boolean released;
  public final ExoPlayer exo;
  PlayerView view;
  public String lastError = "";
  long subDelay;
  float zoom = 1;
  String aspect;
  final ArrayList<TrackDescription> audio = new ArrayList<>(), subs = new ArrayList<>();
  Uri uri, suburi, originalSub;
  boolean audioOnly;

  public PlayerEngine(Context c) {
    context = c;
    exo =
        new ExoPlayer.Builder(
                c,
                new DefaultRenderersFactory(c) {
                  @Override
                  protected androidx.media3.exoplayer.audio.AudioSink buildAudioSink(
                      Context ctx, boolean floats, boolean params) {
                    return new androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(ctx)
                        .setAudioProcessors(
                            new androidx.media3.common.audio.AudioProcessor[] {
                              new androidx.media3.exoplayer.audio.TeeAudioProcessor(meter)
                            })
                        .build();
                  }
                })
            .build();
    exo.setAudioAttributes(
        new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build(),
        true);
    exo.setHandleAudioBecomingNoisy(true);
    exo.addListener(
        new Player.Listener() {
          @Override
          public void onAudioSessionIdChanged(int id) {
            if (enhancer != null) {
              enhancer.release();
              enhancer = null;
            }
            if (desktopEq.eq != null) {
              desktopEq.eq.release();
              desktopEq.eq = null;
            }
            applyAudioSettings();
          }
        });
  }

  public void open(Uri u, boolean aud, long resume) {
    uri = u;
    suburi = null;
    audioOnly = aud;
    exo.setMediaItem(new MediaItem.Builder().setUri(u).build());
    exo.prepare();
    if (resume > 0) exo.seekTo(resume);
    exo.setPlayWhenReady(context.getSharedPreferences("prefs", 0).getBoolean("autoplay", true));
  }

  public void attachViews(PlayerView v, Object unused, boolean captions, boolean texture) {
    view = v;
    v.setUseController(false);
    v.setPlayer(exo);
    v.setKeepContentOnPlayerReset(true);
    styleSubtitles();
    var p = context.getSharedPreferences("prefs", 0);
    int fit = p.getInt("fit", 0);
    v.setResizeMode(
        fit == 0
            ? AspectRatioFrameLayout.RESIZE_MODE_FIT
            : fit == 1
                ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                : AspectRatioFrameLayout.RESIZE_MODE_FILL);
    effects(
        (p.getInt("brightness", 100) - 100) / 100f,
        (p.getInt("contrast", 100) - 100) / 100f,
        p.getInt("saturation", 100) - 100,
        p.getInt("hue", 0));
  }

  public void styleSubtitles() {
    if (view == null || view.getSubtitleView() == null) return;
    var p = context.getSharedPreferences("prefs", 0);
    String name = p.getString("subfont", "Cairo");
    android.graphics.Typeface face =
        name.equals("Cairo")
            ? android.graphics.Typeface.createFromAsset(
                context.getAssets(), "fonts/Cairo-Regular.ttf")
            : android.graphics.Typeface.create(name, android.graphics.Typeface.NORMAL);
    view.getSubtitleView()
        .setStyle(
            new CaptionStyleCompat(
                0xff000000 | p.getInt("subcolor", 0xffffff),
                0x00000000,
                0x00000000,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                0xff000000,
                face));
    view.getSubtitleView().setFractionalTextSize(p.getInt("subsize", 24) / 650f);
  }

  public void detachViews() {
    if (view != null) view.setPlayer(null);
    view = null;
  }

  public void updateVideoSurfaces() {}

  android.media.audiofx.LoudnessEnhancer enhancer;

  public void setBoost(int value) {
    try {
      if (enhancer == null && getAudioSessionId() != 0)
        enhancer = new android.media.audiofx.LoudnessEnhancer(getAudioSessionId());
      exo.setVolume(Math.min(1, value / 100f));
      if (enhancer != null) {
        enhancer.setTargetGain(value > 100 ? (int) (2000 * Math.log10(value / 100.0)) : 0);
        enhancer.setEnabled(value > 100);
      }
    } catch (RuntimeException unsupported) {
      lastError = "Volume boost unavailable on this device";
    }
  }

  Equalizer desktopEq = Equalizer.create();

  public void applyAudioSettings() {
    var p = context.getSharedPreferences("prefs", 0);
    setBoost(p.getInt("boost", 100));
    for (int i = 0; i < 5; i++)
      desktopEq.setAmp(i, p.getInt(i < 2 ? "bass" : i == 2 ? "mid" : "treble", 0));
    setEqualizer(desktopEq);
  }

  public void play() {
    exo.play();
  }

  public void pause() {
    exo.pause();
  }

  public void stop() {
    exo.stop();
  }

  public void release() {
    released = true;
    if (enhancer != null) enhancer.release();
    if (desktopEq.eq != null) desktopEq.eq.release();
    exo.release();
  }

  public boolean isReleased() {
    return released;
  }

  public boolean isPlaying() {
    return exo.isPlaying();
  }

  public long getTime() {
    return exo.getCurrentPosition();
  }

  public long getLength() {
    long d = exo.getDuration();
    return d == C.TIME_UNSET ? 0 : d;
  }

  public long setTime(long p) {
    exo.seekTo(Math.max(0, p));
    return p;
  }

  public void setRate(float r) {
    exo.setPlaybackSpeed(r);
  }

  public float getRate() {
    return exo.getPlaybackParameters().speed;
  }

  public int setVolume(int v) {
    exo.setVolume(Math.max(0, Math.min(1, v / 100f)));
    return v;
  }

  public int getVolume() {
    return (int) (exo.getVolume() * 100);
  }

  public int getAudioSessionId() {
    return exo.getAudioSessionId();
  }

  public void setVideoTrackEnabled(boolean enabled) {
    exo.setTrackSelectionParameters(
        exo.getTrackSelectionParameters()
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enabled)
            .build());
  }

  public enum ScaleType {
    SURFACE_FILL,
    SURFACE_BEST_FIT
  }

  public void setVideoScale(ScaleType s) {
    if (view != null)
      view.setResizeMode(
          s == ScaleType.SURFACE_FILL
              ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM
              : AspectRatioFrameLayout.RESIZE_MODE_FIT);
  }

  public void setAspectRatio(String s) {
    aspect = s;
    if (view != null)
      view.setResizeMode(
          s == null
              ? AspectRatioFrameLayout.RESIZE_MODE_FIT
              : AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
  }

  public void effects(float brightness, float contrast, float saturation, float hue) {
    exo.setVideoEffects(
        java.util.Arrays.asList(
            new androidx.media3.effect.Brightness(brightness),
            new androidx.media3.effect.Contrast(contrast),
            new androidx.media3.effect.HslAdjustment.Builder()
                .adjustSaturation(saturation)
                .adjustHue(hue)
                .build()));
  }

  public static class TrackDescription {
    public final int id;
    public final String name;
    final Tracks.Group group;
    final int track;

    TrackDescription(int i, String n, Tracks.Group g, int t) {
      id = i;
      name = n;
      group = g;
      track = t;
    }
  }

  TrackDescription[] tracks(int kind, ArrayList<TrackDescription> out) {
    out.clear();
    for (Tracks.Group g : exo.getCurrentTracks().getGroups())
      if (g.getType() == kind)
        for (int k = 0; k < g.length; k++) {
          Format f = g.getTrackFormat(k);
          String n =
              f.label != null
                  ? f.label
                  : f.language != null ? f.language : "Track " + (out.size() + 1);
          out.add(new TrackDescription(out.size(), n, g, k));
        }
    return out.toArray(new TrackDescription[0]);
  }

  public TrackDescription[] getAudioTracks() {
    return tracks(C.TRACK_TYPE_AUDIO, audio);
  }

  public TrackDescription[] getSpuTracks() {
    return tracks(C.TRACK_TYPE_TEXT, subs);
  }

  void choose(int id, int type, ArrayList<TrackDescription> list) {
    var b = exo.getTrackSelectionParameters().buildUpon();
    if (id < 0) {
      b.setTrackTypeDisabled(type, true);
    } else if (id < list.size()) {
      var t = list.get(id);
      b.setTrackTypeDisabled(type, false)
          .setOverrideForType(new TrackSelectionOverride(t.group.getMediaTrackGroup(), t.track));
    }
    exo.setTrackSelectionParameters(b.build());
  }

  public boolean setSpuTrack(int id) {
    choose(id, C.TRACK_TYPE_TEXT, subs);
    return true;
  }

  public boolean setAudioTrack(int id) {
    choose(id, C.TRACK_TYPE_AUDIO, audio);
    return true;
  }

  public boolean addSlave(int unused, Uri u, boolean select) {
    originalSub = u;
    suburi = u;
    subDelay = 0;
    return reloadSub();
  }

  boolean reloadSub() {
    if (uri == null || suburi == null) return false;
    try {
      String ext = suburi.toString().toLowerCase(Locale.ROOT);
      String mime =
          ext.endsWith(".vtt")
              ? MimeTypes.TEXT_VTT
              : ext.endsWith(".ass") || ext.endsWith(".ssa")
                  ? MimeTypes.TEXT_SSA
                  : MimeTypes.APPLICATION_SUBRIP;
      long at = getTime();
      boolean play = exo.getPlayWhenReady();
      MediaItem.SubtitleConfiguration cfg =
          new MediaItem.SubtitleConfiguration.Builder(suburi)
              .setMimeType(mime)
              .setLanguage("ar")
              .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
              .build();
      exo.setMediaItem(
          new MediaItem.Builder()
              .setUri(uri)
              .setSubtitleConfigurations(Collections.singletonList(cfg))
              .build(),
          at);
      exo.prepare();
      exo.setPlayWhenReady(play);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public long getSpuDelay() {
    return subDelay;
  }

  public boolean setSpuDelay(long d) {
    if (originalSub == null) return false;
    try {
      java.io.File input = new java.io.File(originalSub.getPath());
      String text =
          new String(
              java.nio.file.Files.readAllBytes(input.toPath()),
              java.nio.charset.StandardCharsets.UTF_8);
      String ext = input.getName().substring(input.getName().lastIndexOf('.'));
      java.util.regex.Pattern pattern =
          java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2}):(\\d{2})([,.])(\\d{2,3})");
      java.util.regex.Matcher m = pattern.matcher(text);
      StringBuffer out = new StringBuffer();
      while (m.find()) {
        boolean centi = m.group(5).length() == 2;
        long ms =
            (Long.parseLong(m.group(1)) * 3600
                        + Long.parseLong(m.group(2)) * 60
                        + Long.parseLong(m.group(3)))
                    * 1000
                + Long.parseLong(m.group(5)) * (centi ? 10 : 1)
                + d / 1000;
        ms = Math.max(0, ms);
        String time =
            String.format(
                java.util.Locale.US,
                centi ? "%d:%02d:%02d.%02d" : "%02d:%02d:%02d" + m.group(4) + "%03d",
                ms / 3600000,
                ms / 60000 % 60,
                ms / 1000 % 60,
                centi ? ms % 1000 / 10 : ms % 1000);
        m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(time));
      }
      m.appendTail(out);
      java.io.File shifted = new java.io.File(context.getFilesDir(), "timed-subtitle" + ext);
      java.nio.file.Files.write(
          shifted.toPath(), out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      suburi = Uri.fromFile(shifted);
      subDelay = d;
      return reloadSub();
    } catch (Exception ex) {
      return false;
    }
  }

  public boolean setEqualizer(Equalizer e) {
    return e.apply(getAudioSessionId());
  }

  public static class Equalizer {
    android.media.audiofx.Equalizer eq;
    final float[] amp = new float[5];

    public static Equalizer create() {
      return new Equalizer();
    }

    public static int getBandCount() {
      return 5;
    }

    public static float getBandFrequency(int i) {
      return new float[] {60, 230, 910, 3600, 14000}[i];
    }

    public void setAmp(int i, float v) {
      amp[i] = v;
    }

    boolean apply(int session) {
      try {
        if (session == 0) return false;
        if (eq == null) eq = new android.media.audiofx.Equalizer(0, session);
        short[] range = eq.getBandLevelRange();
        for (short k = 0; k < Math.min(5, eq.getNumberOfBands()); k++)
          eq.setBandLevel(k, (short) Math.max(range[0], Math.min(range[1], amp[k] * 100)));
        eq.setEnabled(true);
        return true;
      } catch (Exception ex) {
        return false;
      }
    }
  }
}
