/* B+ settings: stored on this device only. No network services. */
(() => {
 const defaults={brightness:100,contrast:100,saturation:100,hue:0,boost:100,bass:0,mid:0,treble:0,autoplay:true,autoNext:true,loop:false,seekStep:5,autoSub:true,subFont:'Cairo',subSize:28,subColor:'#ffffff',subDelay:0,accent:'#2f89fc',language:'ar',playlist:false,rate:1,fit:'contain',shortcutPlay:'Space',shortcutFull:'f',shortcutMute:'m',shortcutNext:'PageDown',shortcutPrev:'PageUp',shortcutStop:'s',shortcutSub:'t',shortcutSubEarlier:'g',shortcutSubLater:'h',shortcutPlaylist:'p',shortcutSettings:',',shortcutSnapshot:'c',shortcutSeekForward:'ArrowRight',shortcutSeekBack:'ArrowLeft',shortcutVolumeUp:'ArrowUp',shortcutVolumeDown:'ArrowDown',theme:'system',opacity:100,vizStyle:'cover',vizSize:'small',thumbPreview:true,updateAuto:true,iconStyle:'phosphor-fill',addSiblings:false,lastSection:'playback',alwaysOnTop:false,sortBy:'name',sortDir:1,shade:-1};
 const keyChoices=['Space','Enter','f','m','p','k','s','t','c',',','n','b','v','PageDown','PageUp','ArrowRight','ArrowLeft','ArrowUp','ArrowDown'];
 let stored={};try{stored=JSON.parse(localStorage.getItem('bplus-prefs')||'{}')||{}}catch{}
 let prefs={...defaults,...stored};
 if(!['ar','en'].includes(prefs.language))prefs.language='ar';
 if(!['dark','light','system'].includes(prefs.theme))prefs.theme=defaults.theme;
 prefs.iconStyle={outline:'phosphor',bold:'phosphor',chips:'phosphor-fill',duo:'phosphor-duotone'}[prefs.iconStyle]||prefs.iconStyle;if(!BPLUS_ICONS.FAMILIES[prefs.iconStyle])prefs.iconStyle='phosphor';
 if(!['cover','spectrum','wave','line','dots','ripple','logo'].includes(prefs.vizStyle))prefs.vizStyle='cover';
 if(!['small','medium','large'].includes(prefs.vizSize))prefs.vizSize='small';
 prefs.opacity=Math.min(100,Math.max(65,Number(prefs.opacity)||100));
 prefs.shade=Math.max(-1,Math.min(100,Math.round(Number(prefs.shade))));if(isNaN(prefs.shade))prefs.shade=-1;
 // Preserve older custom keys before assigning defaults to the newly added actions.
 const used=new Set();Object.keys(defaults).filter(k=>k.startsWith('shortcut')).sort((a,b)=>Number(b in stored)-Number(a in stored)).forEach(k=>{if(!keyChoices.includes(prefs[k])||used.has(prefs[k]))prefs[k]=[defaults[k],...keyChoices].find(v=>!used.has(v));used.add(prefs[k]);});
 window.bplusPrefs=prefs;
 let ctx,source,gain,analyser,eq=[];let systemTheme='dark';window.bplus.systemTheme().then(t=>{systemTheme=t;apply()});window.bplus.onTheme(t=>{systemTheme=t;apply()});
 function audio(){if(ctx)return;try{ctx=new AudioContext();source=ctx.createMediaElementSource(player);let last=source;[120,1000,8000].forEach((hz,i)=>{let f=ctx.createBiquadFilter();f.type=i===0?'lowshelf':i===2?'highshelf':'peaking';f.frequency.value=hz;f.Q.value=1;last.connect(f);last=f;eq.push(f)});gain=ctx.createGain();last.connect(gain);gain.connect(ctx.destination);analyser=ctx.createAnalyser();analyser.fftSize=2048;analyser.smoothingTimeConstant=.45;analyser.minDecibels=-100;analyser.maxDecibels=0;gain.connect(analyser);applyAudio()}catch(e){showOsd(prefs.language==='ar'?'تعذر تشغيل معالجة الصوت':'Audio processing unavailable')}}
 function applyAudio(){if(!ctx)return;gain.gain.value=(prefs.boost/100)*(window.bplusVolumeBoost||1);eq.forEach((f,i)=>f.gain.value=prefs[['bass','mid','treble'][i]]);ctx.resume().catch(()=>{})}
 player.addEventListener('play',()=>{if(prefs.boost!==100||prefs.bass||prefs.mid||prefs.treble||(window.bplusVolumeBoost||1)>1||prefs.vizStyle!=='cover')audio();if(ctx)ctx.resume().catch(()=>{})});
 let cueStyle=document.createElement('style');document.head.append(cueStyle);
 function apply(){
  const root=document.documentElement.style;
  const baseTheme=prefs.theme==='system'?systemTheme:prefs.theme;
  let theme=baseTheme;
  const shadeVars=['--bg','--chrome','--chrome-2','--video-bg','--text','--text-dim','--text-faint','--border','--hover','--panel','--panel-border','--panel-head','--panel-nav','--panel-text'];
  if(prefs.shade>=0){
    // Theme shade slider: 0 = white ... 100 = black; every surface derives from one lightness.
    const L=0.97-prefs.shade*0.0093;
    const dark=L<0.5;
    theme=dark?'dark':'light';
    const grey=x=>{const v=Math.round(Math.min(1,Math.max(0,x))*255);return `rgb(${v},${v},${v})`};
    root.setProperty('--bg',grey(L));
    root.setProperty('--chrome',grey(L+0.045));
    root.setProperty('--chrome-2',grey(L+0.085));
    root.setProperty('--video-bg',grey(L-0.07));
    root.setProperty('--panel',grey(L+0.05));
    root.setProperty('--panel-head',grey(L+0.09));
    root.setProperty('--panel-nav',grey(L+0.02));
    root.setProperty('--panel-border',dark?'rgba(255,255,255,.16)':'rgba(0,0,0,.18)');
    root.setProperty('--panel-text',dark?'#dddddd':'#1a1c20');
    root.setProperty('--text',dark?'#e6e6e6':'#14161a');
    root.setProperty('--text-dim',dark?'#a8a8a8':'#3a3d44');
    root.setProperty('--text-faint',dark?'#8a8a8a':'#555962');
    root.setProperty('--border',dark?'rgba(255,255,255,.06)':'rgba(0,0,0,.1)');
    root.setProperty('--hover',dark?'rgba(255,255,255,.07)':'rgba(0,0,0,.06)');
    if(prefs.shade<=2){ // lightest end: white video area, menus and controls one notch darker so they separate
      ['--bg','--video-bg'].forEach(v=>root.setProperty(v,'#ffffff'));
      [['--chrome','#e9ebee'],['--chrome-2','#dfe2e6'],['--panel','#eceef1'],['--panel-head','#e1e4e8'],['--panel-nav','#e6e8ec'],['--border','#c9cdd3']].forEach(([k,v])=>root.setProperty(k,v));
    }
  } else shadeVars.forEach(v=>root.removeProperty(v));
  document.documentElement.dataset.theme=theme;BPLUS_ICONS.apply(prefs.iconStyle);window.bplus.setOpacity(prefs.opacity/100);
  const pinBtn=document.getElementById('tb-pin');if(pinBtn)pinBtn.classList.toggle('on',!!prefs.alwaysOnTop);if(window.bplus.setAlwaysOnTop)window.bplus.setAlwaysOnTop(!!prefs.alwaysOnTop);
  player.style.filter=`brightness(${prefs.brightness}%) contrast(${prefs.contrast}%) saturate(${prefs.saturation}%) hue-rotate(${prefs.hue}deg)`;
  player.style.objectFit=prefs.fit;if(window.bplusSetLoop)window.bplusSetLoop(prefs.loop);else player.loop=prefs.loop;player.playbackRate=+prefs.rate;rateIdx=RATES.indexOf(+prefs.rate);$('b-speed').textContent=prefs.rate+'×';
  if(!/^#[0-9a-f]{6}$/i.test(prefs.accent))prefs.accent=defaults.accent;
  const effAccent=accentFor(prefs.accent); // very light accents switch to dark grey in Day theme so they stay visible
  const rgb=[1,3,5].map(i=>parseInt(effAccent.slice(i,i+2),16));
  root.setProperty('--accent',effAccent);
  root.setProperty('--accent-viz',theme==='light'?effAccent:prefs.accent); // visualizer ink must contrast with the video area, which follows the shade
  root.setProperty('--accent-soft',`rgba(${rgb.join(',')},0.14)`);
  root.setProperty('--accent-overlay',`rgba(${rgb.join(',')},0.92)`);
  root.setProperty('--accent-light',`rgb(${rgb.map(x=>Math.round(document.documentElement.dataset.theme==='light'?x*.55:x+(255-x)*.52)).join(',')})`);
  const luminance=rgb.map(x=>{x/=255;return x<=.04045?x/12.92:((x+.055)/1.055)**2.4}).reduce((sum,x,i)=>sum+x*[.2126,.7152,.0722][i],0);
  root.setProperty('--accent-ink',luminance>.18?'#101010':'#ffffff');
  if(window.bplusUpdateViz&&apply._lastViz!==prefs.vizStyle+'|'+prefs.vizSize){apply._lastViz=prefs.vizStyle+'|'+prefs.vizSize;window.bplusUpdateViz()}
  document.querySelectorAll('.accent-choice').forEach(b=>{const dot=b.querySelector('i');if(dot)dot.style.background=accentFor(b.dataset.color)});
  document.documentElement.lang=prefs.language;document.documentElement.dir=prefs.language==='ar'?'rtl':'ltr';if(window.bplus.setUILanguage)window.bplus.setUILanguage(prefs.language);cueStyle.textContent=`video::cue { font-size:${prefs.subSize}px; color:${prefs.subColor}; font-family:'${prefs.subFont}','Cairo','Segoe UI',Tahoma,sans-serif; }`;
  applyAudio();
 }
 function save(){localStorage.setItem('bplus-prefs',JSON.stringify(prefs));apply()}
 function syncDelay(){for(const tr of player.textTracks){if(!tr.cues)continue;for(const cue of tr.cues){if(cue._bplusOriginal===undefined)cue._bplusOriginal=[cue.startTime,cue.endTime];cue.startTime=Math.max(0,cue._bplusOriginal[0]+Number(prefs.subDelay));cue.endTime=Math.max(cue.startTime+.01,cue._bplusOriginal[1]+Number(prefs.subDelay))}}}
 player.addEventListener('loadedmetadata',()=>{apply();});
 player.addEventListener('load',syncDelay,true);
 const overlay=document.createElement('div');overlay.className='settings-overlay';overlay.hidden=true;overlay.innerHTML=`<section class="settings-window" role="dialog" aria-modal="true"><header class="settings-header"><div><b>B<span>+</span></b><strong id="settings-title"></strong></div><button id="settings-close" aria-label="Close">✕</button></header><div class="settings-body"><nav id="settings-nav"></nav><main id="settings-content"></main></div><footer class="settings-footer"><small id="settings-note"></small><button id="settings-reset"></button><button id="settings-done"></button></footer></section>`;document.body.append(overlay);
 const content=overlay.querySelector('#settings-content'),nav=overlay.querySelector('#settings-nav');let active='video';
 function accentFor(color){if(!/^#[0-9a-f]{6}$/i.test(color))return color;const c=[1,3,5].map(i=>parseInt(color.slice(i,i+2),16));const lum=c.map(x=>{x/=255;return x<=.04045?x/12.92:((x+.055)/1.055)**2.4}).reduce((s,x,i)=>s+x*[.2126,.7152,.0722][i],0);return document.documentElement.dataset.theme==='light'&&lum>.55?'#3f444d':color}
 const labels={ar:{title:'إعدادات B+',video:'الصورة',audio:'الصوت',playback:'التشغيل',subtitles:'الترجمة',appearance:'المظهر',language:'اللغة',shortcuts:'الاختصارات',files:'الملفات',updates:'التحديثات',updateAuto:'التحقق من التحديثات تلقائيًا عند تشغيل البرنامج',about:'حول البرنامج',done:'تم',reset:'إعادة ضبط القسم',note:'التغييرات تُحفظ تلقائيًا على هذا الجهاز',brightness:'الإضاءة',contrast:'التباين',saturation:'التشبع',hue:'درجة اللون',boost:'تضخيم الصوت',bass:'الجهير',mid:'الترددات المتوسطة',treble:'الترددات العالية',vizStyle:'شكل عرض الصوتيات',vizSize:'حجم عرض الصوتيات',thumbPreview:'صورة مصغرة عند الوقوف على شريط التقدم',iconStyle:'نمط الأيقونات',addSiblings:'إضافة ملفات المجلد بنفس الصيغة عند فتح ملف',autoplay:'التشغيل التلقائي عند فتح ملف',autoNext:'تشغيل الملف التالي تلقائيًا',loop:'تكرار الملف',seekStep:'خطوة التقديم والتأخير (ثانية)',autoSub:'تحميل الترجمة المجاورة تلقائيًا',subFont:'خط الترجمة',subSize:'حجم خط الترجمة',subColor:'لون الترجمة',subDelay:'تأخير الترجمة (ثانية)',accent:'لون التمييز',playlist:'إظهار قائمة التشغيل عند البدء',rate:'سرعة التشغيل الافتراضية',fit:'طريقة ملء الصورة',shortcutPlay:'تشغيل / إيقاف',shortcutFull:'ملء الشاشة',shortcutMute:'كتم الصوت'},en:{title:'B+ Settings',video:'Video',audio:'Audio',playback:'Playback',subtitles:'Subtitles',appearance:'Appearance',language:'Language',shortcuts:'Shortcuts',files:'File types',updates:'Updates',updateAuto:'Check for updates automatically on startup',about:'About',done:'Done',reset:'Reset section',note:'Changes saved automatically on this device',brightness:'Brightness',contrast:'Contrast',saturation:'Saturation',hue:'Hue',boost:'Volume boost',bass:'Bass',mid:'Midrange',treble:'Treble',vizStyle:'Audio visualization',vizSize:'Visualizer size',thumbPreview:'Seek bar thumbnail preview',iconStyle:'Icon style',addSiblings:'Add same-format folder files when opening a file',autoplay:'Play automatically when opening files',autoNext:'Play next file automatically',loop:'Repeat current file',seekStep:'Seek step (seconds)',autoSub:'Load matching subtitle automatically',subFont:'Subtitle font',subSize:'Subtitle font size',subColor:'Subtitle color',subDelay:'Subtitle delay (seconds)',accent:'Accent color',playlist:'Show playlist on startup',rate:'Default playback speed',fit:'Video fit',shortcutPlay:'Play / pause',shortcutFull:'Fullscreen',shortcutMute:'Mute'}};
 const groups={video:['brightness','contrast','saturation','hue','fit'],audio:['boost','bass','mid','treble'],playback:['autoplay','autoNext','addSiblings','loop','seekStep','rate','thumbPreview'],subtitles:['autoSub','subFont','subSize','subColor','subDelay'],appearance:['accent','vizStyle','vizSize','iconStyle','playlist','theme','shade','opacity'],updates:['updateAuto'],shortcuts:['shortcutPlay','shortcutFull','shortcutMute','shortcutNext','shortcutPrev','shortcutStop','shortcutSub','shortcutSubEarlier','shortcutSubLater','shortcutPlaylist','shortcutSettings','shortcutSnapshot','shortcutSeekForward','shortcutSeekBack','shortcutVolumeUp','shortcutVolumeDown']};
 const ranges={opacity:[65,100,1,'%'],brightness:[25,200,1,'%'],contrast:[25,200,1,'%'],saturation:[0,200,1,'%'],hue:[-180,180,1,'°'],boost:[0,200,1,'%'],bass:[-12,12,1,'dB'],mid:[-12,12,1,'dB'],treble:[-12,12,1,'dB'],seekStep:[1,60,1,'s'],subSize:[14,54,1,'px'],subDelay:[-10,10,.1,'s']};
 function t(k){const extra={theme:['السمة','Theme'],shade:['درجة السمة','Theme shade'],opacity:['عتامة النافذة','Window opacity'],shortcutNext:['التالي','Next'],shortcutPrev:['السابق','Previous'],shortcutStop:['إيقاف','Stop'],shortcutSub:['إظهار الترجمة','Toggle subtitles'],shortcutSubEarlier:['تقديم الترجمة','Subtitles earlier'],shortcutSubLater:['تأخير الترجمة','Subtitles later'],shortcutPlaylist:['قائمة التشغيل','Playlist'],shortcutSettings:['الإعدادات','Settings'],shortcutSnapshot:['لقطة شاشة','Snapshot'],shortcutSeekForward:['تقديم','Seek forward'],shortcutSeekBack:['تأخير','Seek back'],shortcutVolumeUp:['رفع الصوت','Volume up'],shortcutVolumeDown:['خفض الصوت','Volume down']};return labels[prefs.language][k]||extra[k]?.[prefs.language==='ar'?0:1]||k}
 function row(key){let r=document.createElement('label');r.className='setting-row';let label=document.createElement('span');label.textContent=t(key);r.append(label);let control=document.createElement('div');control.className='setting-control';let input=document.createElement('input');input.dataset.pref=key;
  if(key==='shade'){input.type='range';input.min=0;input.max=100;input.step=1;input.value=prefs.shade<0?(document.documentElement.dataset.theme==='light'?10:90):prefs.shade;let output=document.createElement('output');output.textContent=prefs.shade<0?(prefs.language==='ar'?'تلقائي':'Auto'):prefs.shade+'%';input.oninput=()=>{prefs.shade=Number(input.value);output.textContent=input.value+'%';save()};control.append(input,output)}
  else if(ranges[key]){let [min,max,step,unit]=ranges[key];input.type='range';input.min=min;input.max=max;input.step=step;input.value=prefs[key];let output=document.createElement('output');output.textContent=prefs[key]+unit;input.oninput=()=>{prefs[key]=Number(input.value);output.textContent=input.value+unit;if(['boost','bass','mid','treble'].includes(key))audio();save();if(key==='subDelay')syncDelay()};control.append(input,output)}
  else if(typeof defaults[key]==='boolean'){input.type='checkbox';input.checked=prefs[key];input.onchange=()=>{prefs[key]=input.checked;save()};control.append(input)}
  else if(key==='subColor'){input.type='color';input.value=prefs[key];input.oninput=()=>{prefs[key]=input.value;save()};control.append(input)}
  else {let options=key==='theme'?['dark','light','system']:key==='vizStyle'?['cover','spectrum','wave','line','dots','ripple','logo']:key==='vizSize'?['small','medium','large']:key==='iconStyle'?Object.keys(BPLUS_ICONS.FAMILIES):key==='rate'?[.5,.75,1,1.25,1.5,2]:key==='fit'?['contain','cover','fill']:key==='subFont'?['Cairo','Segoe UI','Tahoma','Arial','Verdana','Times New Roman','Courier New']:key.startsWith('shortcut')?keyChoices:[];let select=document.createElement('select');select.dataset.pref=key;options.forEach(v=>{let o=document.createElement('option');o.value=v;o.textContent=key==='theme'?({dark:prefs.language==='ar'?'ليلي':'Night',light:prefs.language==='ar'?'نهاري':'Day',system:prefs.language==='ar'?'حسب Windows':'Follow Windows'}[v]):key==='vizStyle'?({cover:prefs.language==='ar'?'الغلاف مع أعمدة':'Cover + bars',spectrum:prefs.language==='ar'?'أعمدة':'Bars',wave:prefs.language==='ar'?'موجة':'Wave',line:prefs.language==='ar'?'خط':'Line',dots:prefs.language==='ar'?'نقاط':'Dots',ripple:prefs.language==='ar'?'دوائر':'Circles',logo:prefs.language==='ar'?'شعار B+':'B+ Logo'}[v]):key==='vizSize'?({small:prefs.language==='ar'?'صغير':'Small',medium:prefs.language==='ar'?'وسط':'Medium',large:prefs.language==='ar'?'كبير':'Large'}[v]):key==='iconStyle'?BPLUS_ICONS.LABELS[v][prefs.language]:v;select.append(o)});select.value=prefs[key];select.onchange=()=>{if(key.startsWith('shortcut')&&Object.keys(prefs).some(k=>k!==key&&k.startsWith('shortcut')&&prefs[k]===select.value)){showOsd(prefs.language==='ar'?'المفتاح مستخدم بالفعل':'Key already in use');select.value=prefs[key];return}prefs[key]=key==='rate'?Number(select.value):select.value;if(key==='theme')prefs.shade=-1;save();if(key==='theme')render()};control.append(select)}r.append(control);return r}
 const palette=[['blue','أزرق','Blue','#2f89fc'],['orange','برتقالي','Orange','#ff5a26'],['green','أخضر','Green','#36ba78'],['purple','بنفسجي','Purple','#a776f4'],['red','أحمر','Red','#ed5252'],['pink','وردي','Pink','#ed6eaf'],['teal','فيروزي','Teal','#26b7b0'],['amber','كهرماني','Amber','#e5ad26'],['cyan','سماوي','Cyan','#16bed9'],['indigo','نيلي','Indigo','#7375ed'],['lime','ليموني','Lime','#9abd36'],['rose','وردي داكن','Rose','#d54e77'],['silver','فضي','Silver','#a4afbf'],['copper','نحاسي','Copper','#cd8c56'],['white','أبيض','White','#ffffff'],['grey','رمادي فاتح','Light grey','#c8ccd4']];
 function renderPalette(){
  const card=document.createElement('div');card.className='settings-card accent-card';
  const r1=document.createElement('div');r1.className='setting-row';let l1=document.createElement('span');l1.textContent=t('accent');r1.append(l1);
  const ar0=prefs.language==='ar';const grid=document.createElement('div');grid.className='accent-dd';
  const cur=palette.find(p=>p[3]===prefs.accent.toLowerCase());
  const btn=document.createElement('button');btn.type='button';btn.className='accent-dd-btn';btn.setAttribute('aria-haspopup','listbox');
  const bdot=document.createElement('i');bdot.style.background=accentFor(prefs.accent);const bname=document.createElement('span');bname.textContent=cur?(ar0?cur[1]:cur[2]):(ar0?'مخصص':'Custom');const chev=document.createElement('b');chev.textContent='▾';btn.append(bdot,bname,chev);
  let pop=null;const closePop=()=>{if(pop){pop.remove();pop=null;document.removeEventListener('mousedown',outside,true)}};
  function outside(e){if(pop&&!pop.contains(e.target)&&e.target!==btn&&!btn.contains(e.target))closePop()}
  btn.onclick=()=>{if(pop){closePop();return}pop=document.createElement('div');pop.className='accent-dd-list';pop.setAttribute('role','listbox');pop.dir=ar0?'rtl':'ltr';
   palette.forEach(([id,ar,en,color])=>{let o=document.createElement('button');o.type='button';o.className='accent-dd-opt';o.dataset.color=color;o.setAttribute('aria-selected',String(prefs.accent.toLowerCase()===color));let d=document.createElement('i');d.style.background=accentFor(color);let n=document.createElement('span');n.textContent=ar0?ar:en;o.append(d,n);o.onclick=()=>{prefs.accent=color;save();closePop();render()};pop.append(o)});
   document.body.append(pop);const r=btn.getBoundingClientRect();const h=Math.min(pop.scrollHeight,240);const up=r.bottom+h>window.innerHeight-8;pop.style.minWidth=r.width+'px';pop.style.left=Math.min(r.left,window.innerWidth-pop.offsetWidth-8)+'px';pop.style.top=(up?r.top-h-4:r.bottom+4)+'px';pop.style.maxHeight='240px';setTimeout(()=>document.addEventListener('mousedown',outside,true),0)};
  grid.append(btn);r1.append(grid);
  const r2=document.createElement('label');r2.className='setting-row accent-custom';let l2=document.createElement('span');l2.textContent=prefs.language==='ar'?'لون مخصص':'Custom color';r2.append(l2);let ctl=document.createElement('div');ctl.className='setting-control';let picker=document.createElement('input');picker.type='color';picker.id='accent-custom-picker';picker.value=prefs.accent;let value=document.createElement('output');value.textContent=prefs.accent.toUpperCase();picker.oninput=()=>{prefs.accent=picker.value;bdot.style.background=picker.value;bname.textContent=ar0?'مخصص':'Custom';value.textContent=picker.value.toUpperCase();save();grid.querySelectorAll('button').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.color===picker.value)))};ctl.append(picker,value);r2.append(ctl);
  card.append(r1,r2);content.append(card);
 }
 function render(){overlay.dir=prefs.language==='ar'?'rtl':'ltr';overlay.querySelector('#settings-title').textContent=t('title');overlay.querySelector('#settings-note').textContent=t('note');overlay.querySelector('#settings-reset').textContent=t('reset');overlay.querySelector('#settings-done').textContent=t('done');
  nav.replaceChildren();Object.keys(labels.ar).filter(k=>['video','audio','playback','subtitles','appearance','language','shortcuts','files','updates','about'].includes(k)).forEach(key=>{let b=document.createElement('button');{let sv=document.createElementNS('http://www.w3.org/2000/svg','svg');sv.dataset.ic=key==='audio'?'volume':'nav-'+key;b.append(sv)}b.append(document.createTextNode(t(key)));b.className=active===key?'active':'';b.onclick=()=>{active=key;prefs.lastSection=key;save();render()};nav.append(b)});BPLUS_ICONS.hydrate(nav);content.replaceChildren();let h=document.createElement('h2');h.textContent=t(active);content.append(h);
  const secDesc={video:['تعديل مظهر الصورة أثناء العرض دون تغيير الملف.','Adjust the picture during playback; the file is unchanged.'],audio:['مستوى الصوت والمعادل.','Volume and equalizer.'],playback:['التشغيل التلقائي والتكرار وخطوة التقديم.','Autoplay, repeat and seek behavior.'],subtitles:['مظهر الترجمة وتوقيتها.','Subtitle appearance and timing.'],appearance:['إعدادات الواجهة وعرض الصوتيات.','Interface settings and the audio visualizer.'],shortcuts:['تخصيص اختصارات لوحة المفاتيح.','Customize keyboard shortcuts.'],language:['لغة واجهة البرنامج.','App interface language.'],files:['أنواع الملفات المدعومة والملفات المؤقتة.','Supported file types and temporary files.'],updates:['تنزيل التحديثات وتثبيتها مع الحفاظ على إعداداتك.','Download and install updates while keeping your settings.'],about:['معلومات البرنامج والمطور.','App and developer information.']};
  if(secDesc[active]){let d=document.createElement('p');d.className='sec-desc';d.textContent=secDesc[active][prefs.language==='ar'?0:1];content.append(d)}
  if(active==='appearance'){
   const ar=prefs.language==='ar';
   const sect=(txt)=>{const h=document.createElement('h3');h.className='sub-title';h.textContent=txt;content.append(h)};
   const cardOf=(keys)=>{const card=document.createElement('div');card.className='settings-card';keys.forEach(k=>card.append(row(k)));content.append(card)};
   sect(ar?'الواجهة':'Interface');
   cardOf(['iconStyle','playlist','theme','shade','opacity']);
   {let p=document.createElement('p');p.className='settings-info';p.textContent=prefs.language==='ar'?'العتامة تؤثر على النافذة كلها بما فيها الفيديو. اختيار «حسب Windows» يتابع تغيير سمة النظام تلقائيًا.':'Opacity affects the whole window, including video. Follow Windows updates with the system theme.';content.append(p)}
   sect(ar?'اللون':'Color');
   renderPalette();
   sect(ar?'عرض الصوتيات':'Visualizer');
   cardOf(['vizStyle','vizSize']);
  } else if(groups[active]){const card=document.createElement('div');card.className='settings-card';groups[active].filter(k=>k!=='accent').forEach(k=>card.append(row(k)));content.append(card)}
  const note=(text)=>{let p=document.createElement('p');p.className='settings-info';p.textContent=text;content.append(p)};
  if(active==='subtitles'){
   const en=prefs.language==='en';
   let syncRow=document.createElement('div');syncRow.className='settings-card';syncRow.id='sub-sync-row';
   let lab=document.createElement('p');lab.className='settings-info';lab.id='sub-sync-val';
   const fmtV=()=>{const v=Number(prefs.subDelay)||0;return (en?'Current offset: ':'الإزاحة الحالية: ')+(v>0?'+':'')+v.toFixed(1)+(en?' s':' ث');};
   lab.textContent=fmtV();
   let bE=document.createElement('button');bE.className='settings-action';bE.textContent=en?'− 0.5s earlier':'تقديم 0.5− ث';
   let bL=document.createElement('button');bL.className='settings-action';bL.textContent=en?'+ 0.5s later':'تأخير 0.5+ ث';
   let bR=document.createElement('button');bR.className='settings-action';bR.textContent=en?'Reset':'إعادة ضبط';
   const bump=(d)=>{prefs.subDelay=Math.max(-10,Math.min(10,Math.round(((+prefs.subDelay||0)+d)*10)/10));save();syncDelay();lab.textContent=fmtV();};
   bE.onclick=()=>bump(-0.5);bL.onclick=()=>bump(0.5);bR.onclick=()=>{prefs.subDelay=0;save();syncDelay();lab.textContent=fmtV();};
   bL.style.marginInlineStart='8px';bR.style.marginInlineStart='8px';
   syncRow.append(lab,bE,bL,bR);content.append(syncRow);
   note(en?'Shift subtitles earlier or later in 0.5s steps, or with the G / H keys during playback.':'قدّم الترجمة أو أخّرها بخطوات 0.5 ثانية، أو بمفتاحي G / H أثناء التشغيل.');
  }
  if(active==='audio')note(prefs.language==='ar'?'التضخيم فوق 100% قد يسبب تشويشًا. اخفضه إذا حدث ذلك. المعادل بثلاثة نطاقات.':'Boost above 100% may distort. Reduce it if needed. Three-band equalizer.');
  if(active==='video')note(prefs.language==='ar'?'التعديلات تؤثر على العرض فقط، ولا تغيّر الملف الأصلي.':'Display effects do not change the original file.');
  if(active==='shortcuts')note(prefs.language==='ar'?'يمكن تغيير 16 اختصارًا أدناه. تظل Ctrl+O لفتح الملفات وCtrl+L للترجمة وCtrl+P للقائمة وF11 لملء الشاشة ثابتة. اختر مفتاحًا مختلفًا لكل إجراء.':'Customize 16 actions below. Ctrl+O: open, Ctrl+L: subtitles, Ctrl+P: playlist and F11: fullscreen remain fixed. Use a different key for each action.');
  if(active==='language'){let select=document.createElement('select');select.innerHTML='<option value="ar">العربية</option><option value="en">English</option>';select.value=prefs.language;select.onchange=()=>{prefs.language=select.value;save();render();translateChrome()};content.append(select);note('العربية / English. Settings, menus and button tooltips follow this choice.');}
  if(active==='files'){
   note(prefs.language==='ar'?'التثبيت يسجل الأنواع أدناه للمستخدم الحالي في «فتح باستخدام». لكي يفتح النقر المزدوج البرنامج، اختر B+ افتراضيًا مرة واحدة في Windows. لا نغيّر اختيارك بالقوة.':'The installer registers the types below in Open with for this user. Choose B+ once as the Windows default for double-click opening. Your defaults are not overridden.');
   let b=document.createElement('button');b.className='settings-action';b.textContent=prefs.language==='ar'?'فتح إعدادات تطبيقات Windows الافتراضية':'Open Windows default apps';b.onclick=()=>window.bplus.openDefaults();content.append(b);
   note('MP4 · M4V · MOV · WebM · MKV · AVI · WMV · FLV · MPEG · MPG · M2V · MTS · M2TS · TS · VOB · 3GP · 3G2 · ASF · DIVX · F4V · OGV');
   note('MP3 · FLAC · WAV · AAC · OGG · M4A · WMA · OPUS · AIFF · AIF · APE · AC3 · DTS · AMR · ALAC · MKA · MP2 · OGA · WEBA');
   note(prefs.language==='ar'?'يعمل FFmpeg المرفق محليًا عند الحاجة. يجهّز أول مسار فيديو وصوت إلى ملف مؤقت قبل التشغيل؛ قد يستغرق الملف الكبير وقتًا ويحتاج مساحة إضافية. إلغاء التجهيز يوقفه. التحويلات تُحذف عند الخروج أو بدء جلسة جديدة. الملف الأصلي لا يتغيّر.':'Bundled FFmpeg runs offline when needed. It remuxes or converts the first video and audio streams to a temporary file before playback. Large files take time and extra disk space. Cancel stops preparation. Conversions are deleted when the app exits or starts a new session. Originals are unchanged.');
   let clearCache=document.createElement('button');clearCache.className='settings-action';clearCache.textContent=prefs.language==='ar'?'مسح الملفات المؤقتة (يوقف التشغيل)':'Clear temporary files (stops playback)';clearCache.onclick=async()=>{playbackSerial++;player.pause();activeResumeKey=null;player.removeAttribute('src');player.load();prep.hidden=true;const ok=await window.bplus.clearMediaCache();showOsd(prefs.language==='ar'?(ok?'تم مسح الملفات المؤقتة':'تعذر المسح. أغلق البرنامج ثم افتحه.'):(ok?'Temporary files cleared':'Could not clear. Close and reopen the app.'));};content.append(clearCache);
   let diagnostics=document.createElement('button');diagnostics.className='settings-action';diagnostics.style.marginInlineStart='8px';diagnostics.textContent=prefs.language==='ar'?'حفظ تقرير فتح الملفات':'Save file-opening report';diagnostics.onclick=()=>window.bplus.exportLaunchReport();content.append(diagnostics);
   note(prefs.language==='ar'?'تقرير فتح الملفات يُحفظ عند طلبك فقط ويحتوي مسارات الملفات المحلية. لا يُرسل إلى أي جهة.':'The file-opening report is saved only on request and includes local file paths. It is not uploaded anywhere.');
   note(prefs.language==='ar'?'الامتداد ليس ضمانًا لكل ترميز. الملفات المحمية DRM والتالفة وبعض الترميزات النادرة غير مدعومة. لا يوجد تبديل لمسارات الصوت أو الترجمات المدمجة. يدعم البرنامج ملفات SRT وVTT الخارجية.':'An extension does not guarantee every codec. DRM, damaged files and some uncommon codecs are unsupported. Embedded audio/subtitle track switching is not included. External SRT and VTT are supported.');
  }
  if(active==='updates'){
   const en=prefs.language==='en';
   window.bplus.appVersion().then(v=>{const el=document.getElementById('upd-cur');if(el)el.textContent=(en?'Current version: B+ ':'الإصدار الحالي: B+ ')+v});
   let cur=document.createElement('p');cur.className='settings-info';cur.id='upd-cur';cur.textContent=(en?'Current version: B+ ':'الإصدار الحالي: B+ ')+(window.BPLUS_APP_VERSION||'');
   let checkBtn=document.createElement('button');checkBtn.className='settings-action';checkBtn.id='upd-check';checkBtn.textContent=en?'Check for updates now':'التحقق من التحديثات الآن';
   let status=document.createElement('p');status.className='settings-info';status.id='upd-status';
   let prog=document.createElement('div');prog.className='upd-progress';prog.id='upd-progress';prog.hidden=true;prog.append(document.createElement('i'));
   let dlBtn=null;let errBox=null;
   checkBtn.onclick=async()=>{checkBtn.disabled=true;status.textContent=en?'Checking for updates...':'جارٍ التحقق من التحديثات...';
    let r;try{r=await window.bplus.updateCheck()}catch(e){r=null}checkBtn.disabled=false;
    if(dlBtn){dlBtn.remove();dlBtn=null}
    if(errBox){errBox.remove();errBox=null}
    if(r&&r.status==='available'){status.textContent=(en?'A new version is available: B+ ':'يتوفر إصدار جديد: B+ ')+r.version;
     dlBtn=document.createElement('button');dlBtn.className='settings-action';dlBtn.id='upd-download';dlBtn.textContent=en?'Download and install':'تنزيل وتثبيت';
     dlBtn.onclick=async()=>{dlBtn.disabled=true;prog.hidden=false;prog.firstElementChild.style.width='0%';
      if(errBox){errBox.remove();errBox=null}
      status.textContent=en?'Downloading... 0%':'جارٍ التنزيل... 0%';
      let d;try{d=await window.bplus.updateDownload(r)}catch(e){d={status:'error',message:String(e&&e.message||e)}}
      if(d&&d.status==='done'){status.textContent=en?'Download complete. The app will restart to install.':'اكتمل التنزيل. سيعاد تشغيل البرنامج للتثبيت.';await window.bplus.updateInstall(d.path);}
      else{status.textContent=(en?'Download failed. ':'فشل التنزيل. ')+(d&&d.message?String(d.message):'')+' '+(en?'Press the button to resume where it stopped.':'اضغط الزر للاستكمال من حيث توقف.');dlBtn.textContent=en?'Resume download':'استكمال التنزيل';dlBtn.disabled=false;prog.hidden=true;
       errBox=document.createElement('div');
       let pg=document.createElement('button');pg.className='settings-action';pg.textContent=en?'Open releases page':'فتح صفحة التحديثات';pg.onclick=()=>window.bplus.updateOpenPage();
       let cp=document.createElement('button');cp.className='settings-action';cp.textContent=en?'Copy details':'نسخ التفاصيل';cp.onclick=async()=>{try{await navigator.clipboard.writeText(String((d&&d.message)||'no-details'));cp.textContent=en?'Copied':'تم النسخ';setTimeout(()=>{cp.textContent=en?'Copy details':'نسخ التفاصيل'},1500)}catch(e){}};
       errBox.append(pg,cp);
       if(d&&d.logPath){let lp=document.createElement('p');lp.className='settings-info';lp.textContent=(en?'Details saved to: ':'سُجلت التفاصيل في: ')+d.logPath;errBox.append(lp);}
       content.append(errBox);}};
     content.append(dlBtn);}
    else if(r&&r.status==='latest')status.textContent=(en?'You are on the latest version (B+ ':'أنت على أحدث إصدار (B+ ')+r.version+').';
    else if(r&&r.status==='none')status.textContent=en?'No updates published yet. You are on the latest version.':'لا توجد تحديثات منشورة بعد. أنت على أحدث إصدار.';
    else{status.textContent=(en?'Could not check for updates. ':'تعذر التحقق من التحديثات. ')+(r&&r.message?String(r.message)+' — ':'')+(en?'Check your internet connection.':'تحقق من اتصال الإنترنت.');
     errBox=document.createElement('div');
     let pg=document.createElement('button');pg.className='settings-action';pg.textContent=en?'Open releases page':'فتح صفحة التحديثات';pg.onclick=()=>window.bplus.updateOpenPage();
     let cp=document.createElement('button');cp.className='settings-action';cp.textContent=en?'Copy details':'نسخ التفاصيل';cp.onclick=async()=>{try{await navigator.clipboard.writeText(String((r&&r.message)||'no-details'));cp.textContent=en?'Copied':'تم النسخ';setTimeout(()=>{cp.textContent=en?'Copy details':'نسخ التفاصيل'},1500)}catch(e){}};
     errBox.append(pg,cp);
     if(r&&r.logPath){let lp=document.createElement('p');lp.className='settings-info';lp.textContent=(en?'Details saved to: ':'سُجلت التفاصيل في: ')+r.logPath;errBox.append(lp);}
     content.append(errBox);}};
   content.append(cur,checkBtn,status,prog);
   note(en?'Updates are downloaded from the official B+ page on GitHub. Your settings are kept when updating.':'تُنزَّل التحديثات من صفحة B+ الرسمية على GitHub. تُحفظ إعداداتك عند التحديث.');
  }
  if(active==='about'){content.style.textAlign='center';}else{content.style.textAlign='start';}
  if(active==='about'){let logo=document.createElement('div');logo.className='about-logo';logo.textContent='B+';content.append(logo);note('B+ 1.0.5');note(prefs.language==='ar'?'تم تطوير البرنامج بواسطة':'Developed by');let identity=document.createElement('div');identity.className='about-identity';identity.dir='auto';identity.textContent=window.BPLUS_IDENTITY?.name||'[الاسم قيد التأكيد]';content.append(identity);note(window.BPLUS_IDENTITY?.email||'[البريد قيد التأكيد]');note(prefs.language==='ar'?'الرجاء الدعاء لوالدي (حلمي سعدون) بالرحمة والمغفرة':'Please pray for my father (Helmy Sadoun) for mercy and forgiveness');note(prefs.language==='ar'?'واجهة أصلية. بدون إعلانات أو تفعيل. مبني على Electron وChromium. محرك FFmpeg منفصل ومرفق. تحتفظ المكونات الخارجية برخصها.':'Original interface. No ads or activation. Built with Electron, Chromium and FFmpeg. Third-party licenses apply.');}
 }
 function open(section){if(document.getElementById('quick-av'))document.getElementById('quick-av').hidden=true;active=section||prefs.lastSection||'playback';prefs.lastSection=active;save();render();overlay.hidden=false;hidePops()}
 function close(){overlay.hidden=true}
 overlay.querySelector('#settings-close').onclick=close;overlay.querySelector('#settings-done').onclick=close;
 overlay.querySelector('#settings-reset').onclick=()=>{(groups[active]||[]).forEach(k=>prefs[k]=defaults[k]);save();syncDelay();render()};
 overlay.addEventListener('click',e=>{if(e.target===overlay)close()});document.addEventListener('keydown',e=>{if(!overlay.hidden&&e.key==='Escape'){e.stopImmediatePropagation();close()}});
 $('b-speed').onclick=(ev)=>{ev.stopPropagation();showPop(menuPop,$('b-speed'),c=>{popHead(c,prefs.language==='ar'?'سرعة التشغيل':'Playback speed');RATES.forEach((r,i)=>popItem(c,r+'×',null,()=>{prefs.rate=r;save();setRate(i);$('b-speed').textContent=r+'×';showOsd(prefs.language==='ar'?'السرعة '+r+'×':'Speed '+r+'×')},{checkable:true,checked:player.playbackRate===r}))})};
 $('b-av').onclick=(e)=>{e.stopPropagation();openQuick()};$('b-set').onclick=()=>open('playback');window.addEventListener('bplus-about',()=>open('about'));window.bplus.onMenuAction(a=>{if(a==='about')open('about')});
 // Configurable shortcuts. Ctrl file dialogs and F11 remain fixed.
 document.addEventListener('keydown',e=>{if(!overlay.hidden||e.ctrlKey||e.metaKey||e.altKey||['INPUT','SELECT','TEXTAREA','BUTTON','A'].includes(e.target.tagName))return;let key=e.key===' '?'Space':e.key.length===1?e.key.toLowerCase():e.key;if(e.shiftKey&&key.length===1)return;
 const subBump=(d)=>{prefs.subDelay=Math.max(-10,Math.min(10,Math.round(((+prefs.subDelay||0)+d)*10)/10));save();syncDelay();const v=prefs.subDelay;showOsd(prefs.language==='ar'?('الترجمة '+(v>0?'متأخرة':'متقدمة')+' '+Math.abs(v).toFixed(1)+' ث'):('Subtitles '+(v>0?'later':'earlier')+' by '+Math.abs(v).toFixed(1)+'s'))};
const actions={shortcutPlay:togglePlay,shortcutSubEarlier:()=>subBump(-0.5),shortcutSubLater:()=>subBump(0.5),shortcutFull:toggleFullscreen,shortcutMute:()=>bMute.click(),shortcutNext:next,shortcutPrev:prev,shortcutStop:()=>$('b-stop').click(),shortcutSub:toggleSubtitleVisible,shortcutPlaylist:()=>playlistEl.classList.toggle('hidden'),shortcutSettings:()=>open(),shortcutSnapshot:takeSnapshot,shortcutSeekForward:()=>{player.currentTime=Math.min(player.duration||0,player.currentTime+(+prefs.seekStep||5));updateSeekUI()},shortcutSeekBack:()=>{player.currentTime=Math.max(0,player.currentTime-(+prefs.seekStep||5));updateSeekUI()},shortcutVolumeUp:()=>{vol.value=Math.min(200,+vol.value+5);vol.dispatchEvent(new Event('input'))},shortcutVolumeDown:()=>{vol.value=Math.max(0,+vol.value-5);vol.dispatchEvent(new Event('input'))}};
 const command=Object.keys(actions).find(k=>prefs[k]===key);if(command){e.preventDefault();e.stopImmediatePropagation();actions[command]();}
 },true);
 function translateChrome(){let en=prefs.language==='en';const titles={'b-play':['تشغيل / إيقاف','Play / pause'],'b-stop':['إيقاف','Stop'],'b-prev':['السابق','Previous'],'b-next':['التالي','Next'],'b-mute':['كتم الصوت','Mute'],'b-sub':['تحميل ملف ترجمة','Load subtitles'],'b-subvis':['إظهار/إخفاء الترجمة','Toggle subtitles'],'b-snap':['لقطة شاشة','Snapshot'],'b-speed':['سرعة التشغيل','Playback speed'],'b-set':['الإعدادات','Settings'],'b-av':['الصورة والصوت','Video & audio'],'b-pl':['قائمة التشغيل','Playlist'],'tb-menu':['القائمة','Menu'],'tb-purefs':['ملء الشاشة بدون قوائم','Clean fullscreen'],'tb-open':['فتح ملف','Open file'],'pl-sort':['ترتيب القائمة','Sort playlist'],'tb-pin':['تثبيت في المقدمة','Always on top'],'win-min':['تصغير','Minimize'],'win-max':['تكبير','Maximize'],'win-close':['إغلاق','Close']};Object.entries(titles).forEach(([id,x])=>$(id).title=x[en?1:0]);document.querySelector('.pl-title').textContent=en?'Playlist':'قائمة التشغيل';document.querySelector('.hint-sub').textContent=en?'Drop video or audio here, or press Ctrl+O':'أفلت ملفات الفيديو أو الصوت هنا، أو اضغط Ctrl+O';if(!playlist.length)plEmpty.innerHTML=en?'Playlist is empty<br><span>Add files to start</span>':'القائمة فارغة<br><span>أضف ملفات للبدء</span>';}
 // Translate menu labels at creation, retaining the original action handlers.
 const originalPop=popItem;const menuEnglish={'فتح ملف...':'Open file...','فتح مجلد...':'Open folder...','تحميل ملف ترجمة...':'Load subtitles...','إظهار / إخفاء الترجمة':'Toggle subtitles','قائمة التشغيل':'Playlist','ملء الشاشة':'Fullscreen','حول B+':'About B+','خروج':'Exit','تشغيل / إيقاف مؤقت':'Play / pause','التالي':'Next','السابق':'Previous','تحميل ترجمة...':'Load subtitles...','لقطة شاشة':'Snapshot'};
 popItem=function(container,label,shortcut,fn,opts){return originalPop(container,prefs.language==='en'?(menuEnglish[label]||label):label,shortcut,fn,opts)};
 const quick=document.createElement('div');quick.id='quick-av';quick.hidden=true;quick.dir='rtl';quick.setAttribute('role','dialog');document.body.append(quick);
 function openQuick(){
  if(!quick.hidden){quick.hidden=true;return}
  hidePops();overlay.hidden=true;quick.replaceChildren();quick.dir=prefs.language==='ar'?'rtl':'ltr';
  let h=document.createElement('header');h.textContent=prefs.language==='ar'?'الصورة والصوت':'Video & audio';let close=document.createElement('button');close.textContent='✕';close.onclick=()=>quick.hidden=true;h.append(close);quick.append(h);
  ['brightness','contrast','saturation','boost','bass','mid','treble'].forEach(key=>quick.append(row(key)));
  let foot=document.createElement('footer');let reset=document.createElement('button');reset.textContent=prefs.language==='ar'?'إعادة ضبط':'Reset';reset.onclick=()=>{['brightness','contrast','saturation','boost','bass','mid','treble'].forEach(k=>prefs[k]=defaults[k]);save();quick.hidden=true;openQuick()};foot.append(reset);quick.append(foot);
  quick.hidden=false;const anchor=$('b-av').getBoundingClientRect();quick.style.left=Math.max(8,Math.min(anchor.right-quick.offsetWidth,innerWidth-quick.offsetWidth-8))+'px';quick.style.top=Math.max(42,anchor.top-quick.offsetHeight-10)+'px';
 }
 quick.addEventListener('click',e=>e.stopPropagation());document.addEventListener('click',()=>quick.hidden=true);document.addEventListener('keydown',e=>{if(e.key==='Escape')quick.hidden=true});
 let updOsdT=0;
 window.bplus.onUpdateProgress(p=>{const el=document.getElementById('upd-progress');const pct=p&&p.total?Math.min(100,Math.round(p.received/p.total*100)):0;
  if(el){el.hidden=false;if(el.firstElementChild)el.firstElementChild.style.width=pct+'%'}
  const st=document.getElementById('upd-status');if(st&&p&&p.total){const mb=x=>(x/1048576).toFixed(1);const en2=prefs.language==='en';st.textContent=(en2?'Downloading... ':'جارٍ التنزيل... ')+pct+'% ('+mb(p.received)+' / '+mb(p.total)+' MB)'+(p.attempt>1?(en2?' - resumed':' - تم الاستئناف'):'')}
  else{const now=Date.now();if(now-updOsdT>900){updOsdT=now;showOsd((prefs.language==='en'?'Downloading update... ':'جارٍ تنزيل التحديث... ')+pct+'%')}}});
 window.bplus.appVersion().then(v=>{window.BPLUS_APP_VERSION=v});
 setTimeout(async()=>{try{if(prefs.updateAuto===false)return;const r=await window.bplus.updateCheck();if(r&&r.status==='available')window.bplus.updatePrompt(r)}catch(e){}},7000);
 playlistEl.classList.toggle('hidden',!prefs.playlist);apply();translateChrome();window.BPLUS_SETTINGS={open,close,prefs,apply,save,audio,applyAudio,overlay,getAudio:()=>({ctx,gain,eq,analyser}),syncDelay};
})();
