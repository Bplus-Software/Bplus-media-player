// Offline helper process. No shell, no remote inputs, no HTTP listener.
const {spawn}=require('child_process');
const fs=require('fs');
const path=require('path');
const crypto=require('crypto');
const {pathToFileURL}=require('url');
const extensions=['mp4','m4v','mov','webm','ogv','ogg','mp3','flac','wav','aac','mkv','avi','wmv','flv','m4a','wma','mpeg','mpg','m2v','mts','m2ts','ts','vob','3gp','3g2','asf','divx','f4v','opus','aiff','aif','ape','ac3','dts','amr','alac','mka','mp2','oga','weba'];
class OfflineEngine{
 constructor({binary,cache,onProgress=()=>{}}){this.binary=binary;this.cache=cache;this.onProgress=onProgress;this.job=null;this.serial=0;}
 cancel(){this.serial++;if(this.job){this.job.kill();this.job=null;}}
 async prepare(input,force=false){
  this.cancel();const serial=this.serial;
  const st=fs.statSync(input);if(!st.isFile())throw Error('NOT_A_FILE');
  if(!extensions.includes(path.extname(input).slice(1).toLowerCase()))throw Error('UNSUPPORTED_EXTENSION');
  const key=crypto.createHash('sha256').update(path.resolve(input)+'\0'+st.size+'\0'+st.mtimeMs).digest('hex');
  fs.mkdirSync(this.cache,{recursive:true});
  // Fast path: containers Chromium demuxes natively play immediately; a decode
  // failure in the renderer re-invokes prepare with force for probe + convert.
  const nativeContainer=['mp4','m4v','mov','webm','ogv','ogg','mp3','flac','wav','aac','m4a','opus','oga','weba'].includes(path.extname(input).slice(1).toLowerCase());
  if(!force&&nativeContainer){this.onProgress({state:'idle'});return {url:pathToFileURL(input).href,converted:false};}
  // Read stream headers only, then choose native playback or local conversion.
  this.onProgress({state:'probing',name:path.basename(input),seconds:0});
  const metadata=await this.run(['-hide_banner','-nostdin','-protocol_whitelist','file,pipe,data','-i',input],serial,true);
  if(serial!==this.serial)throw Error('CANCELED');
  // Embedded cover art is an attached picture, not a video track: never let it
  // block direct playback or enter the converted file.
  const videoStreams=[...metadata.matchAll(/^\s*Stream #.*Video:\s*(\w+)[^\n]*$/gm)].map(m=>({codec:m[1],attached:/\(attached pic\)/.test(m[0])}));
  const video=(videoStreams.find(s=>!s.attached)||{}).codec;
  const audio=metadata.match(/^\s*Stream #.*Audio:\s*(\w+)/m)?.[1];
  if(!video&&!audio)throw Error('NO_READABLE_STREAM');
  const nativeVideo=!video||['h264','vp8','vp9','av1','theora'].includes(video);
  const nativeAudio=!audio||['aac','mp3','flac','opus','vorbis','pcm_s16le','pcm_s24le','pcm_f32le','mp2'].includes(audio);
  if(!force&&nativeContainer&&nativeVideo&&nativeAudio){this.onProgress({state:'idle'});return {url:pathToFileURL(input).href,converted:false};}
  this.onProgress({state:'converting',name:path.basename(input),seconds:0});
  // Audio-only results stay in a plain audio file so every browser decodes them.
  const ext=video?'.mp4':(audio==='mp3'?'.mp3':'.mp4');
  const output=path.join(this.cache,key+ext);
  if(fs.existsSync(output)){this.onProgress({state:'idle'});return {url:pathToFileURL(output).href,converted:true,cached:true};}
  const temp=path.join(this.cache,key+'.partial'+ext);
  try{
   // 0:V selects real video only; attached covers are excluded by the muxer too.
   const args=['-hide_banner','-nostdin','-y','-loglevel','error','-protocol_whitelist','file,pipe,data','-i',input,'-map','0:V:0?','-map','0:a:0?','-sn','-dn'];
   if(video)args.push(...(video==='h264'?['-c:v','copy']:['-vf','scale=trunc(iw/2)*2:trunc(ih/2)*2','-c:v','libx264','-preset','veryfast','-crf','20','-pix_fmt','yuv420p']));
   // Keep MP3 audio as an MP3 file; everything else becomes AAC in MP4.
   const copyAudio=audio==='aac'||audio==='mp3';
   args.push(...(copyAudio?['-c:a','copy']:['-c:a','aac','-b:a','192k']));
   if(ext==='.mp4')args.push('-movflags','+faststart');
   args.push('-progress','pipe:1',temp);
   await this.run(args,serial,false,path.basename(input));
   if(serial!==this.serial)throw Error('CANCELED');
   fs.renameSync(temp,output);return {url:pathToFileURL(output).href,converted:true};
  }finally{try{fs.unlinkSync(temp)}catch{}if(serial===this.serial)this.onProgress({state:'idle'});}
 }
 run(args,serial,probe=false,name=''){
  return new Promise((resolve,reject)=>{
   let child;try{child=spawn(this.binary,args,{windowsHide:true,stdio:['ignore','pipe','pipe']})}catch(e){reject(e);return}this.job=child;let error='',progress='';
   child.stderr.on('data',b=>{error=(error+b.toString()).slice(-65536)});
   child.stdout.on('data',b=>{progress+=b.toString();let lines=progress.split('\n');progress=lines.pop();for(let line of lines){if(line.startsWith('out_time_us='))this.onProgress({state:'converting',name,seconds:Number(line.slice(12))/1000000});}});
   const timer=probe?setTimeout(()=>child.kill(),30000):null;
   child.once('error',()=>{clearTimeout(timer);reject(Error('ENGINE_MISSING'))});
   child.once('close',code=>{clearTimeout(timer);if(this.job===child)this.job=null;if(serial!==this.serial)reject(Error('CANCELED'));else if(probe)resolve(error);else if(code===0)resolve(error);else reject(Error(/No space left|not enough space/i.test(error)?'DISK_FULL':'CONVERSION_FAILED'));});
  });
 }
 clear(){this.cancel();try{fs.rmSync(this.cache,{recursive:true,force:true});fs.mkdirSync(this.cache,{recursive:true});return true}catch{return false}}
}
module.exports={OfflineEngine,extensions};
