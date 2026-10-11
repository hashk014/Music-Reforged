"""Authenticated HTTP workflow checks, including actual MP3 and OGG conversion."""
import io,json,pathlib,subprocess,time,urllib.request,urllib.error,urllib.parse,zipfile

def verify(folder):
    login=json.loads((folder/'plugins/MusicReforged/admin-test.json').read_text())['login']
    parsed=urllib.parse.urlsplit(login);base=urllib.parse.urlunsplit((parsed.scheme,parsed.netloc,'','',''));token=urllib.parse.parse_qs(parsed.fragment)['token'][0]
    def req(path,body=None,auth=True,raw=False):
        headers={'Authorization':'Bearer '+token} if auth else {}
        if body is not None:headers['Content-Type']='application/octet-stream' if raw else 'application/json';body=body if raw else json.dumps(body).encode()
        response=urllib.request.urlopen(urllib.request.Request(base+path,data=body,headers=headers),timeout=20);return response.read()
    def api(path,body=None):return json.loads(req('/api/'+path,body))
    try:req('/api/state',auth=False);raise AssertionError('Unauthenticated admin access')
    except urllib.error.HTTPError as e:assert e.code==403
    html=req('/').decode();assert 'Music Reforged' in html and 'import-form' in html and 'zone-form' in html
    assert b'playlistAction' in req('/app.js') and b'@media' in req('/style.css')
    initial=api('state');assert len(initial['tracks'])>=21
    created=api('playlists',{'op':'create','name':'HttpPlaylist'});station=next(s['id'] for s in created['playlists'] if s['name']=='HttpPlaylist')
    api('playlists',{'op':'add','station':str(station),'track':'Chill'})
    api('playlists',{'op':'add','station':str(station),'track':'Arcade'})
    moved=api('playlists',{'op':'move','station':str(station),'index':1,'to':0});assert next(s for s in moved['playlists'] if s['id']==station)['queue']==['Arcade','Chill']
    api('zones',{'name':'httpzone','station':station,'world':initial['worlds'][0],'x1':100,'y1':0,'z1':100,'x2':104,'y2':120,'z2':104})
    api('rules',{'id':'httprule','event':'JOIN','station':station,'seconds':2,'priority':50})
    api('discs',{'id':'httpdisc','track':'Chill'})
    state=api('state');assert any(z['name']=='httpzone' for z in state['zones']) and any(r['id']=='httprule' for r in state['rules']) and any(d['id']=='httpdisc' for d in state['discs'])
    # Fixture audio is generated locally, then uploaded through exactly the production endpoint.
    mp3=folder/'import-test.mp3'
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','lavfi','-i','sine=frequency=523:duration=0.75','-ac','1','-c:a','libmp3lame',str(mp3)],check=True)
    ogg=(folder/'plugins/MusicReforged/audio/musicreforged/chill_segments/00000.ogg').read_bytes()
    for name,kind,data in [('ImportedMP3','mp3',mp3.read_bytes()),('ImportedOGG','ogg',ogg)]:
        job=json.loads(req('/api/import?name='+name+'&type='+kind,data,raw=True));start=time.monotonic()
        while time.monotonic()-start<60:
            progress=next(j for j in api('jobs') if j['id']==job['id'])
            if progress['status'] in ('done','failed'):break
            time.sleep(.2)
        assert progress['status']=='done',progress
        track=next(t for t in api('state')['tracks'] if t['id']==name);assert track['segments']>0 and track['custom'] and track['seconds']>0
        assert req('/api/audio?id='+name).startswith(b'OggS')
    pack=req('/pack.zip')
    with zipfile.ZipFile(io.BytesIO(pack)) as z:assert len([n for n in z.namelist() if 'import_' in n and n.endswith('.ogg')])>=7
    try:api('zones',{'name':'../escape'});raise AssertionError('Invalid zone accepted')
    except urllib.error.HTTPError as e:assert e.code==400
    api('zones',{'delete':'httpzone'});api('rules',{'delete':'httprule'});api('playlists',{'op':'delete','station':str(station)})
    # The harmless disc definition and imported tracks remain to exercise restart loading.
    return {'authenticated':True,'unauthorizedBlocked':True,'playlistOrder':True,'zoneRuleDisc':True,'mp3Imported':True,'oggImported':True,'packGenerated':True}
