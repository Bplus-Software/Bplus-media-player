package com.bplus.player;

import android.net.Uri;

public class Track {
  public final Uri uri;
  public final String name;
  public final boolean audio;

  public Track(Uri u, String n) {
    uri = u;
    name = n;
    String x = n.toLowerCase();
    audio = x.matches(".*\\.(mp3|m4a|flac|aac|ogg|opus|wav|wma|ape|ac3|dts|alac|aiff)$");
  }
}
