'use strict';
const $ = s => document.querySelector(s);
let state = null, selectedPlaylist = null, chosenFile = null, previewUrl = null;
const titles = {library:['Audio library','The sounds that give your world its identity.'],playlists:['Playlists & radio','Build your ambiences, then play them in-game.'],zones:['Music zones','An ambience for every place in your world.'],rules:['Events','Choose the music for important moments.'],discs:['Custom discs','Turn your tracks into items to share.']};
const fragment = new URLSearchParams(location.hash.slice(1));
if (fragment.has('token')) {sessionStorage.setItem('musicreforged-token',fragment.get('token'));history.replaceState(null,'',location.pathname);}
const token = sessionStorage.getItem('musicreforged-token');
let view = 'library';
function notice(message,error=false) {const n=$('#notice');n.textContent=message;n.hidden=false;n.classList.toggle('error',error);}
function node(tag,text,cls) {const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(cls)n.className=cls;return n;}
function button(label,handler,cls='quiet') {const b=node('button',label,cls);b.type='button';b.addEventListener('click',()=>action(handler,b));return b;}
async function api(path,body) {
  const options={headers:{Authorization:'Bearer '+token}};
  if(body!==undefined){options.method='POST';options.headers['Content-Type']='application/json';options.body=JSON.stringify(body);}
  const response=await fetch('/api/'+path,options);let value;
  try{value=await response.json();}catch{throw new Error('The server returned an unreadable response.');}
  if(!response.ok){if(response.status===403)lock();throw new Error(value.error||'Operation failed.');}
  return value;
}
function lock() {$('#login').hidden=false;document.querySelectorAll('.pane').forEach(p=>p.hidden=true);$('#connection-label').textContent='Session closed';$('#connection-lamp').classList.remove('ready');}
async function action(work,control) {if(control)control.disabled=true;try{await work();}catch(e){notice(e.message,true);}finally{if(control)control.disabled=false;}}
function selectView(name) {
  view=name;document.querySelectorAll('.pane').forEach(p=>p.hidden=p.id!=='view-'+name);document.querySelectorAll('.nav').forEach(b=>b.classList.toggle('active',b.dataset.view===name));
  $('#page-title').textContent=titles[name][0];$('#page-description').textContent=titles[name][1];
  if(!state)lock();
}
document.querySelectorAll('.nav').forEach(b=>b.addEventListener('click',()=>selectView(b.dataset.view)));
function choices(select,list,valueKey,labelKey) {
  const previous=select.value;select.replaceChildren();for(const item of list){const o=node('option',labelKey(item));o.value=valueKey(item);select.append(o);}if([...select.options].some(o=>o.value===previous))select.value=previous;
}
function duration(seconds){const m=Math.floor(seconds/60),s=Math.floor(seconds%60);return m+':'+String(s).padStart(2,'0');}
function playlistName(id){return state.playlists.find(p=>p.id===id)?.name||'Station '+id;}
async function refresh() {state=await api('state');$('#login').hidden=true;$('#connection-label').textContent='Server connected';$('#connection-lamp').classList.add('ready');$('#server').textContent=state.server;$('#pack-label').textContent='Pack ready · '+state.tracks.length+' tracks';render();selectView(view);}
function render() {
  $('#track-count').textContent='('+state.tracks.length+')';renderTracks();
  const songs=state.tracks;
  choices($('#playlist-track'),songs,t=>t.id,t=>t.id);choices($('#disc-track'),songs,t=>t.id,t=>t.id);
  document.querySelectorAll('.station-select').forEach(s=>choices(s,state.playlists,p=>String(p.id),p=>p.name+' (#'+p.id+')'));
  choices($('#world-select'),state.worlds,w=>w,w=>w);
  if(!state.playlists.some(p=>p.id===selectedPlaylist))selectedPlaylist=state.playlists[0]?.id??null;
  renderPlaylists();renderZones();renderRules();renderDiscs();
}
function renderTracks(){
  const q=$('#search').value.toLocaleLowerCase('en');const body=$('#tracks');body.replaceChildren();
  for(const track of state.tracks.filter(t=>t.id.toLocaleLowerCase('en').includes(q))){
    const row=node('tr');const title=node('td',track.id);title.append(node('small',track.custom?'Custom sound':'Minecraft sound'));
    const time=node('td',duration(track.seconds),'duration');const fade=node('td',track.segments>0?'Progressive fade':'Classic playback','badge');const play=node('td');
    const b=button('▶',()=>preview(track),'listen');b.setAttribute('aria-label','Listen '+track.id);if(!track.custom){b.disabled=true;b.title='This sound is provided by Minecraft.';}play.append(b);row.append(title,time,fade,play);body.append(row);
  }
  if(!body.children.length){const row=node('tr');const cell=node('td','No tracks match your search.','empty');cell.colSpan=4;row.append(cell);body.append(row);}
}
async function preview(track){
  const response=await fetch('/api/audio?id='+encodeURIComponent(track.id),{headers:{Authorization:'Bearer '+token}});
  if(!response.ok){const value=await response.json();throw new Error(value.error||'This sound is not available for preview.');}
  if(previewUrl)URL.revokeObjectURL(previewUrl);previewUrl=URL.createObjectURL(await response.blob());const audio=$('#preview');audio.src=previewUrl;
  $('#preview-title').textContent=track.id;$('#preview-description').textContent=duration(track.seconds)+' · preview in this browser';await audio.play();
}
$('#search').addEventListener('input',()=>{if(state)renderTracks();});
function chooseFile(file){
  if(!file)return;if(!/\.(ogg|mp3)$/i.test(file.name)){notice('Choose an OGG or MP3 file.',true);return;}
  chosenFile=file;$('#file-label').textContent=file.name+' · '+(file.size/1024/1024).toFixed(1)+' MB';
  $('#import-name').value=file.name.replace(/\.[^.]+$/,'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/[^A-Za-z0-9 _-]/g,'').slice(0,80)||'My track';
}
$('#audio-file').addEventListener('change',e=>chooseFile(e.target.files[0]));
for(const type of ['dragenter','dragover'])$('#dropzone').addEventListener(type,e=>{e.preventDefault();$('#dropzone').classList.add('drag');});
for(const type of ['dragleave','drop'])$('#dropzone').addEventListener(type,e=>{e.preventDefault();$('#dropzone').classList.remove('drag');if(type==='drop')chooseFile(e.dataTransfer.files[0]);});
$('#import-form').addEventListener('submit',e=>{e.preventDefault();action(async()=>{
  if(!chosenFile)throw new Error('Choose a file to import first.');const name=$('#import-name').value.trim();if(!name)throw new Error('Give the track a name.');
  const ext=chosenFile.name.split('.').pop().toLowerCase();const response=await fetch('/api/import?name='+encodeURIComponent(name)+'&type='+ext,{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/octet-stream'},body:chosenFile});
  const result=await response.json();if(!response.ok)throw new Error(result.error||'Import failed.');notice('Import started. Track preparation and pack generation run automatically.');await pollJobs();
},$('#import-button'));});
let finishedJobs=new Set();
async function pollJobs(){if(!token)return;const jobs=await api('jobs');const root=$('#jobs');root.replaceChildren();let changed=false;
  for(const job of jobs){const el=node('div',undefined,'job');el.append(node('strong',job.name));const progress=node('progress');progress.max=100;progress.value=job.progress;progress.setAttribute('aria-label','Progress for '+job.name);el.append(progress,node('small',job.message));root.append(el);
    if(job.status==='done'&&!finishedJobs.has(job.id)){finishedJobs.add(job.id);changed=true;notice(job.name+' is ready. Send the new pack to players.');}
    if(job.status==='failed'&&!finishedJobs.has(job.id)){finishedJobs.add(job.id);notice(job.message,true);}
  }if(changed)await refresh();
}
function formValues(form){return Object.fromEntries(new FormData(form).entries());}
function formSubmit(id,path,transform=x=>x){$(id).addEventListener('submit',e=>{e.preventDefault();action(async()=>{state=await api(path,transform(formValues(e.target)));render();notice('Saved.');},e.submitter);});}
function renderPlaylists(){
  const list=$('#playlist-list');list.replaceChildren();for(const p of state.playlists){const b=button(p.name,async()=>{selectedPlaylist=p.id;renderPlaylists();},'');b.append(node('small',p.queue.length+' tracks · station '+p.id));b.classList.toggle('selected',p.id===selectedPlaylist);list.append(b);}
  const p=state.playlists.find(p=>p.id===selectedPlaylist);$('#playlist-title').textContent=p?.name||'Create your first playlist';$('#radio-command').textContent=p?'/music radio '+p.id+' · personal radio':'Choose the ambiences that will play on the radio.';
  $('#shuffle').disabled=!p;$('#pause').disabled=!p;$('#shuffle').textContent=p?.shuffle?'Shuffle enabled':'Play in order';$('#pause').textContent=p?.paused?'Resume playback':'Pause playback';
  const queue=$('#queue');queue.replaceChildren();if(!p?.queue.length){queue.append(node('li','Add a track to start the playlist.','empty'));return;}
  p.queue.forEach((track,index)=>{const li=node('li');li.append(node('span',track));const controls=node('div',undefined,'button-row');
    const up=button('↑',()=>playlistAction({op:'move',index,to:index-1}));up.disabled=index===0;up.setAttribute('aria-label','Move up: '+track);
    const down=button('↓',()=>playlistAction({op:'move',index,to:index+1}));down.disabled=index===p.queue.length-1;down.setAttribute('aria-label','Move down: '+track);
    const remove=button('Remove',()=>playlistAction({op:'remove',index}));controls.append(up,down,remove);li.append(controls);queue.append(li);
  });
}
async function playlistAction(value){if(selectedPlaylist===null)throw new Error('Create or select a playlist.');state=await api('playlists',{station:String(selectedPlaylist),...value});render();}
formSubmit('#playlist-create','playlists',v=>({...v,op:'create'}));
$('#playlist-add').addEventListener('submit',e=>{e.preventDefault();action(()=>playlistAction({op:'add',track:$('#playlist-track').value}),e.submitter);});
$('#shuffle').addEventListener('click',()=>action(()=>playlistAction({op:'shuffle'}),$('#shuffle')));$('#pause').addEventListener('click',()=>action(()=>playlistAction({op:'pause'}),$('#pause')));
function record(root,title,description,remove){const row=node('article',undefined,'record');const head=node('div',undefined,'record-head');const text=node('div');text.append(node('h3',title),node('p',description));head.append(text);if(remove)head.append(button('Delete',remove));row.append(head);root.append(row);return row;}
function renderZones(){const root=$('#zones');root.replaceChildren();if(!state.zones.length)root.append(node('p','No zones yet. Select a location in-game or enter its coordinates.','empty'));
  for(const z of state.zones)record(root,z.name,z.world+' · '+playlistName(z.station)+' · ['+z.minX+', '+z.minY+', '+z.minZ+'] to ['+z.maxX+', '+z.maxY+', '+z.maxZ+'] · priority '+z.priority,async()=>{state=await api('zones',{delete:z.name});render();});}
function renderRules(){const root=$('#rules');root.replaceChildren();if(!state.rules.length)root.append(node('p','Add an event and the playlist that should take over.','empty'));
  for(const r of state.rules)record(root,r.id,r.event+' · '+r.selector+' · '+playlistName(r.station)+' · '+r.seconds+' s · priority '+r.priority,async()=>{state=await api('rules',{delete:r.id});render();});}
function renderDiscs(){const root=$('#discs');root.replaceChildren();if(!state.discs.length)root.append(node('p','Choose a track and name your first disc.','empty'));
  for(const d of state.discs){const row=record(root,d.id,d.track);const form=node('div',undefined,'give-form');const select=node('select');select.setAttribute('aria-label','Player to receive '+d.id);choices(select,state.players,n=>n,n=>n);const give=button('Give to player',async()=>{await api('discs',{give:d.id,player:select.value});notice('Disc '+d.id+' given to '+select.value+'.');});give.disabled=!state.players.length;form.append(select,give);row.append(form);}}
formSubmit('#zone-form','zones',v=>{for(const k of ['station','x1','y1','z1','x2','y2','z2','priority'])v[k]=Number(v[k]);return v;});
formSubmit('#rule-form','rules',v=>{for(const k of ['station','seconds','priority'])v[k]=Number(v[k]);return v;});formSubmit('#disc-form','discs');
$('#refresh').addEventListener('click',()=>action(refresh,$('#refresh')));$('#publish-pack').addEventListener('click',()=>action(async()=>{state=await api('pack',{send:true});render();notice('The pack was sent to online players. They must accept it.');},$('#publish-pack')));
if(!token)lock();else action(async()=>{await refresh();await pollJobs();});
setInterval(()=>{if(state)action(pollJobs);},2000);
