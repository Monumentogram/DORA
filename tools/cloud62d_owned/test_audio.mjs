import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const file = new URL('./audio.js', import.meta.url);
assert.ok(fs.existsSync(file), 'Audio helpers must exist');
const {encodeWav, validateTimings, joinChunks} = await import('data:text/javascript;base64,' + fs.readFileSync(file).toString('base64'));
const wav = new DataView(encodeWav(new Float32Array([-1, 0, 1, 2]), 16000));
assert.equal(wav.getUint32(24, true), 16000);
assert.equal(wav.getUint16(22, true), 1);
assert.equal(wav.getUint16(34, true), 16);
assert.equal(wav.getUint32(40, true), 8);
assert.deepEqual([44,46,48,50].map(i=>wav.getInt16(i,true)),[-32768,0,32767,32767]);
assert.deepEqual([...joinChunks([new Float32Array([1,2]),new Float32Array([3])])], [1,2,3]);
assert.throws(()=>encodeWav(new Float32Array([NaN]),16000));
assert.throws(()=>encodeWav(new Float32Array([]),16000));
const words=[{text:'hello',start_us:0,end_us:400000},{text:'world',start_us:450000,end_us:1000000}];
assert.equal(validateTimings(words,['hello','world'],1000000),true);
for(const change of [{start_us:null},{start_us:NaN},{start_us:-1},{end_us:1000001},{end_us:100000},{text:'wrong'}]) {
  assert.throws(()=>validateTimings([words[0],{...words[1],...change}],['hello','world'],1000000));
}
assert.throws(()=>validateTimings(words.slice(0,1),['hello','world'],1000000));
// The audio thread must enforce the frame cap even if the UI thread is delayed.
const messages=[];let Processor;
vm.runInNewContext(fs.readFileSync(new URL('./capture-worklet.js',import.meta.url),'utf8'),{
  AudioWorkletProcessor:class{constructor(){this.port={postMessage:value=>messages.push(value)};}},
  registerProcessor:(_name,implementation)=>{Processor=implementation;},Float32Array,sampleRate:48000
});
const processor=new Processor();
processor.port.onmessage({data:{command:'start',maxFrames:200}});
for(let i=0;i<8;i++)processor.process([[new Float32Array(128).fill(.5)]]);
assert.equal(messages.reduce((sum,item)=>sum+(item.samples?.length||0),0),200);
assert.equal(messages.filter(item=>item.event==='limit').length,1);
// A page already open during deployment can still use the previous start message.
const legacy=new Processor();legacy.port.onmessage({data:'start'});legacy.process([[new Float32Array(128)]]);
assert.equal(messages.reduce((sum,item)=>sum+(item.samples?.length||0),0),328);
console.log('Audio encoding, capture ordering, and human timing validation: PASS');
