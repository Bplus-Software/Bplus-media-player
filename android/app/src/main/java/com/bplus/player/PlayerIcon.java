package com.bplus.player;

import android.graphics.*;
import android.graphics.drawable.Drawable;

public class PlayerIcon extends Drawable {
  final String kind;
  final Paint p = new Paint(3);
  final int color;

  public PlayerIcon(String k, int c) {
    kind = k;
    color = c;
  }

  void line(Canvas c, float... points) {
    for (int i = 0; i + 3 < points.length; i += 2)
      c.drawLine(points[i], points[i + 1], points[i + 2], points[i + 3], p);
  }

  void rect(Canvas c, float l, float t, float r, float b, float radius) {
    c.drawRoundRect(l, t, r, b, radius, radius, p);
  }

  public void draw(Canvas real) {
    Canvas c = real;
    int save = c.save();
    Rect b = getBounds();
    c.translate(b.left, b.top);
    c.scale(b.width() / 24f, b.height() / 24f);
    if (!kind.equals("play") && !kind.equals("pause") && DesktopGlyph.draw(c, kind, color)) {
      c.restoreToCount(save);
      return;
    }
    p.setColor(color);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(1.7f);
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setStrokeJoin(Paint.Join.ROUND);
    switch (kind) {
      case "close":
        line(c, 6, 6, 18, 18);
        line(c, 18, 6, 6, 18);
        break;
      case "camera":
        rect(c, 2, 6, 22, 21, 3);
        line(c, 7, 6, 9, 3, 15, 3, 17, 6);
        c.drawCircle(12, 13, 4, p);
        break;
      case "clock":
        c.drawCircle(12, 12, 9, p);
        line(c, 12, 6, 12, 12, 16, 14);
        break;
      case "eyeoff":
        c.drawOval(2, 6, 22, 18, p);
        line(c, 3, 3, 21, 21);
        break;
      case "volume":
        line(c, 3, 9, 7, 9, 12, 4, 12, 20, 7, 15, 3, 15, 3, 9);
        c.drawArc(11, 6, 21, 18, -60, 120, false, p);
        break;
      case "ratio":
        rect(c, 2, 5, 22, 19, 2);
        line(c, 5, 9, 5, 8, 8, 8);
        line(c, 19, 15, 19, 16, 16, 16);
        break;
      case "rotate":
        c.drawArc(3, 3, 21, 21, 35, 285, false, p);
        line(c, 20, 4, 20, 10, 14, 10);
        break;
      case "heart":
        {
          Path q = new Path();
          q.moveTo(12, 21);
          q.cubicTo(-5, 10, 4, 0, 12, 7);
          q.cubicTo(20, 0, 29, 10, 12, 21);
          c.drawPath(q, p);
          break;
        }
      case "minus":
        line(c, 5, 12, 19, 12);
        break;
      case "up":
        line(c, 5, 14, 12, 7, 19, 14);
        break;
      case "down":
        line(c, 5, 10, 12, 17, 19, 10);
        break;
      case "download":
        line(c, 12, 3, 12, 16);
        line(c, 7, 11, 12, 16, 17, 11);
        line(c, 4, 18, 4, 21, 20, 21, 20, 18);
        break;
      case "palette":
        c.drawCircle(12, 12, 9, p);
        for (float[] q : new float[][] {{8, 8}, {15, 7}, {18, 12}, {7, 14}})
          c.drawCircle(q[0], q[1], 1, p);
        break;
      case "speaker-waves":
        Path speaker = new Path();
        speaker.moveTo(2, 10);
        speaker.lineTo(5, 10);
        speaker.lineTo(9, 6);
        speaker.lineTo(9, 18);
        speaker.lineTo(5, 14);
        speaker.lineTo(2, 14);
        speaker.close();
        c.drawPath(speaker, p);
        for (int wave = 0; wave < 3; wave++) {
          float radius = 5 + wave * 4;
          c.drawArc(9 - radius, 12 - radius, 9 + radius, 12 + radius, -50, 100, false, p);
        }
        break;
      case "equalizer":
        line(c, 5, 3, 5, 21);
        line(c, 12, 3, 12, 21);
        line(c, 19, 3, 19, 21);
        rect(c, 2, 7, 8, 11, 2);
        rect(c, 9, 14, 15, 18, 2);
        rect(c, 16, 5, 22, 9, 2);
        break;
      case "ab":
        c.drawArc(2, 2, 22, 22, 35, 285, false, p);
        line(c, 20, 3, 20, 8, 15, 8);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(8);
        c.drawText("A·B", 12, 15, p);
        break;
      case "rewind":
      case "forward":
        c.drawArc(3, 3, 21, 21, kind.equals("rewind") ? 145 : 35, 285, false, p);
        if (kind.equals("rewind")) line(c, 3, 4, 3, 10, 9, 10);
        else line(c, 21, 4, 21, 10, 15, 10);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(9);
        c.drawText("10", 12, 16, p);
        break;
      case "back":
        line(c, 15, 4, 7, 12, 15, 20);
        break;
      case "search":
        c.drawCircle(10, 10, 6, p);
        line(c, 15, 15, 21, 21);
        break;
      case "folder":
        {
          Path q = new Path();
          q.moveTo(2, 6);
          q.lineTo(9, 6);
          q.lineTo(11, 9);
          q.lineTo(22, 9);
          q.lineTo(22, 21);
          q.lineTo(2, 21);
          q.close();
          c.drawPath(q, p);
          break;
        }
      case "music":
        line(c, 9, 18, 9, 5, 21, 3, 21, 16);
        line(c, 9, 9, 21, 7);
        c.drawOval(3, 16, 9, 21, p);
        c.drawOval(15, 14, 21, 19, p);
        break;
      case "repeat":
        line(c, 5, 7, 19, 7, 16, 4);
        line(c, 19, 17, 5, 17, 8, 20);
        line(c, 20, 8, 20, 13);
        line(c, 4, 16, 4, 11);
        break;
      case "shuffle":
        line(c, 3, 5, 7, 5, 17, 19, 21, 19, 18, 16);
        line(c, 3, 19, 7, 19, 17, 5, 21, 5, 18, 8);
        break;
      case "play":
        {
          Path q = new Path();
          q.moveTo(8, 5);
          q.lineTo(20, 12);
          q.lineTo(8, 19);
          q.close();
          p.setStyle(Paint.Style.FILL);
          c.drawPath(q, p);
          break;
        }
      case "pause":
        p.setStrokeWidth(4);
        line(c, 8, 4, 8, 20);
        line(c, 16, 4, 16, 20);
        break;
      case "gear":
        c.drawCircle(12, 12, 7, p);
        c.drawCircle(12, 12, 2.5f, p);
        for (int i = 0; i < 8; i++) {
          double a = i * Math.PI / 4;
          line(
              c,
              12 + (float) Math.cos(a) * 8,
              12 + (float) Math.sin(a) * 8,
              12 + (float) Math.cos(a) * 10,
              12 + (float) Math.sin(a) * 10);
        }
        break;
      case "queue":
        line(c, 3, 5, 21, 5);
        line(c, 3, 11, 21, 11);
        line(c, 3, 17, 14, 17);
        line(c, 18, 15, 22, 18, 18, 21);
        break;
      case "sun":
        c.drawCircle(12, 12, 4, p);
        for (int i = 0; i < 8; i++) {
          double a = i * Math.PI / 4;
          line(
              c,
              12 + (float) Math.cos(a) * 7,
              12 + (float) Math.sin(a) * 7,
              12 + (float) Math.cos(a) * 10,
              12 + (float) Math.sin(a) * 10);
        }
        break;
      case "sub":
        rect(c, 2, 4, 22, 20, 3);
        line(c, 6, 10, 10, 10);
        line(c, 14, 10, 18, 10);
        line(c, 6, 15, 9, 15);
        line(c, 12, 15, 18, 15);
        break;
      case "speed":
        c.drawArc(3, 3, 21, 21, 140, 260, false, p);
        line(c, 12, 12, 17, 7);
        c.drawCircle(12, 12, 1, p);
        break;
      case "pip":
        rect(c, 2, 4, 22, 20, 2);
        rect(c, 12, 11, 20, 18, 1);
        break;
      case "full":
        line(c, 8, 3, 3, 3, 3, 8);
        line(c, 16, 3, 21, 3, 21, 8);
        line(c, 3, 16, 3, 21, 8, 21);
        line(c, 21, 16, 21, 21, 16, 21);
        break;
      case "lock":
        rect(c, 5, 10, 19, 22, 2);
        c.drawArc(8, 2, 16, 16, 180, 180, false, p);
        break;
      case "next":
        line(c, 19, 4, 19, 20);
        line(c, 5, 4, 16, 12, 5, 20, 5, 4);
        break;
      case "previous":
        line(c, 5, 4, 5, 20);
        line(c, 19, 4, 8, 12, 19, 20, 19, 4);
        break;
      case "more":
        p.setStyle(Paint.Style.FILL);
        for (int y = 5; y < 21; y += 7) c.drawCircle(12, y, 1.4f, p);
        break;
    }
    c.restoreToCount(save);
  }

  public void setAlpha(int a) {
    p.setAlpha(a);
  }

  public void setColorFilter(ColorFilter f) {
    p.setColorFilter(f);
  }

  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }

  public int getIntrinsicWidth() {
    return 24;
  }

  public int getIntrinsicHeight() {
    return 24;
  }
}
