import {encodeWav, joinChunks, validateTimings} from './audio.js';
const $ = id => document.getElementById(id);
const token = location.hash.slice(1) || sessionStorage.getItem('doraSession');
if(token)sessionStorage.setItem('doraSession',token);
history.replaceState(null, '', '/');
let state, currentId, mic, context, capture, chunks = [], recording = false, saving = false, pendingStop;
let audioBuffer, audioUrl, ownVoiceUrl, words = [], activeWord = 0, timer, startedAt, clipAtStart, playUntil = null, draftSave=Promise.resolve(), pendingCapture=null;
let interruptedCapture=false;
let capturedFrames=0,captureRate=0;
const playback = $('playback');
const timingDraftStatus=document.createElement('p');timingDraftStatus.id='timingDraftStatus';timingDraftStatus.className='muted';$('words').before(timingDraftStatus);
function status(message, error=false) { $('status').textContent=message; $('status').className=error?'error':''; }
async function api(path, body) {
  const response = await fetch('/api/'+path, {method:body===undefined?'GET':'POST', cache:'no-store',
    headers:{'X-Dora-Session':token,...(body===undefined?{}:{'Content-Type':'application/json'})},
    body:body===undefined?undefined:JSON.stringify(body)});
  const data=await response.json(); if(!response.ok) throw Error(data.error); return data;
}
function item() { return state.items.find(i=>i.id===currentId); }
function reducedProtocol(){return ['dora-owned-reduced8-v2','dora-owned-easy-en8-v3'].includes(state?.protocol_version);}
function recordingEnabled(){return state?.recording_enabled!==false;}
function finished(i) { return !!i.exclusion || !!(i.recording && i.reference); }
function pendingTiming(i) {return !!i.timing_draft && (!i.timing || JSON.stringify(i.timing_draft.words)!==JSON.stringify(i.timing.words));}
function timingComplete(i) {return !!i.timing && !pendingTiming(i);}
function caseLabel(i) {return `${i.id} · ${i.language.toUpperCase()} ${i.speech_class} ${i.exclusion?'— исключён':!reducedProtocol()&&pendingTiming(i)?'⏱ черновик: подтвердить':!reducedProtocol()&&timingComplete(i)?'✓ разметка':!reducedProtocol()&&i.timing_selected?'⏱ разметить':finished(i)?'✓':i.recording?'— проверить текст':''}`;}
function updateTimingStatus(){const clip=item();timingDraftStatus.textContent=pendingTiming(clip)?'Черновик временных отметок требует подтверждения. Ранее подтверждённая версия не завершает это задание.':timingComplete(clip)?'Подтверждено человеком.':'Отметьте каждое слово и подтвердите независимую ручную разметку.';const option=$('cases').selectedOptions[0];if(option)option.textContent=caseLabel(clip);}
function bind(id, action) { $(id).addEventListener('click', async()=>{ try { await action(); } catch(e) { status(e.message,true); } }); }
function busy() {
  const locked = recording || saving;
  for(const id of ['cases','next','devices','testMic','record','select','finalize','saveReference','exclude']) $(id).disabled=locked;
  $('stop').disabled=!recording || capturedFrames/captureRate<20.25;
  $('closeMic').disabled=locked;
  $('record').disabled=locked || !recordingEnabled() || !state?.attestation.confirmed || !!item()?.recording || !!item()?.exclusion || state?.status!=='ACQUIRING';
  $('record').textContent=pendingCapture?'Повторить локальное сохранение':'Начать запись';
  $('testMic').disabled=locked || !recordingEnabled() || !state?.attestation.confirmed;
  $('devices').disabled=locked || !recordingEnabled();
  $('saveReference').disabled=locked || !item()?.recording || !!item()?.exclusion || state?.status!=='ACQUIRING';
  $('exclude').disabled=locked || state?.status!=='ACQUIRING' || !!item()?.exclusion;
  $('select').disabled=locked || state?.status!=='ACQUIRING';
  $('finalize').disabled=locked || state?.status!=='SELECTED';
}
async function render() {
  const reduced=reducedProtocol();
  $('scopeFacts').textContent=reduced?`8 записей одного владельца: RU 2 READ + 2 SPONTANEOUS; EN ${state.protocol_version==='dora-owned-easy-en8-v3'?'4 READ; EN SPONTANEOUS: NOT_EVALUATED':'2 READ + 2 SPONTANEOUS'}. Оцениваются все восемь. Резервов нет. NOISE и TIMESTAMPS: NOT_EVALUATED. Такой малый корпус не доказывает качество для других говорящих.`:'Исходный протокол корпуса; этапы определяются сохранённым частным состоянием.';
  $('selectionHeading').textContent=reduced?'4. Фиксация всех восьми записей':'4. Отбор и ручные временные отметки';
  $('selectionDescription').textContent=reduced?'После проверки восьми фактических текстов зафиксируйте весь набор. Исключение не заменяется резервом: неполный набор блокирует завершение. Ручная разметка времени не требуется.':'После обработки кандидатов зафиксируйте детерминированный отбор и выполните предусмотренную протоколом ручную разметку.';
  $('select').textContent=reduced?'Зафиксировать все 8 записей':'Зафиксировать отбор';
  $('recordingDeferred').classList.toggle('hidden',recordingEnabled());
  $('attestation').textContent=state.attestation.text;
  $('consent').classList.toggle('hidden',state.attestation.confirmed);
  $('progress').textContent=`${state.items.filter(finished).length} / ${state.items.length} обработано`;
  currentId ||= state.items.find(i=>!finished(i))?.id || state.items[0].id;
  $('cases').replaceChildren(...state.items.map(i=>{
    const o=document.createElement('option');o.value=i.id;
    o.textContent=caseLabel(i);
    return o;
  }));
  $('cases').value=currentId;
  const clip=item();
  $('material').textContent=clip.material;
  $('condition').textContent=`Условие: ${clip.condition}`;
  $('excludeReason').options[0].value='CORRUPTED_OR_UNDECODABLE_AUDIO';
  $('guidance').textContent=clip.speech_class==='READ'?'Прочитайте текст как написано, естественно. Ориентир: 20–45 секунд.':'Говорите своими словами, без заранее подготовленного ответа; не раскрывайте личные данные. Ориентир: 20–60 секунд. Соблюдайте указанное условие записи.';
  $('reference').value=clip.reference?.text || (clip.speech_class==='READ'?clip.material:'');
  $('referenceCheck').checked=false; $('timingCheck').checked=false;
  $('reference').disabled=state.status!=='ACQUIRING' || !!clip.exclusion;
  $('clipInfo').textContent=clip.recording?`Сохранено ${(clip.recording.duration_us/1e6).toFixed(3)} с · WAV 16 кГц mono PCM16 · исходник сохранён · SHA-256 проверен`:'Запись ещё не сохранена.';
  playback.classList.toggle('hidden',!clip.recording);
  $('timing').classList.toggle('hidden',reduced||!clip.timing_selected);
  words=clip.reference?.words.map(text=>({text,start_us:null,end_us:null})) || [];
  if(clip.timing) words=clip.timing.words.map(w=>({...w}));
  if(clip.timing_draft) words=clip.timing_draft.words.map(w=>({...w}));
  updateTimingStatus();
  activeWord=0; renderWords();
  playback.pause();playUntil=null;audioBuffer=null;
  $('ownVoice').pause();$('ownVoice').removeAttribute('src');$('ownVoice').load();
  if(ownVoiceUrl)URL.revokeObjectURL(ownVoiceUrl);
  const recapture=clip.condition==='OWN_VOICE_SPEAKERPHONE_RECAPTURE';
  $('recapture').classList.toggle('hidden',!recapture);
  if(recapture){
    const source=state.items.find(i=>i.language===clip.language&&i.speech_class==='READ'&&i.recording&&!i.exclusion);
    if(source){const r=await fetch('/api/audio/'+encodeURIComponent(source.id),{headers:{'X-Dora-Session':token},cache:'no-store'});if(!r.ok)throw Error('Сначала завершите собственную READ-запись.');ownVoiceUrl=URL.createObjectURL(await r.blob());$('ownVoice').src=ownVoiceUrl;}
  }
  if(audioUrl) URL.revokeObjectURL(audioUrl);
  playback.removeAttribute('src');playback.load();
  if(clip.recording) {
    const response=await fetch('/api/audio/'+encodeURIComponent(clip.id),{headers:{'X-Dora-Session':token},cache:'no-store'});
    if(!response.ok)throw Error('Не удалось открыть сохранённое аудио.');
    const bytes=await response.arrayBuffer();audioUrl=URL.createObjectURL(new Blob([bytes],{type:'audio/wav'})); playback.src=audioUrl;
    const decode=new AudioContext();
    try {audioBuffer=await decode.decodeAudioData(bytes.slice(0));} finally {await decode.close();}
  }
  $('zoom').value=1;$('pan').value=0; draw();busy();
}
async function refresh(nextState) {state=nextState || await api('state');await render();}
async function openMic() {
  if(!recordingEnabled())throw Error('Запись отложена. Сначала сообщите в чате: «Готов записывать».');
  if(!state.attestation.confirmed) throw Error('Сначала подтвердите согласие говорящего.');
  if(mic)return;
  if(!navigator.mediaDevices?.getUserMedia)throw Error('Откройте инструмент в актуальном Chrome или Edge.');
  mic=await navigator.mediaDevices.getUserMedia({audio:{deviceId:$('devices').value?{exact:$('devices').value}:undefined,channelCount:1,echoCancellation:false,noiseSuppression:false,autoGainControl:false},video:false});
  try {
    context=new AudioContext();await context.resume();await context.audioWorklet.addModule('/capture-worklet.js');
    capture=new AudioWorkletNode(context,'dora-capture');
    const source=context.createMediaStreamSource(mic), silence=context.createGain();silence.gain.value=0;
    source.connect(capture);capture.connect(silence);silence.connect(context.destination);
    capture.port.onmessage=({data})=>{
      if(data.samples){chunks.push(data.samples);capturedFrames+=data.samples.length;}
      if(data.peak!==undefined){$('meter').value=data.peak;$('level').textContent=data.peak>=.99?'Перегрузка: говорите тише / дальше':`Уровень ${Math.round(data.peak*100)}%`;}
      if(data.event==='stopped' && pendingStop){pendingStop();pendingStop=null;}
      if(data.event==='limit' && recording)void stopRecording().catch(e=>status(e.message,true));
    };
    const selected=$('devices').value; const devices=await navigator.mediaDevices.enumerateDevices();
    $('devices').replaceChildren(...devices.filter(d=>d.kind==='audioinput').map(d=>{const o=document.createElement('option');o.value=d.deviceId;o.textContent=d.label||'Микрофон';return o;}));
    if(selected)$('devices').value=selected;
    const track=mic.getAudioTracks()[0];
    track.onended=()=>{if(recording)void stopRecording().catch(e=>status(e.message,true));};
  } catch(e) {await closeMic();throw e;}
}
async function closeMic() {
  if(mic)mic.getTracks().forEach(t=>{t.onended=null;t.stop();});mic=null;
  if(context)await context.close();context=null;capture=null;$('meter').value=0;$('level').textContent='Микрофон выключен';
}
function base64(buffer) {let s='';const data=new Uint8Array(buffer);for(let i=0;i<data.length;i+=32768)s+=String.fromCharCode(...data.subarray(i,i+32768));return btoa(s);}
async function stopRecording() {
  if(!recording)return;
  recording=false;saving=true;clearInterval(timer);busy();
  try {
    const rate=context.sampleRate;
    interruptedCapture=false;
    let stopTimeout;
    try {await Promise.race([new Promise(resolve=>{pendingStop=resolve;capture.port.postMessage('stop');}),new Promise(resolve=>{stopTimeout=setTimeout(()=>{interruptedCapture=true;resolve();},3000);})]);}
    finally {clearTimeout(stopTimeout);}
    const samples=joinChunks(chunks);await closeMic();
    status('Проверка и локальное сохранение WAV…');
    const source=encodeWav(samples,rate);
    const targetLength=Math.round(samples.length*16000/rate);
    const offline=new OfflineAudioContext(1,targetLength,16000);
    // Resample the actual preserved PCM16 source, preserving source/final provenance.
    const buffer=offline.createBuffer(1,samples.length,rate), pcm=buffer.getChannelData(0), view=new DataView(source);
    for(let i=0;i<samples.length;i++)pcm[i]=view.getInt16(44+i*2,true)/32768;
    const node=offline.createBufferSource();node.buffer=buffer;node.connect(offline.destination);node.start();
    const rendered=await offline.startRendering();const evaluation=encodeWav(rendered.getChannelData(0),16000);
    pendingCapture={id:clipAtStart,source:base64(source),evaluation:base64(evaluation)};
    let updated=await api('capture',pendingCapture);
    if(interruptedCapture)updated=await api('exclude',{id:clipAtStart,reason:'CORRUPTED_OR_UNDECODABLE_AUDIO'});
    await refresh(updated);pendingCapture=null;chunks=[];
    status(interruptedCapture?'Микрофон не подтвердил остановку. Полученный исходник сохранён, кандидат исключён до отбора.':'Запись сохранена. Прослушайте её и подтвердите фактический текст.',interruptedCapture);
  } catch(e) {await refresh().catch(()=>{});if(item()?.exclusion)pendingCapture=null;throw e;}
  finally {pendingStop=null;await closeMic();saving=false;busy();}
}
bind('attest',async()=>{if(!$('consentCheck').checked)throw Error('Нужно явное подтверждение.');await refresh(await api('attest',{confirmed:true}));status('Согласие сохранено. Можно выбрать микрофон и начать.');});
bind('testMic',async()=>{await openMic();status('Микрофон включён только для проверки уровня. Запись начнётся по кнопке.');});
bind('closeMic',closeMic);
$('devices').addEventListener('change',()=>closeMic());
bind('record',async()=>{
  if(pendingCapture) {saving=true;busy();try{let updated=await api('capture',pendingCapture);if(interruptedCapture)updated=await api('exclude',{id:pendingCapture.id,reason:'CORRUPTED_OR_UNDECODABLE_AUDIO'});await refresh(updated);pendingCapture=null;chunks=[];status('Сохранение повторено успешно.');}finally{saving=false;busy();}return;}
  saving=true;busy();try{await openMic();}finally{saving=false;busy();}
  chunks=[];capturedFrames=0;captureRate=context.sampleRate;clipAtStart=currentId;recording=true;startedAt=performance.now();
  const limit=item().speech_class==='READ'?44:59;
  capture.port.postMessage({command:'start',maxFrames:Math.floor(captureRate*limit)});busy();
  status('Идёт запись. Говорите естественно; остановите по завершении задания.');
  timer=setInterval(()=>{
    const seconds=capturedFrames/captureRate;$('elapsed').textContent=seconds.toFixed(1)+' с';$('stop').disabled=seconds<20.25;
    if(seconds>=limit || performance.now()-startedAt>=90000)void stopRecording().catch(e=>status(e.message,true));
  },100);
});
bind('stop',stopRecording);
$('cases').addEventListener('change',async()=>{currentId=$('cases').value;try{await render();}catch(e){status(e.message,true);}});
bind('next',async()=>{await draftSave;const target=state.items.find(i=>state.status==='ACQUIRING'?!finished(i):i.timing_selected&&!timingComplete(i));if(target){currentId=target.id;await render();}else status('Все задания этого этапа обработаны. Используйте следующий шаг ниже.');});
bind('saveReference',async()=>{if(!$('referenceCheck').checked)throw Error('Подтвердите проверку каждого слова.');await refresh(await api('reference',{id:currentId,text:$('reference').value,confirmed:true}));status('Человеческий эталон сохранён. Переходите к следующему заданию.');});
bind('exclude',async()=>{if(!confirm('Исключить этот кандидат с выбранной причиной? Запись сохранится в частном журнале.'))return;await refresh(await api('exclude',{id:currentId,reason:$('excludeReason').value}));status('Предварительное исключение сохранено.');});
bind('select',async()=>{state=await api('select',{});currentId=state.items.find(i=>i.timing_selected&&!i.timing)?.id||state.items[0].id;await render();status(reducedProtocol()?'Все 8 записей зафиксированы. Можно завершить частный корпус без временной разметки.':'Отбор заморожен. Выполните предусмотренную протоколом ручную разметку.');});
function renderWords() {
  $('words').replaceChildren(...words.map((word,i)=>{
    const button=document.createElement('button');button.className='word'+(i===activeWord?' selected':'')+(word.start_us!==null&&word.end_us!==null?' done':'');
    button.append(document.createTextNode(`${i+1}. ${word.text}`));const small=document.createElement('small');small.textContent=`${word.start_us===null?'—':(word.start_us/1e6).toFixed(3)} → ${word.end_us===null?'—':(word.end_us/1e6).toFixed(3)}`;button.append(small);
    button.onclick=()=>{activeWord=i;renderWords();};return button;
  }));
}
function viewport(){const duration=audioBuffer?.duration||1,width=duration/Number($('zoom').value),start=Number($('pan').value)*(duration-width);return{duration,width,start};}
function draw(){
  const canvas=$('waveform'),ctx=canvas.getContext('2d'),{duration,width,start}=viewport();ctx.clearRect(0,0,canvas.width,canvas.height);
  if(!audioBuffer)return;
  const samples=audioBuffer.getChannelData(0),rate=audioBuffer.sampleRate;ctx.strokeStyle='#357e71';ctx.beginPath();
  for(let x=0;x<canvas.width;x++){let low=0,high=0;const a=Math.floor((start+x/canvas.width*width)*rate),b=Math.min(samples.length,Math.ceil((start+(x+1)/canvas.width*width)*rate));for(let i=a;i<b;i++){low=Math.min(low,samples[i]);high=Math.max(high,samples[i]);}ctx.moveTo(x,90+low*82);ctx.lineTo(x,90+high*82);}ctx.stroke();
  const cursor=(playback.currentTime-start)/width*canvas.width;ctx.strokeStyle='#b54b27';ctx.beginPath();ctx.moveTo(cursor,0);ctx.lineTo(cursor,180);ctx.stroke();
  ctx.fillStyle='#304b55';ctx.font='13px system-ui';ctx.fillText(`${start.toFixed(3)} с`,6,17);ctx.fillText(`${Math.min(duration,start+width).toFixed(3)} с`,canvas.width-90,17);
  $('cursor').textContent=`Позиция ${playback.currentTime.toFixed(3)} с`;
}
$('waveform').addEventListener('click',e=>{if(!audioBuffer)return;const rect=e.currentTarget.getBoundingClientRect(),{start,width}=viewport();playback.currentTime=start+Math.max(0,Math.min(1,(e.clientX-rect.left)/rect.width))*width;draw();});
for(const id of ['zoom','pan'])$(id).addEventListener('input',draw);
$('speed').addEventListener('change',()=>{playback.playbackRate=Number($('speed').value);});
function saveDraft(){const payload={id:currentId,words:words.map(w=>({...w}))};item().timing_draft={words:payload.words,authoritative:false};updateTimingStatus();draftSave=draftSave.then(()=>api('timing-draft',payload)).catch(e=>status('Черновик не сохранён: '+e.message,true));}
function mark(end){if(!words[activeWord]||!audioBuffer||state.status==='FINALIZED')return;words[activeWord][end?'end_us':'start_us']=Math.min(item().recording.duration_us,Math.round(playback.currentTime*1e6));if(end&&activeWord<words.length-1)activeWord++;$('timingCheck').checked=false;renderWords();saveDraft();}
bind('markStart',()=>mark(false));bind('markEnd',()=>mark(true));
bind('clearWord',()=>{if(words[activeWord]&&state.status!=='FINALIZED'){words[activeWord].start_us=null;words[activeWord].end_us=null;renderWords();saveDraft();}});
bind('playWord',async()=>{const w=words[activeWord];if(w?.start_us===null||w?.end_us===null)throw Error('Сначала отметьте начало и конец слова.');playback.currentTime=w.start_us/1e6;playUntil=w.end_us/1e6;await playback.play();});
playback.addEventListener('timeupdate',()=>{if(playUntil!==null&&playback.currentTime>=playUntil){playback.pause();playUntil=null;}draw();});
document.addEventListener('keydown',e=>{
  if(['INPUT','TEXTAREA','SELECT'].includes(e.target.tagName)||!item()?.timing_selected)return;
  if(['s','ы','e','у',' ','ArrowLeft','ArrowRight'].includes(e.key)){e.preventDefault();
    if(e.key==='s'||e.key==='ы')mark(false);else if(e.key==='e'||e.key==='у')mark(true);
    else if(e.key===' '){playUntil=null;if(playback.paused)void playback.play();else playback.pause();}
    else playback.currentTime=Math.max(0,Math.min(audioBuffer.duration,playback.currentTime+(e.key==='ArrowLeft'?-.01:.01)));draw();
  }
});
bind('saveTiming',async()=>{if(!$('timingCheck').checked)throw Error('Подтвердите независимую ручную разметку.');validateTimings(words,item().reference.words,item().recording.duration_us);await draftSave;await refresh(await api('timing',{id:currentId,words,confirmed:true}));status('Все временные отметки клипа проверены и сохранены.');});
bind('finalize',async()=>{await refresh(await api('finalize',{}));status(reducedProtocol()?'Частный корпус подготовлен: 8 записей. Фактические тексты подтверждены. Шум и точность таймкодов не оценены. Передача в AWS ещё не выполнялась.':'Частный корпус подготовлен. Запись и разметка завершены. Передача в AWS ещё не выполнялась.');});
window.addEventListener('beforeunload',e=>{if(recording||saving){e.preventDefault();e.returnValue='';}});
try {if(!token)throw Error('Запустите инструмент через launch.cmd: требуется локальная сессия.');await refresh();status(reducedProtocol()&&!state.recording_enabled?'Готово. Запись отложена по вашему решению. Сейчас никаких действий не требуется; сохранённые данные остаются на этом компьютере.':'Готово. Выполняйте шаги по порядку; данные остаются на этом компьютере.');}catch(e){status(e.message,true);}
