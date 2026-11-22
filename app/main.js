const startupAt = performance.now();
const { app, BrowserWindow, Menu, dialog, ipcMain, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const { pathToFileURL } = require('url');

const {OfflineEngine,extensions}=require('./media-engine');
const MEDIA_EXTS = extensions.map(ext=>'.'+ext);
const SUB_EXTS = ['.srt', '.vtt'];
const {parseMediaArguments} = require('./launch');
let win = null;
let rendererReady = false;
let pendingExternal = parseMediaArguments(process.argv);
const launchLog=[];
function logLaunch(event, paths){launchLog.push({event,at:new Date().toISOString(),paths});if(launchLog.length>25)launchLog.shift();}
logLaunch('initial-arguments',process.argv);
let deliveryId=1;
function queueExternal(paths) {
 if (!paths.length) return;
 pendingExternal = paths;deliveryId++;logLaunch('queued',paths);
 flushExternal();
}
function flushExternal() {
 if (!rendererReady || !win || win.isDestroyed() || !pendingExternal.length) return;
 win.webContents.send('media:external', {id:deliveryId,entries:pendingExternal.map(mediaEntry)});
}
ipcMain.on('renderer:ready', () => {rendererReady=true;flushExternal();});
ipcMain.handle('launch:pull',()=>{rendererReady=true;logLaunch('renderer-pull',pendingExternal);return {id:deliveryId,entries:pendingExternal.map(mediaEntry)};});
ipcMain.on('launch:ack',(e,id)=>{if(id===deliveryId){pendingExternal=[];logLaunch('acknowledged',[]);}});
ipcMain.handle('launch:diagnostics',()=>({argv:process.argv,log:launchLog,packaged:app.isPackaged,version:app.getVersion()}));
ipcMain.handle('launch:export',async()=>{
 const result=await dialog.showSaveDialog(win,{defaultPath:'Bplus-launch-report.json',filters:[{name:'JSON',extensions:['json']}]});
 if(result.canceled||!result.filePath)return false;
 fs.writeFileSync(result.filePath,JSON.stringify({version:app.getVersion(),platform:process.platform,os:require('os').release(),packaged:app.isPackaged,executable:process.execPath,argv:process.argv,launchEvents:launchLog,decoderPresent:fs.existsSync(getDecoderBinary())},null,2));return true;
});
let uiLang='ar';
ipcMain.on('ui:language',(e,l)=>{uiLang=l==='en'?'en':'ar';});
/* ---------- Update feature: checks public GitHub Releases, no token ---------- */
const UPD_REPO='basem-sadoun/Bplus';
function cmpVer(a,b){const p=s=>String(s||'').replace(/^v/i,'').split('.').map(n=>parseInt(n,10)||0);const x=p(a),y=p(b);for(let i=0;i<3;i++){if(x[i]!==y[i])return x[i]-y[i]}return 0}
async function fetchLatestRelease(){
 const r=await require('electron').net.fetch('https://api.github.com/repos/'+UPD_REPO+'/releases/latest',{headers:{'User-Agent':'Bplus-Player'}});
 if(r.status===404)return{status:'none'};
 if(!r.ok)return{status:'error',code:r.status};
 const j=await r.json();
 const asset=(j.assets||[]).find(a=>/\.exe$/i.test(a.name));
 return{status:'ok',version:String(j.tag_name||'').replace(/^v/i,''),name:j.name||'',notes:j.body||'',asset:asset?{name:asset.name,url:asset.browser_download_url,size:asset.size}:null};
}
ipcMain.handle('update:check',async()=>{
 try{
  const rel=await fetchLatestRelease();
  if(rel.status==='none')return{status:'none'};
  if(rel.status==='error')return{status:'error'};
  if(!rel.asset)return{status:'error',message:'no-asset'};
  return{status:cmpVer(rel.version,app.getVersion())>0?'available':'latest',version:rel.version,name:rel.name,notes:rel.notes,asset:rel.asset};
 }catch(e){return{status:'error',message:String(e&&e.message||e)}}
});
let updDownloading=false;
async function downloadUpdate(info){
 if(updDownloading)return{status:'busy'};
 updDownloading=true;
 try{
  const r=await require('electron').net.fetch(info.asset.url,{headers:{'User-Agent':'Bplus-Player'}});
  if(!r.ok||!r.body){updDownloading=false;return{status:'error',code:r.status}}
  const dest=path.join(app.getPath('temp'),info.asset.name);
  const total=Number(r.headers.get('content-length'))||info.asset.size||0;
  const out=fs.createWriteStream(dest);
  const reader=r.body.getReader();let received=0;
  for(;;){const{done,value}=await reader.read();if(done)break;received+=value.length;if(!out.write(value))await new Promise(res=>out.once('drain',res));send('update:progress',{received,total});}
  await new Promise((res,rej)=>out.end(err=>err?rej(err):res));
  updDownloading=false;
  return{status:'done',path:dest};
 }catch(err){updDownloading=false;return{status:'error',message:String(err&&err.message||err)}}
}
ipcMain.handle('update:download',async(e,info)=>downloadUpdate(info));
ipcMain.handle('update:install',async(e,setupPath)=>{
 try{const{spawn}=require('child_process');const c=spawn(setupPath,[],{detached:true,stdio:'ignore'});c.unref();app.quit();return true}catch(err){return false}
});
ipcMain.handle('update:prompt',async(e,info)=>{
 const en=uiLang==='en';
 const r=await dialog.showMessageBox(win,{type:'info',title:'B+',noLink:true,
  message:en?('A new version of B+ is available: '+info.version):('يتوفر إصدار جديد من B+: '+info.version),
  detail:String(info.notes||'').slice(0,600),
  buttons:en?['Download and install','Later']:['تنزيل وتثبيت','لاحقًا'],defaultId:0,cancelId:1});
 if(r.response!==0)return false;
 const d=await downloadUpdate(info);
 if(d.status==='done'){const{spawn}=require('child_process');const c=spawn(d.path,[],{detached:true,stdio:'ignore'});c.unref();app.quit();return true}
 await dialog.showMessageBox(win,{type:'error',title:'B+',noLink:true,message:en?'Download failed. Check your internet connection and try again.':'فشل التنزيل. تحقق من اتصال الإنترنت وحاول مجددًا.'});
 return false;
});
ipcMain.handle('app:version',()=>app.getVersion());
ipcMain.handle('dialog:confirm',async(e,opts)=>{const r=await dialog.showMessageBox(win,{type:'warning',buttons:opts.buttons,defaultId:0,cancelId:1,title:opts.title,message:opts.message,detail:opts.detail});return r.response;});
ipcMain.handle('media:trash',async(e,p)=>{try{await shell.trashItem(p);return {ok:true};}catch(err){return {ok:false,error:String(err)};}});
ipcMain.handle('window:opacity',(e,value)=>{if(win)win.setOpacity(Math.min(1,Math.max(.65,Number(value)||1)));});
ipcMain.handle('window:always-on-top',(e,flag)=>{if(!win)return false;win.setAlwaysOnTop(!!flag);return win.isAlwaysOnTop();});
ipcMain.handle('system:theme',()=>require('electron').nativeTheme.shouldUseDarkColors?'dark':'light');

if (process.env.BPLUS_TEST || process.env.BPLUS_DEMO) {
  app.commandLine.appendSwitch('disable-gpu');
}
app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required');

function mediaEntry(p) {
  const e = { path: p, url: pathToFileURL(p).href, name: path.basename(p) };
  try { const s = fs.statSync(p); e.size = s.size; e.mtime = s.mtimeMs; } catch {}
  return e;
}

function send(channel, payload) {
  if (win && !win.isDestroyed()) win.webContents.send(channel, payload);
}

async function openMedia() {
  const r = await dialog.showOpenDialog(win, {
    properties: ['openFile', 'multiSelections'],
    filters: [
      { name: uiLang==='en' ? 'Media files' : 'ملفات الوسائط', extensions: MEDIA_EXTS.map(e => e.slice(1)) },
      { name: uiLang==='en' ? 'All files' : 'كل الملفات', extensions: ['*'] }
    ]
  });
  if (!r.canceled && r.filePaths.length) send('playlist:add', r.filePaths.map(mediaEntry));
}

async function openFolder() {
  const r = await dialog.showOpenDialog(win, { properties: ['openDirectory'] });
  if (r.canceled || !r.filePaths.length) return;
  const dir = r.filePaths[0];
  const files = fs.readdirSync(dir)
    .filter(f => MEDIA_EXTS.includes(path.extname(f).toLowerCase()))
    .sort()
    .map(f => mediaEntry(path.join(dir, f)));
  if (files.length) send('playlist:add', files);
}

async function openSubtitle() {
  const r = await dialog.showOpenDialog(win, {
    properties: ['openFile'],
    filters: [{ name: uiLang==='en' ? 'Subtitle files' : 'ملفات الترجمة', extensions: ['srt', 'vtt'] }]
  });
  if (r.canceled || !r.filePaths.length) return;
  const p = r.filePaths[0];
  send('subtitle:load', { name: path.basename(p), content: fs.readFileSync(p, 'utf8') });
}

function getDecoderBinary(){return process.platform==='win32'?(app.isPackaged?path.join(process.resourcesPath,'ffmpeg','ffmpeg.exe'):path.join(__dirname,'..','vendor','ffmpeg','ffmpeg.exe')):(process.env.BPLUS_FFMPEG||'/usr/bin/ffmpeg');}
let engine;
app.whenReady().then(()=>{
 const binary=getDecoderBinary();
 const cache=path.join(app.getPath('temp'),'Bplus-offline-'+require('os').userInfo().username.replace(/[^a-z0-9]/gi,'_'));
 engine=new OfflineEngine({binary,cache,onProgress:info=>send('media:preparation',info)});
 // Converted files are session-only. Originals are never modified.
 engine.clear();
});
ipcMain.handle('media:prepare',(e,p,force)=>engine.prepare(p,force));
ipcMain.handle('media:cancel',()=>engine.cancel());
ipcMain.handle('media:clear-cache',()=>engine.clear());
ipcMain.handle('media:open', openMedia);
ipcMain.handle('media:entries', (e, paths) => paths.map(mediaEntry));
// Same-format siblings of an opened file, natural-sorted. Read-only directory listing.
ipcMain.handle('media:siblings', (e, p) => {
  try {
    const dir = path.dirname(p);
    const ext = path.extname(p).toLowerCase();
    if (!ext) return [mediaEntry(p)];
    return fs.readdirSync(dir, { withFileTypes: true })
      .filter(d => d.isFile() && path.extname(d.name).toLowerCase() === ext)
      .map(d => d.name)
      .sort((a, b) => a.localeCompare(b, 'en', { numeric: true, sensitivity: 'base' }))
      .map(f => mediaEntry(path.join(dir, f)));
  } catch { return [mediaEntry(p)]; }
});
ipcMain.handle('media:open-folder', openFolder);
ipcMain.handle('subtitle:open', openSubtitle);
ipcMain.handle('cover:extract', async (e, p) => {
 try {
  const bin = getDecoderBinary();
  if (!bin || !fs.existsSync(bin) || !p || !fs.existsSync(p)) return null;
  const dir = path.join(app.getPath('userData'), 'covers');
  fs.mkdirSync(dir, { recursive: true });
  const out = path.join(dir, 'cover-' + require('crypto').createHash('sha1').update(String(p)).digest('hex') + '.png');
  if (!fs.existsSync(out) || !fs.statSync(out).size) {
   await new Promise((resolve) => {
    require('child_process').execFile(bin, ['-y', '-v', 'error', '-i', p, '-an', '-vframes', '1', '-c:v', 'png', out], { timeout: 20000 }, () => resolve());
   });
  }
  if (fs.existsSync(out) && fs.statSync(out).size > 0) return pathToFileURL(out).href;
  try { fs.unlinkSync(out); } catch {}
  return null;
 } catch { return null; }
});
ipcMain.handle('app:about', () => send('menu:action', 'about'));
ipcMain.handle('subtitle:read', (e, p) => ({ name: path.basename(p), content: fs.readFileSync(p, 'utf8') }));
ipcMain.handle('subtitle:auto', (e, videoPath) => {
  const dir = path.dirname(videoPath);
  const base = path.basename(videoPath, path.extname(videoPath)).toLowerCase();
  try {
    for (const f of fs.readdirSync(dir)) {
      const ext = path.extname(f).toLowerCase();
      if (SUB_EXTS.includes(ext) && path.basename(f, ext).toLowerCase() === base) {
        return { name: f, content: fs.readFileSync(path.join(dir, f), 'utf8') };
      }
    }
  } catch (_) {}
  return null;
});

ipcMain.handle('snapshot:save', async (e, dataUrl) => {
  const r = await dialog.showSaveDialog(win, {
    defaultPath: 'B+-snapshot.png',
    filters: [{ name: 'PNG', extensions: ['png'] }]
  });
  if (r.canceled || !r.filePath) { send('snapshot:saved', null); return; }
  const base64 = dataUrl.replace(/^data:image\/png;base64,/, '');
  fs.writeFileSync(r.filePath, Buffer.from(base64, 'base64'));
  send('snapshot:saved', path.basename(r.filePath));
});

ipcMain.handle('system:defaults', () => process.platform === 'win32' ? shell.openExternal('ms-settings:defaultapps') : Promise.resolve('Windows only'));
ipcMain.handle('system:info', () => ({ platform: process.platform, installed: !process.env.PORTABLE_EXECUTABLE_FILE && app.isPackaged }));

ipcMain.on('win:min', () => win && win.minimize());
ipcMain.on('win:max-toggle', () => { if (win) { win.isMaximized() ? win.unmaximize() : win.maximize(); } });
ipcMain.on('win:close', () => win && win.close());

ipcMain.on('test:result', (e, result) => {
  console.log('TEST_RESULT ' + JSON.stringify(result));
  app.exit(result && result.ok ? 0 : 1);
});

function createWindow() {
  win = new BrowserWindow({
    width: 1120,
    height: 720,
    minWidth: 680,
    minHeight: 440,
    frame: false,
    backgroundColor: '#0e0e11',
    title: 'B+ Media Player',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      spellcheck: false
    }
  });
  win.webContents.session.webRequest.onBeforeRequest({ urls: ['http://*/*', 'https://*/*', 'ws://*/*', 'wss://*/*'] }, (details, cb) => cb({ cancel: true }));
  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  win.webContents.on('will-navigate', e => e.preventDefault());
  Menu.setApplicationMenu(null);
  win.once('ready-to-show', () => { if(process.env.BPLUS_BENCH) console.log('STARTUP_MS '+Math.round(performance.now()-startupAt)); });
  win.webContents.on('did-start-loading',()=>{rendererReady=false;});
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));
  win.webContents.on('did-finish-load', () => {
    if (process.env.BPLUS_TEST && process.env.BPLUS_TEST_FILES) {
      send('test:run', JSON.parse(process.env.BPLUS_TEST_FILES).map(mediaEntry));
    } else if (process.env.BPLUS_DEMO && process.env.BPLUS_TEST_FILES) {
      send('playlist:add', JSON.parse(process.env.BPLUS_TEST_FILES).map(mediaEntry));
    } else { flushExternal(); }
  });
}

const single = app.requestSingleInstanceLock();
if (!single) app.quit();
else {
  app.on('second-instance', (e, argv, cwd) => {
    queueExternal(parseMediaArguments(argv,cwd));
    if (win) { if (win.isMinimized()) win.restore(); win.focus(); }
  });
  app.on('open-file',(e,p)=>{e.preventDefault();queueExternal([p]);});
  app.whenReady().then(()=>{createWindow();require('electron').nativeTheme.on('updated',()=>send('theme:changed',require('electron').nativeTheme.shouldUseDarkColors?'dark':'light'));});
}
app.on('before-quit',()=>{if(engine)engine.clear();});
app.on('window-all-closed', () => app.quit());
