package com.auction.ui;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Bộ tổng hợp âm thanh UI cao cấp thuần Java (không cần file mp3 ngoài).
 * Tạo tiếng chuông thủy tinh mềm mượt khi đặt giá, thắng phiên, duyệt phòng hoặc thả cảm xúc.
 * Tự động im lặng an toàn nếu máy không có thiết bị âm thanh hoặc người dùng bấm Tắt tiếng.
 */
public final class SoundEffects {

    private static volatile boolean enabled = true;

    private SoundEffects() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean toggleEnabled() {
        enabled = !enabled;
        if (enabled) {
            playReactionTap();
        }
        return enabled;
    }

    public static void playBidPop() {
        playChordAsync(new double[]{587.33, 880.0}, 110, 0.14);
    }

    public static void playReactionTap() {
        playChordAsync(new double[]{987.77, 1318.5}, 75, 0.10);
    }

    public static void playApproveChime() {
        playArpeggioAsync(new double[]{523.25, 659.25, 783.99, 1046.5}, 70, 0.15);
    }

    public static void playWinFanfare() {
        playArpeggioAsync(new double[]{523.25, 659.25, 783.99, 987.77, 1318.5}, 85, 0.17);
    }

    private static void playChordAsync(double[] freqs, int durationMs, double volume) {
        if (!enabled) {
            return;
        }
        Thread t = new Thread(() -> renderTone(freqs, durationMs, volume), "bidx-sfx");
        t.setDaemon(true);
        t.start();
    }

    private static void playArpeggioAsync(double[] notes, int stepMs, double volume) {
        if (!enabled) {
            return;
        }
        Thread t = new Thread(() -> {
            for (double freq : notes) {
                renderTone(new double[]{freq, freq * 2.0}, stepMs, volume);
            }
        }, "bidx-sfx-arp");
        t.setDaemon(true);
        t.start();
    }

    private static void renderTone(double[] freqs, int durationMs, double volume) {
        float sampleRate = 22050f;
        int samples = (int) (sampleRate * durationMs / 1000.0);
        byte[] buffer = new byte[samples * 2];

        for (int i = 0; i < samples; i++) {
            double progress = (double) i / samples;
            // Envelope attack nhanh + decay mượt như tiếng chuông pha lê
            double envelope = Math.sin(Math.min(1.0, progress * 12.0) * Math.PI * 0.5) * Math.pow(1.0 - progress, 1.8);
            double wave = 0.0;
            for (int fIdx = 0; fIdx < freqs.length; fIdx++) {
                double weight = (fIdx == 0) ? 0.72 : 0.28 / freqs.length;
                wave += weight * Math.sin(2.0 * Math.PI * freqs[fIdx] * i / sampleRate);
            }
            short sample = (short) (wave * envelope * volume * Short.MAX_VALUE);
            buffer[i * 2] = (byte) (sample & 0xFF);
            buffer[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format);
            line.start();
            line.write(buffer, 0, buffer.length);
            line.drain();
        } catch (Exception ignored) {
            // Bỏ qua an toàn trên môi trường không có audio output
        }
    }
}
