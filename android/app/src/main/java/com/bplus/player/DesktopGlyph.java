package com.bplus.player;

import android.content.Context;
import android.graphics.*;
import java.io.*;
import org.json.*;
import org.xmlpull.v1.*;

/** Offline glyphs from the desktop app, with the original licenses. */
final class DesktopGlyph {
  static JSONObject families;
  static Context app;
  static String family = "phosphor-fill";
  static final java.util.Map<String, Picture> cache = new java.util.HashMap<>();

  static void init(Context c) {
    app = c.getApplicationContext();
    family = c.getSharedPreferences("prefs", 0).getString("iconStyle", "phosphor-fill");
    if (families == null)
      try (InputStream in = c.getAssets().open("icons/desktop.json")) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        families = new JSONObject(out.toString("UTF-8"));
      } catch (Exception ignored) {
      }
  }

  static String map(String k) {
    switch (k) {
      case "previous":
        return "prev";
      case "queue":
        return "playlist";
      case "folder":
        return "open";
      case "more":
        return "menu";
      case "sub":
        return "nav-subtitles";
      case "ratio":
        return "nav-video";
      case "download":
        return "nav-updates";
      case "equalizer":
        return "sliders";
      case "palette":
        return "nav-appearance";
      case "up":
        return "caret-up";
      case "down":
        return "caret-down";
      default:
        return k;
    }
  }

  static boolean draw(Canvas canvas, String kind, int color) {
    if (families == null) return false;
    String key = map(kind), id = family + "|" + key + "|" + color;
    Picture picture = cache.get(id);
    if (picture != null) {
      canvas.drawPicture(picture);
      return true;
    }
    try {
      JSONObject f = families.getJSONObject(family),
          glyph = f.getJSONObject("glyphs").optJSONObject(key);
      if (glyph == null) return false;
      picture = new Picture();
      Canvas c = picture.beginRecording(24, 24);
      String[] vb = glyph.getString("vb").split(" ");
      c.scale(24 / Float.parseFloat(vb[2]), 24 / Float.parseFloat(vb[3]));
      c.translate(-Float.parseFloat(vb[0]), -Float.parseFloat(vb[1]));
      JSONObject attrs = f.getJSONObject("attrs");
      XmlPullParser xml = android.util.Xml.newPullParser();
      xml.setInput(new StringReader("<svg>" + glyph.getString("b") + "</svg>"));
      int event;
      while ((event = xml.next()) != XmlPullParser.END_DOCUMENT) {
        if (event != XmlPullParser.START_TAG) continue;
        String tag = xml.getName();
        Paint p = new Paint(3);
        p.setColor(color);
        String opacity = xml.getAttributeValue(null, "opacity");
        if (opacity != null) p.setAlpha((int) (255 * Float.parseFloat(opacity)));
        String fill = xml.getAttributeValue(null, "fill");
        if (fill == null) fill = attrs.optString("fill", "currentColor");
        p.setStyle(fill.equals("none") ? Paint.Style.STROKE : Paint.Style.FILL);
        String stroke = xml.getAttributeValue(null, "stroke-width");
        p.setStrokeWidth(
            stroke != null ? Float.parseFloat(stroke) : (float) attrs.optDouble("stroke-width", 2));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        if (tag.equals("path")) {
          Path path =
              androidx.core.graphics.PathParser.createPathFromPathData(
                  xml.getAttributeValue(null, "d"));
          String rule = xml.getAttributeValue(null, "fill-rule");
          if ("evenodd".equals(rule)) path.setFillType(Path.FillType.EVEN_ODD);
          c.drawPath(path, p);
        } else if (tag.equals("circle"))
          c.drawCircle(num(xml, "cx"), num(xml, "cy"), num(xml, "r"), p);
        else if (tag.equals("rect"))
          c.drawRoundRect(
              num(xml, "x"),
              num(xml, "y"),
              num(xml, "x") + num(xml, "width"),
              num(xml, "y") + num(xml, "height"),
              num(xml, "rx"),
              num(xml, "rx"),
              p);
        else if (tag.equals("line"))
          c.drawLine(num(xml, "x1"), num(xml, "y1"), num(xml, "x2"), num(xml, "y2"), p);
        else if (tag.equals("polyline") || tag.equals("polygon")) {
          String[] points = xml.getAttributeValue(null, "points").trim().split("[ ,]+");
          Path path = new Path();
          for (int k = 0; k + 1 < points.length; k += 2) {
            float x = Float.parseFloat(points[k]), y = Float.parseFloat(points[k + 1]);
            if (k == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
          }
          if (tag.equals("polygon")) path.close();
          c.drawPath(path, p);
        }
      }
      picture.endRecording();
      cache.put(id, picture);
      canvas.drawPicture(picture);
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  static float num(XmlPullParser x, String key) {
    String v = x.getAttributeValue(null, key);
    return v == null ? 0 : Float.parseFloat(v);
  }
}
