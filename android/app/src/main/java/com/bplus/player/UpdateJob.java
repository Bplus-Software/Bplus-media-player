package com.bplus.player;

import android.app.job.*;

public class UpdateJob extends JobService {
  public boolean onStartJob(JobParameters p) {
    if (!getSharedPreferences("prefs", 0).getBoolean("autoUpdate", true)) return false;
    UpdateEngine.check(this, false, () -> jobFinished(p, false));
    return true;
  }

  public boolean onStopJob(JobParameters p) {
    return true;
  }
}
