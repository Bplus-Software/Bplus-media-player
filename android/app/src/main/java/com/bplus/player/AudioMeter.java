package com.bplus.player;

import androidx.media3.common.C;
import androidx.media3.exoplayer.audio.TeeAudioProcessor;
import java.nio.*;

/** Reads the decoded local PCM stream, never the microphone. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class AudioMeter implements TeeAudioProcessor.AudioBufferSink {
  public volatile float[] bands = new float[32], wave = new float[128];
  public volatile float rms;
  private int channels = 2, encoding = C.ENCODING_PCM_16BIT;
  private long last;

  public void flush(int rate, int channels, int encoding) {
    this.channels = channels;
    this.encoding = encoding;
  }

  public void handleBuffer(ByteBuffer source) {
    long now = android.os.SystemClock.elapsedRealtime();
    if (now - last < 35) return;
    last = now;
    ByteBuffer b = source.duplicate().order(ByteOrder.nativeOrder());
    int bytes = encoding == C.ENCODING_PCM_FLOAT ? 4 : 2;
    if (encoding != C.ENCODING_PCM_FLOAT && encoding != C.ENCODING_PCM_16BIT) return;
    int n = Math.min(256, b.remaining() / (bytes * channels));
    if (n < 32) return;
    float[] samples = new float[256];
    double sum = 0;
    for (int i = 0; i < n; i++) {
      float v = 0;
      for (int c = 0; c < channels; c++)
        v += encoding == C.ENCODING_PCM_FLOAT ? b.getFloat() : b.getShort() / 32768f;
      v /= channels;
      samples[i] = v;
      sum += v * v;
    }
    rms = (float) Math.sqrt(sum / n);
    float[] w = new float[128];
    for (int i = 0; i < 128; i++) w[i] = samples[i * n / 128];
    wave = w;
    float[] out = new float[32], old = bands;
    for (int k = 0; k < 32; k++) {
      double re = 0, im = 0;
      double freq = 1 + Math.pow(120, k / 31.0);
      for (int i = 0; i < n; i++) {
        double angle = 2 * Math.PI * freq * i / n,
            window = .5 - .5 * Math.cos(2 * Math.PI * i / (n - 1));
        re += samples[i] * window * Math.cos(angle);
        im -= samples[i] * window * Math.sin(angle);
      }
      float level = (float) Math.min(1, Math.sqrt(re * re + im * im) / n * 10);
      out[k] = Math.max(level, old[k] * .84f);
    }
    bands = out;
  }
}
