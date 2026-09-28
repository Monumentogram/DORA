// Reduced protocol UI test: eight synthetic records, deferred microphone, no human timing.
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const require=createRequire(import.meta.url),{chromium}=require('playwright');
for(const profile of ['--reduced8','--easy-en8']){
const child=spawn(process.env.DORA_PYTHON||'python',[fileURLToPath(new URL('./test_browser_fixture.py',import.meta.url)),profile],{stdio:['ignore','pipe','pipe'],windowsHide:true});
let browser;
try{
  const url=await new Promise((resolve,reject)=>{let text='';child.stdout.on('data',data=>{text+=data;if(text.includes('\n'))resolve(text.trim());});child.stderr.on('data',data=>reject(Error(String(data))));});
  browser=await chromium.launch({channel:'chrome',headless:true});const page=await browser.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(()=>{window.micCalls=0;navigator.mediaDevices.getUserMedia=async()=>{window.micCalls++;throw Error('Hardware microphone forbidden');};});
  await page.goto(url);await page.locator('#status').filter({hasText:'Готово'}).waitFor();
  assert.equal(await page.locator('#cases option').count(),8);
  assert.match(await page.locator('#scopeFacts').innerText(),/NOT_EVALUATED/);
  if(profile==='--easy-en8'){
    assert.match(await page.locator('#scopeFacts').innerText(),/EN 4 READ; EN SPONTANEOUS: NOT_EVALUATED/);
    const classes=await page.evaluate(async()=>{const r=await fetch('/api/state',{headers:{'X-Dora-Session':sessionStorage.getItem('doraSession')}});return (await r.json()).items.filter(i=>i.language==='en').map(i=>i.speech_class);});
    assert.deepEqual(classes,['READ','READ','READ','READ']);
  }
  assert.equal(await page.locator('#testMic').isDisabled(),true);
  assert.equal(await page.locator('#record').isDisabled(),true);
  assert.equal(await page.locator('#devices').isDisabled(),true);
  assert.equal(await page.locator('#recordingDeferred').isVisible(),true);
  const rejected=await page.evaluate(async()=>{const r=await fetch('/api/capture',{method:'POST',headers:{'Content-Type':'application/json','X-Dora-Session':sessionStorage.getItem('doraSession')},body:JSON.stringify({id:'synthetic',source:'AA==',evaluation:'AA=='})});return{status:r.status,body:await r.json()};});
  assert.deepEqual(rejected,{status:400,body:{error:'RECORDING_DEFERRED'}});
  await page.locator('#referenceCheck').check();await page.locator('#saveReference').click();await page.locator('#status').filter({hasText:'Человеческий эталон сохранён'}).waitFor();
  assert.match(await page.locator('#progress').innerText(),/8 \/ 8/);
  await page.locator('#select').click();await page.locator('#status').filter({hasText:'Все 8 записей зафиксированы'}).waitFor();
  assert.equal(await page.locator('#timing').isVisible(),false);
  await page.locator('#finalize').click();await page.locator('#status').filter({hasText:'Частный корпус подготовлен'}).waitFor();
  assert.equal(await page.locator('#finalize').isDisabled(),true);
  assert.equal(await page.evaluate(()=>window.micCalls),0);
  assert.deepEqual(errors,[]);
  console.log(profile+' browser: all eight verified/selected/finalized without timing, deferred microphone and HTTP capture denial: PASS');
}finally{await browser?.close();child.kill();}

}
