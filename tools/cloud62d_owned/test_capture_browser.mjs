// Capture test uses an oscillator MediaStream; NEVER reads a hardware microphone.
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const require=createRequire(import.meta.url),{chromium}=require('playwright');
const child=spawn(process.env.DORA_PYTHON||'python',[fileURLToPath(new URL('./test_browser_fixture.py',import.meta.url)),'--record'],{stdio:['ignore','pipe','pipe'],windowsHide:true});
let browser,page,fixtureErrors='';const errors=[],responses=[],requestFailures=[];
child.stderr.on('data',data=>{fixtureErrors=(fixtureErrors+String(data)).slice(-5000);});
try{
  const url=await new Promise((resolve,reject)=>{let text='';child.stdout.on('data',data=>{text+=data;if(text.includes('\n'))resolve(text.trim());});child.stderr.on('data',data=>reject(Error(String(data))));});
  browser=await chromium.launch({channel:'chrome',headless:true});page=await browser.newPage();
  page.on('pageerror',e=>errors.push(e.message));
  page.on('response',r=>{const path=new URL(r.url()).pathname;if(path.startsWith('/api/'))responses.push({path,status:r.status()});});
  page.on('requestfailed',r=>requestFailures.push({path:new URL(r.url()).pathname,error:r.failure()?.errorText}));
  await page.addInitScript(()=>{
    window.captureRequests=0;
    const NativeAudioContext=window.AudioContext;window.testAudioContexts=[];
    window.AudioContext=class extends NativeAudioContext{constructor(...args){super(...args);window.testAudioContexts.push(this);}};
    navigator.mediaDevices.getUserMedia=async()=>{
      window.captureRequests++;const ctx=new AudioContext(),osc=ctx.createOscillator(),dest=ctx.createMediaStreamDestination();
      osc.frequency.value=440;osc.connect(dest);osc.start();await ctx.resume();window.syntheticContext=ctx;return dest.stream;
    };
    navigator.mediaDevices.enumerateDevices=async()=>[{kind:'audioinput',deviceId:'synthetic',label:'Synthetic oscillator (no microphone)'}];
  });
  await page.goto(url);await page.locator('#status').filter({hasText:'Готово'}).waitFor();
  assert.equal(await page.evaluate(()=>window.captureRequests),0);
  await page.locator('#consentCheck').check();await page.locator('#attest').click();
  await page.locator('#status').filter({hasText:'Согласие сохранено'}).waitFor();
  await page.locator('#record').click();await page.locator('#status').filter({hasText:'Идёт запись'}).waitFor();
  assert.equal(await page.locator('#stop').isDisabled(),true);
  // Simulate audio-clock lag while wall time advances; stop eligibility must use samples.
  await page.evaluate(async()=>{const capture=window.testAudioContexts.at(-1);await capture.suspend();await new Promise(resolve=>setTimeout(resolve,1500));await capture.resume();});
  await page.waitForFunction(()=>!document.querySelector('#stop').disabled,{},{timeout:25000});
  await page.locator('#stop').click();await page.locator('#status').filter({hasText:'Запись сохранена'}).waitFor({timeout:15000});
  assert.equal(await page.locator('#record').isDisabled(),true);
  assert.equal(await page.locator('#playback').isVisible(),true);
  assert.equal(await page.evaluate(()=>window.captureRequests),1);
  assert.deepEqual(errors,[]);
  console.log('Browser AudioWorklet capture + original preservation + native-rate to mono16kPCM16 conversion + duration validation: PASS (oscillator only)');
}catch(e){
  console.error('Synthetic capture failure diagnostics:',JSON.stringify({
    status:await page?.locator('#status').innerText().catch(()=>''),
    elapsed:await page?.locator('#elapsed').innerText().catch(()=>''),
    errors,responses,requestFailures,fixtureErrors}));
  throw e;
}finally{await browser?.close();child.kill();}
