package com.bplus.player;

import android.app.*;
import android.content.*;
import android.content.pm.*;

public class InstallResultReceiver extends BroadcastReceiver {
  public void onReceive(Context c, Intent i) {
    int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
      Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
      if (confirm == null) return;
      UpdateEngine.phase = "confirm";
      UpdateEngine.changed();
      Activity foreground = UpdateEngine.foregroundActivity.get();
      if (foreground != null && !foreground.isFinishing()) {
        try {
          foreground.startActivity(confirm);
          return;
        } catch (RuntimeException unavailable) {
        }
      }
      PendingIntent p =
          PendingIntent.getActivity(
              c,
              91,
              confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
              PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      NotificationManager nm =
          (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
      nm.createNotificationChannel(
          new NotificationChannel("updates", "تحديثات B+", NotificationManager.IMPORTANCE_DEFAULT));
      try {
        nm.notify(
            91,
            new Notification.Builder(c, "updates")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("B+: تأكيد تثبيت التحديث")
                .setContentText("اضغط لمراجعة تثبيت التحديث في أندرويد")
                .setContentIntent(p)
                .setAutoCancel(true)
                .build());
      } catch (SecurityException e) {
      }
    } else if (status != PackageInstaller.STATUS_SUCCESS) {
      UpdateEngine.phase = "error";
      UpdateEngine.status = "لم يتم تثبيت التحديث. يمكنك المحاولة مرة أخرى.";
      UpdateEngine.changed();
      UpdateEngine.notifyReady(c, UpdateEngine.status);
    } else {
      UpdateEngine.phase = "installed";
      UpdateEngine.changed();
      c.getSharedPreferences("updates", 0)
          .edit()
          .remove("ready")
          .remove("sha256")
          .putBoolean("installOnReturn", false)
          .apply();
    }
  }
}
