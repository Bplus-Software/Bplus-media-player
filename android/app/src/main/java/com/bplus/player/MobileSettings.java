package com.bplus.player;

import android.graphics.*;
import android.view.*;
import android.widget.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class MobileSettings {
  final MainActivity a;
  LinearLayout page;
  String current;
  static final String[] IDS = {
    "picture",
    "audio",
    "playback",
    "subtitles",
    "appearance",
    "language",
    "shortcuts",
    "files",
    "updates",
    "about"
  };
  static final String[] AR = {
    "الصورة",
    "الصوت",
    "التشغيل",
    "الترجمة",
    "المظهر",
    "اللغة",
    "الاختصارات",
    "الملفات",
    "التحديثات",
    "حول البرنامج"
  };
  static final String[] EN = {
    "Video",
    "Audio",
    "Playback",
    "Subtitles",
    "Appearance",
    "Language",
    "Shortcuts",
    "File types",
    "Updates",
    "About"
  };
  static final String[] ICONS = {
    "nav-video",
    "volume",
    "nav-playback",
    "nav-subtitles",
    "nav-appearance",
    "nav-language",
    "nav-shortcuts",
    "nav-files",
    "nav-updates",
    "nav-about"
  };

  MobileSettings(MainActivity a) {
    this.a = a;
  }

  String t(String ar, String en) {
    return a.tr(ar, en);
  }

  void open(String section) {
    if ("player".equals(a.screen)
        && (section.equals("audio") || section.equals("picture") || section.equals("subtitles"))) {
      playerSheet(section);
      return;
    }

    if (section.equals("main")) section = a.prefs.getString("settingsCategory", "picture");
    if (section.equals("video")) section = "picture";
    current = section;
    a.prefs.edit().putString("settingsCategory", section).apply();
    if (section.equals("updates")) {
      a.showUpdates();
      return;
    }
    a.screen = "settings";
    a.base("");
    page = a.col();
    page.setPadding(a.dp(18), 0, a.dp(18), a.dp(24));
    a.scroll().addView(page);
    navigation(page, section);
    a.spacer(page, 18);
    int ix = java.util.Arrays.asList(IDS).indexOf(section);
    TextView heading = a.text(ix < 0 ? section : t(AR[ix], EN[ix]), 25, a.ink);
    heading.setTypeface(a.font, Typeface.BOLD);
    page.addView(heading);
    note(
        t("التغييرات تُحفظ تلقائيًا على هذا الجهاز", "Changes saved automatically on this device"));
    switch (section) {
      case "appearance":
        appearance();
        break;
      case "picture":
        picture();
        break;
      case "audio":
        audio();
        break;
      case "playback":
        playback();
        break;
      case "subtitles":
        subtitles();
        break;
      case "language":
        {
          LinearLayout c = card(t("لغة واجهة البرنامج", "App interface language"));
          segments(
              c,
              new String[] {"العربية", "English"},
              a.prefs.getBoolean("english", false) ? 1 : 0,
              k -> {
                a.prefs.edit().putBoolean("english", k == 1).apply();
                open(current);
              });
          break;
        }
      case "shortcuts":
        shortcuts();
        break;
      case "files":
        files();
        break;
      case "about":
        {
          LinearLayout c = card("B+ Media Player");
          c.addView(a.text("B+ " + BuildConfig.VERSION_NAME + " · Android", 19, a.ink));
          note(
              t(
                  "مشغل محلي. ملفاتك لا تُرفع إلى أي جهة.",
                  "Local playback. Your files are not uploaded."));
          a.settingButton(c, t("التراخيص والمكونات", "Licenses and components"), a::licenses);
          break;
        }
    }
    a.spacer(page, 14);
    a.settingButton(page, t("رجوع للمكتبة", "Back to library"), a::showLibrary);
    a.bottomNav("settings");
  }

  void navigation(LinearLayout container, String section) {
    for (int r = 0; r < 2; r++) {
      LinearLayout tabs = a.row();
      for (int col = 0; col < 5; col++) {
        int i = r * 5 + col;
        final String id = IDS[i];
        TextView cell = a.text(t(AR[i], EN[i]), 10, section.equals(id) ? a.accent : a.muted);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(a.dp(2), a.dp(6), a.dp(2), a.dp(6));
        PlayerIcon icon = new PlayerIcon(ICONS[i], section.equals(id) ? a.accent : a.muted);
        icon.setBounds(0, 0, a.dp(21), a.dp(21));
        cell.setCompoundDrawables(null, icon, null, null);
        cell.setCompoundDrawablePadding(a.dp(3));
        cell.setMaxLines(1);
        cell.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cell.setBackground(a.shape(section.equals(id) ? a.panel : a.bg, 12));
        cell.setOnClickListener(v -> open(id));
        tabs.addView(cell, new LinearLayout.LayoutParams(0, a.dp(64), 1));
      }
      container.addView(tabs);
    }
  }

  void audioSheet() {
    playerSheet("audio");
  }

  void playerSheet(String section) {
    if (a.activeSheet != null) a.activeSheet.dismiss();
    current = section;
    page = a.col();
    if (section.equals("audio")) audio();
    else if (section.equals("picture")) picture();
    else subtitles();
    int index = java.util.Arrays.asList(IDS).indexOf(section);
    a.new SheetBuilder(a)
        .setTitle(index < 0 ? section : t(AR[index], EN[index]))
        .setView(page)
        .setPositiveButton(t("تم", "Done"), null)
        .show();
  }

  LinearLayout card(String label) {
    a.sectionLabel(page, label);
    LinearLayout c = a.col();
    c.setPadding(a.dp(16), a.dp(12), a.dp(16), a.dp(14));
    c.setBackground(a.shape(a.panel, 18));
    page.addView(c);
    return c;
  }

  void note(String text) {
    TextView v = a.text(text, 12, a.muted);
    v.setPadding(0, a.dp(8), 0, a.dp(10));
    page.addView(v);
  }

  void label(LinearLayout c, String text) {
    TextView v = a.text(text, 14, a.ink);
    v.setPadding(0, a.dp(8), 0, a.dp(8));
    c.addView(v);
  }

  void segments(LinearLayout c, String[] names, int selected, java.util.function.IntConsumer set) {
    LinearLayout row = a.row();
    row.setBackground(a.shape(a.bg, 12));
    for (int i = 0; i < names.length; i++) {
      final int n = i;
      TextView b = a.text(names[i], 12, i == selected ? a.ink : a.muted);
      b.setGravity(Gravity.CENTER);
      b.setPadding(a.dp(4), a.dp(9), a.dp(4), a.dp(9));
      b.setMinHeight(a.dp(44));
      if (i == selected) b.setBackground(a.shape(0x334d99ff, 10));
      b.setOnClickListener(v -> set.accept(n));
      row.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
    }
    c.addView(row);
  }

  void slider(
      LinearLayout c,
      String ar,
      String en,
      String key,
      int min,
      int max,
      int def,
      java.util.function.IntConsumer apply) {
    TextView value = a.text(t(ar, en) + ": " + a.prefs.getInt(key, def), 14, a.ink);
    c.addView(value);
    SeekBar bar = new SeekBar(a);
    bar.setMax(max - min);
    bar.setProgress(a.prefs.getInt(key, def) - min);
    a.styleSeek(bar);
    c.addView(bar, new LinearLayout.LayoutParams(-1, a.dp(66)));
    bar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar b) {}

          public void onStopTrackingTouch(SeekBar b) {}

          public void onProgressChanged(SeekBar b, int progress, boolean user) {
            if (user) {
              int v = progress + min;
              value.setText(t(ar, en) + ": " + v);
              a.prefs.edit().putInt(key, v).apply();
              apply.accept(v);
            }
          }
        });
  }

  void flag(LinearLayout c, String ar, String en, String key, boolean def) {
    a.toggle(c, t(ar, en), key, def);
  }

  void appearance() {
    LinearLayout c = card(t("الواجهة", "Interface"));
    label(c, t("نمط الأيقونات", "Icon style"));
    String[]
        names = {"Phosphor", "Phosphor Fill", "Phosphor Duotone", "Lucide", "Material Rounded"},
        ids = {"phosphor", "phosphor-fill", "phosphor-duotone", "lucide", "material"};
    for (int i = 0; i < ids.length; i++) {
      final String id = ids[i];
      LinearLayout row = a.row();
      row.addView(a.text(names[i], 13, a.ink), new LinearLayout.LayoutParams(0, a.dp(42), 1));
      String old = DesktopGlyph.family;
      DesktopGlyph.family = id;
      for (String icon : new String[] {"play", "volume", "gear"}) {
        android.graphics.Bitmap b =
            Bitmap.createBitmap(a.dp(24), a.dp(24), Bitmap.Config.ARGB_8888);
        Canvas can = new Canvas(b);
        can.scale(a.dp(24) / 24f, a.dp(24) / 24f);
        DesktopGlyph.draw(can, icon, a.accent);
        ImageView img = new ImageView(a);
        img.setImageBitmap(b);
        row.addView(img, new LinearLayout.LayoutParams(a.dp(30), a.dp(42)));
      }
      DesktopGlyph.family = old;
      if (id.equals(a.prefs.getString("iconStyle", "phosphor-fill")))
        row.setBackground(a.shape(0x224d99ff, 8));
      row.setOnClickListener(
          v -> {
            a.prefs.edit().putString("iconStyle", id).apply();
            DesktopGlyph.init(a);
            open(current);
          });
      c.addView(row);
    }
    flag(c, "إظهار قائمة التشغيل عند البدء", "Show playlist on startup", "playlistStartup", false);
    label(c, t("السمة", "Theme"));
    String theme = a.prefs.getString("theme", "dark");
    segments(
        c,
        new String[] {t("ليلي", "Night"), t("نهاري", "Day"), t("حسب النظام", "Follow system")},
        theme.equals("dark") ? 0 : theme.equals("light") ? 1 : 2,
        k -> {
          a.prefs
              .edit()
              .putString("theme", new String[] {"dark", "light", "system"}[k])
              .remove("shade")
              .apply();
          open(current);
        });
    slider(c, "درجة السمة", "Theme shade", "shade", 0, 100, 92, v -> {});
    a.settingButton(c, t("تطبيق الدرجة", "Apply shade"), () -> open(current));
    label(
        c,
        t(
            "عتامة النافذة: يديرها أندرويد. لا يمكن جعل الفيديو والتطبيق شفافين فوق التطبيقات"
                + " الأخرى.",
            "Window opacity is managed by Android. Whole-app desktop transparency is"
                + " unavailable."));
    c = card(t("اللون", "Color"));
    label(c, t("لون التمييز", "Accent color"));
    int[] colors = {
      0xff2f89fc,
      0xffff5a26,
      0xff36ba78,
      0xffa776f4,
      0xffed5252,
      0xffed6eaf,
      0xff26b7b0,
      0xffe5ad26,
      0xff16bed9,
      0xff7375ed,
      0xff9abd36,
      0xffd54e77,
      0xffa4afbf,
      0xffcd8c56,
      0xffffffff,
      0xffc8ccd4
    };
    for (int r = 0; r < 2; r++) {
      LinearLayout row = a.row();
      for (int z = 0; z < 8; z++) {
        final int color = colors[r * 8 + z];
        TextView swatch = a.text(a.accent == color ? "✓" : "", 15, Color.BLACK);
        swatch.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable bg = a.shape(color, 12);
        if (a.accent == color) bg.setStroke(a.dp(3), a.ink);
        swatch.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, a.dp(38), 1);
        lp.setMargins(a.dp(3), a.dp(4), a.dp(3), a.dp(4));
        row.addView(swatch, lp);
        swatch.setOnClickListener(
            v -> {
              a.prefs.edit().putInt("accent", color).apply();
              open(current);
            });
      }
      c.addView(row);
    }
    a.settingButton(c, t("لون مخصص", "Custom color"), this::customColor);
    c = card(t("عرض الصوتيات", "Visualizer"));
    label(c, t("شكل عرض الصوتيات", "Audio visualization"));
    a.settingButton(c, t("معاينة الأشكال السبعة", "Preview all seven styles"), this::visualPicker);
    label(c, t("حجم عرض الصوتيات", "Visualizer size"));
    segments(
        c,
        new String[] {t("صغير", "Small"), t("وسط", "Medium"), t("كبير", "Large")},
        a.prefs.getInt("vizSize", 0),
        k -> {
          a.prefs.edit().putInt("vizSize", k).apply();
          open(current);
        });
  }

  void visualPicker() {
    LinearLayout grid = a.col();
    String[] ar = {"الغلاف مع أعمدة", "أعمدة", "موجة", "خط", "نقاط", "دوائر", "شعار B+"},
        en = {"Cover + bars", "Bars", "Wave", "Line", "Dots", "Circles", "B+ Logo"};
    for (int r = 0; r < 4; r++) {
      LinearLayout row = a.row();
      for (int z = 0; z < 2; z++) {
        final int k = r * 2 + z;
        if (k >= 7) break;
        LinearLayout cell = a.col();
        cell.setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(6));
        cell.setBackground(
            a.shape(
                a.prefs.getString("vizStyle", "cover").equals(VisualizerView.STYLES[k])
                    ? 0x334d99ff
                    : a.bg,
                12));
        cell.addView(
            new VisualizerView(a, VisualizerView.STYLES[k]),
            new LinearLayout.LayoutParams(-1, a.dp(62)));
        TextView title = a.text(t(ar[k], en[k]), 12, a.ink);
        title.setGravity(Gravity.CENTER);
        cell.addView(title);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, a.dp(100), 1);
        lp.setMargins(a.dp(3), a.dp(3), a.dp(3), a.dp(3));
        row.addView(cell, lp);
        cell.setOnClickListener(
            v -> {
              a.prefs.edit().putString("vizStyle", VisualizerView.STYLES[k]).apply();
              if (a.activeSheet != null) a.activeSheet.dismiss();
              if (a.rings != null) a.rings.invalidate();
            });
      }
      grid.addView(row);
    }
    a.new SheetBuilder(a)
        .setTitle(t("شكل عرض الصوتيات", "Audio visualization"))
        .setView(grid)
        .show();
  }

  void customColor() {
    EditText input = new EditText(a);
    input.setSingleLine();
    input.setText(String.format("#%06X", a.accent & 0xffffff));
    a.new SheetBuilder(a)
        .setTitle(t("لون مخصص · #RRGGBB", "Custom color · #RRGGBB"))
        .setView(input)
        .setPositiveButton(
            t("حفظ", "Save"),
            (d, n) -> {
              String value = input.getText().toString().trim();
              if (value.matches("#[0-9a-fA-F]{6}")) {
                a.prefs.edit().putInt("accent", Color.parseColor(value)).apply();
                open(current);
              } else a.toast(t("اكتب ست خانات بعد #", "Enter six digits after #"));
            })
        .show();
  }

  void picture() {
    LinearLayout c =
        card(
            t(
                "تعديل مظهر الصورة أثناء العرض دون تغيير الملف.",
                "Adjust the picture during playback; the file is unchanged."));
    String[] keys = {"brightness", "contrast", "saturation", "hue"},
        ar = {"الإضاءة", "التباين", "التشبع", "درجة اللون"},
        en = {"Brightness", "Contrast", "Saturation", "Hue"};
    for (int i = 0; i < 4; i++) {
      final int ix = i;
      slider(
          c,
          ar[i],
          en[i],
          keys[i],
          i == 3 ? -180 : i == 2 ? 0 : 25,
          i == 3 ? 180 : 200,
          i == 3 ? 0 : 100,
          v -> applyPicture());
    }
    label(c, t("طريقة ملء الصورة", "Video fit"));
    segments(
        c,
        new String[] {t("ملاءمة", "Fit"), t("تكبير", "Zoom"), t("تمديد", "Stretch")},
        a.prefs.getInt("fit", 0),
        k -> {
          a.prefs.edit().putInt("fit", k).apply();
          if (a.s.player.view != null)
            a.s.player.view.setResizeMode(
                k == 0
                    ? androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    : k == 1
                        ? androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        : androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL);
          open(current);
        });
  }

  void applyPicture() {
    if (a.s != null)
      a.s.player.effects(
          (a.prefs.getInt("brightness", 100) - 100) / 100f,
          (a.prefs.getInt("contrast", 100) - 100) / 100f,
          a.prefs.getInt("saturation", 100) - 100,
          a.prefs.getInt("hue", 0));
  }

  void audio() {
    LinearLayout c = card(t("مستوى الصوت والمعادل", "Volume and equalizer"));
    slider(c, "تضخيم الصوت", "Volume boost", "boost", 0, 200, 100, v -> a.s.player.setBoost(v));
    String[] keys = {"bass", "mid", "treble"},
        ar = {"الجهير", "الترددات المتوسطة", "الترددات العالية"},
        en = {"Bass", "Midrange", "Treble"};
    for (int i = 0; i < 3; i++)
      slider(c, ar[i], en[i], keys[i], -12, 12, 0, v -> a.s.player.applyAudioSettings());
    note(
        t(
            "التضخيم فوق 100% قد يسبب تشويشًا. اخفضه إذا حدث ذلك. المعادل بثلاثة نطاقات.",
            "Boost above 100% may distort. Reduce it if needed. Three-band equalizer."));
  }

  void playback() {
    LinearLayout c =
        card(t("التشغيل التلقائي والتكرار وخطوة التقديم", "Autoplay, repeat and seek behavior"));
    flag(
        c,
        "التشغيل التلقائي عند فتح ملف",
        "Play automatically when opening files",
        "autoplay",
        true);
    flag(c, "تشغيل الملف التالي تلقائيًا", "Play next file automatically", "autoNext", true);
    flag(
        c,
        "إضافة ملفات المجلد بنفس الصيغة عند فتح ملف",
        "Add same-format folder files when opening a file",
        "siblings",
        false);
    flag(c, "تكرار الملف", "Repeat current file", "repeat", false);
    slider(
        c, "خطوة التقديم والتأخير (ثانية)", "Seek step (seconds)", "seekStep", 1, 60, 5, v -> {});
    label(c, t("سرعة التشغيل الافتراضية", "Default playback speed"));
    segments(
        c,
        new String[] {"0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×"},
        java.util.Arrays.asList(.5f, .75f, 1f, 1.25f, 1.5f, 2f)
            .indexOf(a.prefs.getFloat("speed", 1f)),
        k -> {
          a.prefs.edit().putFloat("speed", new float[] {.5f, .75f, 1f, 1.25f, 1.5f, 2f}[k]).apply();
          open(current);
        });
    flag(c, "صورة مصغرة عند سحب شريط التقدم", "Seek bar thumbnail preview", "thumbPreview", true);
    LinearLayout mobile = card(t("خيارات أندرويد", "Android options"));
    flag(mobile, "تذكّر مكان التوقف", "Remember playback position", "remember", true);
    flag(mobile, "تشغيل في الخلفية", "Background playback", "background", true);
    flag(mobile, "صورة فوق صورة", "Picture in picture", "pip", true);
  }

  void subtitles() {
    LinearLayout c = card(t("مظهر الترجمة وتوقيتها", "Subtitle appearance and timing"));
    label(
        c,
        t(
            "تحميل الترجمة المجاورة تلقائيًا: يحتاج اختيار مجلد يمنح أندرويد الوصول إليه. لم يُفعّل"
                + " بعد.",
            "Load matching subtitle automatically requires an Android folder grant. Not enabled"
                + " yet."));
    a.settingButton(
        c,
        t("خط الترجمة", "Subtitle font"),
        () ->
            a.new SheetBuilder(a)
                .setTitle(t("خط الترجمة", "Subtitle font"))
                .setItems(
                    new String[] {"Cairo", "Sans serif", "Serif", "Monospace"},
                    (d, k) -> {
                      a.prefs
                          .edit()
                          .putString(
                              "subfont",
                              new String[] {"Cairo", "sans-serif", "serif", "monospace"}[k])
                          .apply();
                      a.s.player.styleSubtitles();
                    })
                .show());
    slider(
        c,
        "حجم خط الترجمة",
        "Subtitle font size",
        "subsize",
        14,
        54,
        24,
        v -> a.s.player.styleSubtitles());
    a.settingButton(
        c,
        t("لون الترجمة", "Subtitle color"),
        () ->
            a.new SheetBuilder(a)
                .setItems(
                    new String[] {t("أبيض", "White"), t("أصفر", "Yellow"), t("أخضر", "Green")},
                    (d, k) -> {
                      a.prefs
                          .edit()
                          .putInt("subcolor", new int[] {0xffffff, 0xffff00, 0x66ff66}[k])
                          .apply();
                      a.s.player.styleSubtitles();
                    })
                .show());
    a.settingButton(c, t("تأخير الترجمة (ثانية)", "Subtitle delay (seconds)"), a::delay);
    a.settingButton(c, t("فتح ملف ترجمة", "Open subtitle file"), () -> a.pick(101, "*/*"));
    note(
        t(
            "Cairo مرفق. خطوط Windows غير المرفقة لها بدائل على أندرويد. توقيت الترجمة للملفات"
                + " الخارجية فقط.",
            "Cairo is bundled. Unbundled Windows fonts use Android alternatives. Timing applies to"
                + " external subtitle files."));
  }

  void shortcuts() {
    LinearLayout c = card(t("اختصارات لوحة مفاتيح خارجية", "External keyboard shortcuts"));
    label(
        c,
        t(
            "التخصيص الكامل لـ 16 اختصارًا لم يُنقل بعد. المتاح حاليًا:",
            "Full customization of 16 actions is not ported yet. Currently available:"));
    for (String s :
        new String[] {
          "Space · " + t("تشغيل / إيقاف", "Play / pause"),
          "F · " + t("ملء الشاشة", "Fullscreen"),
          "← / → · " + t("تقديم / تأخير", "Seek back / forward")
        }) label(c, s);
  }

  void files() {
    LinearLayout c =
        card(
            t(
                "أنواع الملفات المدعومة والملفات المؤقتة",
                "Supported file types and temporary files"));
    a.settingButton(c, t("فتح ملف", "Open file"), () -> a.pick(100, "*/*"));
    a.settingButton(c, t("إضافة مجلد", "Add folder"), a::folder);
    a.settingButton(
        c,
        t("تنظيف الملفات المحذوفة من المكتبة", "Remove deleted files from library"),
        a::refreshMissingFiles);
    label(
        c,
        t(
            "أندرويد يدير «فتح باستخدام» وأذونات المجلدات. فك الترميز حسب Media3 وترميزات الجهاز،"
                + " وليس FFmpeg الخاص بـ Windows.",
            "Android manages Open with and folder access. Decoding uses Media3 and device codecs,"
                + " not Windows FFmpeg."));
    label(
        c,
        t(
            "تقرير فتح الملفات ومسح تحويلات Windows لا ينطبقان على هذا الإصدار.",
            "The Windows file-opening report and conversion-cache controls are not in this"
                + " version."));
  }
}
