package com.monumentogram.dora.vadadmission;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.util.Log;
import android.widget.TextView;
import com.k2fsa.sherpa.onnx.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.JSONObject;

/** Disposable admission harness. No microphone permission or DORA data access. */
public final class SmokeActivity extends Activity {
    private static final String MODEL_SHA = "1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("Isolated VAD runtime admission in progress");
        setContentView(view);
        new Thread(() -> {
            JSONObject result = new JSONObject();
            Vad vad = null;
            try {
                byte[] modelBytes = readAsset("silero.onnx");
                String modelSha = hex(MessageDigest.getInstance("SHA-256").digest(modelBytes));
                check(modelBytes.length == 2327524 && MODEL_SHA.equals(modelSha));
                File model = new File(getFilesDir(), "silero.onnx");
                Files.write(model.toPath(), modelBytes);
                Arrays.fill(modelBytes, (byte)0);
                LibraryLoader.setAutoLoadEnabled(false);
                System.loadLibrary("onnxruntime");
                System.loadLibrary("sherpa-onnx-jni");
                result.put("sherpaVersion", VersionInfo.getVersion());
                result.put("upstreamEmbeddedGitLabel", VersionInfo.getGitSha1());
                result.put("onnxruntimeVersion", VersionInfo.getOnnxruntimeVersion());
                check("1.13.8".equals(VersionInfo.getVersion()));
                check("1.28.2".equals(VersionInfo.getOnnxruntimeVersion()));
                result.put("abi", Build.SUPPORTED_ABIS[0]);
                result.put("api", Build.VERSION.SDK_INT);
                result.put("modelSha256", modelSha);
                result.put("runtimeAarSha256", new String(readAsset("candidate.sha256"), "UTF-8").trim());
                VadModelConfig config = VadModelConfig.builder().setDebug(false)
                    .setSampleRate(16000).setNumThreads(1).setProvider("cpu")
                    .setSileroVadModelConfig(SileroVadModelConfig.builder().setModel(model.getAbsolutePath()).build()).build();
                vad = new Vad(config);
                result.put("initialized", true);
                float[] zeros = new float[512];
                int silencePositive = 0;
                for (int i=0;i<100;i++) {
                    vad.acceptWaveform(zeros);
                    if (vad.isSpeechDetected()) silencePositive++;
                }
                check(silencePositive == 0);
                result.put("silenceWindows", 100);
                result.put("silencePositiveWindows", silencePositive);
                vad.reset();
                byte[] pcm = readAsset("speech.pcm");
                check(pcm.length > 16000 && pcm.length % 2 == 0);
                result.put("fixtureSha256", hex(MessageDigest.getInstance("SHA-256").digest(pcm)));
                ByteBuffer samples = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
                int windows=0, speechPositive=0;
                while (samples.remaining() >= 1024) {
                    float[] frame = new float[512];
                    for (int i=0;i<512;i++) frame[i] = samples.getShort() / 32768.0f;
                    vad.acceptWaveform(frame);
                    if (vad.isSpeechDetected()) speechPositive++;
                    Arrays.fill(frame, 0.0f);
                    windows++;
                }
                Arrays.fill(pcm, (byte)0);
                check(speechPositive > 0);
                result.put("speechWindows", windows);
                result.put("speechPositiveWindows", speechPositive);
                vad.flush();
                int segments=0;
                while (!vad.empty()) {
                    SpeechSegment segment = vad.front();
                    check(segment.getSamples().length > 0 && segment.getStart() >= 0);
                    Arrays.fill(segment.getSamples(), 0.0f);
                    vad.pop(); segments++;
                }
                check(segments > 0);
                result.put("speechSegments", segments);
                vad.reset();
                for (int i=0;i<100;i++) vad.acceptWaveform(zeros);
                check(!vad.isSpeechDetected());
                result.put("resetToSilence", true);
                Set<String> libraries = new TreeSet<>();
                for (String line : Files.readAllLines(Paths.get("/proc/self/maps"))) {
                    if (line.contains("libonnxruntime.so")) libraries.add("libonnxruntime.so");
                    if (line.contains("libsherpa-onnx-jni.so")) libraries.add("libsherpa-onnx-jni.so");
                    check(!line.toLowerCase(Locale.ROOT).matches(".*(espeak|piper).*"));
                }
                check(libraries.size() == 2);
                result.put("loadedCandidateLibraries", new org.json.JSONArray(libraries));
                vad.release(); vad = null;
                result.put("closed", true);
                result.put("result", "PASS_ISOLATED_RUNTIME_SMOKE");
            } catch (Throwable error) {
                try { result.put("result", "FAIL"); result.put("failureType", error.getClass().getSimpleName()); }
                catch (Exception ignored) { }
            } finally {
                if (vad != null) vad.release();
            }
            final String receipt = result.toString();
            try { Files.write(new File(getFilesDir(), "receipt.json").toPath(), receipt.getBytes("UTF-8")); }
            catch (Exception ignored) { }
            Log.i("DoraVadAdmission", receipt);
            runOnUiThread(() -> view.setText(receipt));
        }, "isolated-vad-admission").start();
    }
    private static void check(boolean condition) { if (!condition) throw new IllegalStateException("ADMISSION_CHECK_FAILED"); }
    private byte[] readAsset(String name) throws IOException {
        try (InputStream input = getAssets().open(name); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n=input.read(buffer)) != -1) output.write(buffer,0,n);
            Arrays.fill(buffer,(byte)0); return output.toByteArray();
        }
    }
    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }
}
