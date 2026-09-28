export function joinChunks(chunks) {
  const result = new Float32Array(chunks.reduce((n, c) => n + c.length, 0));
  let offset = 0;
  for (const chunk of chunks) { result.set(chunk, offset); offset += chunk.length; }
  return result;
}

export function encodeWav(samples, rate) {
  if (!samples.length || !Number.isInteger(rate) || rate < 8000 || rate > 192000) throw Error('INVALID_AUDIO');
  const bytes = new ArrayBuffer(44 + samples.length * 2), view = new DataView(bytes);
  const text = (offset, value) => [...value].forEach((c, i) => view.setUint8(offset + i, c.charCodeAt(0)));
  text(0, 'RIFF'); view.setUint32(4, bytes.byteLength - 8, true); text(8, 'WAVE'); text(12, 'fmt ');
  view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, 1, true);
  view.setUint32(24, rate, true); view.setUint32(28, rate * 2, true); view.setUint16(32, 2, true); view.setUint16(34, 16, true);
  text(36, 'data'); view.setUint32(40, samples.length * 2, true);
  samples.forEach((v, i) => {
    if (!Number.isFinite(v)) throw Error('NONFINITE_AUDIO');
    v = Math.max(-1, Math.min(1, v));
    view.setInt16(44 + i * 2, Math.round(v * (v < 0 ? 32768 : 32767)), true);
  });
  return bytes;
}

export function validateTimings(words, expected, durationUs) {
  if (!words.length || words.length !== expected.length) throw Error('Все слова должны иметь отметки.');
  let previousStart = -1, previousEnd = -1;
  words.forEach((w, i) => {
    if (w.text !== expected[i] || !Number.isInteger(w.start_us) || !Number.isInteger(w.end_us) ||
        w.start_us < 0 || w.end_us < w.start_us || w.end_us > durationUs ||
        w.start_us < previousStart || w.end_us < previousEnd) throw Error(`Проверьте границы слова ${i + 1}: ${w.text}`);
    previousStart = w.start_us; previousEnd = w.end_us;
  });
  return true;
}
