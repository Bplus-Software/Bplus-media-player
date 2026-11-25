package com.bplus.player;

import android.content.*;
import android.graphics.*;
import android.view.*;

public final class VisualizerView extends View {
  final MainActivity a;
  final String previewStyle;
  final Paint p = new Paint(3);
  static final String[] STYLES = {"cover", "spectrum", "wave", "line", "dots", "ripple", "logo"};

  VisualizerView(MainActivity a, String preview) {
    super(a);
    this.a = a;
    previewStyle = preview;
  }

  protected void onDraw(Canvas c) {
    boolean preview = previewStyle != null, playing = a.s != null && a.s.player.isPlaying();
    String style = preview ? previewStyle : a.prefs.getString("vizStyle", "cover");
    float size =
        preview
            ? 1
            : a.prefs.getInt("vizSize", 0) == 0
                ? .65f
                : a.prefs.getInt("vizSize", 0) == 1 ? .82f : 1;
    float w = getWidth() * size, h = getHeight() * size, x = getWidth() / 2f, y = getHeight() / 2f;
    c.save();
    c.translate(x - w / 2, y - h / 2);
    p.setColor(a.accent);
    p.setStyle(Paint.Style.FILL);
    p.setStrokeWidth(a.dp(2));
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setStrokeJoin(Paint.Join.ROUND);
    float[] bands = a.s == null ? new float[32] : a.s.player.meter.bands,
        wave = a.s == null ? new float[128] : a.s.player.meter.wave;
    float rms = a.s == null ? 0 : a.s.player.meter.rms;
    if (style.equals("cover")) {
      float side = Math.min(w, h) * .56f;
      RectF box =
          new RectF(
              (w - side) / 2,
              (h - side) / 2 - side * .1f,
              (w + side) / 2,
              (h + side) / 2 - side * .1f);
      if (a.cover != null && !preview) c.drawBitmap(a.cover, null, box, p);
      else {
        p.setColor(a.panel);
        c.drawRoundRect(box, a.dp(12), a.dp(12), p);
        p.setColor(a.accent);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(side * .32f);
        p.setTypeface(a.font);
        c.drawText("B+", w / 2, h / 2 + side * .03f, p);
      }
      for (int k = 0; k < 24; k++) {
        float v =
            preview ? (float) (.15 + .65 * Math.abs(Math.sin(k * .7))) : playing ? bands[k] : .02f;
        float bw = w * .65f / 24, left = w * .175f + k * bw;
        c.drawRoundRect(
            left, h * .85f - v * h * .15f, left + bw * .65f, h * .85f, a.dp(1), a.dp(1), p);
      }
    } else if (style.equals("spectrum") || style.equals("dots")) {
      int n = style.equals("dots") ? 24 : 32;
      for (int k = 0; k < n; k++) {
        float
            v =
                preview
                    ? (float) (.13 + .7 * Math.abs(Math.sin(k * .44)))
                    : playing ? bands[k] * .8f : .015f,
            bw = w / n,
            left = k * bw,
            high = h * (.03f + v * .65f);
        if (style.equals("dots")) {
          float dot = Math.min(bw * .28f, h / 24);
          for (float z = h * .82f; z > h * .82f - high; z -= dot * 3)
            c.drawCircle(left + bw * .5f, z, dot, p);
        } else
          c.drawRoundRect(
              left + bw * .18f, h * .82f - high, left + bw * .8f, h * .82f, a.dp(2), a.dp(2), p);
      }
    } else if (style.equals("wave") || style.equals("line")) {
      Path path = new Path();
      for (int k = 0; k < 128; k++) {
        float v =
            preview
                ? (float) (Math.sin(k * .3) * (.2 + .18 * Math.sin(k * .07)))
                : playing ? wave[k] : 0;
        float yy = h * .5f - v * h * .65f;
        if (k == 0) path.moveTo(0, yy);
        else path.lineTo(k * w / 127, yy);
      }
      p.setStyle(Paint.Style.STROKE);
      c.drawPath(path, p);
      if (style.equals("wave")) {
        for (int k = 127; k >= 0; k--) {
          float v =
              preview
                  ? (float) (.07 + .3 * Math.abs(Math.sin(k * .3)))
                  : playing ? Math.abs(wave[k]) : 0;
          path.lineTo(k * w / 127, h * .5f + v * h * .65f);
        }
        path.close();
        p.setAlpha(100);
        p.setStyle(Paint.Style.FILL);
        c.drawPath(path, p);
      }
    } else {
      float r = Math.min(w, h) * .33f;
      if (style.equals("ripple")) {
        p.setStyle(Paint.Style.STROKE);
        for (int k = 1; k <= 4; k++) {
          p.setAlpha(255 - k * 35);
          c.drawCircle(w / 2, h / 2, r * k / 4 * (1 + (preview ? .1f : rms)), p);
        }
      } else {
        p.setStyle(Paint.Style.STROKE);
        for (int k = 0; k < 32; k++) {
          double t = k * Math.PI / 16;
          float v =
              preview ? .1f + (float) Math.abs(Math.sin(k)) * .15f : playing ? bands[k] * .4f : 0;
          c.drawLine(
              w / 2 + (float) Math.cos(t) * r,
              h / 2 + (float) Math.sin(t) * r,
              w / 2 + (float) Math.cos(t) * r * (1.08f + v),
              h / 2 + (float) Math.sin(t) * r * (1.08f + v),
              p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(a.font);
        p.setTextSize(r * .6f);
        c.drawText("B+", w / 2, h / 2 + r * .18f, p);
      }
    }
    p.setAlpha(255);
    c.restore();
    if (!preview && playing) postInvalidateDelayed(40);
  }
}
