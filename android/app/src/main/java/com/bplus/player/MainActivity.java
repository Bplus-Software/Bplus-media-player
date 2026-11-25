package com.bplus.player;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.view.*;
import android.widget.*;
import androidx.media3.ui.PlayerView;
import java.util.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class MainActivity extends Activity {
  PlaybackService s;
  boolean bound, video, locked, drag, attached;
  FrameLayout root, stage;
  LinearLayout page, chrome, videoLevelPanel;
  Boolean videoLevelVolume;
  String videoPanelKind = "";
  PlayerView surface;
  TextView title, time, play;
  SeekBar seek;
  Handler h = new Handler();
  int bg = 0xff12171e,
      panel = 0xff1b2430,
      ink = 0xffedf2f7,
      muted = 0xff9aa9b9,
      accent = 0xff4d99ff;
  android.content.SharedPreferences prefs;
  Typeface font;
  String screen = "library";
  String mediaTab = "video", libraryFilter = "all", searchQuery = "", folderFilter = "";
  LinearLayout topBar;
  TextView endTime, miniPlay, gestureIndicator;
  String shownTrack = "";
  float gx, gy, startBright;
  int startVol;
  long gtime;
  boolean gesture;
  int gestureAxis;

  VisualizerView rings;
  ImageView preview;
  volatile boolean thumbnailBusy;
  boolean fullMode;
  boolean resumed;
  android.graphics.Bitmap cover;

  String langMode() {
    String m = prefs.getString("lang", null);
    if (m == null) {
      m = prefs.contains("english") ? (prefs.getBoolean("english", false) ? "en" : "ar") : "system";
      prefs.edit().putString("lang", m).apply();
    }
    return m;
  }

  boolean english() {
    String m = langMode();
    if (m.equals("en")) return true;
    if (m.equals("ar")) return false;
    String l = getResources().getConfiguration().getLocales().get(0).getLanguage();
    return !l.equals("ar");
  }

  String tr(String ar, String en) {
    return english() ? en : ar;
  }

  final ServiceConnection conn =
      new ServiceConnection() {
        public void onServiceConnected(ComponentName n, IBinder b) {
          s = ((PlaybackService.Local) b).get();
          bound = true;
          s.changed =
              () ->
                  runOnUiThread(
                      () -> {
                        if ("player".equals(screen)
                            && s.track() != null
                            && video != !s.track().audio) showPlayer();
                        refresh();
                      });
          if (!handle(getIntent())) {
            if (getIntent().getBooleanExtra("openUpdates", false)) settings("updates");
            else if (prefs.getBoolean("playlistStartup", false)) {
              showLibrary();
              queue();
            } else showLibrary();
          }
          refreshMissingFiles();
          UpdateEngine.schedule(MainActivity.this);
          if (prefs.getBoolean("autoUpdate", true)) UpdateEngine.check(MainActivity.this, false);
        }

        public void onServiceDisconnected(ComponentName n) {
          bound = false;
          s = null;
        }
      };

  public void onCreate(Bundle b) {
    super.onCreate(b);
    prefs = getSharedPreferences("prefs", 0);
    restoreLibraryLocation();
    if (!prefs.getBoolean("desktopShadeScale", false)) {
      var edit = prefs.edit().putBoolean("desktopShadeScale", true);
      if (prefs.contains("shade")) edit.putInt("shade", 100 - prefs.getInt("shade", 8));
      edit.apply();
    }
    try {
      font = Typeface.createFromAsset(getAssets(), "fonts/Cairo-Regular.ttf");
    } catch (Exception e) {
      font = Typeface.DEFAULT;
    }
    startService(new Intent(this, PlaybackService.class));
    bindService(new Intent(this, PlaybackService.class), conn, BIND_AUTO_CREATE);
    h.post(ticker);
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
            != PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, 44);
  }

  void palette() {
    DesktopGlyph.init(this);
    String theme = prefs.getString("theme", "dark");
    boolean light =
        theme.equals("light")
            || (theme.equals("system")
                && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    != Configuration.UI_MODE_NIGHT_YES);
    int shade = 100 - prefs.getInt("shade", light ? 4 : 92), v = (int) (shade * 2.35);
    bg = Color.rgb(Math.max(0, v - 2), Math.min(252, v + 3), Math.min(255, v + 10));
    panel = Color.rgb(Math.min(255, v + 8), Math.min(255, v + 17), Math.min(255, v + 26));
    ink = shade > 55 ? 0xff182230 : 0xffedf2f7;
    muted = shade > 55 ? 0xff526275 : 0xff9aa9b9;
    accent = prefs.getInt("accent", 0xff4d99ff);
  }

  int dp(float n) {
    return (int) (getResources().getDisplayMetrics().density * n + .5f);
  }

  LinearLayout col() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(1);
    return l;
  }

  LinearLayout row() {
    LinearLayout l = new LinearLayout(this);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  void pad(View v, int n) {
    v.setPadding(dp(n), dp(n), dp(n), dp(n));
  }

  GradientDrawable shape(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  TextView text(String t, int size, int color) {
    TextView v = new TextView(this);
    v.setText(t);
    v.setTextSize(size);
    v.setTextColor(color);
    v.setTypeface(font);
    v.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    return v;
  }

  TextView button(String t, Runnable f) {
    TextView b = text(t, 16, accent);
    b.setGravity(Gravity.CENTER);
    b.setMinHeight(dp(48));
    b.setMinWidth(dp(48));
    pad(b, 8);
    b.setContentDescription(t);
    String key = iconKey(t);
    if (key != null) {
      b.setText("");
      PlayerIcon icon = new PlayerIcon(key, accent);
      icon.setBounds(0, 0, dp(24), dp(24));
      b.setCompoundDrawables(null, icon, null, null);
      b.setPadding(dp(12), dp(12), dp(12), dp(12));
    }
    b.setOnClickListener(
        v -> {
          if (!locked) f.run();
        });
    return b;
  }

  void spacer(LinearLayout l, int n) {
    l.addView(new View(this), new LinearLayout.LayoutParams(1, dp(n)));
  }

  String iconKey(String text) {
    switch (text) {
      case "⚙":
        return "gear";
      case "⋮":
        return "more";
      case "▶":
        return "play";
      case "Ⅱ":
        return "pause";
      case "CC":
        return "sub";
      case "1×":
        return "speed";
      case "☀":
        return "sun";
      case "☷":
        return "queue";
      case "▣":
        return "pip";
      case "⛶":
        return "full";
      case "⌑":
        return "lock";
      case "|◀":
        return "previous";
      case "▶|":
        return "next";
      default:
        return null;
    }
  }

  class IconControl extends TextView {
    String key;
    int tint;
    int iconSize = 23;

    IconControl(String key, int color) {
      super(MainActivity.this);
      this.key = key;
      this.tint = color;
      setGravity(Gravity.CENTER);
      setMinWidth(dp(48));
      setMinHeight(dp(48));
      setBackground(
          new RippleDrawable(
              android.content.res.ColorStateList.valueOf(0x224d99ff),
              null,
              shape(Color.WHITE, 32)));
    }

    protected void onDraw(Canvas c) {
      super.onDraw(c);
      PlayerIcon icon = new PlayerIcon(key, tint);
      int size = dp(iconSize);
      int x = (getWidth() - size) / 2, y = (getHeight() - size) / 2;
      icon.setBounds(x, y, x + size, y + size);
      icon.draw(c);
    }
  }

  void setPlayIcon() {
    if (play instanceof IconControl) {
      IconControl control = (IconControl) play;
      control.key = s.player.isPlaying() ? "pause" : "play";
      control.tint = video ? Color.WHITE : ink;
      control.iconSize = 36;
      control.invalidate();
    }
  }

  void base(String name) {
    h.removeCallbacks(hideVideoControls);
    detach();
    rings = null;
    play = null;
    miniPlay = null;
    seek = null;
    preview = null;
    videoLevelPanel = null;
    videoPanelKind = "";
    cover = null;
    locked = false;
    palette();
    getWindow().getDecorView().setSystemUiVisibility(0);
    root = new FrameLayout(this);
    root.setBackgroundColor(bg);
    root.setLayoutDirection(
        english() ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
    page = col();
    root.addView(page, new FrameLayout.LayoutParams(-1, -1));
    root.setOnApplyWindowInsetsListener(
        (v, i) -> {
          android.graphics.Insets z = i.getInsets(WindowInsets.Type.systemBars());
          v.setPadding(z.left, z.top, z.right, z.bottom);
          return i;
        });
    setContentView(root);
    getWindow().setStatusBarColor(bg);
    getWindow().setNavigationBarColor(bg);
    getWindow()
        .clearFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    topBar = row();
    topBar.setPadding(dp(12), 0, dp(12), 0);
    topBar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    TextView brand = text("B+", 28, accent);
    brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    brand.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
    topBar.addView(brand, new LinearLayout.LayoutParams(0, dp(64), 1));
    topBar.addView(iconButton("search", tr("بحث", "Search"), this::search));
    topBar.addView(iconButton("more", tr("المزيد", "More"), this::menu));
    page.addView(topBar);
    if (!name.isEmpty()) {
      LinearLayout heading = col();
      heading.setPadding(dp(20), dp(12), dp(20), dp(12));
      TextView h1 = text(name, 27, ink);
      h1.setTypeface(font, Typeface.BOLD);
      heading.addView(h1);
      page.addView(heading);
    }
  }

  TextView iconButton(String key, String label, Runnable action) {
    IconControl b = new IconControl(key, ink);
    b.setContentDescription(label);
    b.setOnClickListener(
        v -> {
          if (!locked) action.run();
        });
    return b;
  }

  String displayName(String name) {
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }

  void oneLine(TextView t) {
    t.setMaxLines(1);
    t.setEllipsize(android.text.TextUtils.TruncateAt.END);
    t.setTextDirection(View.TEXT_DIRECTION_LOCALE);
    t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
  }

  TextView actionButton(String label, boolean primary, Runnable action) {
    TextView b = button(label, action);
    b.setTextSize(14);
    b.setTextColor(primary ? Color.WHITE : accent);
    GradientDrawable d = shape(primary ? accent : Color.TRANSPARENT, 9);
    if (!primary) d.setStroke(dp(1), accent);
    b.setBackground(d);
    return b;
  }

  void search() {
    EditText input = new EditText(this);
    input.setSingleLine();
    input.setText(searchQuery);
    input.setHint(tr("اسم الملف", "File name"));
    new SheetBuilder(this)
        .setTitle(tr("بحث في المكتبة", "Search library"))
        .setView(input)
        .setPositiveButton(
            tr("بحث", "Search"),
            (d, w) -> {
              searchQuery = input.getText().toString().trim();
              showLibrary(libraryFilter);
            })
        .setNegativeButton(tr("إلغاء", "Cancel"), null)
        .setNeutralButton(
            tr("مسح", "Clear"),
            (d, w) -> {
              searchQuery = "";
              showLibrary(libraryFilter);
            })
        .show();
  }

  void bottomNav(String selected) {
    LinearLayout nav = row();
    nav.setPadding(dp(8), dp(3), dp(8), dp(3));
    nav.setBackgroundColor(panel);
    String[][] items = {
      {"video", tr("الفيديو", "Video"), "play"},
      {"audio", tr("الصوتيات", "Audio"), "music"},
      {"folders", tr("المجلدات", "Folders"), "folder"},
      {"settings", tr("الإعدادات", "Settings"), "gear"}
    };
    for (String[] item : items) {
      LinearLayout cell = col();
      cell.setGravity(Gravity.CENTER);
      TextView ico = iconButton(item[2], item[1], () -> {});
      PlayerIcon d = new PlayerIcon(item[2], item[0].equals(selected) ? accent : muted);
      d.setBounds(0, 0, dp(21), dp(21));
      if (ico instanceof IconControl) {
        ((IconControl) ico).tint = item[0].equals(selected) ? accent : muted;
        ((IconControl) ico).iconSize = 21;
      }
      ico.setMinHeight(0);
      ico.setPadding(dp(8), dp(4), dp(8), dp(4));
      ico.setClickable(false);
      cell.addView(ico, new LinearLayout.LayoutParams(dp(48), dp(29)));
      TextView label = text(item[1], 11, item[0].equals(selected) ? accent : muted);
      label.setGravity(Gravity.CENTER);
      cell.addView(label);
      cell.setContentDescription(item[1]);
      cell.setOnClickListener(
          v -> {
            if (item[0].equals("settings")) settings("main");
            else {
              mediaTab = item[0];
              folderFilter = "";
              searchQuery = "";
              showLibrary("all");
            }
          });
      nav.addView(cell, new LinearLayout.LayoutParams(0, dp(60), 1));
    }
    page.addView(nav);
  }

  void loadThumb(Track t, ImageView target, TextView meta) {
    new Thread(
            () -> {
              MediaMetadataRetriever mm = new MediaMetadataRetriever();
              try {
                mm.setDataSource(this, t.uri);
                byte[] art = mm.getEmbeddedPicture();
                Bitmap bm =
                    art != null
                        ? BitmapFactory.decodeByteArray(art, 0, art.length)
                        : t.audio
                            ? null
                            : mm.getScaledFrameAtTime(
                                0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 270);
                String duration = mm.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                String height =
                    mm.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                String ext =
                    t.name.contains(".")
                        ? t.name.substring(t.name.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT)
                        : "";
                String detail =
                    ext
                        + (height == null ? "" : " · " + height + "p")
                        + (duration == null ? "" : " · " + format(Long.parseLong(duration)));
                runOnUiThread(
                    () -> {
                      if (bm != null) {
                        target.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        target.setImageBitmap(bm);
                      }
                      if (meta != null) meta.setText(detail);
                    });
              } catch (Exception ignored) {
              } finally {
                try {
                  mm.release();
                } catch (Exception ignored) {
                }
              }
            })
        .start();
  }

  ImageView thumb(Track t) {
    ImageView v = new ImageView(this);
    v.setScaleType(ImageView.ScaleType.CENTER_CROP);
    v.setBackground(shape(panel, 9));
    v.setClipToOutline(true);
    v.setScaleType(ImageView.ScaleType.CENTER);
    v.setImageDrawable(new PlayerIcon(t.audio ? "music" : "play", muted));
    return v;
  }

  void miniPlayer() {
    if (s == null || s.track() == null) return;
    LinearLayout mini = row();
    mini.setPadding(dp(16), dp(5), dp(12), dp(5));
    mini.setBackgroundColor(panel);
    TextView name = text(s.track().name, 13, ink);
    oneLine(name);
    mini.addView(name, new LinearLayout.LayoutParams(0, dp(44), 1));
    name.setGravity(Gravity.CENTER_VERTICAL);
    name.setOnClickListener(v -> showPlayer());
    miniPlay =
        iconButton(
            s.player.isPlaying() ? "pause" : "play",
            tr("تشغيل أو إيقاف", "Play or pause"),
            () -> s.toggle());
    mini.addView(miniPlay);
    page.addView(mini);
  }

  ScrollView scroll() {
    ScrollView sc = new ScrollView(this);
    page.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
    return sc;
  }

  void rule(LinearLayout l) {
    View v = new View(this);
    v.setBackgroundColor(panel);
    l.addView(v, new LinearLayout.LayoutParams(-1, dp(1)));
  }

  void showLibrary() {
    showLibrary(libraryFilter);
  }

  void showLibrary(String filter) {
    screen = "library";
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    fullMode = false;
    libraryFilter = filter;
    saveLibraryLocation();
    boolean folders = mediaTab.equals("folders");
    base(tr("مكتبتك", "Your library"));
    LinearLayout tabs = row();
    tabs.setPadding(dp(20), 0, dp(20), 0);
    String[][] items = {
      {tr("الكل", "All"), "all"},
      {tr("المفضلة", "Favorites"), "favorites"},
      {tr("آخر تشغيل", "Recent"), "recent"}
    };
    if (!folders) {
      for (String[] item : items) {
        LinearLayout cell = col();
        TextView label = button(item[0], () -> showLibrary(item[1]));
        label.setTextSize(13);
        label.setTextColor(filter.equals(item[1]) ? accent : muted);
        cell.addView(label, new LinearLayout.LayoutParams(-1, dp(44)));
        View line = new View(this);
        line.setBackgroundColor(filter.equals(item[1]) ? accent : panel);
        cell.addView(line, new LinearLayout.LayoutParams(-1, dp(2)));
        tabs.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
      }
      page.addView(tabs);
    }
    LinearLayout list = col();
    list.setPadding(dp(20), dp(20), dp(20), dp(24));
    ScrollView sc = scroll();
    sc.setFillViewport(false);
    sc.addView(list);
    if (folders) {
      TextView note =
          text(
              tr(
                  "أضف المجلدات اللي تحب تظهر في مكتبتك.",
                  "Choose the folders you want in your library."),
              14,
              muted);
      list.addView(note);
      spacer(list, 20);
      for (android.content.UriPermission permission :
          getContentResolver().getPersistedUriPermissions()) {
        Uri u = permission.getUri();
        if (!DocumentsContract.isTreeUri(u)) continue;
        settingButton(
            list,
            name(u),
            () -> {
              folderFilter = u.toString();
              mediaTab = "video";
              showLibrary("all");
            });
      }
      list.addView(actionButton(tr("إضافة مجلد", "Add folder"), true, this::folder));
    } else {
      String last = prefs.getString("lastUri", "");
      Track recent =
          last.isEmpty() ? null : new Track(Uri.parse(last), prefs.getString("lastName", "Media"));
      if (recent != null
          && recent.audio == mediaTab.equals("audio")
          && searchQuery.isEmpty()
          && filter.equals("all")) {
        FrameLayout hero = new FrameLayout(this);
        hero.setBackground(shape(panel, 14));
        hero.setClipToOutline(true);
        ImageView image = thumb(recent);
        hero.addView(image, new FrameLayout.LayoutParams(-1, -1));
        loadThumb(recent, image, null);
        View shade = new View(this);
        shade.setBackground(
            new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x00000000, 0xf5091620}));
        hero.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout content = col();
        content.setPadding(dp(16), dp(16), dp(16), dp(16));
        content.addView(text(tr("كمّل من مكان ما وقفت", "Continue watching"), 12, 0xffccd9e5));
        TextView name = text(displayName(recent.name), 18, Color.WHITE);
        oneLine(name);
        content.addView(name);
        TextView pos =
            text(format(getSharedPreferences("resume", 0).getLong(last, 0)), 12, 0xffccd9e5);
        content.addView(pos);
        spacer(content, 8);
        TextView resume =
            actionButton(tr("كمّل المشاهدة", "Continue"), true, () -> openLibraryTrack(recent));
        content.addView(resume, new LinearLayout.LayoutParams(dp(150), dp(44)));
        hero.addView(content, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        list.addView(hero, new LinearLayout.LayoutParams(-1, dp(208)));
        spacer(list, 22);
      }
      LinearLayout section = row();
      TextView label =
          text(
              searchQuery.isEmpty()
                  ? tr(
                      mediaTab.equals("audio") ? "الصوتيات" : "الفيديوهات",
                      mediaTab.equals("audio") ? "Audio" : "Videos")
                  : tr("نتائج البحث", "Search results"),
              20,
              ink);
      label.setTypeface(font, Typeface.BOLD);
      section.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
      TextView sort =
          button(
              tr("الاسم ↓", "Name ↓"),
              () ->
                  new SheetBuilder(this)
                      .setItems(
                          new String[] {
                            tr("بالاسم", "By name"), tr("عكس الترتيب", "Reverse order")
                          },
                          (d, n) -> {
                            var a = library();
                            if (n == 0)
                              a.sort(Comparator.comparing(t -> t.name.toLowerCase(Locale.ROOT)));
                            else Collections.reverse(a);
                            saveLibrary(a);
                            showLibrary(filter);
                          })
                      .show());
      sort.setTextColor(muted);
      sort.setTextSize(11);
      section.addView(sort);
      list.addView(section);
      ArrayList<Track> saved = displayedLibrary();
      int count = saved.size();
      for (Track t : saved) addTrack(list, t, false);
      if (count == 0) {
        spacer(list, 24);
        TextView empty =
            text(
                tr(
                    filter.equals("favorites") ? "مفضلتك لسه فاضية" : "ملفاتك هتظهر هنا",
                    filter.equals("favorites") ? "No favorites yet" : "Your files belong here"),
                18,
                ink);
        empty.setGravity(Gravity.CENTER);
        list.addView(empty);
        TextView hint =
            text(
                tr(
                    "افتح ملف أو أضف مجلد من موبايلك.",
                    "Open a file or add a folder from your phone."),
                13,
                muted);
        hint.setGravity(Gravity.CENTER);
        list.addView(hint);
        spacer(list, 28);
      }
      spacer(list, 16);
      list.addView(
          actionButton(
              tr("افتح ملف من الموبايل", "Open a file from your phone"),
              false,
              () -> pick(100, "*/*")),
          new LinearLayout.LayoutParams(-1, dp(48)));
      spacer(list, 8);
      TextView add = button(tr("إضافة مجلد", "Add folder"), this::folder);
      add.setTextSize(13);
      list.addView(add);
    }
    miniPlayer();
    bottomNav(mediaTab);
  }

  void saveLibraryLocation() {
    prefs
        .edit()
        .putString("libraryTab", mediaTab)
        .putString("libraryFilter", libraryFilter)
        .putString("librarySearch", searchQuery)
        .putString("libraryFolder", folderFilter)
        .apply();
  }

  void restoreLibraryLocation() {
    mediaTab = prefs.getString("libraryTab", "video");
    if (!Arrays.asList("video", "audio", "folders").contains(mediaTab)) mediaTab = "video";
    libraryFilter = prefs.getString("libraryFilter", "all");
    searchQuery = prefs.getString("librarySearch", "");
    folderFilter = prefs.getString("libraryFolder", "");
  }

  ArrayList<Track> displayedLibrary() {
    ArrayList<Track> items = library();
    items.removeIf(
        t ->
            t.audio != mediaTab.equals("audio")
                || (!folderFilter.isEmpty()
                    && !folderFilter.equals(prefs.getString("parent:" + t.uri, "")))
                || (libraryFilter.equals("favorites")
                    && !prefs.getBoolean("favorite:" + t.uri, false))
                || (libraryFilter.equals("recent") && !prefs.contains("played:" + t.uri))
                || !t.name.toLowerCase(Locale.ROOT).contains(searchQuery.toLowerCase(Locale.ROOT)));
    if (libraryFilter.equals("recent"))
      items.sort(
          (a, b) ->
              Long.compare(
                  prefs.getLong("played:" + b.uri, 0), prefs.getLong("played:" + a.uri, 0)));
    return items;
  }

  void openLibraryTrack(Track t) {
    saveLibraryLocation();
    ArrayList<Track> items = displayedLibrary();
    boolean present = false;
    for (Track item : items) if (item.uri.equals(t.uri)) present = true;
    if (!present) items.add(0, t);
    remember(t);
    s.loadList(items, t);
    showPlayer();
  }

  ArrayList<Track> library() {
    ArrayList<Track> a = new ArrayList<>();
    try {
      org.json.JSONArray j = new org.json.JSONArray(prefs.getString("library", "[]"));
      for (int i = 0; i < j.length(); i++) {
        var o = j.getJSONObject(i);
        a.add(new Track(Uri.parse(o.getString("u")), o.getString("n")));
      }
    } catch (Exception e) {
    }
    return a;
  }

  void clearTrackReferences(Track track) {
    String uri = track.uri.toString();
    var edit =
        prefs.edit().remove("favorite:" + uri).remove("played:" + uri).remove("parent:" + uri);
    if (uri.equals(prefs.getString("lastUri", ""))) edit.remove("lastUri").remove("lastName");
    edit.apply();
    getSharedPreferences("resume", 0).edit().remove(uri).apply();
    if (s != null) {
      Track current = s.track();
      s.queue.removeIf(t -> t.uri.equals(track.uri));
      s.index = s.queue.indexOf(current);
      if (current != null && current.uri.equals(track.uri)) s.stopPlayback();
    }
  }

  void removeFromLibrary(Track track) {
    ArrayList<Track> saved = library();
    saved.removeIf(t -> t.uri.equals(track.uri));
    saveLibrary(saved);
    clearTrackReferences(track);
    if ("library".equals(screen)) showLibrary();
  }

  volatile boolean checkingLibrary;
  long lastLibraryCheck;

  void refreshMissingFiles() {
    if (checkingLibrary) return;
    checkingLibrary = true;
    lastLibraryCheck = android.os.SystemClock.elapsedRealtime();
    new Thread(
            () -> {
              ArrayList<Track> missing = new ArrayList<>();
              for (Track track : library()) {
                try (android.os.ParcelFileDescriptor fd =
                    getContentResolver().openFileDescriptor(track.uri, "r")) {
                  if (fd == null) continue;
                } catch (java.io.FileNotFoundException missingFile) {
                  if (isDefinitelyMissing(track)) missing.add(track);
                } catch (Exception inaccessible) {
                  /* A temporary provider or permission failure is not evidence of deletion. */
                }
              }
              runOnUiThread(
                  () -> {
                    for (Track track : missing) removeFromLibrary(track);
                    checkingLibrary = false;
                  });
            })
        .start();
  }

  boolean isDefinitelyMissing(Track track) {
    if ("file".equals(track.uri.getScheme()))
      return !new java.io.File(track.uri.getPath()).exists();
    if (!"content".equals(track.uri.getScheme())) return false;
    try (android.database.Cursor cursor =
        getContentResolver()
            .query(
                track.uri,
                new String[] {android.provider.OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null)) {
      return cursor != null && cursor.getCount() == 0;
    } catch (Exception providerUnavailable) {
      return false;
    }
  }

  void saveLibrary(ArrayList<Track> a) {
    org.json.JSONArray j = new org.json.JSONArray();
    for (Track t : a)
      try {
        j.put(new org.json.JSONObject().put("u", t.uri.toString()).put("n", t.name));
      } catch (Exception e) {
      }
    prefs.edit().putString("library", j.toString()).apply();
  }

  void remember(Track t) {
    var a = library();
    for (Track x : a) if (x.uri.equals(t.uri)) return;
    a.add(t);
    saveLibrary(a);
  }

  void addTrack(LinearLayout list, Track t, boolean q) {
    LinearLayout r = row();
    r.setPadding(0, dp(12), 0, dp(12));
    ImageView image = thumb(t);
    r.addView(image, new LinearLayout.LayoutParams(dp(78), dp(55)));
    LinearLayout info = col();
    info.setGravity(Gravity.CENTER_VERTICAL);
    info.setPadding(dp(12), 0, dp(8), 0);
    TextView n = text(t.name, 14, ink);
    oneLine(n);
    info.addView(n);
    TextView meta =
        text(t.audio ? tr("ملف صوتي", "Audio file") : tr("ملف فيديو", "Video file"), 11, muted);
    meta.setTextDirection(View.TEXT_DIRECTION_LTR);
    info.addView(meta);
    r.addView(info, new LinearLayout.LayoutParams(0, dp(56), 1));
    r.addView(iconButton("more", tr("خيارات الملف", "File options"), () -> trackMenu(t, q)));
    r.setOnClickListener(
        v -> {
          if (q) open(t, true);
          else openLibraryTrack(t);
        });
    list.addView(r);
    rule(list);
    loadThumb(t, image, meta);
  }

  void trackMenu(Track t, boolean q) {
    new SheetBuilder(this)
        .setTitle(t.name)
        .setItems(
            new String[] {
              tr("تشغيل", "Play"),
              tr("أضف لقائمة التشغيل", "Add to queue"),
              tr("إزالة من القائمة فقط", "Remove from list only"),
              tr("نقل لأعلى", "Move up"),
              tr("نقل لأسفل", "Move down"),
              tr("إضافة أو إزالة من المفضلة", "Toggle favorite")
            },
            (d, n) -> {
              if (n == 5) {
                prefs
                    .edit()
                    .putBoolean("favorite:" + t.uri, !prefs.getBoolean("favorite:" + t.uri, false))
                    .apply();
                showLibrary();
              } else if (n == 0) {
                if (q) open(t, true);
                else openLibraryTrack(t);
              } else if (n == 1) {
                s.queue.add(t);
                toast(tr("اتضاف للقائمة", "Added to queue"));
              } else {
                ArrayList<Track> a = q ? s.queue : library();
                int i = -1;
                for (int k = 0; k < a.size(); k++) if (a.get(k).uri.equals(t.uri)) i = k;
                Track current = s.track();
                if (i >= 0) {
                  if (n == 2) {
                    if (!q) {
                      removeFromLibrary(t);
                      return;
                    }
                    if (q && i == s.index) s.stopPlayback();
                    a.remove(i);
                  } else {
                    int z = n == 3 ? i - 1 : i + 1;
                    if (z >= 0 && z < a.size()) Collections.swap(a, i, z);
                  }
                }
                if (q) {
                  s.index = a.indexOf(current);
                  queue();
                } else {
                  saveLibrary(a);
                  showLibrary();
                }
              }
            })
        .show();
  }

  void queue() {
    LinearLayout l = col();
    pad(l, 16);
    for (Track t : new ArrayList<>(s.queue)) addTrack(l, t, true);
    LinearLayout tools = row();
    tools.addView(
        button(
            tr("تكرار", "Repeat") + (s.repeat ? " ✓" : ""),
            () -> {
              s.repeat = !s.repeat;
              toast(s.repeat ? "ON" : "OFF");
            }));
    tools.addView(
        button(
            tr("عشوائي", "Shuffle") + (s.shuffle ? " ✓" : ""),
            () -> {
              s.shuffle = !s.shuffle;
              toast(s.shuffle ? "ON" : "OFF");
            }));
    tools.addView(
        button(
            tr("بالاسم", "By name"),
            () -> {
              Track t = s.track();
              s.queue.sort(Comparator.comparing(a -> a.name.toLowerCase()));
              s.index = s.queue.indexOf(t);
              toast("✓");
            }));
    l.addView(tools);
    ScrollView sc = new ScrollView(this);
    sc.addView(l);
    new SheetBuilder(this)
        .setTitle(tr("قائمة التشغيل", "Playback queue"))
        .setView(sc)
        .setPositiveButton(tr("تم", "Done"), null)
        .show();
  }

  void pick(int code, String type) {
    Intent i =
        new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(type)
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    if (code == 100) {
      i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
      i.putExtra(
          Intent.EXTRA_MIME_TYPES,
          new String[] {"video/*", "audio/*", "application/ogg", "application/octet-stream"});
    }
    startActivityForResult(i, code);
  }

  void folder() {
    startActivityForResult(
        new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),
        102);
  }

  String name(Uri u) {
    try (android.database.Cursor c =
        getContentResolver()
            .query(u, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (c != null && c.moveToFirst()) return c.getString(0);
    } catch (Exception e) {
    }
    return u.getLastPathSegment() == null ? "Media" : u.getLastPathSegment();
  }

  void grant(Uri u, int flags) {
    try {
      getContentResolver()
          .takePersistableUriPermission(u, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION);
    } catch (Exception e) {
    }
  }

  public void onActivityResult(int req, int res, Intent data) {
    super.onActivityResult(req, res, data);
    if (res != RESULT_OK || data == null || s == null) return;
    if (req == 102) {
      Uri tree = data.getData();
      grant(tree, data.getFlags());
      new Thread(
              () -> {
                var a = library();
                try {
                  Uri children =
                      DocumentsContract.buildChildDocumentsUriUsingTree(
                          tree, DocumentsContract.getTreeDocumentId(tree));
                  try (android.database.Cursor c =
                      getContentResolver()
                          .query(
                              children,
                              new String[] {
                                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                                DocumentsContract.Document.COLUMN_MIME_TYPE
                              },
                              null,
                              null,
                              null)) {
                    if (c != null)
                      while (c.moveToNext()) {
                        String mime = c.getString(2);
                        if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                          Uri u = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0));
                          boolean has = false;
                          for (Track t : a) if (t.uri.equals(u)) has = true;
                          if (!has) a.add(new Track(u, c.getString(1)));
                          prefs.edit().putString("parent:" + u, tree.toString()).apply();
                        }
                      }
                  }
                  saveLibrary(a);
                  runOnUiThread(this::showLibrary);
                } catch (Exception e) {
                  runOnUiThread(() -> toast(tr("تعذر قراءة المجلد", "Could not read folder")));
                }
              })
          .start();
      return;
    }
    if (req == 101) {
      Uri u = data.getData();
      grant(u, data.getFlags());
      if (u != null) loadSubtitle(u);
      return;
    }
    ArrayList<Uri> uris = new ArrayList<>();
    if (data.getClipData() != null)
      for (int k = 0; k < data.getClipData().getItemCount(); k++)
        uris.add(data.getClipData().getItemAt(k).getUri());
    else if (data.getData() != null) uris.add(data.getData());
    boolean first = true;
    for (Uri u : uris) {
      grant(u, data.getFlags());
      Track t = new Track(u, name(u));
      remember(t);
      if (first) {
        s.load(t, false);
        first = false;
      } else s.queue.add(t);
    }
    showPlayer();
  }

  void loadSubtitle(Uri u) {
    new Thread(
            () -> {
              try {
                java.io.File f =
                    new java.io.File(
                        getFilesDir(),
                        "selected-subtitle."
                            + (name(u).contains(".")
                                ? name(u)
                                    .substring(name(u).lastIndexOf('.') + 1)
                                    .replaceAll("[^a-zA-Z0-9]", "")
                                : "srt"));
                try (java.io.InputStream in = getContentResolver().openInputStream(u);
                    java.io.OutputStream out = new java.io.FileOutputStream(f)) {
                  if (in == null) throw new java.io.IOException();
                  byte[] b = new byte[8192];
                  int n, total = 0;
                  while ((n = in.read(b)) > 0) {
                    total += n;
                    if (total > 16 * 1024 * 1024)
                      throw new java.io.IOException("Subtitle too large");
                    out.write(b, 0, n);
                  }
                }
                String encoding = prefs.getString("subencoding", "UTF-8");
                if (!encoding.equals("UTF-8")) {
                  byte[] raw = java.nio.file.Files.readAllBytes(f.toPath());
                  java.nio.file.Files.write(
                      f.toPath(),
                      new String(raw, java.nio.charset.Charset.forName(encoding))
                          .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                runOnUiThread(
                    () ->
                        toast(
                            s.player.addSlave(0, Uri.fromFile(f), true)
                                ? tr("تم تحميل الترجمة", "Subtitles loaded")
                                : tr("تعذر تحميل الترجمة", "Could not load subtitles")));
              } catch (Exception e) {
                runOnUiThread(() -> toast(tr("تعذر قراءة الترجمة", "Could not read subtitles")));
              }
            })
        .start();
  }

  boolean handle(Intent i) {
    if (i == null || s == null) return false;
    Uri u = i.getData();
    if (Intent.ACTION_SEND.equals(i.getAction())) u = i.getParcelableExtra(Intent.EXTRA_STREAM);
    if (u == null) return false;
    grant(u, i.getFlags());
    open(new Track(u, name(u)), false);
    setIntent(new Intent(this, MainActivity.class));
    return true;
  }

  public void onNewIntent(Intent i) {
    super.onNewIntent(i);
    setIntent(i);
    if (!handle(i) && i.getBooleanExtra("openUpdates", false)) settings("updates");
  }

  void open(Track t, boolean append) {
    if (!append) {
      mediaTab = t.audio ? "audio" : "video";
      libraryFilter = "all";
      searchQuery = "";
      folderFilter = "";
      saveLibraryLocation();
    }
    remember(t);
    s.load(t, append);
    if (prefs.getBoolean("siblings", false)) {
      String parent = prefs.getString("parent:" + t.uri, "");
      String ext =
          t.name.contains(".")
              ? t.name.substring(t.name.lastIndexOf('.')).toLowerCase(Locale.ROOT)
              : "";
      if (!parent.isEmpty() && !ext.isEmpty())
        for (Track other : library())
          if (!other.uri.equals(t.uri)
              && parent.equals(prefs.getString("parent:" + other.uri, ""))
              && other.name.toLowerCase(Locale.ROOT).endsWith(ext)) s.queue.add(other);
    }
    showPlayer();
  }

  void detach() {
    if (s != null && attached)
      try {
        s.player.detachViews();
      } catch (Exception e) {
      }
    attached = false;
    surface = null;
  }

  void showPlayer() {
    if (s == null || s.track() == null) {
      showLibrary();
      return;
    }
    screen = "player";
    fullMode = false;
    video = !s.track().audio;
    shownTrack = s.track().uri.toString();
    base("");
    topBar.setVisibility(View.GONE);
    if (video) {
      stage = new VideoGestureFrame();
      stage.setBackgroundColor(Color.BLACK);
      page.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));
      surface = new PlayerView(this);
      stage.addView(surface, new FrameLayout.LayoutParams(-1, -1));
      try {
        s.player.attachViews(surface, null, true, false);
        attached = true;
        s.player.setVideoTrackEnabled(true);
        s.player.setVideoScale(PlayerEngine.ScaleType.SURFACE_BEST_FIT);
      } catch (Exception e) {
        toast(tr("تعذر عرض الفيديو", "Could not show video"));
      }

      getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      LinearLayout videoTop = row();
      videoTop.setPadding(dp(8), dp(4), dp(8), dp(4));
      videoTop.setBackground(
          new GradientDrawable(
              GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xcc000000, 0x00000000}));
      videoTop.addView(iconButton("back", tr("رجوع", "Back"), this::showLibrary));
      title = text(s.track().name, 14, Color.WHITE);
      oneLine(title);
      videoTop.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
      title.setGravity(Gravity.CENTER_VERTICAL);
      LinearLayout upper = col();
      upper.addView(videoTop);
      LinearLayout secondary = row();
      secondary.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
      secondary.setPadding(dp(8), 0, dp(8), 0);
      addVideoTool(secondary, "ratio", tr("الأبعاد", "Aspect"), this::aspect);
      addVideoTool(secondary, "camera", tr("لقطة", "Snapshot"), this::snapshot);
      addVideoTool(secondary, "pip", tr("صورة مصغرة", "PiP"), this::pip);
      addVideoTool(secondary, "queue", tr("القائمة", "Queue"), this::queue);
      addVideoTool(secondary, "more", tr("أدوات", "Tools"), this::videoOptions);
      upper.addView(secondary);
      stage.addView(upper, new FrameLayout.LayoutParams(-1, dp(118), Gravity.TOP));
      topBar = upper;
      chrome = col();
      chrome.setPadding(dp(12), dp(12), dp(12), dp(6));
      chrome.setBackground(
          new GradientDrawable(
              GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x00000000, 0xe6000000}));
      videoLevelPanel = col();
      videoLevelPanel.setVisibility(View.GONE);
      LinearLayout.LayoutParams levelParams = new LinearLayout.LayoutParams(-1, -2);
      levelParams.bottomMargin = dp(18);
      chrome.addView(videoLevelPanel, levelParams);
      addTimeline(chrome);
      LinearLayout tools = row();
      tools.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
      addVideoTool(tools, "sub", tr("الترجمة", "Subtitles"), this::subtitles);
      addVideoTool(tools, "volume", tr("الصوت", "Volume"), () -> directVideoLevel(true));
      View audioControl = tools.getChildAt(tools.getChildCount() - 1);
      audioControl.setOnLongClickListener(
          v -> {
            if (!locked) audioTracks();
            return true;
          });
      addVideoTool(tools, "speed", tr("السرعة", "Speed"), this::speed);

      addVideoTool(tools, "sun", tr("الإضاءة", "Brightness"), () -> directVideoLevel(false));
      addVideoTool(tools, "rotate", tr("تدوير", "Rotate"), this::fullscreen);
      addVideoTool(tools, "lock", tr("قفل", "Lock"), this::lockVideo);
      chrome.addView(tools);
      stage.addView(chrome, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
      LinearLayout center = row();
      center.setGravity(Gravity.CENTER);
      center.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
      TextView back =
          iconButton("rewind", tr("رجوع 10 ثوانٍ", "Back 10 seconds"), () -> s.seek(-10000));
      back.setTextColor(Color.WHITE);
      center.addView(
          iconButton("previous", tr("السابق", "Previous"), () -> s.next(-1)),
          new LinearLayout.LayoutParams(dp(44), dp(56)));
      center.addView(back, new LinearLayout.LayoutParams(dp(48), dp(56)));
      play = iconButton("play", tr("تشغيل أو إيقاف", "Play or pause"), () -> s.toggle());
      play.setBackground(shape(0x66000000, 40));
      LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(72), dp(72));
      cp.setMargins(dp(8), 0, dp(8), 0);
      center.addView(play, cp);
      TextView forward =
          iconButton("forward", tr("تقديم 10 ثوانٍ", "Forward 10 seconds"), () -> s.seek(10000));
      forward.setTextColor(Color.WHITE);
      center.addView(forward, new LinearLayout.LayoutParams(dp(48), dp(56)));
      center.addView(
          iconButton("next", tr("التالي", "Next"), () -> s.next(1)),
          new LinearLayout.LayoutParams(dp(44), dp(56)));
      stage.addView(center, new FrameLayout.LayoutParams(-1, dp(80), Gravity.CENTER));
      center.setTag("transport");
      preview = new ImageView(this);
      preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
      preview.setBackground(shape(panel, 8));
      preview.setVisibility(View.GONE);
      FrameLayout.LayoutParams pp =
          new FrameLayout.LayoutParams(
              dp(160), dp(100), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
      pp.bottomMargin = dp(130);
      stage.addView(preview, pp);
      gestureIndicator = text("", 16, Color.WHITE);
      gestureIndicator.setGravity(Gravity.CENTER);
      gestureIndicator.setPadding(dp(12), dp(10), dp(12), dp(10));
      gestureIndicator.setBackground(shape(0xdd1b2430, 14));
      gestureIndicator.setVisibility(View.GONE);
      FrameLayout.LayoutParams indicatorParams =
          new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
      indicatorParams.topMargin = dp(130);
      stage.addView(gestureIndicator, indicatorParams);
    } else {
      topBar.setVisibility(View.VISIBLE);
      topBar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
      ((TextView) topBar.getChildAt(0)).setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
      LinearLayout heading = row();
      heading.setPadding(dp(20), dp(4), dp(20), dp(8));
      TextView now = text(tr("بيشتغل دلوقتي", "Now playing"), 23, ink);
      now.setTypeface(font, Typeface.BOLD);
      heading.addView(now, new LinearLayout.LayoutParams(0, -2, 1));
      heading.addView(iconButton("queue", tr("قائمة التشغيل", "Queue"), this::queue));
      page.addView(heading);
      ScrollView sc = scroll();
      LinearLayout audio = col();
      audio.setPadding(dp(20), dp(4), dp(20), dp(20));
      sc.setFillViewport(true);
      sc.addView(audio);
      stage = new FrameLayout(this);
      int side = Math.min(getResources().getDisplayMetrics().widthPixels - dp(104), dp(320));
      stage.setBackground(shape(panel, 16));
      rings = new VisualizerView(this, null);
      stage.addView(rings, new FrameLayout.LayoutParams(-1, -1));
      LinearLayout.LayoutParams art = new LinearLayout.LayoutParams(side, side);
      art.gravity = Gravity.CENTER_HORIZONTAL;
      art.topMargin = dp(12);
      art.bottomMargin = dp(18);
      audio.addView(stage, art);
      Track track = s.track();
      new Thread(
              () -> {
                MediaMetadataRetriever mm = new MediaMetadataRetriever();
                try {
                  mm.setDataSource(this, track.uri);
                  byte[] data = mm.getEmbeddedPicture();
                  if (data != null) cover = BitmapFactory.decodeByteArray(data, 0, data.length);
                  runOnUiThread(
                      () -> {
                        if (rings != null) rings.invalidate();
                      });
                } catch (Exception ignored) {
                } finally {
                  try {
                    mm.release();
                  } catch (Exception ignored) {
                  }
                }
              })
          .start();
      title = text(displayName(s.track().name), 22, ink);
      title.setTypeface(font, Typeface.BOLD);
      title.setGravity(Gravity.CENTER);
      oneLine(title);
      title.setGravity(Gravity.CENTER);
      audio.addView(title);
      TextView sub = text(tr("ملف صوتي من مكتبتك", "Audio from your library"), 13, muted);
      sub.setGravity(Gravity.CENTER);
      audio.addView(sub);
      spacer(audio, 12);
      chrome = col();
      audio.addView(chrome);
      addTimeline(chrome);
      LinearLayout controls = row();
      controls.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
      controls.setGravity(Gravity.CENTER);
      controls.addView(
          iconButton(
              "repeat",
              tr("تكرار", "Repeat"),
              () -> {
                s.repeat = !s.repeat;
                toast(
                    s.repeat
                        ? tr("التكرار مفعّل", "Repeat on")
                        : tr("التكرار متوقف", "Repeat off"));
              }),
          new LinearLayout.LayoutParams(0, dp(60), 1));
      controls.addView(
          iconButton("previous", tr("السابق", "Previous"), () -> s.next(-1)),
          new LinearLayout.LayoutParams(0, dp(60), 1));
      play = iconButton("play", tr("تشغيل أو إيقاف", "Play or pause"), () -> s.toggle());
      controls.addView(play, new LinearLayout.LayoutParams(dp(76), dp(68)));
      controls.addView(
          iconButton("next", tr("التالي", "Next"), () -> s.next(1)),
          new LinearLayout.LayoutParams(0, dp(60), 1));
      controls.addView(
          iconButton(
              "shuffle",
              tr("عشوائي", "Shuffle"),
              () -> {
                s.shuffle = !s.shuffle;
                toast(
                    s.shuffle
                        ? tr("العشوائي مفعّل", "Shuffle on")
                        : tr("العشوائي متوقف", "Shuffle off"));
              }),
          new LinearLayout.LayoutParams(0, dp(60), 1));
      chrome.addView(controls);
      LinearLayout tools = row();
      tools.setGravity(Gravity.CENTER);
      tools.addView(
          iconButton("gear", tr("إعدادات الصوت", "Audio settings"), () -> settings("audio")),
          new LinearLayout.LayoutParams(0, dp(54), 1));
      tools.addView(
          iconButton("speaker-waves", tr("الصوت", "Volume"), this::audioVolume),
          new LinearLayout.LayoutParams(0, dp(54), 1));
      TextView rate = button("1×", this::speed);
      tools.addView(rate, new LinearLayout.LayoutParams(0, dp(54), 1));
      chrome.addView(tools);
      spacer(audio, 12);
      rule(audio);
      spacer(audio, 12);
      TextView next = text(tr("بعد كده", "Up next"), 18, ink);
      audio.addView(next);
      Track upcoming = s.index + 1 < s.queue.size() ? s.queue.get(s.index + 1) : null;
      TextView nextName =
          text(
              upcoming == null
                  ? tr("أضف ملفات لقائمة التشغيل", "Add files to your queue")
                  : upcoming.name,
              13,
              muted);
      oneLine(nextName);
      audio.addView(nextName);
      audio.setMinimumHeight(0);
    }
    refresh();
    if (video) h.postDelayed(hideVideoControls, 3500);
  }

  void addTimeline(LinearLayout container) {
    seek = new SeekBar(this);
    styleSeek(seek);
    seek.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    seek.setPadding(dp(8), dp(10), dp(8), dp(10));
    seek.setMax(1000);
    seek.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
    seek.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));
    container.addView(seek, new LinearLayout.LayoutParams(-1, dp(30)));
    LinearLayout times = row();
    times.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    time = text("00:00", 11, muted);
    endTime = text("00:00", 11, muted);
    time.setTextDirection(View.TEXT_DIRECTION_LTR);
    endTime.setTextDirection(View.TEXT_DIRECTION_LTR);
    endTime.setGravity(Gravity.RIGHT);
    times.addView(time, new LinearLayout.LayoutParams(0, dp(24), 1));
    times.addView(endTime, new LinearLayout.LayoutParams(0, dp(24), 1));
    container.addView(times);
    seek.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar b) {
            drag = true;
          }

          public void onStopTrackingTouch(SeekBar b) {
            s.player.setTime(s.player.getLength() * b.getProgress() / 1000);
            drag = false;
            if (preview != null) preview.setVisibility(View.GONE);
          }

          public void onProgressChanged(SeekBar b, int value, boolean user) {
            if (user) {
              long at = s.player.getLength() * value / 1000;
              time.setText(format(at));
              thumbnail(at);
            }
          }
        });
  }

  void addVideoTool(LinearLayout row, String key, String label, Runnable action) {
    LinearLayout cell = col();
    cell.setGravity(Gravity.CENTER);
    TextView icon = iconButton(key, label, action);
    icon.setClickable(false);
    cell.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(36)));
    TextView text = text(label, 10, ink);
    text.setGravity(Gravity.CENTER);
    cell.addView(text);
    cell.setMinimumHeight(dp(52));
    cell.setContentDescription(label);
    if (key.equals("volume") || key.equals("sun") || key.equals("speed"))
      cell.setTag("level-toggle");
    cell.setOnClickListener(
        v -> {
          if (!locked) action.run();
        });
    row.addView(cell, new LinearLayout.LayoutParams(0, dp(52), 1));
  }

  void lockVideo() {
    locked = true;
    setVideoControls(false);
    toast(tr("اضغط مطولًا على الفيديو لفك القفل", "Long press the video to unlock"));
  }

  void aspect() {
    new SheetBuilder(this)
        .setTitle(tr("أبعاد الصورة", "Aspect ratio"))
        .setItems(
            new String[] {
              tr("ملاءمة الشاشة", "Fit"),
              tr("تكبير لملء الشاشة", "Zoom to fill"),
              tr("تمديد لملء الشاشة", "Stretch to fill")
            },
            (d, k) -> {
              if (s.player.view != null)
                s.player.view.setResizeMode(
                    k == 0
                        ? androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        : k == 1
                            ? androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            : androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL);
            })
        .show();
  }

  void videoOptions() {
    new SheetBuilder(this)
        .setTitle(tr("أدوات الفيديو", "Video tools"))
        .setItems(
            new String[] {
              tr("قائمة التشغيل", "Queue"),
              tr("مؤقت الإيقاف", "Sleep timer"),
              tr("تكرار A-B", "A-B repeat"),
              tr("معادل الصوت", "Audio equalizer"),
              tr("الصوت والإضاءة", "Volume and brightness"),
              tr("إعدادات الفيديو", "Video settings")
            },
            (d, n) -> {
              if (n == 0) queue();
              if (n == 1) sleepTimer();
              if (n == 2) abRepeat();
              if (n == 3) settings("audio");
              if (n == 4) brightness();
              if (n == 5) settings("picture");
            })
        .show();
  }

  void sleepTimer() {
    new SheetBuilder(this)
        .setTitle(tr("مؤقت الإيقاف", "Sleep timer"))
        .setItems(
            new String[] {
              tr("إيقاف المؤقت", "Turn timer off"),
              tr("بعد 15 دقيقة", "15 minutes"),
              tr("بعد 30 دقيقة", "30 minutes"),
              tr("بعد ساعة", "1 hour")
            },
            (d, n) -> {
              s.sleepUntil =
                  n == 0
                      ? 0
                      : SystemClock.elapsedRealtime() + new long[] {0, 900000, 1800000, 3600000}[n];
              toast(n == 0 ? tr("تم إيقاف المؤقت", "Timer off") : tr("المؤقت مفعّل", "Timer set"));
            })
        .show();
  }

  void abRepeat() {
    new SheetBuilder(this)
        .setTitle(tr("تكرار A-B", "A-B repeat"))
        .setItems(
            new String[] {
              tr("حدد بداية A هنا", "Set A here"),
              tr("حدد نهاية B هنا", "Set B here"),
              tr("إلغاء التكرار A-B", "Clear A-B repeat")
            },
            (d, n) -> {
              if (n == 0) {
                s.repeatA = s.player.getTime();
                s.repeatB = -1;
              }
              if (n == 1) {
                if (s.repeatA < 0 || s.player.getTime() <= s.repeatA + 500) {
                  toast(
                      tr(
                          "حدد A أولًا، ثم تقدم نصف ثانية على الأقل",
                          "Set A first, then advance at least half a second"));
                  return;
                }
                s.repeatB = s.player.getTime();
              }
              if (n == 2) {
                s.repeatA = -1;
                s.repeatB = -1;
              }
              toast(n == 2 ? tr("تم الإلغاء", "Cleared") : tr("تم تحديد النقطة", "Point set"));
            })
        .show();
  }

  final Runnable hideVideoControls =
      () -> {
        if ("player".equals(screen)
            && video
            && s != null
            && s.player.isPlaying()
            && !locked
            && this.activeSheet == null) setVideoControls(false);
      };

  void setVideoControls(boolean visible) {
    h.removeCallbacks(hideVideoControls);
    if (visible)
      h.postDelayed(
          hideVideoControls,
          videoLevelPanel != null && videoLevelPanel.getVisibility() == View.VISIBLE ? 6500 : 3500);
    if (!video || stage == null) return;
    int visibility = visible ? View.VISIBLE : View.GONE;
    chrome.setVisibility(visibility);
    topBar.setVisibility(visibility);
    for (int k = 0; k < stage.getChildCount(); k++)
      if ("transport".equals(stage.getChildAt(k).getTag()))
        stage.getChildAt(k).setVisibility(visibility);
  }

  void refresh() {
    if (resumed && s != null && android.os.SystemClock.elapsedRealtime() - lastLibraryCheck > 30000)
      refreshMissingFiles();
    if ("updates".equals(screen)) refreshUpdateCard();
    if (s == null || s.player == null) return;
    if ("player".equals(screen) && seek != null) {
      if (s.track() != null && !s.track().uri.toString().equals(shownTrack)) {
        showPlayer();
        return;
      }
      setPlayIcon();
      if (s.track() != null) title.setText(video ? s.track().name : displayName(s.track().name));
      if (!drag) {
        seek.setProgress(
            s.player.getLength() > 0
                ? (int) (s.player.getTime() * 1000 / s.player.getLength())
                : 0);
        time.setText(format(s.player.getTime()));
        endTime.setText(format(s.player.getLength()));
      }
      if (!s.error.isEmpty()) {
        toast(s.error);
        s.error = "";
      }
    }
    if (miniPlay != null) {
      PlayerIcon d =
          new PlayerIcon(s.player.isPlaying() ? "pause" : "play", video ? Color.WHITE : ink);
      d.setBounds(0, 0, dp(23), dp(23));
      if (miniPlay instanceof IconControl) {
        ((IconControl) miniPlay).key = s.player.isPlaying() ? "pause" : "play";
        miniPlay.invalidate();
      }
    }
    if (rings != null) rings.invalidate();
  }

  String format(long ms) {
    long z = Math.max(0, ms) / 1000;
    return z >= 3600
        ? String.format(Locale.US, "%d:%02d:%02d", z / 3600, z / 60 % 60, z % 60)
        : String.format(Locale.US, "%02d:%02d", z / 60, z % 60);
  }

  final Runnable ticker =
      new Runnable() {
        public void run() {
          refresh();
          h.postDelayed(this, 400);
        }
      };

  final Runnable hideGestureIndicator =
      () -> {
        if (gestureIndicator != null) gestureIndicator.setVisibility(View.GONE);
      };

  void showGestureLevel(boolean volume, int percent) {
    if (gestureIndicator == null) return;
    gestureIndicator.setText(
        tr(volume ? "الصوت" : "الإضاءة", volume ? "Volume" : "Brightness") + " " + percent + "%");
    gestureIndicator.setVisibility(View.VISIBLE);
    h.removeCallbacks(hideGestureIndicator);
    h.postDelayed(hideGestureIndicator, 1300);
  }

  void directVideoLevel(boolean volume) {
    if (videoLevelPanel == null) return;
    if (videoLevelPanel.getVisibility() == View.VISIBLE
        && videoPanelKind.equals(volume ? "volume" : "brightness")) {
      videoLevelPanel.setVisibility(View.GONE);
      setVideoControls(true);
      return;
    }
    videoLevelVolume = volume;
    videoPanelKind = volume ? "volume" : "brightness";
    if (gestureIndicator != null) gestureIndicator.setVisibility(View.GONE);
    setVideoControls(true);
    videoLevelPanel.removeAllViews();
    videoLevelPanel.setVisibility(View.VISIBLE);
    if (stage.getWidth() > stage.getHeight())
      for (int i = 0; i < stage.getChildCount(); i++)
        if ("transport".equals(stage.getChildAt(i).getTag()))
          stage.getChildAt(i).setVisibility(View.GONE);
    videoLevelPanel.setPadding(dp(10), dp(2), dp(10), dp(2));
    videoLevelPanel.setBackground(shape(0xdd1b2430, 12));
    AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
    int max = volume ? audio.getStreamMaxVolume(3) : 100;
    float current = getWindow().getAttributes().screenBrightness;
    if (current < 0) current = systemBrightness();
    int value = volume ? audio.getStreamVolume(3) : Math.round(current * 100);
    LinearLayout heading = row();
    TextView label =
        text(
            tr(volume ? "الصوت" : "الإضاءة", volume ? "Volume" : "Brightness")
                + " "
                + Math.round(value * 100f / max)
                + "%",
            12,
            Color.WHITE);
    heading.addView(label, new LinearLayout.LayoutParams(0, dp(30), 1));
    videoLevelPanel.addView(heading);
    SeekBar slider = new SeekBar(this);
    slider.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    styleSeek(slider);
    slider.setMax(max);
    slider.setProgress(value);
    slider.setContentDescription(
        tr(volume ? "مستوى الصوت" : "مستوى الإضاءة", volume ? "Volume level" : "Brightness level"));
    videoLevelPanel.addView(slider, new LinearLayout.LayoutParams(-1, dp(40)));
    slider.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar bar) {
            h.removeCallbacks(hideVideoControls);
          }

          public void onStopTrackingTouch(SeekBar bar) {
            h.postDelayed(hideVideoControls, 5000);
          }

          public void onProgressChanged(SeekBar bar, int p, boolean user) {
            if (!user) return;
            if (volume) audio.setStreamVolume(3, p, 0);
            else {
              var attributes = getWindow().getAttributes();
              attributes.screenBrightness = Math.max(.02f, p / 100f);
              getWindow().setAttributes(attributes);
            }
            label.setText(
                tr(volume ? "الصوت" : "الإضاءة", volume ? "Volume" : "Brightness")
                    + " "
                    + Math.round(p * 100f / max)
                    + "%");
          }
        });
  }

  float systemBrightness() {
    return Math.max(
        .02f,
        Math.min(
            1f,
            Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128)
                / 255f));
  }

  boolean hitLevelToggle(View view, float x, float y) {
    if ("level-toggle".equals(view.getTag())) {
      Rect rect = new Rect(0, 0, view.getWidth(), view.getHeight());
      stage.offsetDescendantRectToMyCoords(view, rect);
      return rect.contains((int) x, (int) y);
    }
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
        if (hitLevelToggle(((ViewGroup) view).getChildAt(i), x, y)) return true;
    return false;
  }

  final class VideoGestureFrame extends FrameLayout {
    float downX, downY;
    boolean timelineTouch, ownsGesture, controlTouch;

    boolean scaling;
    long lastTap;
    float tapX, tapY;
    final ScaleGestureDetector scaleDetector;

    VideoGestureFrame() {
      super(MainActivity.this);
      scaleDetector =
          new ScaleGestureDetector(
              MainActivity.this,
              new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScaleBegin(ScaleGestureDetector detector) {
                  return !locked;
                }

                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                  if (surface == null) return false;
                  float scale =
                      Math.max(1f, Math.min(3f, surface.getScaleX() * detector.getScaleFactor()));
                  surface.setScaleX(scale);
                  surface.setScaleY(scale);
                  gestureIndicator.setText(tr("تكبير ", "Zoom ") + Math.round(scale * 100) + "%");
                  gestureIndicator.setVisibility(View.VISIBLE);
                  h.removeCallbacks(hideGestureIndicator);
                  h.postDelayed(hideGestureIndicator, 1300);
                  return true;
                }
              });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
      int action = event.getActionMasked();
      if (!locked) scaleDetector.onTouchEvent(event);
      if (event.getPointerCount() > 1) {
        if (!scaling) {
          MotionEvent cancel = MotionEvent.obtain(event);
          cancel.setAction(MotionEvent.ACTION_CANCEL);
          super.dispatchTouchEvent(cancel);
          cancel.recycle();
        }
        scaling = true;
        return true;
      }
      if (scaling) {
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) scaling = false;
        return true;
      }
      if (action == MotionEvent.ACTION_DOWN) {
        downX = event.getX();
        downY = event.getY();
        if (videoLevelPanel != null && videoLevelPanel.getVisibility() == View.VISIBLE) {
          Rect level = new Rect(0, 0, videoLevelPanel.getWidth(), videoLevelPanel.getHeight());
          offsetDescendantRectToMyCoords(videoLevelPanel, level);
          if (!level.contains((int) downX, (int) downY) && !hitLevelToggle(chrome, downX, downY)) {
            videoLevelPanel.setVisibility(View.GONE);
            setVideoControls(true);
          }
        }
        timelineTouch = false;
        controlTouch = false;
        ownsGesture = false;
        for (int i = 0; i < getChildCount(); i++) {
          View child = getChildAt(i);
          if (child.getVisibility() == View.VISIBLE
              && (child == topBar || child == chrome || "transport".equals(child.getTag()))) {
            Rect rect =
                new Rect(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
            if (rect.contains((int) downX, (int) downY)) controlTouch = true;
          }
        }
        if (seek != null && chrome != null && chrome.getVisibility() == View.VISIBLE) {
          Rect rect = new Rect(0, 0, seek.getWidth(), seek.getHeight());
          offsetDescendantRectToMyCoords(seek, rect);
          timelineTouch = rect.contains((int) downX, (int) downY);
          if (videoLevelPanel != null && videoLevelPanel.getVisibility() == View.VISIBLE) {
            Rect level = new Rect(0, 0, videoLevelPanel.getWidth(), videoLevelPanel.getHeight());
            offsetDescendantRectToMyCoords(videoLevelPanel, level);
            timelineTouch |= level.contains((int) downX, (int) downY);
          }
        }
        touch(event);
        getParent().requestDisallowInterceptTouchEvent(true);
        super.dispatchTouchEvent(event);
        return true;
      }
      if (!timelineTouch && !locked && action == MotionEvent.ACTION_MOVE && !ownsGesture) {
        float dx = event.getX() - downX, dy = event.getY() - downY;
        if (Math.abs(dx) > dp(12) || Math.abs(dy) > dp(12)) {
          ownsGesture = true;
          MotionEvent cancel = MotionEvent.obtain(event);
          cancel.setAction(MotionEvent.ACTION_CANCEL);
          super.dispatchTouchEvent(cancel);
          cancel.recycle();
        }
      }
      if (ownsGesture || locked) {
        touch(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
          ownsGesture = false;
        return true;
      }
      boolean handled = super.dispatchTouchEvent(event);
      if (action == MotionEvent.ACTION_UP && !controlTouch && !timelineTouch) {
        long now = SystemClock.uptimeMillis();
        if (lastTap > 0
            && now - lastTap < 300
            && Math.abs(event.getX() - tapX) < dp(60)
            && Math.abs(event.getY() - tapY) < dp(60)) {
          if (event.getX() < getWidth() / 3f) {
            s.seek(-10000);
            gestureIndicator.setText(tr("رجوع 10 ثوانٍ", "Back 10 seconds"));
          } else if (event.getX() > getWidth() * 2 / 3f) {
            s.seek(10000);
            gestureIndicator.setText(tr("تقديم 10 ثوانٍ", "Forward 10 seconds"));
          } else {
            s.toggle();
            gestureIndicator.setText(tr("تشغيل / إيقاف", "Play / pause"));
          }
          gestureIndicator.setVisibility(View.VISIBLE);
          h.postDelayed(hideGestureIndicator, 1300);
          lastTap = 0;
        } else {
          lastTap = now;
          tapX = event.getX();
          tapY = event.getY();
          touch(event);
        }
      }
      return true;
    }
  }

  boolean touch(MotionEvent e) {
    if (locked) {
      if (e.getAction() == 0) gtime = SystemClock.uptimeMillis();
      if (e.getAction() == 1 && SystemClock.uptimeMillis() - gtime > 600) {
        locked = false;
        setVideoControls(true);
      }
      return true;
    }
    if (e.getAction() == 0) {
      gx = e.getX();
      gy = e.getY();
      gtime = s.player.getTime();
      startBright = getWindow().getAttributes().screenBrightness;
      if (startBright < 0) startBright = systemBrightness();
      startVol = ((AudioManager) getSystemService(AUDIO_SERVICE)).getStreamVolume(3);
      gesture = false;
      gestureAxis = 0;
      return true;
    }
    if (e.getAction() == 2) {
      float dx = e.getX() - gx, dy = gy - e.getY();
      if (Math.abs(dx) > dp(12) || Math.abs(dy) > dp(12)) gesture = true;
      if (!gesture) return true;
      if (gestureAxis == 0) gestureAxis = Math.abs(dx) > Math.abs(dy) ? 1 : 2;
      if (gestureAxis == 1) {
        long target = Math.max(0, Math.min(s.player.getLength(), gtime + (long) (dx * 160)));
        s.player.setTime(target);
        gestureIndicator.setText(tr("انتقال: ", "Seek: ") + format(target));
        gestureIndicator.setVisibility(View.VISIBLE);
        h.removeCallbacks(hideGestureIndicator);
        h.postDelayed(hideGestureIndicator, 1300);
      } else if (gx < stage.getWidth() / 2) {
        var a = getWindow().getAttributes();
        a.screenBrightness = Math.max(.02f, Math.min(1, startBright + dy / stage.getHeight()));
        getWindow().setAttributes(a);
        showGestureLevel(false, Math.round(a.screenBrightness * 100));
      } else {
        AudioManager a = (AudioManager) getSystemService(AUDIO_SERVICE);
        a.setStreamVolume(
            3,
            Math.max(
                0,
                Math.min(
                    a.getStreamMaxVolume(3),
                    startVol + (int) (dy / stage.getHeight() * a.getStreamMaxVolume(3)))),
            0);
        showGestureLevel(true, Math.round(a.getStreamVolume(3) * 100f / a.getStreamMaxVolume(3)));
      }
      return true;
    }
    if (e.getAction() == 1 && !gesture) setVideoControls(chrome.getVisibility() != View.VISIBLE);
    return true;
  }

  void fullscreen() {
    fullMode = !fullMode;
    if (fullMode) {
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
      getWindow().getDecorView().setSystemUiVisibility(4 | 2 | 4096);
    } else {
      getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
      getWindow().getDecorView().setSystemUiVisibility(0);
    }
  }

  void thumbnail(long at) {
    if (!prefs.getBoolean("thumbPreview", true)
        || !video
        || preview == null
        || thumbnailBusy
        || s.track() == null) return;
    thumbnailBusy = true;
    Uri uri = s.track().uri;
    new Thread(
            () -> {
              Bitmap bm = null;
              try {
                MediaMetadataRetriever mm = new MediaMetadataRetriever();
                mm.setDataSource(this, uri);
                bm =
                    mm.getScaledFrameAtTime(
                        at * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 200);
                mm.release();
              } catch (Exception e) {
              }
              Bitmap result = bm;
              runOnUiThread(
                  () -> {
                    thumbnailBusy = false;
                    if (result != null && drag && preview != null) {
                      preview.setImageBitmap(result);
                      preview.setVisibility(View.VISIBLE);
                    }
                  });
            })
        .start();
  }

  void pip() {
    if (s == null || s.track() == null || s.track().audio) return;
    if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
      toast(tr("صورة فوق صورة غير متاحة", "PiP unavailable"));
      return;
    }
    try {
      ArrayList<RemoteAction> actions = new ArrayList<>();
      for (String a : new String[] {"prev", "toggle", "next"}) {
        PendingIntent pi =
            PendingIntent.getService(
                this,
                a.hashCode(),
                new Intent(this, PlaybackService.class).setAction(a),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        actions.add(
            new RemoteAction(
                android.graphics.drawable.Icon.createWithResource(
                    this,
                    a.equals("prev")
                        ? android.R.drawable.ic_media_previous
                        : a.equals("next")
                            ? android.R.drawable.ic_media_next
                            : android.R.drawable.ic_media_play),
                a,
                a,
                pi));
      }
      Rect hint = new Rect();
      stage.getGlobalVisibleRect(hint);
      enterPictureInPictureMode(
          new PictureInPictureParams.Builder()
              .setAspectRatio(new android.util.Rational(16, 9))
              .setActions(actions)
              .setSourceRectHint(hint)
              .build());
    } catch (Exception e) {
      toast(tr("فعّل صورة فوق صورة من إعدادات أندرويد", "Enable PiP in Android settings"));
    }
  }

  protected void onUserLeaveHint() {
    super.onUserLeaveHint();
    if ("player".equals(screen)
        && s != null
        && s.player.isPlaying()
        && video
        && prefs.getBoolean("pip", true)) pip();
  }

  public void onPictureInPictureModeChanged(boolean on, Configuration c) {
    super.onPictureInPictureModeChanged(on, c);
    if (on && s != null) s.player.setVideoTrackEnabled(true);
    if (!on && !resumed) {
      KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
      if (!km.isKeyguardLocked() && s != null) s.pause();
    }
    if (page == null || stage == null) return;
    for (int i = 0; i < page.getChildCount(); i++)
      if (page.getChildAt(i) != stage)
        page.getChildAt(i).setVisibility(on ? View.GONE : View.VISIBLE);
    for (int i = 0; i < stage.getChildCount(); i++)
      if (stage.getChildAt(i) != surface)
        stage.getChildAt(i).setVisibility(on ? View.GONE : View.VISIBLE);
    if (preview != null) preview.setVisibility(View.GONE);
    if (!on && video) {
      page.getChildAt(0).setVisibility(View.GONE);
      setVideoControls(!locked);
    }
  }

  protected void onResume() {
    super.onResume();
    resumed = true;
    UpdateEngine.foregroundActivity = new java.lang.ref.WeakReference<>(this);
    if (s != null) refreshMissingFiles();
    if (prefs != null
        && getSharedPreferences("updates", 0).getBoolean("installOnReturn", false)
        && getPackageManager().canRequestPackageInstalls()
        && !getSharedPreferences("updates", 0).getString("ready", "").isEmpty())
      UpdateEngine.installReady(this);
    if (s != null && "player".equals(screen) && video) s.player.setVideoTrackEnabled(true);
  }

  protected void onPause() {
    resumed = false;
    UpdateEngine.foregroundActivity.clear();
    super.onPause();
  }

  protected void onStop() {
    super.onStop();
    if (s != null && !isInPictureInPictureMode()) {
      if (!prefs.getBoolean("background", true)) s.pause();
      else if (video) s.player.setVideoTrackEnabled(false);
    }
  }

  public void onConfigurationChanged(Configuration c) {
    super.onConfigurationChanged(c);
    if (surface != null && s != null) s.player.updateVideoSurfaces();
  }

  public void onBackPressed() {
    if ("player".equals(screen)) {
      if (videoLevelPanel != null && videoLevelPanel.getVisibility() == View.VISIBLE) {
        videoLevelPanel.setVisibility(View.GONE);
        setVideoControls(true);
        return;
      }
      if (fullMode) {
        fullscreen();
        return;
      }
      showLibrary();
      setRequestedOrientation(-1);
    } else if (!"library".equals(screen)) showLibrary();
    else super.onBackPressed();
  }

  protected void onDestroy() {
    h.removeCallbacksAndMessages(null);
    detach();
    if (bound) {
      s.changed = null;
      unbindService(conn);
    }
    super.onDestroy();
  }

  void speed() {
    boolean inline = video && "player".equals(screen) && videoLevelPanel != null;
    if (inline
        && videoLevelPanel.getVisibility() == View.VISIBLE
        && videoPanelKind.equals("speed")) {
      videoLevelPanel.setVisibility(View.GONE);
      setVideoControls(true);
      return;
    }
    LinearLayout content;
    if (inline) {
      videoPanelKind = "speed";
      videoLevelPanel.removeAllViews();
      content = videoLevelPanel;
      content.setVisibility(View.VISIBLE);
      content.setPadding(dp(10), dp(2), dp(10), dp(2));
      content.setBackground(shape(0xdd1b2430, 12));
      setVideoControls(true);
      if (stage.getWidth() > stage.getHeight())
        for (int i = 0; i < stage.getChildCount(); i++)
          if ("transport".equals(stage.getChildAt(i).getTag()))
            stage.getChildAt(i).setVisibility(View.GONE);
    } else {
      content = col();
      pad(content, 20);
    }
    float rate = s.player.getRate();
    TextView label =
        text(tr("السرعة ", "Speed ") + String.format(Locale.US, "%.2fx", rate), 12, Color.WHITE);
    content.addView(label);
    SeekBar bar = new SeekBar(this);
    bar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    bar.setMax(55);
    bar.setProgress(Math.round((rate - .25f) / .05f));
    styleSeek(bar);
    bar.setContentDescription(tr("سرعة التشغيل", "Playback speed"));
    content.addView(bar, new LinearLayout.LayoutParams(-1, dp(40)));
    bar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar b) {
            h.removeCallbacks(hideVideoControls);
          }

          public void onStopTrackingTouch(SeekBar b) {
            if (inline) h.postDelayed(hideVideoControls, 6500);
          }

          public void onProgressChanged(SeekBar b, int p, boolean user) {
            if (!user) return;
            float speed = .25f + p * .05f;
            s.player.setRate(speed);
            prefs.edit().putFloat("speed", speed).apply();
            label.setText(tr("السرعة ", "Speed ") + String.format(Locale.US, "%.2fx", speed));
          }
        });
    LinearLayout presets = row();
    presets.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    for (float r : new float[] {.5f, 1f, 1.5f, 2f}) {
      TextView preset =
          button(
              String.format(Locale.US, "%.1fx", r),
              () -> {
                bar.setProgress(Math.round((r - .25f) / .05f));
                s.player.setRate(r);
                prefs.edit().putFloat("speed", r).apply();
                label.setText(tr("السرعة ", "Speed ") + String.format(Locale.US, "%.2fx", r));
              });
      preset.setPadding(dp(4), 0, dp(4), 0);
      preset.setMinHeight(0);
      preset.setMinimumHeight(0);
      preset.setTextSize(12);
      preset.setGravity(Gravity.CENTER);
      presets.addView(preset, new LinearLayout.LayoutParams(0, dp(36), 1));
    }
    content.addView(presets);
    if (!inline)
      new SheetBuilder(this)
          .setTitle(tr("سرعة التشغيل", "Playback speed"))
          .setView(content)
          .setPositiveButton(tr("تم", "Done"), null)
          .show();
  }

  void slider(
      LinearLayout l, String label, int max, int value, java.util.function.IntConsumer update) {
    TextView v = text(label + ": " + value, 14, ink);
    l.addView(v);
    SeekBar b = new SeekBar(this);
    styleSeek(b);
    b.setMax(max);
    b.setProgress(value);
    l.addView(b);
    b.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar a) {}

          public void onStopTrackingTouch(SeekBar a) {}

          public void onProgressChanged(SeekBar a, int p, boolean u) {
            v.setText(label + ": " + p);
            if (u) update.accept(p);
          }
        });
  }

  void audioVolume() {
    LinearLayout content = col();
    pad(content, 20);
    AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
    int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    int current = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
    TextView value = text(Math.round(current * 100f / Math.max(1, max)) + "%", 14, ink);
    value.setTextDirection(View.TEXT_DIRECTION_LTR);
    value.setGravity(english() ? Gravity.LEFT : Gravity.RIGHT);
    content.addView(value);
    SeekBar bar = new SeekBar(this);
    bar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    bar.setMax(max);
    bar.setProgress(current);
    bar.setContentDescription(tr("مستوى الصوت", "Volume level"));
    styleSeek(bar);
    content.addView(bar, new LinearLayout.LayoutParams(-1, dp(48)));
    bar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar b) {}

          public void onStopTrackingTouch(SeekBar b) {}

          public void onProgressChanged(SeekBar b, int progress, boolean user) {
            if (!user) return;
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
            int actual = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            value.setText(Math.round(actual * 100f / Math.max(1, max)) + "%");
          }
        });
    new SheetBuilder(this).setTitle(tr("الصوت", "Volume")).setView(content).show();
  }

  void brightness() {
    LinearLayout l = col();
    pad(l, 20);
    AudioManager a = (AudioManager) getSystemService(AUDIO_SERVICE);
    slider(
        l,
        tr("الصوت", "Volume"),
        a.getStreamMaxVolume(3),
        a.getStreamVolume(3),
        p -> a.setStreamVolume(3, p, 0));
    slider(
        l,
        tr("الإضاءة", "Brightness"),
        100,
        50,
        p -> {
          var x = getWindow().getAttributes();
          x.screenBrightness = Math.max(.02f, p / 100f);
          getWindow().setAttributes(x);
        });
    new SheetBuilder(this)
        .setTitle(tr("الإضاءة والصوت", "Brightness and volume"))
        .setView(l)
        .setPositiveButton(tr("تم", "Done"), null)
        .show();
  }

  void subtitles() {
    ArrayList<String> a =
        new ArrayList<>(
            Arrays.asList(
                tr("فتح ملف ترجمة", "Open subtitle file"),
                tr("إخفاء الترجمة", "Hide subtitles"),
                tr("توقيت الترجمة", "Subtitle timing"),
                tr("إعدادات الترجمة", "Subtitle settings")));
    ArrayList<Integer> ids = new ArrayList<>();
    var tracks = s.player.getSpuTracks();
    if (tracks != null)
      for (var t : tracks)
        if (t.id >= 0) {
          a.add(t.name);
          ids.add(t.id);
        }
    new SheetBuilder(this)
        .setTitle(tr("الترجمة", "Subtitles"))
        .setItems(
            a.toArray(new String[0]),
            (d, n) -> {
              if (n == 0) pick(101, "*/*");
              else if (n == 1) s.player.setSpuTrack(-1);
              else if (n == 2) delay();
              else if (n == 3) settings("subtitles");
              else s.player.setSpuTrack(ids.get(n - 4));
            })
        .show();
  }

  void delay() {
    if (s.player.originalSub == null) {
      toast(
          tr(
              "افتح ملف ترجمة خارجي لضبط توقيته",
              "Open an external subtitle file to adjust timing"));
      return;
    }
    LinearLayout l = row();
    TextView v = text(String.format(Locale.US, "%.1f s", s.player.getSpuDelay() / 1e6), 19, ink);
    l.addView(
        button(
            "− 0.5",
            () -> {
              s.player.setSpuDelay(s.player.getSpuDelay() - 500000);
              v.setText(String.format(Locale.US, "%.1f s", s.player.getSpuDelay() / 1e6));
            }));
    l.addView(v);
    l.addView(
        button(
            "+ 0.5",
            () -> {
              s.player.setSpuDelay(s.player.getSpuDelay() + 500000);
              v.setText(String.format(Locale.US, "%.1f s", s.player.getSpuDelay() / 1e6));
            }));
    new SheetBuilder(this)
        .setTitle(tr("تقديم وتأخير الترجمة", "Subtitle timing"))
        .setView(l)
        .setPositiveButton(tr("تم", "Done"), null)
        .show();
  }

  void toggle(LinearLayout l, String name, String key, boolean def) {
    Switch sw = new Switch(this);
    sw.setText(name);
    sw.setTextColor(ink);
    sw.setTextSize(15);
    sw.setTypeface(font);
    sw.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));
    pad(sw, 12);
    sw.setChecked(prefs.getBoolean(key, def));
    sw.setOnCheckedChangeListener(
        (v, on) -> {
          prefs.edit().putBoolean(key, on).apply();
          if (key.equals("autoUpdate")) UpdateEngine.schedule(this);
                  });
    l.addView(sw);
    rule(l);
  }

  void settingButton(LinearLayout l, String label, Runnable action) {
    LinearLayout r = row();
    r.setPadding(0, dp(12), 0, dp(12));
    r.addView(rowGlyph(rowIcon(label), accent), new LinearLayout.LayoutParams(dp(44), dp(48)));
    TextView t = text(label, 15, ink);
    t.setMinHeight(dp(36));
    t.setGravity(Gravity.CENTER_VERTICAL);
    r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    TextView arrow = text(english() ? "›" : "‹", 22, muted);
    arrow.setGravity(Gravity.CENTER);
    r.addView(arrow, new LinearLayout.LayoutParams(dp(32), dp(44)));
    r.setOnClickListener(v -> action.run());
    l.addView(r);
    rule(l);
  }

  void settingsHome() {
    new MobileSettings(this).open("main");
  }

  void sectionLabel(LinearLayout l, String label) {
    spacer(l, 20);
    TextView t = text(label, 12, accent);
    l.addView(t);
    spacer(l, 4);
  }

  void settings(String section) {
    if ("player".equals(screen) && "audio".equals(section)) {
      new MobileSettings(this).audioSheet();
      return;
    }
    new MobileSettings(this).open(section);
  }

  void snapshot() {
    if (s == null || s.track() == null) return;
    Uri u = s.track().uri;
    long at = s.player.getTime();
    new Thread(
            () -> {
              try {
                MediaMetadataRetriever mm = new MediaMetadataRetriever();
                mm.setDataSource(this, u);
                Bitmap bm = mm.getFrameAtTime(at * 1000, MediaMetadataRetriever.OPTION_CLOSEST);
                mm.release();
                if (bm == null) throw new java.io.IOException();
                ContentValues values = new ContentValues();
                values.put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    "Bplus-" + System.currentTimeMillis() + ".png");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Bplus");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
                Uri dest =
                    getContentResolver()
                        .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (dest == null) throw new java.io.IOException();
                try (java.io.OutputStream out = getContentResolver().openOutputStream(dest)) {
                  bm.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                getContentResolver().update(dest, values, null, null);
                runOnUiThread(
                    () -> toast(tr("تم حفظ الصورة في Pictures/Bplus", "Saved to Pictures/Bplus")));
              } catch (Exception e) {
                runOnUiThread(
                    () ->
                        toast(
                            tr(
                                "تعذر حفظ اللقطة لهذا الملف",
                                "Snapshot unavailable for this file")));
              }
            })
        .start();
  }

  public boolean onKeyDown(int code, KeyEvent e) {
    if (s != null && "player".equals(screen)) {
      if (code == KeyEvent.KEYCODE_SPACE || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
        s.toggle();
        return true;
      }
      if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
        s.seek(prefs.getInt("seekStep", 5) * 1000L);
        return true;
      }
      if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
        s.seek(-prefs.getInt("seekStep", 5) * 1000L);
        return true;
      }
      if (code == KeyEvent.KEYCODE_F) {
        fullscreen();
        return true;
      }
    }
    return super.onKeyDown(code, e);
  }

  void licenses() {
    StringBuilder b = new StringBuilder();
    for (String f : new String[] {"APACHE-2.0.txt", "Cairo-OFL.txt", "Media3-NOTICE.txt"})
      try {
        java.io.InputStream in = getAssets().open("licenses/" + f);
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        in.close();
        b.append(f).append("\n").append(o.toString("UTF-8")).append("\n\n");
      } catch (Exception e) {
      }
    ScrollView sc = new ScrollView(this);
    TextView t = text(b.toString(), 12, ink);
    pad(t, 16);
    sc.addView(t);
    new SheetBuilder(this).setTitle("Licenses").setView(sc).setPositiveButton("OK", null).show();
  }

  void menu() {
    new SheetBuilder(this)
        .setItems(
            new String[] {
              tr("افتح ملف", "Open file"),
              tr("قائمة التشغيل", "Queue"),
              tr("المسار الصوتي", "Audio track"),
              tr("نسبة الصورة", "Aspect ratio"),
              tr("الإعدادات", "Settings"),
              tr("إيقاف التشغيل", "Stop playback")
            },
            (d, n) -> {
              if (n == 0) pick(100, "*/*");
              if (n == 1) queue();
              if (n == 2) audioTracks();
              if (n == 3)
                new SheetBuilder(this)
                    .setItems(
                        new String[] {
                          tr("ملاءمة الشاشة", "Fit"),
                          tr("تكبير لملء الشاشة", "Zoom to fill"),
                          tr("تمديد لملء الشاشة", "Stretch to fill")
                        },
                        (di, k) -> {
                          if (s.player.view != null)
                            s.player.view.setResizeMode(
                                k == 0
                                    ? androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    : k == 1
                                        ? androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        : androidx.media3.ui.AspectRatioFrameLayout
                                            .RESIZE_MODE_FILL);
                        })
                    .show();
              if (n == 4) settings(prefs.getString("lastSection", "main"));
              if (n == 5) s.stopPlayback();
            })
        .show();
  }

  void audioTracks() {
    var tracks = s.player.getAudioTracks();
    if (tracks == null) return;
    if (tracks.length == 0) {
      new SheetBuilder(this)
          .setTitle(tr("مسارات الصوت", "Audio tracks"))
          .setView(
              text(
                  tr(
                      "لا توجد مسارات صوت إضافية في هذا الملف.",
                      "No additional audio tracks are available in this file."),
                  15,
                  ink))
          .setPositiveButton(tr("تم", "Done"), null)
          .show();
      return;
    }
    String[] a = new String[tracks.length];
    for (int i = 0; i < a.length; i++) a[i] = tracks[i].name;
    new SheetBuilder(this).setItems(a, (d, n) -> s.player.setAudioTrack(tracks[n].id)).show();
  }

  TextView updateState, updateVersion, updateNotes, updateProgressLabel, updateAction;
  ProgressBar updateProgress;

  void showUpdates() {
    screen = "updates";
    base("");
    LinearLayout l = col();
    l.setPadding(dp(20), dp(8), dp(20), dp(24));
    scroll().addView(l);
    new MobileSettings(this).navigation(l, "updates");
    spacer(l, 18);
    TextView heading = text(tr("التحديثات", "Updates"), 25, ink);
    heading.setTypeface(font, Typeface.BOLD);
    l.addView(heading);
    spacer(l, 12);
    TextView intro =
        text(
            tr(
                "نسختك الحالية " + BuildConfig.VERSION_NAME,
                "Current version " + BuildConfig.VERSION_NAME),
            13,
            muted);
    l.addView(intro);
    spacer(l, 22);
    LinearLayout card = col();
    card.setPadding(dp(20), dp(20), dp(20), dp(20));
    card.setBackground(shape(panel, 18));
    TextView icon = rowGlyph("download", accent);
    card.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
    updateState = text("", 22, ink);
    updateState.setTypeface(font, Typeface.BOLD);
    card.addView(updateState);
    updateVersion = text("", 14, accent);
    card.addView(updateVersion);
    spacer(card, 18);
    updateProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    updateProgress.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    updateProgress.setMax(100);
    updateProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
    updateProgress.setProgressBackgroundTintList(
        android.content.res.ColorStateList.valueOf(0xff35424f));
    card.addView(updateProgress, new LinearLayout.LayoutParams(-1, dp(6)));
    spacer(card, 8);
    updateProgressLabel = text("", 12, muted);
    card.addView(updateProgressLabel);
    spacer(card, 16);
    updateAction =
        actionButton(
            tr("تحديث الآن", "Update now"),
            true,
            () -> {
              if (UpdateEngine.busy) return;
              UpdateEngine.directUpdate(this);
              refreshUpdateCard();
            });
    card.addView(updateAction, new LinearLayout.LayoutParams(-1, dp(50)));
    l.addView(card);
    spacer(l, 24);
    sectionLabel(l, tr("الجديد في النسخة", "What's new"));
    updateNotes = text("", 14, ink);
    l.addView(updateNotes);
    spacer(l, 20);
    TextView note =
        text(
            tr(
                "زر واحد للتنزيل والتحقق وفتح مثبّت أندرويد. قد يطلب إذن التثبيت أول مرة. التحديث"
                    + " اليدوي يوقف التشغيل مؤقتًا، ويستخدم الشبكة الحالية.",
                "One button downloads, verifies and opens Android's installer. First use may need"
                    + " install permission. Manual update pauses playback and uses the current"
                    + " network."),
            12,
            muted);
    l.addView(note);
    spacer(l, 18);
    toggle(
        l,
        tr("فحص وتنزيل تلقائي عبر Wi-Fi", "Automatic Wi-Fi check and download"),
        "autoUpdate",
        true);
    toggle(l, tr("الإصدارات التجريبية", "Prereleases"), "beta", false);
    bottomNav("settings");
    refreshUpdateCard();
  }

  void refreshUpdateCard() {
    if (updateState == null || !"updates".equals(screen)) return;
    String phase = UpdateEngine.phase;
    String ar = "تحديث B+", en = "Update B+";
    switch (phase) {
      case "checking":
        ar = "بندور على تحديث";
        en = "Checking for updates";
        break;
      case "current":
        ar = "أنت على آخر نسخة";
        en = "You're up to date";
        break;
      case "downloading":
        ar = "جاري التنزيل";
        en = "Downloading update";
        break;
      case "verifying":
        ar = "بنتأكد من أمان النسخة";
        en = "Verifying update";
        break;
      case "ready":
        ar = "جاهز للتثبيت";
        en = "Ready to install";
        break;
      case "permission":
        ar = "اسمح بالتثبيت مرة واحدة";
        en = "Allow installation once";
        break;
      case "installing":
        ar = "جاري فتح مثبّت أندرويد";
        en = "Opening Android installer";
        break;
      case "wifi":
        ar = "التنزيل مستني Wi-Fi";
        en = "Waiting for Wi-Fi";
        break;
      case "confirm":
        ar = "أكد التثبيت في أندرويد";
        en = "Confirm installation in Android";
        break;
      case "installed":
        ar = "تم التثبيت";
        en = "Update installed";
        break;
      case "error":
        ar = "التحديث ما كملش";
        en = "Update interrupted";
        break;
    }
    updateState.setText(tr(ar, en));
    updateVersion.setText(
        UpdateEngine.availableVersion.isEmpty()
            ? "B+ Media Player"
            : "B+ " + UpdateEngine.availableVersion);
    boolean loading = phase.equals("checking") || phase.equals("verifying");
    updateProgress.setIndeterminate(loading);
    updateProgress.setProgress(UpdateEngine.progress);
    updateProgress.setVisibility(loading || phase.equals("downloading") ? View.VISIBLE : View.GONE);
    updateProgressLabel.setText(
        phase.equals("downloading")
            ? UpdateEngine.progress
                + "% · "
                + String.format(
                    Locale.US,
                    "%.1f / %.1f MB",
                    UpdateEngine.downloadBytes / 1048576.0,
                    UpdateEngine.totalBytes / 1048576.0)
            : phase.equals("error")
                ? UpdateEngine.status
                : tr(
                    "من GitHub. بيتحقق من توقيع التطبيق قبل التثبيت.",
                    "From GitHub. App signature is checked before installation."));
    updateNotes.setText(
        UpdateEngine.releaseNotes.isEmpty()
            ? tr(
                "تفاصيل النسخة الجديدة هتظهر هنا قبل فتح المثبّت.",
                "New release details appear here before the installer opens.")
            : UpdateEngine.releaseNotes);
    updateAction.setText(
        phase.equals("permission")
            ? tr("السماح والمتابعة", "Allow and continue")
            : phase.equals("downloading") || loading
                ? tr("جاري التحديث…", "Updating…")
                : tr("تحديث الآن", "Update now"));
    updateAction.setAlpha(UpdateEngine.busy ? .55f : 1);
  }

  // All in-app choices share this Cairo/icon bottom sheet. System file pickers remain
  // Android-owned.
  Dialog activeSheet;

  String rowIcon(String label) {
    String v = label.toLowerCase(Locale.ROOT);
    if (v.contains("إعدادات") || v.contains("settings")) return "gear";
    if (v.contains("a-b")
        || v.contains("بداية a")
        || v.contains("نهاية b")
        || v.contains("set a")
        || v.contains("set b")) return "ab";
    if (v.contains("معادل") || v.contains("equalizer")) return "equalizer";
    if (v.contains("مؤقت")
        || v.contains("دقيقة")
        || v.contains("ساعة")
        || v.contains("minutes")
        || v.contains("hour")) return "clock";
    if (v.contains("قفل") || v.contains("lock")) return "lock";
    if (v.contains("لقطة") || v.contains("snapshot")) return "camera";
    if (v.contains("توقيت") || v.contains("تأخير") || v.contains("timer") || v.contains("timing"))
      return "clock";
    if (v.contains("إخفاء") || v.contains("hide")) return "eyeoff";
    if (v.contains("بحث") || v.contains("search")) return "search";
    if (v.contains("ترجمة")
        || v.contains("subtitle")
        || v.contains("خط")
        || v.contains("font")
        || v.contains("utf")
        || v.contains("serif")
        || v.contains("mono")
        || v.contains("1256")) return "sub";
    if (v.contains("سرعة") || v.contains("speed") || v.contains("×")) return "speed";
    if (v.contains("صورة")
        || v.contains("aspect")
        || v.contains("fit")
        || v.contains("zoom")
        || v.contains("stretch")) return "ratio";
    if (v.contains("إضاءة")
        || v.contains("brightness")
        || v.contains("shade")
        || v.contains("درجة")) return "sun";
    if (v.contains("صوت") || v.contains("audio") || v.contains("volume") || v.contains("hz"))
      return "volume";
    if (v.contains("مفضلة") || v.contains("favorite")) return "heart";
    if (v.contains("إزالة") || v.contains("remove")) return "minus";
    if (v.contains("أعلى") || v.contains("up")) return "up";
    if (v.contains("أسفل") || v.contains("down")) return "down";
    if (v.contains("تحديث") || v.contains("update")) return "download";
    if (v.contains("رجوع") || v.contains("back")) return "back";
    if (v.contains("ملف") || v.contains("file") || v.contains("مجلد") || v.contains("folder"))
      return "folder";
    if (v.contains("قائمة") || v.contains("queue") || v.contains("ترتيب") || v.contains("name"))
      return "queue";
    if (v.contains("تكرار") || v.contains("repeat")) return "repeat";
    if (v.contains("تشغيل") || v.contains("play")) return "play";
    if (v.contains("لون")
        || v.contains("color")
        || v.contains("blue")
        || v.contains("teal")
        || v.contains("purple")
        || v.contains("orange")
        || v.contains("red")
        || v.contains("grey")
        || v.contains("أبيض")
        || v.contains("أصفر")
        || v.contains("أخضر")) return "palette";
    if (v.contains("سمة")
        || v.contains("theme")
        || v.contains("light")
        || v.contains("dark")
        || v.contains("system")
        || v.contains("فاتح")
        || v.contains("داكن")
        || v.contains("النظام")) return "sun";
    return "gear";
  }

  TextView rowGlyph(String key, int color) {
    TextView v = text("", 14, color);
    v.setGravity(Gravity.CENTER);
    PlayerIcon icon = new PlayerIcon(key, color);
    icon.setBounds(0, 0, dp(23), dp(23));
    v.setCompoundDrawables(null, icon, null, null);
    v.setPadding(dp(10), dp(10), dp(10), dp(10));
    return v;
  }

  class SheetBuilder {
    String heading;
    String[] choices;
    View custom;
    DialogInterface.OnClickListener onChoice;
    java.util.ArrayList<String> actionNames = new java.util.ArrayList<>();
    java.util.ArrayList<DialogInterface.OnClickListener> actions = new java.util.ArrayList<>();

    SheetBuilder(Context ignored) {}

    SheetBuilder setTitle(String t) {
      heading = t;
      return this;
    }

    SheetBuilder setItems(String[] items, DialogInterface.OnClickListener listener) {
      choices = items;
      onChoice = listener;
      return this;
    }

    SheetBuilder setView(View v) {
      custom = v;
      return this;
    }

    SheetBuilder setPositiveButton(String t, DialogInterface.OnClickListener l) {
      actionNames.add(t);
      actions.add(l);
      return this;
    }

    SheetBuilder setNegativeButton(String t, DialogInterface.OnClickListener l) {
      actionNames.add(t);
      actions.add(l);
      return this;
    }

    SheetBuilder setNeutralButton(String t, DialogInterface.OnClickListener l) {
      actionNames.add(t);
      actions.add(l);
      return this;
    }

    Dialog show() {
      h.removeCallbacks(hideVideoControls);
      Dialog dialog = new Dialog(MainActivity.this);
      activeSheet = dialog;
      LinearLayout shell = col();
      shell.setLayoutDirection(root.getLayoutDirection());
      shell.setPadding(dp(20), dp(10), dp(20), dp(20));
      GradientDrawable background = shape(panel, 24);
      background.setCornerRadii(new float[] {dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
      shell.setBackground(background);
      View handle = new View(MainActivity.this);
      handle.setBackground(shape(muted, 3));
      LinearLayout.LayoutParams grip = new LinearLayout.LayoutParams(dp(32), dp(4));
      grip.gravity = Gravity.CENTER_HORIZONTAL;
      grip.bottomMargin = dp(14);
      shell.addView(handle, grip);
      LinearLayout head = row();
      TextView title =
          text(heading == null ? tr("خيارات التشغيل", "Playback options") : heading, 21, ink);
      title.setTypeface(font, Typeface.BOLD);
      head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
      head.addView(iconButton("close", tr("إغلاق", "Close"), dialog::dismiss));
      shell.addView(head);
      spacer(shell, 8);
      ScrollView scroll = new ScrollView(MainActivity.this);
      scroll.setFillViewport(false);
      scroll.setVerticalScrollBarEnabled(false);
      LinearLayout content = col();
      scroll.addView(content);
      shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
      if (choices != null) {
        for (int i = 0; i < choices.length; i++) {
          final int selected = i;
          LinearLayout item = row();
          item.setPadding(0, dp(4), 0, dp(4));
          String label = choices[i];
          item.addView(
              rowGlyph(rowIcon(label), accent), new LinearLayout.LayoutParams(dp(44), dp(48)));
          TextView text = text(label, 15, ink);
          text.setMaxLines(2);
          item.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
          TextView arrow = text("‹", 21, muted);
          arrow.setGravity(Gravity.CENTER);
          item.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(48)));
          item.setMinimumHeight(dp(60));
          item.setContentDescription(label);
          item.setBackground(
              new android.graphics.drawable.RippleDrawable(
                  android.content.res.ColorStateList.valueOf(0x224d99ff),
                  null,
                  shape(Color.WHITE, 10)));
          item.setOnClickListener(
              v -> {
                dialog.dismiss();
                if (onChoice != null) onChoice.onClick(dialog, selected);
              });
          content.addView(item);
        }
      }
      if (custom != null) {
        if (custom.getParent() != null) ((ViewGroup) custom.getParent()).removeView(custom);
        styleSheetContent(custom);
        content.addView(custom);
      }
      if (!actionNames.isEmpty()) {
        spacer(shell, 12);
        LinearLayout buttons = row();
        for (int k = 0; k < actionNames.size(); k++) {
          final int index = k;
          TextView b =
              actionButton(
                  actionNames.get(k),
                  k == 0,
                  () -> {
                    dialog.dismiss();
                    if (actions.get(index) != null)
                      actions.get(index).onClick(dialog, DialogInterface.BUTTON_POSITIVE - index);
                  });
          LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(48), 1);
          bp.setMargins(dp(3), 0, dp(3), 0);
          buttons.addView(b, bp);
        }
        shell.addView(buttons);
      }
      dialog.setContentView(shell);
      Window window = dialog.getWindow();
      if (window != null) {
        window.setBackgroundDrawable(
            new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(.54f);
        window.setGravity(Gravity.BOTTOM);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
      }
      dialog.setOnDismissListener(
          d -> {
            if (activeSheet == dialog) activeSheet = null;
            if (video && "player".equals(screen)) h.postDelayed(hideVideoControls, 3500);
          });
      dialog.show();
      int width = getResources().getDisplayMetrics().widthPixels;
      int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * .8f);
      shell.measure(
          View.MeasureSpec.makeMeasureSpec(Math.min(width, dp(560)), View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST));
      int desired = shell.getMeasuredHeight();
      if (choices != null)
        desired =
            Math.min(maxHeight, dp(100 + choices.length * 60 + (actionNames.isEmpty() ? 0 : 64)));
      if (custom != null)
        desired = Math.min(maxHeight, Math.max(dp(240), custom.getMeasuredHeight() + dp(155)));
      window.setLayout(Math.min(width, dp(560)), desired);
      return dialog;
    }
  }

  void styleSheetContent(View view) {
    if (view instanceof TextView) {
      TextView t = (TextView) view;
      t.setTypeface(font);
      t.setTextColor(ink);
      if (view instanceof EditText)
        t.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
    }
    if (view instanceof SeekBar) styleSeek((SeekBar) view);
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int k = 0; k < group.getChildCount(); k++) styleSheetContent(group.getChildAt(k));
    }
  }

  void styleSeek(SeekBar bar) {
    GradientDrawable track = shape(0xff35424f, 3);
    GradientDrawable fill = shape(accent, 3);
    android.graphics.drawable.ClipDrawable clip =
        new android.graphics.drawable.ClipDrawable(
            fill, Gravity.LEFT, android.graphics.drawable.ClipDrawable.HORIZONTAL);
    android.graphics.drawable.LayerDrawable layers =
        new android.graphics.drawable.LayerDrawable(new Drawable[] {track, clip});
    layers.setId(0, android.R.id.background);
    layers.setId(1, android.R.id.progress);
    layers.setLayerHeight(0, dp(3));
    layers.setLayerHeight(1, dp(3));
    layers.setLayerGravity(0, Gravity.CENTER_VERTICAL);
    layers.setLayerGravity(1, Gravity.CENTER_VERTICAL);
    bar.setProgressDrawable(layers);
    GradientDrawable thumb = shape(ink, 10);
    thumb.setSize(dp(12), dp(12));
    bar.setThumb(thumb);
    bar.setSplitTrack(false);
    bar.setPadding(dp(8), dp(12), dp(8), dp(12));
    bar.setMinHeight(dp(44));
    bar.setMaxHeight(dp(44));
  }

  void toast(String t) {
    Toast.makeText(this, t, Toast.LENGTH_LONG).show();
  }
}
