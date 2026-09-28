// Native AudioContext-rate mono capture. No audio leaves this browser/server pair.
class Capture extends AudioWorkletProcessor {
  constructor() {
    super(); this.recording = false; this.blocks = 0; this.frames=0; this.maxFrames=0;
    this.port.onmessage = ({data}) => {
      const legacy=data==='start';
      this.recording = legacy || (data?.command === 'start' && Number.isInteger(data.maxFrames) && data.maxFrames>0);
      if(this.recording){this.frames=0;this.maxFrames=legacy?Math.floor(sampleRate*60):data.maxFrames;}
      this.port.postMessage({event: this.recording ? 'started' : 'stopped'});
    };
  }
  process(inputs) {
    const channels = inputs[0];
    if (!channels?.length) return true;
    const mono = new Float32Array(channels[0].length);
    for (const channel of channels) for (let i = 0; i < mono.length; i++) mono[i] += channel[i] / channels.length;
    if (this.recording) {
      const remaining=this.maxFrames-this.frames;
      const captured=remaining<mono.length?mono.slice(0,remaining):mono;
      this.frames+=captured.length;
      this.port.postMessage({samples: captured}, [captured.buffer]);
      if(this.frames>=this.maxFrames){this.recording=false;this.port.postMessage({event:'limit'});}
    }
    if (++this.blocks % 16 === 0) {
      // Calculate independently of the transferred buffer.
      let peak = 0;
      for (const channel of channels) for (const value of channel) peak = Math.max(peak, Math.abs(value));
      this.port.postMessage({peak});
    }
    return true;
  }
}
registerProcessor('dora-capture', Capture);
