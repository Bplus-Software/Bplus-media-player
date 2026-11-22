const path = require('path');
const fs = require('fs');
const {fileURLToPath, pathToFileURL} = require('url');
const supported = new Set(require('./media-engine').extensions.map(ext=>'.'+ext));
function parseMediaArguments(argv, cwd=process.cwd(), platform=process.platform, exists=p=>fs.statSync(p).isFile()) {
 const paths=[]; const pth=platform==='win32'?path.win32:path;
 for(let value of argv.slice(1)) {
  if(typeof value!=='string')continue;
  value=value.trim();
  if(value.startsWith('"')&&value.endsWith('"'))value=value.slice(1,-1);
  if(!value||value.startsWith('-'))continue;
  try {
   if(/^file:/i.test(value)) {
    if(platform===process.platform)value=fileURLToPath(value);
    else {const u=new URL(value);value=decodeURIComponent(u.pathname);if(platform==='win32'){value=value.replace(/^\/([A-Za-z]:)/,'$1').replace(/\//g,'\\');if(u.hostname)value='\\\\'+u.hostname+value;}}
   }
   const absolute=pth.normalize(pth.isAbsolute(value)?value:pth.resolve(cwd,value));
   if(supported.has(pth.extname(absolute).toLowerCase())&&exists(absolute)&&!paths.includes(absolute))paths.push(absolute);
  }catch{}
 }
 return paths;
}
module.exports={parseMediaArguments};
