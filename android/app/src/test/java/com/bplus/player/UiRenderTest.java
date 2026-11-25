package com.bplus.player;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "ar-rSA-w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class UiRenderTest {
  MainActivity a;
  PlaybackService service;

  @Before
  public void prepare() {
    Application app = RuntimeEnvironment.getApplication();
    app.getSharedPreferences("prefs", 0)
        .edit()
        .clear()
        .putString("theme", "dark")
        .putBoolean("autoUpdate", false)
        .putInt("shade", 92)
        .putBoolean("desktopShadeScale", true)
        .apply();
    service = Robolectric.buildService(PlaybackService.class).create().get();
    Shadows.shadowOf(app)
        .setComponentNameAndServiceForBindService(
            new ComponentName(app, PlaybackService.class), service.onBind(new Intent()));
    a = Robolectric.buildActivity(MainActivity.class).create().start().resume().get();
    a.s = service;
    a.bound = false;
  }

  void render(String name, int width, int height) throws Exception {
    View root = a.root;
    root.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    root.layout(0, 0, width, height);
    root.getViewTreeObserver().dispatchOnGlobalLayout();
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    root.draw(new Canvas(bitmap));
    File dir = new File(System.getProperty("bplus.renderDir", "build/ui-renders"));
    dir.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    Assert.assertTrue(root.getWidth() == width);
  }

  Track video(String name) {
    Track t = new Track(Uri.parse("file:///sample/" + name), name);
    org.robolectric.shadows.util.DataSource source =
        org.robolectric.shadows.util.DataSource.toDataSource(a, t.uri);
    org.robolectric.shadows.ShadowMediaMetadataRetriever.addMetadata(
        source, android.media.MediaMetadataRetriever.METADATA_KEY_DURATION, "1122000");
    org.robolectric.shadows.ShadowMediaMetadataRetriever.addMetadata(
        source, android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT, "1080");
    org.robolectric.shadows.ShadowMediaMetadataRetriever.addScaledFrame(
        source, 0, 480, 270, scene(480, 270));
    return t;
  }

  Bitmap scene(int width, int height) {
    Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(b);
    Paint p = new Paint(3);
    p.setShader(new LinearGradient(0, 0, 0, height, 0xffabc8d5, 0xff567382, Shader.TileMode.CLAMP));
    c.drawRect(0, 0, width, height, p);
    p.setShader(null);
    p.setColor(0xffeee1b6);
    c.drawCircle(width * .79f, height * .2f, height * .11f, p);
    float[][] mountains = {
      {-.1f, 1, .24f, .15f, .65f, 1},
      {.26f, 1, .64f, .3f, 1.15f, 1},
      {-.1f, 1, .26f, .52f, .9f, 1},
      {.36f, 1, .78f, .62f, 1.3f, 1}
    };
    int[] colors = {0xff506e7e, 0xff607e8e, 0xff264655, 0xff355665};
    for (int i = 0; i < mountains.length; i++) {
      Path path = new Path();
      float[] v = mountains[i];
      path.moveTo(v[0] * width, v[1] * height);
      path.lineTo(v[2] * width, v[3] * height);
      path.lineTo(v[4] * width, v[5] * height);
      path.close();
      p.setColor(colors[i]);
      c.drawPath(path, p);
    }
    return b;
  }

  void fixtureFrame() {
    ImageView frame = new ImageView(a);
    frame.setImageBitmap(scene(1280, 720));
    frame.setScaleType(ImageView.ScaleType.FIT_CENTER);
    a.surface.setVisibility(View.INVISIBLE);
    a.stage.addView(frame, 0, new android.widget.FrameLayout.LayoutParams(-1, -1));
  }

  void settle() {
    try {
      Thread.sleep(150);
    } catch (Exception ignored) {
    }
    Shadows.shadowOf(Looper.getMainLooper()).idle();
  }

  @Test
  public void libraryEmpty() throws Exception {
    a.showLibrary("all");
    render("01-empty-native", 786, 1704);
  }

  @Test
  public void libraryPopulated() throws Exception {
    java.util.ArrayList<Track> tracks = new java.util.ArrayList<>();
    tracks.add(video("رحلة إلى الجبال.mp4"));
    tracks.add(video("SVID_20261123_214146_1.mp4"));
    tracks.add(video("الطريق الهادئ.mkv"));
    a.saveLibrary(tracks);
    a.prefs
        .edit()
        .putString("lastUri", tracks.get(0).uri.toString())
        .putString("lastName", tracks.get(0).name)
        .apply();
    a.getSharedPreferences("resume", 0)
        .edit()
        .putLong(tracks.get(0).uri.toString(), 432000)
        .apply();
    a.showLibrary("all");
    settle();
    render("02-library-native", 786, 1704);
  }

  @Test
  public void videoPortrait() throws Exception {
    service.queue.add(video("SVID_20261123_214146_1.mp4"));
    service.index = 0;
    a.showPlayer();
    fixtureFrame();
    render("03-video-portrait-native", 786, 1704);
  }

  @Test
  public void videoLandscape() throws Exception {
    service.queue.add(video("رحلة إلى الجبال.mp4"));
    service.index = 0;
    a.showPlayer();
    fixtureFrame();
    render("04-video-landscape-native", 1704, 786);
  }

  @Test
  public void audio() throws Exception {
    service.queue.add(video("هدوء المساء.mp3"));
    service.index = 0;
    a.showPlayer();
    render("05-audio-native", 786, 1704);
  }

  @Test
  public void settings() throws Exception {
    a.settings("main");
    render("06-settings-native", 786, 1704);
  }

  @Test
  public void smallLibrary() throws Exception {
    a.showLibrary("all");
    render("07-small-empty-native", 640, 1280);
  }

  @Test
  public void longFileName() throws Exception {
    service.queue.add(video("SVID_20261123_214146_1_very_long_filename_recording.mp4"));
    service.index = 0;
    a.showPlayer();
    fixtureFrame();
    render("08-small-video-native", 640, 1280);
    Assert.assertEquals(1, a.title.getMaxLines());
  }

  @Test
  public void videoControlsToggleAndUnlock() {
    service.queue.add(video("sample.mp4"));
    service.index = 0;
    a.showPlayer();
    a.setVideoControls(false);
    Assert.assertEquals(View.GONE, a.chrome.getVisibility());
    a.setVideoControls(true);
    Assert.assertEquals(View.VISIBLE, a.chrome.getVisibility());
    a.locked = true;
    a.touch(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 20, 20, 0));
    org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(800));
    a.touch(MotionEvent.obtain(0, 800, MotionEvent.ACTION_UP, 20, 20, 0));
    Assert.assertFalse(a.locked);
  }

  @Test
  public void filtersAndNavigation() {
    java.util.ArrayList<Track> tracks = new java.util.ArrayList<>();
    Track one = video("First.mp4"), two = video("Second.mp3");
    tracks.add(one);
    tracks.add(two);
    a.saveLibrary(tracks);
    a.prefs.edit().putBoolean("favorite:" + one.uri, true).apply();
    a.showLibrary("favorites");
    Assert.assertTrue(hasText(a.root, "First.mp4"));
    Assert.assertFalse(hasText(a.root, "Second.mp3"));
    a.mediaTab = "audio";
    a.showLibrary("all");
    Assert.assertTrue(hasText(a.root, "Second.mp3"));
    Assert.assertFalse(hasText(a.root, "First.mp4"));
    a.searchQuery = "missing";
    a.showLibrary("all");
    Assert.assertFalse(hasText(a.root, "Second.mp3"));
    a.settings("main");
    Assert.assertTrue(hasText(a.root, "الإعدادات"));
  }

  @Test
  public void pipExitKeepsOnlyPlayerHeader() {
    service.queue.add(video("sample.mp4"));
    service.index = 0;
    a.showPlayer();
    a.onPictureInPictureModeChanged(true, a.getResources().getConfiguration());
    Assert.assertEquals(View.GONE, a.topBar.getVisibility());
    a.onPictureInPictureModeChanged(false, a.getResources().getConfiguration());
    Assert.assertEquals(View.VISIBLE, a.topBar.getVisibility());
    Assert.assertEquals(View.GONE, a.page.getChildAt(0).getVisibility());
    Assert.assertEquals(View.GONE, a.preview.getVisibility());
  }

  @Test
  public void folderFilter() {
    Track one = video("One.mp4"), two = video("Two.mp4");
    java.util.ArrayList<Track> tracks = new java.util.ArrayList<>();
    tracks.add(one);
    tracks.add(two);
    a.saveLibrary(tracks);
    a.prefs
        .edit()
        .putString("parent:" + one.uri, "folderA")
        .putString("parent:" + two.uri, "folderB")
        .apply();
    a.folderFilter = "folderA";
    a.showLibrary("all");
    Assert.assertTrue(hasText(a.root, "One.mp4"));
    Assert.assertFalse(hasText(a.root, "Two.mp4"));
  }

  void renderSheet(String name) throws Exception {
    a.activeSheet
        .getWindow()
        .getDecorView()
        .measure(
            View.MeasureSpec.makeMeasureSpec(786, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1100, View.MeasureSpec.AT_MOST));
    View sheet = a.activeSheet.getWindow().getDecorView();
    sheet.layout(0, 0, 786, sheet.getMeasuredHeight());
    Bitmap bitmap = Bitmap.createBitmap(786, sheet.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
    sheet.draw(new Canvas(bitmap));
    File dir = new File(System.getProperty("bplus.renderDir", "build/ui-renders"));
    dir.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
  }

  void setupVideo() {
    service.queue.add(video("رحلة إلى الجبال.mp4"));
    service.index = 0;
    a.showPlayer();
    fixtureFrame();
  }

  @Test
  public void videoToolsSheet() throws Exception {
    setupVideo();
    a.videoOptions();
    renderSheet("11-video-tools-sheet");
  }

  @Test
  public void subtitleSheet() throws Exception {
    setupVideo();
    a.subtitles();
    renderSheet("12-subtitle-sheet");
  }

  @Test
  public void speedSheet() throws Exception {
    setupVideo();
    a.speed();
    render("13-speed-inline", 786, 1529);
  }

  @Test
  public void updatedControls() throws Exception {
    setupVideo();
    render("14-player-controls", 786, 1704);
  }

  @Test
  public void updateProgress() throws Exception {
    UpdateEngine.phase = "downloading";
    UpdateEngine.availableVersion = "1.0.3";
    UpdateEngine.progress = 62;
    UpdateEngine.downloadBytes = 7800000;
    UpdateEngine.totalBytes = 12500000;
    UpdateEngine.releaseNotes =
        "تحسين قوائم وأدوات الفيديو\n"
            + "تنزيل وتثبيت في خطوة واحدة\n"
            + "إصلاح تحديث المكتبة بعد إزالة الملفات";
    a.showUpdates();
    render("15-updates-progress", 786, 1704);
    UpdateEngine.phase = "idle";
  }

  @Test
  public void centeredPlayPause() throws Exception {
    a.base("");
    a.page.removeAllViews();
    LinearLayout row = a.row();
    row.setGravity(Gravity.CENTER);
    a.page.addView(row, new LinearLayout.LayoutParams(-1, -1));
    for (String key : new String[] {"play", "pause"}) {
      MainActivity.IconControl button = a.new IconControl(key, Color.WHITE);
      button.iconSize = 36;
      button.setBackground(a.shape(0xff26313c, 40));
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(144, 144);
      lp.setMargins(28, 0, 28, 0);
      row.addView(button, lp);
    }
    render("16-play-pause-closeup", 786, 340);
  }

  @Test
  public void removedTrackClearsEveryReference() {
    Track track = video("Removed.mp4");
    java.util.ArrayList<Track> saved = new java.util.ArrayList<>();
    saved.add(track);
    a.saveLibrary(saved);
    a.prefs
        .edit()
        .putString("lastUri", track.uri.toString())
        .putString("lastName", track.name)
        .putBoolean("favorite:" + track.uri, true)
        .putLong("played:" + track.uri, 1)
        .apply();
    a.removeFromLibrary(track);
    Assert.assertTrue(a.library().isEmpty());
    Assert.assertFalse(a.prefs.contains("lastUri"));
    Assert.assertFalse(a.prefs.contains("favorite:" + track.uri));
    Assert.assertFalse(a.prefs.contains("played:" + track.uri));
  }

  @Test
  public void deletedFileDropsCard() throws Exception {
    File file = new File(a.getFilesDir(), "gone.mp4");
    try (FileOutputStream out = new FileOutputStream(file)) {
      out.write(1);
    }
    Track track = new Track(Uri.fromFile(file), "gone.mp4");
    java.util.ArrayList<Track> saved = new java.util.ArrayList<>();
    saved.add(track);
    a.saveLibrary(saved);
    Assert.assertTrue(file.delete());
    a.refreshMissingFiles();
    for (int k = 0; k < 10 && a.checkingLibrary; k++) {
      Thread.sleep(20);
      Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    Assert.assertTrue(a.library().isEmpty());
  }

  @Test
  public void playlistSheet() throws Exception {
    setupVideo();
    service.queue.add(video("المقطع التالي.mp4"));
    a.queue();
    renderSheet("17-queue-sheet");
  }

  @Test
  public void brightnessSheet() throws Exception {
    setupVideo();
    a.brightness();
    renderSheet("18-brightness-sheet");
  }

  @Test
  public void equalizerScreen() throws Exception {
    setupVideo();
    a.settings("audio");
    render("19-equalizer", 786, 1704);
  }

  @Test
  public void updatedLandscape() throws Exception {
    setupVideo();
    render("20-landscape-controls", 1704, 786);
  }

  @Test
  public void aspectSheet() throws Exception {
    setupVideo();
    a.aspect();
    renderSheet("21-aspect-sheet");
  }

  @Test
  public void timerAndAbSheets() throws Exception {
    setupVideo();
    a.sleepTimer();
    renderSheet("22-timer-sheet");
    a.activeSheet.dismiss();
    a.abRepeat();
    renderSheet("23-ab-sheet");
  }

  @Test
  public void installConfirmationOpensDirectly() {
    Intent confirmation = new Intent("android.intent.action.VIEW");
    Intent result =
        new Intent()
            .putExtra(
                android.content.pm.PackageInstaller.EXTRA_STATUS,
                android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION)
            .putExtra(Intent.EXTRA_INTENT, confirmation);
    new InstallResultReceiver().onReceive(a, result);
    Assert.assertEquals(
        "android.intent.action.VIEW", Shadows.shadowOf(a).getNextStartedActivity().getAction());
    Assert.assertEquals("confirm", UpdateEngine.phase);
  }

  @Test
  public void updateFailureHasRetryState() {
    new InstallResultReceiver()
        .onReceive(
            a,
            new Intent()
                .putExtra(
                    android.content.pm.PackageInstaller.EXTRA_STATUS,
                    android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED));
    Assert.assertEquals("error", UpdateEngine.phase);
  }

  @Test
  public void updateSuccessClearsStagedFile() {
    a.getSharedPreferences("updates", 0)
        .edit()
        .putString("ready", "fixture.apk")
        .putString("sha256", "fixture")
        .putBoolean("installOnReturn", true)
        .apply();
    new InstallResultReceiver()
        .onReceive(
            a,
            new Intent()
                .putExtra(
                    android.content.pm.PackageInstaller.EXTRA_STATUS,
                    android.content.pm.PackageInstaller.STATUS_SUCCESS));
    Assert.assertFalse(a.getSharedPreferences("updates", 0).contains("ready"));
    Assert.assertFalse(a.getSharedPreferences("updates", 0).getBoolean("installOnReturn", true));
  }

  @Test
  public void sheetsHaveIconsAndCairo() {
    setupVideo();
    a.aspect();
    Assert.assertNotNull(a.activeSheet);
    Assert.assertTrue(a.activeSheet.isShowing());
  }

  @Test
  public void groupedAppearance() throws Exception {
    a.settings("appearance");
    render("24-appearance-top", 786, 1704);
    android.widget.ScrollView scroll = findScroll(a.root);
    scroll.fullScroll(View.FOCUS_DOWN);
    render("25-appearance-bottom", 786, 1704);
  }

  android.widget.ScrollView findScroll(View v) {
    if (v instanceof android.widget.ScrollView) return (android.widget.ScrollView) v;
    if (v instanceof android.view.ViewGroup) {
      android.view.ViewGroup g = (android.view.ViewGroup) v;
      for (int k = 0; k < g.getChildCount(); k++) {
        android.widget.ScrollView s = findScroll(g.getChildAt(k));
        if (s != null) return s;
      }
    }
    return null;
  }

  @Test
  public void visualPickerAllSeven() throws Exception {
    a.settings("appearance");
    new MobileSettings(a).visualPicker();
    renderSheet("26-visualizer-picker");
  }

  @Test
  public void groupedPlayback() throws Exception {
    a.settings("playback");
    render("27-playback-settings", 786, 1704);
  }

  @Test
  public void visualizerStyles() throws Exception {
    for (String style : VisualizerView.STYLES) {
      VisualizerView view = new VisualizerView(a, style);
      view.layout(0, 0, 600, 240);
      Bitmap image = Bitmap.createBitmap(600, 240, Bitmap.Config.ARGB_8888);
      image.eraseColor(0xff10151c);
      view.draw(new Canvas(image));
      File dir = new File(System.getProperty("bplus.renderDir"));
      try (FileOutputStream out = new FileOutputStream(new File(dir, "viz-" + style + ".png"))) {
        image.compress(Bitmap.CompressFormat.PNG, 100, out);
      }
    }
  }

  @Test
  public void audioMeterReadsPcmWithoutConsumingInput() {
    AudioMeter meter = new AudioMeter();
    meter.flush(48000, 2, androidx.media3.common.C.ENCODING_PCM_16BIT);
    java.nio.ByteBuffer pcm =
        java.nio.ByteBuffer.allocate(1024).order(java.nio.ByteOrder.nativeOrder());
    for (int k = 0; k < 256; k++) {
      short v = (short) (Math.sin(k * .3) * 16000);
      pcm.putShort(v).putShort(v);
    }
    pcm.flip();
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));
    meter.handleBuffer(pcm);
    Assert.assertEquals(0, pcm.position());
    Assert.assertTrue(meter.rms > .2f);
    Assert.assertEquals(32, meter.bands.length);
    Assert.assertEquals(128, meter.wave.length);
  }

  @Test
  public void visualizerSelectionPersists() {
    a.settings("appearance");
    new MobileSettings(a).visualPicker();
    Assert.assertTrue(hasText(a.activeSheet.getWindow().getDecorView(), "نقاط"));
    Assert.assertTrue(hasText(a.activeSheet.getWindow().getDecorView(), "شعار B+"));
  }

  @Test
  public void emptyAudioTracksHasClearState() {
    setupVideo();
    a.audioTracks();
    Assert.assertTrue(hasText(a.activeSheet.getWindow().getDecorView(), "مسارات الصوت"));
  }

  @Test
  public void deletedFilesDoNotClearExistingFile() throws Exception {
    File file = new File(a.getFilesDir(), "keep.mp4");
    try (FileOutputStream out = new FileOutputStream(file)) {
      out.write(1);
    }
    java.util.ArrayList<Track> library = new java.util.ArrayList<>();
    library.add(new Track(Uri.fromFile(file), "keep.mp4"));
    a.saveLibrary(library);
    a.refreshMissingFiles();
    for (int k = 0; k < 20 && a.checkingLibrary; k++) {
      Thread.sleep(20);
      Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    Assert.assertEquals(1, a.library().size());
  }

  @Test
  public void adaptiveIconHasThemedLayer() {
    android.graphics.drawable.Drawable icon = a.getDrawable(R.mipmap.ic_launcher);
    Assert.assertTrue(icon instanceof android.graphics.drawable.AdaptiveIconDrawable);
    android.graphics.drawable.AdaptiveIconDrawable adaptive =
        (android.graphics.drawable.AdaptiveIconDrawable) icon;
    Assert.assertNotNull(adaptive.getForeground());
    Assert.assertNotNull(adaptive.getBackground());
    Assert.assertNotNull(adaptive.getMonochrome());
    try {
      for (boolean themed : new boolean[] {false, true}) {
        Bitmap image = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888);
        image.eraseColor(0xffd5e4df);
        Canvas canvas = new Canvas(image);
        android.graphics.drawable.Drawable item = themed ? adaptive.getMonochrome() : adaptive;
        item.setBounds(40, 40, 360, 360);
        if (themed) item.setTint(0xff174c3b);
        item.draw(canvas);
        File dir = new File(System.getProperty("bplus.renderDir"));
        try (FileOutputStream out =
            new FileOutputStream(
                new File(dir, themed ? "30-themed-native.png" : "29-adaptive-native.png"))) {
          image.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
      }
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @Test
  public void updatesRetainsEverySettingsCategory() throws Exception {
    a.settings("updates");
    for (String label : MobileSettings.AR)
      Assert.assertTrue("Missing category " + label, hasText(a.root, label));
    UpdateEngine.phase = "idle";
    UpdateEngine.availableVersion = "";
    UpdateEngine.releaseNotes = "";
    a.refreshUpdateCard();
    render("32-updates-navigation", 786, 1704);
    View appearance = findText(a.root, "المظهر");
    Assert.assertNotNull(appearance);
    appearance.performClick();
    Assert.assertEquals("settings", a.screen);
    Assert.assertEquals("appearance", a.prefs.getString("settingsCategory", ""));
    Assert.assertTrue(hasText(a.root, "نمط الأيقونات"));
    render("33-return-to-appearance", 786, 1704);
  }

  @Test
  public void rememberedUpdatesDoesNotTrapSettings() {
    a.settings("updates");
    a.showLibrary();
    a.settingsHome();
    Assert.assertEquals("updates", a.screen);
    Assert.assertTrue(hasText(a.root, "التشغيل"));
    findText(a.root, "التشغيل").performClick();
    Assert.assertEquals("playback", a.prefs.getString("settingsCategory", ""));
    Assert.assertTrue(hasText(a.root, "سرعة التشغيل الافتراضية"));
  }

  View findText(View view, String query) {
    if (view instanceof TextView && ((TextView) view).getText().toString().equals(query))
      return view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int k = 0; k < group.getChildCount(); k++) {
        View result = findText(group.getChildAt(k), query);
        if (result != null) return result;
      }
    }
    return null;
  }

  @Test
  public void audioSettingsStaysOverVideo() throws Exception {
    setupVideo();
    android.view.View original = a.root;
    Track track = service.track();
    a.settings("audio");
    Assert.assertEquals("player", a.screen);
    Assert.assertSame(original, a.root);
    Assert.assertSame(track, service.track());
    Assert.assertNotNull(a.activeSheet);
    Assert.assertTrue(hasText(a.activeSheet.getWindow().getDecorView(), "تضخيم الصوت: 100"));
    renderSheet("34-audio-over-video");
    a.activeSheet.dismiss();
    Assert.assertEquals("player", a.screen);
    Assert.assertSame(original, a.root);
  }

  @Test
  public void videoSurfaceDispatchesVerticalSwipes() throws Exception {
    setupVideo();
    a.surface.setVisibility(View.VISIBLE);
    render("35-gesture-baseline", 786, 1529);
    a.setVideoControls(false);
    android.media.AudioManager audio =
        (android.media.AudioManager) a.getSystemService(android.content.Context.AUDIO_SERVICE);
    int max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
    audio.setStreamVolume(3, max / 2, 0);
    int before = audio.getStreamVolume(3);
    float w = a.stage.getWidth(), h = a.stage.getHeight();
    swipe(w * .8f, h * .7f, w * .8f, h * .2f);
    Assert.assertTrue(audio.getStreamVolume(3) > before);
    android.view.WindowManager.LayoutParams lp = a.getWindow().getAttributes();
    lp.screenBrightness = .4f;
    a.getWindow().setAttributes(lp);
    swipe(w * .2f, h * .7f, w * .2f, h * .2f);
    Assert.assertTrue(a.getWindow().getAttributes().screenBrightness > .7f);
  }

  @Test
  public void verticalSwipeStartingOnControlsReachesVolume() throws Exception {
    setupVideo();
    a.surface.setVisibility(View.VISIBLE);
    render("36-visible-controls", 786, 1529);
    a.setVideoControls(true);
    render("36-visible-controls", 786, 1529);
    android.media.AudioManager audio =
        (android.media.AudioManager) a.getSystemService(android.content.Context.AUDIO_SERVICE);
    audio.setStreamVolume(3, 2, 0);
    float w = a.stage.getWidth(), h = a.stage.getHeight();
    swipe(w * .8f, h - a.dp(26), w * .8f, h * .2f);
    Assert.assertTrue(audio.getStreamVolume(3) > 2);
  }

  @Test
  public void gestureContainerPreservesTapsAndTimeline() throws Exception {
    setupVideo();
    a.surface.setVisibility(View.VISIBLE);
    render("37-gesture-controls", 786, 1529);
    a.setVideoControls(true);
    render("37-gesture-controls", 786, 1529);
    View tool = findText(a.root, "أدوات");
    Assert.assertNotNull(tool);
    tool = (View) tool.getParent();
    android.graphics.Rect bounds =
        new android.graphics.Rect(0, 0, tool.getWidth(), tool.getHeight());
    a.stage.offsetDescendantRectToMyCoords(tool, bounds);
    float x = bounds.centerX(), y = bounds.centerY();
    tool.performClick();
    Assert.assertNotNull(a.activeSheet);
    Assert.assertTrue(hasText(a.activeSheet.getWindow().getDecorView(), "أدوات الفيديو"));
    a.activeSheet.dismiss();
    android.media.AudioManager audio =
        (android.media.AudioManager) a.getSystemService(android.content.Context.AUDIO_SERVICE);
    audio.setStreamVolume(3, 2, 0);
    int before = audio.getStreamVolume(3);
    bounds = new android.graphics.Rect(0, 0, a.seek.getWidth(), a.seek.getHeight());
    a.stage.offsetDescendantRectToMyCoords(a.seek, bounds);
    x = bounds.left + bounds.width() * .8f;
    y = bounds.centerY();
    swipe(x, y, x, y - a.dp(160));
    Assert.assertEquals(before, audio.getStreamVolume(3));
  }

  @Test
  public void pausedNotificationHasZeroSpeedAndStableState() throws Exception {
    service.queue.add(video("Notification.mp4"));
    service.index = 0;
    service.notifyState();
    java.lang.reflect.Field field = PlaybackService.class.getDeclaredField("lastSessionState");
    field.setAccessible(true);
    android.media.session.PlaybackState state =
        (android.media.session.PlaybackState) field.get(service);
    Assert.assertEquals(0f, state.getPlaybackSpeed(), 0f);
    service.notifyState();
    Assert.assertSame(state, field.get(service));
    Assert.assertTrue(state.getLastPositionUpdateTime() >= 0);
  }

  @Test
  public void notificationSeekToStartPublishesExactZero() throws Exception {
    service.queue.add(video("Reset.mp4"));
    service.index = 0;
    service.notifyState();
    java.lang.reflect.Field field = PlaybackService.class.getDeclaredField("lastSessionState");
    field.setAccessible(true);
    field.set(
        service, new android.media.session.PlaybackState.Builder().setState(2, 500, 0f).build());
    java.lang.reflect.Field sf = PlaybackService.class.getDeclaredField("session");
    sf.setAccessible(true);
    android.media.session.MediaSession session =
        (android.media.session.MediaSession) sf.get(service);
    java.lang.reflect.Field cb = PlaybackService.class.getDeclaredField("sessionCallback");
    cb.setAccessible(true);
    ((android.media.session.MediaSession.Callback) cb.get(service)).onSeekTo(0);
    Assert.assertEquals(
        0, ((android.media.session.PlaybackState) field.get(service)).getPosition());
  }

  @Test
  public void directVideoLevelsAndIndicators() throws Exception {
    setupVideo();
    a.setVideoControls(true);
    render("38-direct-video-controls", 786, 1529);
    a.directVideoLevel(true);
    render("39-inline-volume", 786, 1529);
    Assert.assertEquals(View.VISIBLE, a.videoLevelPanel.getVisibility());
    Assert.assertNull(a.activeSheet);
    a.directVideoLevel(false);
    render("40-inline-brightness", 786, 1529);
    Assert.assertEquals("player", a.screen);
    a.videoLevelPanel.setVisibility(View.GONE);
    a.setVideoControls(false);
    android.media.AudioManager audio =
        (android.media.AudioManager) a.getSystemService(android.content.Context.AUDIO_SERVICE);
    audio.setStreamVolume(3, 2, 0);
    swipe(
        a.stage.getWidth() * .8f,
        a.stage.getHeight() * .7f,
        a.stage.getWidth() * .8f,
        a.stage.getHeight() * .2f);
    Assert.assertTrue(audio.getStreamVolume(3) > 2);
    Assert.assertEquals(View.VISIBLE, a.gestureIndicator.getVisibility());
    render("41-volume-swipe-indicator", 786, 1529);
  }

  @Test
  public void volumePermissionDeclared() throws Exception {
    Assert.assertEquals(
        android.content.pm.PackageManager.PERMISSION_GRANTED,
        a.getPackageManager()
            .checkPermission(
                android.Manifest.permission.MODIFY_AUDIO_SETTINGS, a.getPackageName()));
  }

  @Test
  public void volumeTapAndLongPressHaveDifferentActions() throws Exception {
    setupVideo();
    a.setVideoControls(true);
    render("42-control-actions", 786, 1529);
    View icon = findText(a.root, "الصوت");
    Assert.assertNotNull(icon);
    View cell = (View) icon.getParent();
    cell.performClick();
    Assert.assertNotNull(a.videoLevelPanel);
    Assert.assertEquals(View.VISIBLE, a.videoLevelPanel.getVisibility());
    Assert.assertNull(a.activeSheet);
    Assert.assertTrue(cell.performLongClick());
    Assert.assertNotNull(a.activeSheet);
    Assert.assertEquals("player", a.screen);
  }

  @Test
  public void gesturesWorkWhenChildConsumesTouchAndInLandscape() throws Exception {
    setupVideo();
    a.surface.setVisibility(View.VISIBLE);
    a.surface.setOnTouchListener((v, e) -> true);
    render("43-landscape-controls", 1529, 786);
    a.setVideoControls(false);
    android.media.AudioManager audio =
        (android.media.AudioManager) a.getSystemService(android.content.Context.AUDIO_SERVICE);
    audio.setStreamVolume(3, 2, 0);
    float x = a.stage.getWidth() * .8f, y = a.stage.getHeight() * .8f;
    swipe(x, y, x, a.stage.getHeight() * .2f);
    Assert.assertTrue(audio.getStreamVolume(3) > 2);
    android.view.WindowManager.LayoutParams lp = a.getWindow().getAttributes();
    lp.screenBrightness = .4f;
    a.getWindow().setAttributes(lp);
    swipe(a.stage.getWidth() * .2f, y, a.stage.getWidth() * .2f, a.stage.getHeight() * .2f);
    Assert.assertTrue(a.getWindow().getAttributes().screenBrightness > .7f);
    a.surface.setVisibility(View.INVISIBLE);
    a.directVideoLevel(true);
    render("44-landscape-volume", 1529, 786);
  }

  @Test
  public void playerPictureAndSubtitleSettingsStayInPlayer() throws Exception {
    setupVideo();
    View original = a.root;
    a.settings("picture");
    Assert.assertEquals("player", a.screen);
    Assert.assertSame(original, a.root);
    Assert.assertNotNull(a.activeSheet);
    renderSheet("45-video-settings-over-video");
    a.activeSheet.dismiss();
    a.settings("subtitles");
    Assert.assertEquals("player", a.screen);
    Assert.assertSame(original, a.root);
    Assert.assertNotNull(a.activeSheet);
    renderSheet("46-subtitle-settings-over-video");
    a.activeSheet.dismiss();
  }

  @Test
  public void doubleTapWorksEvenWhenVideoChildConsumesTaps() throws Exception {
    setupVideo();
    a.surface.setVisibility(View.VISIBLE);
    a.surface.setOnTouchListener((v, e) -> true);
    render("47-taps", 786, 1529);
    a.setVideoControls(false);
    org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1));
    float x = a.stage.getWidth() * .8f, y = a.stage.getHeight() * .3f;
    swipe(x, y, x, y);
    org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(100));
    swipe(x, y, x, y);
    Assert.assertEquals("تقديم 10 ثوانٍ", a.gestureIndicator.getText().toString());
    a.surface.setVisibility(View.INVISIBLE);
    render("48-double-tap-feedback", 786, 1529);
  }

  @Test
  public void inlineSliderBackDoesNotLeavePlayer() throws Exception {
    setupVideo();
    a.directVideoLevel(true);
    a.onBackPressed();
    Assert.assertEquals("player", a.screen);
    Assert.assertEquals(View.GONE, a.videoLevelPanel.getVisibility());
  }

  @Test
  public void pinchZoomChangesVideoScale() throws Exception {
    setupVideo();
    a.surface.setVisibility(View.VISIBLE);
    render("49-pinch-baseline", 786, 1529);
    a.setVideoControls(false);
    long now = android.os.SystemClock.uptimeMillis();
    float cy = a.stage.getHeight() / 2f;
    MotionEvent first = MotionEvent.obtain(now, now, 0, 300, cy, 0);
    a.stage.dispatchTouchEvent(first);
    first.recycle();
    pinchEvent(now, now + 30, 5 | (1 << 8), 300, 480, cy);
    pinchEvent(now, now + 80, 2, 220, 560, cy);
    pinchEvent(now, now + 130, 2, 150, 630, cy);
    Assert.assertTrue(a.surface.getScaleX() > 1f);
    Assert.assertEquals(a.surface.getScaleX(), a.surface.getScaleY(), 0f);
    Assert.assertTrue(a.gestureIndicator.getText().toString().startsWith("تكبير"));
    a.surface.setVisibility(View.INVISIBLE);
    render("50-pinch-feedback", 786, 1529);
    pinchEvent(now, now + 160, 6 | (1 << 8), 150, 630, cy);
    MotionEvent up = MotionEvent.obtain(now, now + 180, 1, 150, cy, 0);
    a.stage.dispatchTouchEvent(up);
    up.recycle();
  }

  void pinchEvent(long down, long time, int action, float left, float right, float y) {
    MotionEvent.PointerProperties[] props = {
      new MotionEvent.PointerProperties(), new MotionEvent.PointerProperties()
    };
    MotionEvent.PointerCoords[] coords = {
      new MotionEvent.PointerCoords(), new MotionEvent.PointerCoords()
    };
    for (int i = 0; i < 2; i++) {
      props[i].id = i;
      props[i].toolType = 1;
      coords[i].x = i == 0 ? left : right;
      coords[i].y = y;
      coords[i].pressure = 1;
      coords[i].size = 1;
    }
    MotionEvent e =
        MotionEvent.obtain(
            down,
            time,
            action,
            2,
            props,
            coords,
            0,
            0,
            1,
            1,
            0,
            0,
            android.view.InputDevice.SOURCE_TOUCHSCREEN,
            0);
    a.stage.dispatchTouchEvent(e);
    e.recycle();
  }

  @Test
  public void levelPanelTogglesAndDismissesOutside() throws Exception {
    setupVideo();
    a.setVideoControls(true);
    render("51-level-base", 786, 1529);
    a.directVideoLevel(false);
    render("52-raised-brightness-no-close", 786, 1529);
    Assert.assertFalse(hasDescription(a.videoLevelPanel, "إغلاق"));
    android.graphics.Rect
        panel =
            new android.graphics.Rect(
                0, 0, a.videoLevelPanel.getWidth(), a.videoLevelPanel.getHeight()),
        timeline = new android.graphics.Rect(0, 0, a.seek.getWidth(), a.seek.getHeight());
    a.stage.offsetDescendantRectToMyCoords(a.videoLevelPanel, panel);
    a.stage.offsetDescendantRectToMyCoords(a.seek, timeline);
    Assert.assertTrue(panel.bottom < timeline.top);
    a.directVideoLevel(false);
    Assert.assertEquals(View.GONE, a.videoLevelPanel.getVisibility());
    a.directVideoLevel(true);
    render("53-raised-volume-no-close", 786, 1529);
    swipe(
        a.stage.getWidth() * .5f,
        a.stage.getHeight() * .25f,
        a.stage.getWidth() * .5f,
        a.stage.getHeight() * .25f);
    Assert.assertEquals(View.GONE, a.videoLevelPanel.getVisibility());
    a.directVideoLevel(true);
    render("54-raised-landscape", 1529, 786);
    a.videoLevelPanel.setVisibility(View.GONE);
    a.directVideoLevel(true);
    render("54-raised-landscape", 1529, 786);
  }

  boolean hasDescription(View view, String label) {
    if (label.contentEquals(
        view.getContentDescription() == null ? "" : view.getContentDescription())) return true;
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
        if (hasDescription(((ViewGroup) view).getChildAt(i), label)) return true;
    return false;
  }

  @Test
  public void importantControlsBottomAndSecondaryTop() throws Exception {
    setupVideo();
    a.setVideoControls(true);
    render("55-priority-controls-portrait", 786, 1529);
    for (String label : new String[] {"الصوت", "الإضاءة", "الترجمة", "السرعة", "تدوير", "قفل"})
      Assert.assertTrue(hasText(a.chrome, label));
    for (String label : new String[] {"الأبعاد", "لقطة", "أدوات"}) {
      Assert.assertTrue(hasText(a.topBar, label));
      Assert.assertFalse(hasText(a.chrome, label));
    }
    a.directVideoLevel(false);
    render("56-priority-controls-raised-slider", 786, 1529);
    render("57-priority-controls-landscape", 1529, 786);
    a.videoLevelPanel.setVisibility(View.GONE);
    a.directVideoLevel(false);
    render("57-priority-controls-landscape", 1529, 786);
  }

  @Test
  public void audioEqualizerLeftBrandAndSpeed() throws Exception {
    for (boolean english : new boolean[] {false, true}) {
      a.prefs.edit().putString("lang", english ? "en" : "ar").apply();
      service.queue.clear();
      service.queue.add(video("Evening recitation.mp3"));
      service.index = 0;
      a.showPlayer();
      render(english ? "60-audio-english-left-brand" : "61-audio-arabic-left-brand", 786, 1704);
      Assert.assertFalse(
          hasDescription(a.root, english ? "Volume and brightness" : "الصوت والإضاءة"));
      Assert.assertTrue(hasDescription(a.root, english ? "Volume" : "الصوت"));
      Assert.assertEquals(
          english ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL,
          a.root.getLayoutDirection());
      Assert.assertEquals(
          Gravity.LEFT | Gravity.CENTER_VERTICAL, ((TextView) a.topBar.getChildAt(0)).getGravity());
    }
    a.speed();
    Assert.assertNotNull(a.activeSheet);
    renderSheet("62-audio-speed-slider");
  }

  @Test
  public void videoSpeedSliderInline() throws Exception {
    setupVideo();
    a.setVideoControls(true);
    render("63-video-speed-base", 786, 1529);
    a.speed();
    render("64-video-speed-slider", 786, 1529);
    Assert.assertEquals("speed", a.videoPanelKind);
    Assert.assertNull(a.activeSheet);
    a.speed();
    Assert.assertEquals(View.GONE, a.videoLevelPanel.getVisibility());
  }

  @Test
  public void audioIconChoices() throws Exception {
    a.base("");
    android.widget.LinearLayout choices = a.row();
    choices.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    choices.setPadding(a.dp(12), a.dp(16), a.dp(12), a.dp(16));
    String[] names = {"A · Speaker", "B · More waves", "C · Equalizer"};
    for (int i = 0; i < 3; i++) {
      android.widget.LinearLayout cell = a.col();
      cell.setGravity(Gravity.CENTER);
      TextView label = a.text(names[i], 12, a.ink);
      label.setGravity(Gravity.CENTER);
      cell.addView(label);
      android.widget.ImageView icon = new android.widget.ImageView(a);
      if (i == 1) {
        Bitmap bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        Paint p = new Paint(3);
        p.setColor(a.ink);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(7);
        p.setStrokeCap(Paint.Cap.ROUND);
        Path speaker = new Path();
        speaker.moveTo(10, 48);
        speaker.lineTo(28, 48);
        speaker.lineTo(48, 30);
        speaker.lineTo(48, 90);
        speaker.lineTo(28, 72);
        speaker.lineTo(10, 72);
        speaker.close();
        c.drawPath(speaker, p);
        for (int k = 0; k < 3; k++) {
          float r = 24 + k * 18;
          c.drawArc(48 - r, 60 - r, 48 + r, 60 + r, -50, 100, false, p);
        }
        icon.setImageBitmap(bitmap);
      } else icon.setImageDrawable(new PlayerIcon(i == 0 ? "volume" : "equalizer", a.ink));
      cell.addView(icon, new android.widget.LinearLayout.LayoutParams(a.dp(64), a.dp(80)));
      choices.addView(cell, new android.widget.LinearLayout.LayoutParams(0, -2, 1));
    }
    a.page.addView(choices);
    render("65-audio-icon-options", 900, 460);
  }

  @Test
  public void audioLibraryNextPreviousAndBack() throws Exception {
    Track first = video("First.mp3"), second = video("Second.mp3"), third = video("Third.mp3");
    a.saveLibrary(
        new java.util.ArrayList<>(
            java.util.Arrays.asList(first, video("Film.mp4"), second, third)));
    a.mediaTab = "audio";
    a.showLibrary("all");
    findText(a.root, "Second.mp3").getParent();
    ((View) findText(a.root, "Second.mp3").getParent().getParent()).performClick();
    Assert.assertEquals(3, service.queue.size());
    Assert.assertEquals(second.uri, service.track().uri);
    service.next(1);
    a.refresh();
    Assert.assertEquals(third.uri, service.track().uri);
    Assert.assertTrue(hasText(a.root, "Third"));
    service.next(-1);
    Assert.assertEquals(second.uri, service.track().uri);
    a.onBackPressed();
    Assert.assertEquals("audio", a.mediaTab);
    Assert.assertEquals("library", a.screen);
    Assert.assertTrue(hasText(a.root, "Second.mp3"));
    Assert.assertFalse(hasText(a.root, "Film.mp4"));
    render("66-audio-list-return", 786, 1704);
  }

  @Test
  public void filteredLibraryQueueMatchesVisibleOrder() {
    Track first = video("First.mp3"), second = video("Second.mp3"), third = video("Third.mp3");
    a.saveLibrary(
        new java.util.ArrayList<>(
            java.util.Arrays.asList(third, second, first, video("Film.mp4"))));
    a.prefs
        .edit()
        .putBoolean("favorite:" + first.uri, true)
        .putBoolean("favorite:" + third.uri, true)
        .apply();
    a.mediaTab = "audio";
    a.showLibrary("favorites");
    a.openLibraryTrack(third);
    Assert.assertEquals(2, service.queue.size());
    Assert.assertEquals(first.uri, service.queue.get(1).uri);
    service.next(1);
    Assert.assertEquals(first.uri, service.track().uri);
    a.onBackPressed();
    Assert.assertEquals("favorites", a.libraryFilter);
    a.searchQuery = "Third";
    a.showLibrary();
    a.openLibraryTrack(third);
    Assert.assertEquals(1, service.queue.size());
  }

  @Test
  public void recentAndFolderQueuesMatchDisplay() {
    Track one = video("One.mp3"), two = video("Two.mp3"), three = video("Three.mp3");
    a.saveLibrary(new java.util.ArrayList<>(java.util.Arrays.asList(one, two, three)));
    a.prefs
        .edit()
        .putLong("played:" + one.uri, 100)
        .putLong("played:" + two.uri, 300)
        .putString("parent:" + one.uri, "folder-a")
        .putString("parent:" + two.uri, "folder-a")
        .putString("parent:" + three.uri, "folder-b")
        .apply();
    a.mediaTab = "audio";
    a.libraryFilter = "recent";
    a.openLibraryTrack(two);
    Assert.assertEquals(2, service.queue.size());
    Assert.assertEquals(one.uri, service.queue.get(1).uri);
    a.folderFilter = "folder-b";
    a.libraryFilter = "all";
    a.openLibraryTrack(three);
    Assert.assertEquals(1, service.queue.size());
    Assert.assertEquals(three.uri, service.track().uri);
  }

  @Test
  public void libraryLocationSurvivesNewActivity() {
    a.mediaTab = "audio";
    a.libraryFilter = "favorites";
    a.searchQuery = "Recitation";
    a.folderFilter = "folder-a";
    a.saveLibraryLocation();
    MainActivity reopened = Robolectric.buildActivity(MainActivity.class).create().get();
    Assert.assertEquals("audio", reopened.mediaTab);
    Assert.assertEquals("favorites", reopened.libraryFilter);
    Assert.assertEquals("Recitation", reopened.searchQuery);
    Assert.assertEquals("folder-a", reopened.folderFilter);
    reopened.onDestroy();
  }

  @Test
  public void videoLibraryUsesListAndQueueDoesNotReplaceIt() {
    Track one = video("One.mp4"), two = video("Two.mp4"), audio = video("Song.mp3");
    a.saveLibrary(new java.util.ArrayList<>(java.util.Arrays.asList(one, audio, two)));
    a.mediaTab = "video";
    a.libraryFilter = "all";
    a.openLibraryTrack(one);
    Assert.assertEquals(2, service.queue.size());
    service.next(1);
    Assert.assertEquals(two.uri, service.track().uri);
    a.open(one, true);
    Assert.assertEquals(2, service.queue.size());
    Assert.assertEquals(one.uri, service.track().uri);
  }

  @Test
  public void manualNextOverridesRepeatButEndRepeats() {
    Track one = video("One.mp3"), two = video("Two.mp3");
    service.loadList(java.util.Arrays.asList(one, two), one);
    a.prefs.edit().putBoolean("repeat", true).apply();
    service.next(1);
    Assert.assertEquals(two.uri, service.track().uri);
    service.advanceAfterEnd();
    Assert.assertEquals(two.uri, service.track().uri);
    service.next(-1);
    Assert.assertEquals(one.uri, service.track().uri);
  }

  @Test
  public void audioVolumeOnlyAndMatchedTransportIcons() throws Exception {
    for (boolean english : new boolean[] {false, true}) {
      a.prefs.edit().putString("lang", english ? "en" : "ar").apply();
      service.queue.clear();
      service.queue.add(video("Evening recitation.mp3"));
      service.index = 0;
      a.showPlayer();
      render(english ? "68-audio-matched-icons-en" : "67-audio-matched-icons-ar", 786, 1704);
      findDescription(a.root, english ? "Volume" : "الصوت").performClick();
      View sheet = a.activeSheet.getWindow().getDecorView();
      Assert.assertEquals(1, countSeekBars(sheet));
      Assert.assertFalse(hasText(sheet, english ? "Bass" : "الجهير"));
      Assert.assertEquals("player", a.screen);
      renderSheet(english ? "70-audio-volume-only-en" : "69-audio-volume-only-ar");
      a.activeSheet.dismiss();
    }
    for (String family :
        new String[] {"phosphor", "phosphor-fill", "phosphor-duotone", "lucide", "material"}) {
      DesktopGlyph.family = family;
      Assert.assertEquals("prev", DesktopGlyph.map("previous"));
      Assert.assertNotNull(
          DesktopGlyph.families
              .getJSONObject(family)
              .getJSONObject("glyphs")
              .optJSONObject(DesktopGlyph.map("previous")));
      Assert.assertNotNull(
          DesktopGlyph.families
              .getJSONObject(family)
              .getJSONObject("glyphs")
              .optJSONObject("next"));
    }
    DesktopGlyph.init(a);
  }

  int countSeekBars(View view) {
    int count = view instanceof SeekBar ? 1 : 0;
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
        count += countSeekBars(((ViewGroup) view).getChildAt(i));
    return count;
  }

  View findDescription(View view, String label) {
    if (label.contentEquals(
        view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
        View found = findDescription(((ViewGroup) view).getChildAt(i), label);
        if (found != null) return found;
      }
    return null;
  }

  void swipe(float x1, float y1, float x2, float y2) {
    long now = android.os.SystemClock.uptimeMillis();
    for (int i = 0; i < 3; i++) {
      android.view.MotionEvent event =
          android.view.MotionEvent.obtain(
              now,
              now + i * 100,
              i == 0 ? 0 : i == 1 ? 2 : 1,
              i == 0 ? x1 : x2,
              i == 0 ? y1 : y2,
              0);
      a.stage.dispatchTouchEvent(event);
      event.recycle();
    }
  }

  boolean hasText(View view, String query) {
    if (view instanceof TextView && ((TextView) view).getText().toString().equals(query))
      return true;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int k = 0; k < group.getChildCount(); k++)
        if (hasText(group.getChildAt(k), query)) return true;
    }
    return false;
  }

  void deviceLocale(String lang) {
    android.content.res.Configuration c =
        new android.content.res.Configuration(a.getResources().getConfiguration());
    c.setLocales(new android.os.LocaleList(new java.util.Locale(lang)));
    a.getResources().updateConfiguration(c, a.getResources().getDisplayMetrics());
  }

  @Test
  public void defaultLanguageFollowsDevice() throws Exception {
    a.prefs.edit().clear().apply();
    deviceLocale("ar");
    Assert.assertFalse(a.english());
    Assert.assertEquals("لغة الجهاز", a.tr("لغة الجهاز", "Device language"));
    deviceLocale("en");
    Assert.assertTrue(a.english());
    deviceLocale("fr");
    Assert.assertTrue(a.english());
    Assert.assertEquals("system", a.langMode());
    a.prefs.edit().putString("lang", "ar").apply();
    Assert.assertFalse(a.english());
    a.prefs.edit().clear().putBoolean("english", true).apply();
    deviceLocale("ar");
    Assert.assertTrue(a.english());
    Assert.assertEquals("en", a.langMode());
    a.prefs.edit().clear().apply();
    deviceLocale("en");
    a.settings("language");
    render("71-language-device-english", 786, 1704);
    deviceLocale("ar");
    a.settings("language");
    render("72-language-device-arabic", 786, 1704);
  }
}
