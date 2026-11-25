package com.bplus.player;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.*;
import android.net.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import org.json.*;

public final class UpdateEngine {
  static java.lang.ref.WeakReference<Activity> foregroundActivity =
      new java.lang.ref.WeakReference<>(null);
  static final String REPO = "Bplus-Software/Bplus-media-player";
  static volatile boolean busy;
  static volatile boolean installing;
  static String status = "";
  static volatile String phase = "idle", availableVersion = "", releaseNotes = "";
  static volatile int progress = 0;
  static volatile long downloadBytes = 0, totalBytes = 0;
  static volatile Runnable observer;

  static void changed() {
    if (observer != null) new Handler(Looper.getMainLooper()).post(observer);
  }

  static void directUpdate(Activity activity) {
    PlaybackService service = PlaybackService.current;
    if (service != null && service.player.isPlaying()) service.pause();
    activity.getSharedPreferences("updates", 0).edit().putBoolean("installOnReturn", true).apply();
    phase = "checking";
    progress = 0;
    downloadBytes = 0;
    totalBytes = 0;
    changed();
    String ready = activity.getSharedPreferences("updates", 0).getString("ready", "");
    if (!ready.isEmpty() && new File(ready).exists()) {
      installReady(activity);
      return;
    }
    check(activity, true);
  }

  static class Candidate {
    String version, name, url, digest, notes;
    long size;

    Candidate(String v, JSONObject a) {
      version = v;
      name = a.optString("name");
      url = a.optString("browser_download_url");
      digest = a.optString("digest");
      size = a.optLong("size");
    }
  }

  static int compare(String a, String b) {
    String[] x = a.replaceFirst("^[vV]", "").split("\\."),
        y = b.replaceFirst("^[vV]", "").split("\\.");
    for (int i = 0; i < 3; i++) {
      int m = i < x.length ? Integer.parseInt(x[i]) : 0,
          n = i < y.length ? Integer.parseInt(y[i]) : 0;
      if (m != n) return Integer.compare(m, n);
    }
    return 0;
  }

  static Candidate select(JSONArray releases, String installed, boolean beta) throws Exception {
    Candidate best = null;
    for (int i = 0; i < releases.length(); i++) {
      JSONObject r = releases.getJSONObject(i);
      if (r.optBoolean("draft") || r.optBoolean("prerelease") && !beta) continue;
      String tag = r.optString("tag_name");
      if (!tag.matches("v?\\d+\\.\\d+\\.\\d+") || compare(tag, installed) <= 0) continue;
      String v = tag.replaceFirst("^v", "");
      String exact = "Bplus-Media-Player-" + v + ".apk";
      JSONArray aa = r.optJSONArray("assets");
      if (aa == null) continue;
      JSONObject selected = null;
      int count = 0;
      for (int k = 0; k < aa.length(); k++) {
        JSONObject a = aa.getJSONObject(k);
        if (exact.equals(a.optString("name"))) {
          selected = a;
          count++;
        }
      }
      if (count != 1) continue;
      String url = selected.optString("browser_download_url");
      if (!url.startsWith("https://github.com/" + REPO + "/releases/download/" + tag + "/")
          || selected.optLong("size") <= 0) continue;
      if (selected.optLong("size") > 512L * 1024 * 1024) continue;
      if (best == null || compare(v, best.version) > 0) {
        best = new Candidate(v, selected);
        best.notes = r.optString("body", "");
      }
    }
    return best;
  }

  static HttpURLConnection connect(String url, long offset) throws Exception {
    URL u = new URL(url);
    for (int k = 0; k < 6; k++) {
      String host = u.getHost();
      if (!u.getProtocol().equals("https")
          || !(host.equals("api.github.com")
              || host.equals("github.com")
              || host.equals("release-assets.githubusercontent.com")
              || host.equals("objects.githubusercontent.com")))
        throw new IOException("Untrusted update host");
      HttpURLConnection c = (HttpURLConnection) u.openConnection();
      c.setConnectTimeout(20000);
      c.setReadTimeout(30000);
      c.setInstanceFollowRedirects(false);
      c.setRequestProperty("User-Agent", "Bplus-Android-Player");
      if (offset > 0) c.setRequestProperty("Range", "bytes=" + offset + "-");
      int code = c.getResponseCode();
      if (code >= 300 && code < 400) {
        u = new URL(u, c.getHeaderField("Location"));
        c.disconnect();
        continue;
      }
      return c;
    }
    throw new IOException("Too many redirects");
  }

  static void say(Context c, String text, boolean visible) {
    status = text;
    changed();
    if (visible)
      new Handler(Looper.getMainLooper())
          .post(
              () ->
                  android.widget.Toast.makeText(c, text, android.widget.Toast.LENGTH_LONG).show());
  }

  static void check(Context c, boolean manual) {
    check(c, manual, null);
  }

  static void check(Context c, boolean manual, Runnable done) {
    if (busy) {
      if (done != null) done.run();
      return;
    }
    busy = true;
    phase = "checking";
    changed();
    Context app = c.getApplicationContext();
    new Thread(
            () -> {
              try {
                var prefs = app.getSharedPreferences("prefs", 0);
                HttpURLConnection con =
                    connect("https://api.github.com/repos/" + REPO + "/releases?per_page=30", 0);
                if (con.getResponseCode() != 200)
                  throw new IOException("GitHub HTTP " + con.getResponseCode());
                String json;
                try (InputStream in = con.getInputStream()) {
                  ByteArrayOutputStream text = new ByteArrayOutputStream();
                  byte[] buf = new byte[8192];
                  int n;
                  while ((n = in.read(buf)) > 0) {
                    text.write(buf, 0, n);
                    if (text.size() > 3_000_000) throw new IOException("Release list too large");
                  }
                  json = text.toString("UTF-8");
                }
                String installed =
                    app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName;
                Candidate cand =
                    select(new JSONArray(json), installed, prefs.getBoolean("beta", false));
                if (cand == null) {
                  app.getSharedPreferences("updates", 0)
                      .edit()
                      .putBoolean("installOnReturn", false)
                      .apply();
                  availableVersion = "";
                  releaseNotes = "";
                  phase = "current";
                  say(app, "لا يوجد تحديث Android أحدث في القناة المختارة.", manual);
                  return;
                }
                availableVersion = cand.version;
                releaseNotes = cand.notes;
                totalBytes = cand.size;
                changed();
                if (prefs.getBoolean("wifiOnly", true) && !manual) {
                  ConnectivityManager cm =
                      (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
                  NetworkCapabilities n = cm.getNetworkCapabilities(cm.getActiveNetwork());
                  if (n == null || !n.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    phase = "wifi";
                    say(app, "يوجد تحديث. التنزيل في انتظار Wi-Fi.", manual);
                    return;
                  }
                }
                phase = "downloading";
                say(app, "جارٍ تنزيل تحديث B+ " + cand.version, false);
                File file = new File(app.getFilesDir(), "update-" + cand.version + ".apk");
                long have = file.exists() ? file.length() : 0;
                if (have > cand.size) {
                  file.delete();
                  have = 0;
                }
                if (have < cand.size) {
                  con = connect(cand.url, have);
                  int code = con.getResponseCode();
                  if (code != 200 && code != 206) throw new IOException("Download HTTP " + code);
                  boolean append = have > 0 && code == 206;
                  try (InputStream in = con.getInputStream();
                      FileOutputStream out = new FileOutputStream(file, append)) {
                    byte[] buf = new byte[65536];
                    int n;
                    long total = append ? have : 0;
                    while ((n = in.read(buf)) > 0) {
                      total += n;
                      downloadBytes = total;
                      int percent = (int) (total * 100 / cand.size);
                      if (percent != progress) {
                        progress = percent;
                        changed();
                      }
                      if (total > cand.size) throw new IOException("Oversized APK");
                      out.write(buf, 0, n);
                    }
                  }
                }
                if (file.length() != cand.size) throw new IOException("Incomplete download");
                String sha = hash(file);
                if (!cand.digest.isEmpty()
                    && (!cand.digest.startsWith("sha256:")
                        || !sha.equalsIgnoreCase(cand.digest.substring(7)))) {
                  file.delete();
                  throw new IOException("APK digest mismatch");
                }
                phase = "verifying";
                changed();
                verify(app, file);
                phase = "ready";
                progress = 100;
                changed();
                app.getSharedPreferences("updates", 0)
                    .edit()
                    .putString("ready", file.getAbsolutePath())
                    .putString("sha256", sha)
                    .apply();
                notifyReady(app, "تم تنزيل التحديث والتحقق منه. التثبيت متاح من إعدادات B+.");
                if (manual) {
                  new Handler(Looper.getMainLooper()).post(() -> installReady(c));
                } else if (PlaybackService.current == null
                    || PlaybackService.current.track() == null) installReady(app);
                else say(app, "التحديث جاهز. التثبيت بعد إيقاف التشغيل من الإعدادات.", manual);
              } catch (Exception e) {
                phase = "error";
                say(app, "تعذر التحديث: " + e.getMessage(), manual);
              } finally {
                busy = false;
                if (done != null) done.run();
              }
            })
        .start();
  }

  static String hash(File file) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    try (InputStream in = new FileInputStream(file)) {
      byte[] b = new byte[65536];
      int n;
      while ((n = in.read(b)) > 0) md.update(b, 0, n);
    }
    return hex(md.digest());
  }

  static String hex(byte[] x) {
    StringBuilder b = new StringBuilder();
    for (byte v : x) b.append(String.format(java.util.Locale.US, "%02x", v));
    return b.toString();
  }

  static void verify(Context c, File f) throws Exception {
    PackageManager pm = c.getPackageManager();
    PackageInfo
        got = pm.getPackageArchiveInfo(f.getPath(), PackageManager.GET_SIGNING_CERTIFICATES),
        own = pm.getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
    if (got == null
        || !c.getPackageName().equals(got.packageName)
        || got.getLongVersionCode() <= own.getLongVersionCode())
      throw new SecurityException("Wrong package or old version");
    if (got.signingInfo == null || own.signingInfo == null)
      throw new SecurityException("Missing signing identity");
    android.content.pm.Signature[] a = got.signingInfo.getApkContentsSigners(),
        b = own.signingInfo.getApkContentsSigners();
    if (a.length != 1 || b.length != 1 || !a[0].equals(b[0]))
      throw new SecurityException("Signing certificate mismatch");
  }

  static void installReady(Context c) {
    if (installing) return;
    installing = true;
    Context app = c.getApplicationContext();
    new Thread(
            () -> {
              try {
                PlaybackService svc = PlaybackService.current;
                if (svc != null && svc.track() != null && svc.playing) {
                  notifyReady(app, "أوقف التشغيل أولًا، ثم ثبّت التحديث من إعدادات B+.");
                  return;
                }
                var prefs = app.getSharedPreferences("updates", 0);
                File f = new File(prefs.getString("ready", "/nonexistent"));
                if (!f.exists()) return;
                verify(app, f);
                String sha = hash(f);
                if (!sha.equals(prefs.getString("sha256", "")))
                  throw new SecurityException("Downloaded APK changed");
                if (!app.getPackageManager().canRequestPackageInstalls()) {
                  phase = "permission";
                  changed();
                  notifyReady(app, "اسمح لـ B+ بتثبيت التحديثات من إعدادات أندرويد.");
                  if (c instanceof Activity)
                    ((Activity) c)
                        .runOnUiThread(
                            () ->
                                c.startActivity(
                                    new Intent(
                                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:" + c.getPackageName()))));
                  return;
                }
                boolean userInitiated =
                    app.getSharedPreferences("updates", 0).getBoolean("installOnReturn", false);
                phase = "installing";
                changed();
                app.getSharedPreferences("updates", 0)
                    .edit()
                    .putBoolean("installOnReturn", false)
                    .apply();
                PackageInstaller pi = app.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams p =
                    new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                p.setAppPackageName(app.getPackageName());
                p.setRequireUserAction(
                    userInitiated
                        ? PackageInstaller.SessionParams.USER_ACTION_REQUIRED
                        : PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                int id = pi.createSession(p);
                try (PackageInstaller.Session session = pi.openSession(id);
                    InputStream in = new FileInputStream(f);
                    OutputStream out = session.openWrite("base.apk", 0, f.length())) {
                  byte[] buf = new byte[65536];
                  int n;
                  while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                  session.fsync(out);
                  out.close();
                  in.close();
                  Intent result =
                      new Intent(app, InstallResultReceiver.class)
                          .setAction("com.bplus.player.INSTALL_RESULT");
                  PendingIntent pending =
                      PendingIntent.getBroadcast(
                          app,
                          id,
                          result,
                          PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                  session.commit(pending.getIntentSender());
                }
              } catch (Exception e) {
                phase = "error";
                status = e.getMessage();
                changed();
                notifyReady(app, "تعذر التثبيت: " + e.getMessage());
              } finally {
                installing = false;
              }
            })
        .start();
  }

  static void notifyReady(Context c, String text) {
    NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    nm.createNotificationChannel(
        new NotificationChannel("updates", "تحديثات B+", NotificationManager.IMPORTANCE_DEFAULT));
    Intent i = new Intent(c, MainActivity.class).putExtra("openUpdates", true);
    PendingIntent p =
        PendingIntent.getActivity(
            c, 90, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    try {
      nm.notify(
          90,
          new Notification.Builder(c, "updates")
              .setSmallIcon(android.R.drawable.stat_sys_download_done)
              .setContentTitle("B+ Media Player")
              .setContentText(text)
              .setStyle(new Notification.BigTextStyle().bigText(text))
              .setContentIntent(p)
              .setAutoCancel(true)
              .build());
    } catch (SecurityException e) {
    }
  }

  static void schedule(Context c) {
    JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    if (!c.getSharedPreferences("prefs", 0).getBoolean("autoUpdate", true)) {
      js.cancel(420);
      return;
    }
    js.schedule(
        new JobInfo.Builder(420, new ComponentName(c, UpdateJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(6 * 60 * 60 * 1000L)
            .build());
  }
}
