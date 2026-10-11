// Music Reforged loopback protocol client; this verifies packets, not audible playback.
const path = require('path');
const fs = require('fs');
const root = process.env.MUSIC_TOOL_CACHE || path.resolve(__dirname, '../.tools');
const mc = require(path.join(root, 'protocol/node_modules/minecraft-protocol'));
const data = require(path.join(root, 'protocol/node_modules/minecraft-data'));
const [,, port, version] = process.argv;
const protocol = data.versions.pc.find(v => v.minecraftVersion === version).version;
const schema = data(version)?.version.version === protocol ? version : data.supportedVersions.pc.find(v => data(v)?.version.version === protocol) || version;
const opts = {host:'127.0.0.1', port:Number(port), username:'Hash014Test', auth:'offline', version:schema, disableChatSigning:true, clientSettings:{viewDistance:2}};
const client = mc.createClient(opts); opts.protocolVersion = protocol;
let sounds = 0, stops = 0, packs = 0, lastPosition;
client.on('error', e => {console.error('CLIENT ERROR', e.stack); process.exitCode=1;});
client.on('end', reason => {console.log('CLIENT END', reason, 'sounds='+sounds, 'stops='+stops, 'packs='+packs); process.exit();});
client.on('disconnect', p => console.log('CLIENT DISCONNECT', JSON.stringify(p)));
client.on('login', () => console.log('CLIENT LOGIN', version, protocol));
client.on('playerJoin', () => console.log('CLIENT JOIN'));
client.on('position', p => {
  if (p.teleportId !== undefined) client.write('teleport_confirm', {teleportId:p.teleportId});
  else if (version.startsWith('1.8.')) {lastPosition={x:p.x,y:p.y,z:p.z,yaw:p.yaw,pitch:p.pitch,onGround:false}; client.write('position_look',lastPosition);}
});
setInterval(()=>{if(lastPosition&&client.state==='play')client.write('position_look',lastPosition);},50).unref();
client.on('packet', (p,meta) => {
  if (['chat','system_chat','player_chat'].includes(meta.name)) {
    let component=p;
    if(typeof p.message==='string'){try{component=JSON.parse(p.message);}catch{}}
    const text=JSON.stringify(component);
    if(text.includes('open_url')&&text.includes('/#token='))console.log('CLIENT ADMINLINK OPEN_URL');
  }
  if (meta.name.includes('sound') && !meta.name.includes('stop')) {sounds++; console.log('CLIENT SOUND',meta.name,JSON.stringify(p));}
  if (meta.name==='stop_sound') {stops++; console.log('CLIENT STOP',JSON.stringify(p));}
  if (meta.name==='custom_payload' && p.channel==='MC|StopSound') {stops++; console.log('CLIENT STOP',p.channel,p.data.toString('hex'));}
  if (['resource_pack_send','resource_pack_push','add_resource_pack'].includes(meta.name)) {packs++; console.log('CLIENT PACK',JSON.stringify(p));}
});
setTimeout(()=>{console.error('CLIENT TIMEOUT');client.end();},120000).unref();
