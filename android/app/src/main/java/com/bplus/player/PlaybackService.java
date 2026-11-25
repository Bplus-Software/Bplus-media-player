package com.bplus.player;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.media.session.*;
import android.os.*;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import java.util.*;

public class PlaybackService extends Service {
  public static PlaybackService current;
  public PlayerEngine player;
  public final ArrayList<Track> queue = new ArrayList<>();
  public int index = -1;
  public boolean repeat = false, shuffle = false;
  public Runnable changed;
  public String error = "";
  public long sleepUntil = 0, repeatA = -1, repeatB = -1;
  private MediaSession session;
  private MediaSession.Callback sessionCallback;
  private String sessionTrack = null;
  private long sessionDuration = -1;
  private PlaybackState lastSessionState;

  private AudioManager am;
  private AudioFocusRequest focus;
  private final Handler h = new Handler();
  private android.os.ParcelFileDescriptor mediaFd;
  private long pendingSeek = -1;
  private boolean resumeFocus = false;
  private int ticks = 0;
  private boolean foreground = false;
  public long lastActive;
  public volatile boolean playing;

  public class Local extends Binder {
    public PlaybackService get() {
      return PlaybackService.this;
    }
  }

  public IBinder onBind(Intent i) {
    return new Local();
  }

  public void onCreate() {
    super.onCreate();
    current = this;
    am = (AudioManager) getSystemService(AUDIO_SERVICE);
    player = new PlayerEngine(this);
    player.exo.addListener(
        new Player.Listener() {
          public void onIsPlayingChanged(boolean value) {
            playing = value;
            notifyState();
            if (changed != null) changed.run();
          }

          public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_ENDED) {
              save();
              if (getSharedPreferences("prefs", 0).getBoolean("autoNext", true)
                  || getSharedPreferences("prefs", 0).getBoolean("repeat", false))
                advanceAfterEnd();
              else pause();
            }
            notifyState();
            if (changed != null) changed.run();
          }

          public void onPositionDiscontinuity(
              Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
            lastSessionState = null;
            updateSession();
          }

          public void onPlaybackParametersChanged(
              androidx.media3.common.PlaybackParameters parameters) {
            updateSession();
          }

          public void onTimelineChanged(androidx.media3.common.Timeline timeline, int reason) {
            updateSession();
          }

          public void onPlayerError(PlaybackException ex) {
            error = "تعذر تشغيل هذا الملف أو المسار على الجهاز. " + ex.getErrorCodeName();
            if (changed != null) changed.run();
          }
        });

    session = new MediaSession(this, "BplusPlayer");
    sessionCallback =
        new MediaSession.Callback() {
          public void onPlay() {
            resume();
          }

          public void onPause() {
            pause();
          }

          public void onStop() {
            stopPlayback();
          }

          public void onSeekTo(long p) {
            player.setTime(p);
            lastSessionState = null;
            updateSession();
          }

          public void onSkipToNext() {
            next(1);
          }

          public void onSkipToPrevious() {
            next(-1);
          }

          public void onFastForward() {
            seek(10000);
          }

          public void onRewind() {
            seek(-10000);
          }
        };
    session.setCallback(sessionCallback);
    session.setActive(true);
    focus =
        new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build())
            .setOnAudioFocusChangeListener(
                f -> {
                  if (f == AudioManager.AUDIOFOCUS_LOSS
                      || f == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    resumeFocus = player.isPlaying() && f == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
                    pause();
                  } else if (f == AudioManager.AUDIOFOCUS_GAIN) {
                    if (resumeFocus) {
                      resumeFocus = false;
                      resume();
                    }
                    player.setVolume(100);
                  } else if (f == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
                    player.setVolume(25);
                })
            .build();
    if (Build.VERSION.SDK_INT >= 33)
      registerReceiver(
          noisy,
          new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
          Context.RECEIVER_NOT_EXPORTED);
    else registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
        .createNotificationChannel(
            new NotificationChannel(
                "playback", "تشغيل الوسائط", NotificationManager.IMPORTANCE_LOW));
    h.post(tick);
  }

  private final BroadcastReceiver noisy =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
          pause();
        }
      };
  private final Runnable tick =
      new Runnable() {
        public void run() {
          if (player != null && !player.isReleased()) {
            if (sleepUntil > 0 && SystemClock.elapsedRealtime() >= sleepUntil) {
              sleepUntil = 0;
              pause();
            }
            if (repeatA >= 0
                && repeatB > repeatA
                && player.isPlaying()
                && player.getTime() >= repeatB) player.setTime(repeatA);
            if (++ticks % 5 == 0) save();
            if (index >= 0) updateSession();
          }
          h.postDelayed(this, 1000);
        }
      };

  public int onStartCommand(Intent i, int flags, int id) {
    String a = i == null ? null : i.getAction();
    if ("play".equals(a)) resume();
    else if ("pause".equals(a)) pause();
    else if ("toggle".equals(a)) toggle();
    else if ("next".equals(a)) next(1);
    else if ("prev".equals(a)) next(-1);
    else if ("stop".equals(a)) stopPlayback();
    return START_NOT_STICKY;
  }

  public void load(Track t, boolean append) {
    if (!append) {
      queue.clear();
      index = -1;
    }
    for (int n = 0; n < queue.size(); n++)
      if (queue.get(n).uri.equals(t.uri)) {
        playIndex(n);
        return;
      }
    queue.add(t);
    playIndex(queue.size() - 1);
  }

  public void loadList(List<Track> items, Track selected) {
    save();
    queue.clear();
    index = -1;
    int selectedIndex = -1;
    HashSet<String> seen = new HashSet<>();
    for (Track item : items) {
      if (!seen.add(item.uri.toString())) continue;
      if (item.uri.equals(selected.uri)) selectedIndex = queue.size();
      queue.add(item);
    }
    if (selectedIndex < 0) {
      selectedIndex = queue.size();
      queue.add(selected);
    }
    playIndex(selectedIndex);
  }

  public Track track() {
    return index >= 0 && index < queue.size() ? queue.get(index) : null;
  }

  public void playIndex(int n) {
    repeatA = -1;
    repeatB = -1;
    if (n < 0 || n >= queue.size()) return;
    save();
    player.stop();
    index = n;
    error = "";
    Track t = track();
    pendingSeek =
        getSharedPreferences("prefs", 0).getBoolean("remember", true)
            ? getSharedPreferences("resume", 0).getLong(t.uri.toString(), 0)
            : 0;
    getSharedPreferences("prefs", 0)
        .edit()
        .putLong("played:" + t.uri, System.currentTimeMillis())
        .putString("lastUri", t.uri.toString())
        .putString("lastName", t.name)
        .apply();
    Notification no = notification();
    startForeground(10, no, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
    foreground = true;
    player.open(t.uri, t.audio, pendingSeek);
    pendingSeek = -1;
    player.setRate(getSharedPreferences("prefs", 0).getFloat("speed", 1f));
    player.applyAudioSettings();
    lastActive = android.os.SystemClock.elapsedRealtime();
    notifyState();
    if (changed != null) changed.run();
  }

  public void resume() {
    if (track() == null) return;
    if (!foreground) {
      startForeground(10, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
      foreground = true;
    }
    if (player.exo.getPlaybackState() == Player.STATE_IDLE) {
      player.open(
          track().uri,
          track().audio,
          getSharedPreferences("resume", 0).getLong(track().uri.toString(), 0));
    } else {
      player.play();
      lastActive = android.os.SystemClock.elapsedRealtime();
      notifyState();
    }
  }

  public void pause() {
    player.pause();
    save();
    notifyState();
  }

  public void toggle() {
    if (player.isPlaying()) pause();
    else resume();
  }

  public void seek(long d) {
    player.setTime(Math.max(0, Math.min(player.getLength(), player.getTime() + d)));
  }

  void advanceAfterEnd() {
    if (repeat || getSharedPreferences("prefs", 0).getBoolean("repeat", false)) playIndex(index);
    else next(1);
  }

  public void next(int d) {
    if (queue.isEmpty()) return;
    int n = shuffle ? new Random().nextInt(queue.size()) : index + d;
    if (n >= queue.size()) {
      pause();
      player.setTime(0);
      return;
    }
    if (n < 0) n = 0;
    playIndex(n);
  }

  public void save() {
    Track t = track();
    if (t != null && player.getTime() > 0)
      getSharedPreferences("resume", 0).edit().putLong(t.uri.toString(), player.getTime()).apply();
  }

  public void stopPlayback() {
    save();
    player.stop();
    am.abandonAudioFocusRequest(focus);
    session.setActive(false);
    stopForeground(STOP_FOREGROUND_REMOVE);
    foreground = false;
    if (changed != null) changed.run();
    if (getSharedPreferences("prefs", 0).getBoolean("autoUpdate", true))
      UpdateEngine.installReady(this);
  }

  private PendingIntent act(String a) {
    return PendingIntent.getService(
        this,
        a.hashCode(),
        new Intent(this, PlaybackService.class).setAction(a),
        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
  }

  private Notification notification() {
    Track t = track();
    return new Notification.Builder(this, "playback")
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle(t == null ? "B+ Media Player" : t.name)
        .setContentText("B+ Media Player")
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
        .setOngoing(player.isPlaying())
        .setStyle(
            new Notification.MediaStyle()
                .setMediaSession(session.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2))
        .addAction(
            new Notification.Action.Builder(
                    android.R.drawable.ic_media_previous, "السابق", act("prev"))
                .build())
        .addAction(
            new Notification.Action.Builder(
                    player.isPlaying()
                        ? android.R.drawable.ic_media_pause
                        : android.R.drawable.ic_media_play,
                    player.isPlaying() ? "إيقاف مؤقت" : "تشغيل",
                    act("toggle"))
                .build())
        .addAction(
            new Notification.Action.Builder(android.R.drawable.ic_media_next, "التالي", act("next"))
                .build())
        .addAction(
            new Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel, "إيقاف", act("stop"))
                .build())
        .build();
  }

  private void updateSession() {
    Track t = track();
    if (session == null) return;
    session.setActive(true);
    String key = t == null ? "" : t.uri.toString();
    long duration = Math.max(0, player.getLength());
    if (!key.equals(sessionTrack) || duration != sessionDuration) {
      lastSessionState = null;
      sessionTrack = key;
      sessionDuration = duration;
      session.setMetadata(
          new MediaMetadata.Builder()
              .putString(MediaMetadata.METADATA_KEY_TITLE, t == null ? "B+" : t.name)
              .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
              .build());
    }
    int state =
        player.exo.getPlaybackState() == Player.STATE_BUFFERING
            ? PlaybackState.STATE_BUFFERING
            : player.isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
    float speed = state == PlaybackState.STATE_PLAYING ? player.getRate() : 0f;
    long position = Math.max(0, player.getTime());
    if (duration > 0) position = Math.min(duration, position);
    long now = SystemClock.elapsedRealtime();
    if (lastSessionState != null
        && lastSessionState.getState() == state
        && lastSessionState.getPlaybackSpeed() == speed) {
      long expected =
          lastSessionState.getPosition()
              + (long) ((now - lastSessionState.getLastPositionUpdateTime()) * speed);
      if (Math.abs(position - expected) < 750) return;
    }
    lastSessionState =
        new PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY
                    | PlaybackState.ACTION_PAUSE
                    | PlaybackState.ACTION_PLAY_PAUSE
                    | PlaybackState.ACTION_STOP
                    | PlaybackState.ACTION_SEEK_TO
                    | PlaybackState.ACTION_SKIP_TO_NEXT
                    | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    | PlaybackState.ACTION_FAST_FORWARD
                    | PlaybackState.ACTION_REWIND)
            .setState(state, position, speed, now)
            .build();
    session.setPlaybackState(lastSessionState);
  }

  public void notifyState() {
    if (session == null) return;
    updateSession();
    if (foreground)
      ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(10, notification());
  }

  public void onDestroy() {
    h.removeCallbacksAndMessages(null);
    save();
    try {
      unregisterReceiver(noisy);
    } catch (Exception e) {
    }
    player.stop();
    player.release();
    try {
      if (mediaFd != null) mediaFd.close();
    } catch (Exception ex) {
    }
    session.release();
    am.abandonAudioFocusRequest(focus);
    current = null;
    super.onDestroy();
  }
}
