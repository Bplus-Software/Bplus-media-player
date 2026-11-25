package com.bplus.player;

import static org.junit.Assert.*;

import org.json.*;
import org.junit.Test;

public class UpdateSelectorTest {
  JSONObject asset(String name, String tag) throws Exception {
    return new JSONObject()
        .put("name", name)
        .put("size", 500)
        .put(
            "browser_download_url",
            "https://github.com/Bplus-Software/Bplus-media-player/releases/download/"
                + tag
                + "/"
                + name);
  }

  JSONObject rel(String tag, boolean beta, String... names) throws Exception {
    JSONArray a = new JSONArray();
    for (String n : names) a.put(asset(n, tag));
    return new JSONObject()
        .put("tag_name", tag)
        .put("prerelease", beta)
        .put("draft", false)
        .put("assets", a);
  }

  @Test
  public void choosesApkRegardlessOfOrder() throws Exception {
    String apk = "Bplus-Media-Player-1.0.2.apk";
    for (boolean reverse : new boolean[] {true, false}) {
      JSONArray r =
          new JSONArray()
              .put(
                  reverse
                      ? rel("v1.0.2", false, apk, "Bplus-1.0.2-Setup.exe")
                      : rel("v1.0.2", false, "Bplus-1.0.2-Setup.exe", apk));
      assertEquals(apk, UpdateEngine.select(r, "1.0.1", false).name);
    }
  }

  @Test
  public void prereleaseOptIn() throws Exception {
    JSONArray r = new JSONArray().put(rel("v1.0.2", true, "Bplus-Media-Player-1.0.2.apk"));
    assertNull(UpdateEngine.select(r, "1.0.1", false));
    assertNotNull(UpdateEngine.select(r, "1.0.1", true));
  }

  @Test
  public void neverSameOrDowngrade() throws Exception {
    JSONArray r = new JSONArray().put(rel("v1.0.1", false, "Bplus-Media-Player-1.0.1.apk"));
    assertNull(UpdateEngine.select(r, "1.0.1", true));
    assertNull(UpdateEngine.select(r, "1.0.2", true));
  }

  @Test
  public void missingOrDuplicateFailClosed() throws Exception {
    assertNull(
        UpdateEngine.select(
            new JSONArray().put(rel("v1.0.2", false, "Bplus-1.0.2-Setup.exe")), "1.0.1", true));
    assertNull(
        UpdateEngine.select(
            new JSONArray()
                .put(
                    rel(
                        "v1.0.2",
                        false,
                        "Bplus-Media-Player-1.0.2.apk",
                        "Bplus-Media-Player-1.0.2.apk")),
            "1.0.1",
            true));
  }

  @Test
  public void draftAndForeignUrlIgnored() throws Exception {
    JSONObject r = rel("v1.0.2", false, "Bplus-Media-Player-1.0.2.apk");
    r.put("draft", true);
    assertNull(UpdateEngine.select(new JSONArray().put(r), "1.0.1", true));
    r.put("draft", false);
    r.getJSONArray("assets")
        .getJSONObject(0)
        .put("browser_download_url", "https://example.com/file.apk");
    assertNull(UpdateEngine.select(new JSONArray().put(r), "1.0.1", true));
  }

  @Test
  public void highestValidNotApiOrder() throws Exception {
    JSONArray r =
        new JSONArray()
            .put(rel("v1.0.2", false, "Bplus-Media-Player-1.0.2.apk"))
            .put(rel("v1.0.8", false, "Bplus-1.0.8-Setup.exe"))
            .put(rel("v1.0.3", false, "Bplus-Media-Player-1.0.3.apk"));
    assertEquals("1.0.3", UpdateEngine.select(r, "1.0.1", false).version);
  }

  @Test
  public void emptyInvalidTagAndVersionMath() throws Exception {
    assertNull(UpdateEngine.select(new JSONArray(), "1.0.1", true));
    assertNull(
        UpdateEngine.select(
            new JSONArray().put(rel("v99.0.0-beta", false, "x.apk")), "1.0.1", true));
    assertTrue(UpdateEngine.compare("1.0.10", "1.0.9") > 0);
  }

  @Test
  public void sharedReleaseWithoutApkIsUpToDate() throws Exception {
    JSONArray r =
        new JSONArray()
            .put(rel("v1.0.11", false, "Bplus-1.0.11-Setup.exe", "Bplus-Media-Player-1.0.11-Setup.exe", "SHA256SUMS.txt"));
    assertNull(UpdateEngine.select(r, "1.0.10", false));
    r.put(rel("v1.0.11", false, "Bplus-1.0.11-Setup.exe", "Bplus-Media-Player-1.0.11.apk", "SHA256SUMS.txt"));
    assertEquals("Bplus-Media-Player-1.0.11.apk", UpdateEngine.select(r, "1.0.10", false).name);
  }
}
