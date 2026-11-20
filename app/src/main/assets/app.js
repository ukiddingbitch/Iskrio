'use strict';
const $=s=>document.querySelector(s), $$=s=>[...document.querySelectorAll(s)];
const icon=n=>`<svg aria-hidden="true"><use href="#i-${n}"/></svg>`;
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const uid=()=>Date.now().toString(36)+Math.random().toString(36).slice(2,7);
const clone=o=>JSON.parse(JSON.stringify(o));
const state={page:'home',library:{projects:[],favorites:[],templates:[]},project:null,selected:null,tool:'text',history:[],redo:[],frame:0,playing:false,category:'All',query:'',pen:false,loaded:false};
const images=new Map(),imagePixels=new Map(); let timer,saveTimer,drawToken=0,drag=null,playTimer=null,previousFocus=null,libraryTimer;
function toast(s){$('#toast').textContent=s;$('#toast').classList.add('show');clearTimeout(timer);timer=setTimeout(()=>$('#toast').classList.remove('show'),3200);}
function busy(s){$('#busy-text').textContent=s;$('#busy').hidden=false;if($('#cancel-operation'))$('#cancel-operation').hidden=!activeOperation;if($('#busy-internet'))$('#busy-internet').hidden=!['link','link-cancelling'].includes(activeOperation);updateInternetControls();}
function idle(){$('#busy').hidden=true;}
function modal(title,body){previousFocus=document.activeElement;$('#modal-title').textContent=title;$('#modal-body').innerHTML=body;$('#modal').hidden=false;setTimeout(()=>$('#modal-body input, #modal-body button, #modal-body select')?.focus(),30);}
function closeModal(keepInternet=false){const linkDialog=$('#modal-body #import-link-url')||$('#modal-body [data-link-option]');$('#modal').hidden=true;if(!keepInternet&&linkDialog&&internetEnabled)setInternetEnabled(false);previousFocus?.focus();}
window.nativeError=s=>{activeOperation=null;idle();if(!state.loaded){state.loaded=true;render();}toast(s);};window.nativeProgress=n=>busy('Importing media… '+n+'%');window.nativeSaved=n=>{idle();toast('Saved: '+n);};window.nativeCancelled=kind=>{activeOperation=null;idle();toast(kind==='process'?'Processing cancelled.':kind==='export'?'Export cancelled.':kind==='extract'||kind==='link'?'Import cancelled.':'File selection cancelled.');};
window.librarySaved=()=>{if($('#save-status'))$('#save-status').textContent='Saved on this device';};
window.nativeLibrary=data=>{try{state.library=validateLibrary(data);state.loaded=true;render();}catch(e){toast('The library is invalid. Restore a backup.');state.loaded=true;render();}};
function validateLibrary(o){if(!o||!Array.isArray(o.projects)||!Array.isArray(o.favorites)||o.projects.length>100||o.templates&&(!Array.isArray(o.templates)||o.templates.length>100))throw Error('Invalid library');const templates=(o.templates||[]).map(t=>{if(!t||!safeId(t.id)||typeof t.name!=='string'||!/^data:image\/(png|jpeg|webp);base64,/.test(t.src||'')||!Number.isFinite(t.w)||!Number.isFinite(t.h)||t.w<1||t.h<1)throw Error('Invalid template');return t;});return {projects:o.projects.map(validateProject),favorites:o.favorites.filter(x=>typeof x==='string'),templates};}
const safeId=value=>typeof value==='string'&&/^[A-Za-z0-9_-]{1,80}$/.test(value);
const allowedTypes=['text','image','sticker','path','shape','chart'];
function validateProject(p){
 if(p&&p.fps==null)p.fps=30;if(p&&(!Number.isFinite(p.fps)||p.fps<1||p.fps>50))throw Error('Frame rate must be between 1 and 50 FPS.');
 if(!p||!safeId(p.id)||typeof p.name!=='string'||!Number.isFinite(p.w)||!Number.isFinite(p.h)||p.w<(p.frames?.length?1:64)||p.h<(p.frames?.length?1:64)||p.w>4096||p.h>4096||!Array.isArray(p.layers)||p.layers.length>150)throw Error('Invalid project');
 if(typeof p.bg!=='string'||!/^#[a-f0-9]{6}$/i.test(p.bg))throw Error('Invalid background color');
 if(p.video&&!Array.isArray(p.video.clips)){const v=p.video;if(!localVideoSource(v.src)||!Number.isFinite(v.durationMs)||v.durationMs<=0||v.durationMs>600000||!Number.isFinite(v.startMs)||!Number.isFinite(v.endMs)||v.startMs<0||v.endMs<=v.startMs||v.endMs>v.durationMs||!Number.isFinite(v.volume)||v.volume<0||v.volume>1||typeof v.muted!=='boolean'||typeof v.hasAudio!=='boolean'||!Number.isFinite(v.width)||!Number.isFinite(v.height)||v.width<1||v.height<1||v.width>4096||v.height>4096)throw Error('Invalid video project');}
 if(p.frames&&(!Array.isArray(p.frames)||p.frames.length>900||p.frames.some(x=>!isImage(x))))throw Error('Invalid frames');
 for(const l of p.layers){if(!l||!safeId(l.id))throw Error('Invalid layer identifier');for(const key of ['rotation','opacity','size','strokeWidth','lineWidth','brightness','saturate','contrast','grayscale','sepia','invert','blur'])if(l[key]!=null&&(!Number.isFinite(l[key])||Math.abs(l[key])>10000))throw Error('Invalid layer settings');for(const key of ['color','stroke'])if(l[key]!=null&&(typeof l[key]!=='string'||!/^#[a-f0-9]{6}$/i.test(l[key])))throw Error('Invalid layer color');if(!allowedTypes.includes(l.type)||!Number.isFinite(l.x)||!Number.isFinite(l.y)||!Number.isFinite(l.w)||!Number.isFinite(l.h)||l.w<=0||l.h<=0||l.w>10000||l.h>10000)throw Error('Invalid layer');if(l.type==='image'&&!isImage(l.src))throw Error('Invalid image');if(l.type==='path'&&(!Array.isArray(l.points)||l.points.length>30000||l.points.some(p=>!Array.isArray(p)||p.length!==2||!p.every(Number.isFinite))))throw Error('Invalid drawing');if(l.type==='chart'&&(!Array.isArray(l.values)||l.values.length>30||l.values.some(v=>!Number.isFinite(v)||v<0)||!Array.isArray(l.labels)||l.labels.some(x=>typeof x!=='string')))throw Error('Invalid chart');if(l.text!=null&&(typeof l.text!=='string'||l.text.length>2000))throw Error('Invalid text');}
 if(p.frameDurations!=null&&(!Array.isArray(p.frameDurations)||p.frameDurations.length!==p.frames.length||p.frameDurations.some(value=>!Number.isFinite(value)||value<=0||value>655350)||p.frameDurations.reduce((sum,value)=>sum+value,0)>3600000))throw Error('Invalid animation timing');
 if(p.playbackRate!=null&&(!Number.isFinite(p.playbackRate)||p.playbackRate<.25||p.playbackRate>2))throw Error('Invalid animation speed');
 if(p.loopCount!=null&&(!Number.isInteger(p.loopCount)||p.loopCount<0||p.loopCount>65536))throw Error('Invalid animation loop count');
 if(p.transparent!=null&&typeof p.transparent!=='boolean')throw Error('Invalid transparency');
 if(p.frames?.some(src=>src.startsWith('data:image/svg+xml')&&!LEGACY_TEMPLATE_SOURCES.includes(src)))throw Error('External SVG frames are not allowed.');
 p.layers.forEach(l=>{if(l.type==='image'&&l.src.startsWith('data:image/svg+xml')&&!LEGACY_TEMPLATE_SOURCES.includes(l.src))throw Error('External SVG is not allowed. Import a PNG or JPEG instead.');});return p;
}
function isImage(s){return typeof s==='string'&&s.length<30e6&&(/^data:image\/(png|jpeg|webp|svg\+xml)[;,]/.test(s)||localFrameSource(s));}
async function loadLibrary(){if(window.Android){Android.getLibrary();return;}try{const raw=localStorage.getItem('iskrio.library');if(raw)state.library=validateLibrary(JSON.parse(raw));}catch(e){toast('Could not load the library.');}state.loaded=true;render();}
function persistLibrary(){clearTimeout(libraryTimer);const data=JSON.stringify(state.library);if(window.Android)Android.putLibrary(data);else try{localStorage.setItem('iskrio.library',data);window.librarySaved();}catch(e){toast('Storage is full. Export a project backup.');}}
function showPage(page){if(page==='library')page='home';stopPlay();if(state.page==='editor'&&state.project)saveProject();if(PAGER_PAGES.includes(page)&&PAGER_PAGES.includes(state.page)&&$('#page-track')){navigatePager(page,true);return;}swipeStart=null;state.page=page;state.pen=false;document.body.classList.remove('editing');render();window.scrollTo(0,0);}
function templateCards(rows){return rows.map(t=>`<article class="template-card"><button class="template-open" data-template="${t.id}" aria-label="Open ${esc(t.name)}"><img class="template-art" alt="${esc(t.name)}" src="${esc(t.src)}"><h3>${esc(t.name)}</h3></button><button class="template-menu" data-template-menu="${t.id}" aria-label="Template options">⋯</button><button class="fav ${state.library.favorites.includes(t.id)?'active':''}" data-favorite="${t.id}" aria-label="Favorite template" aria-pressed="${state.library.favorites.includes(t.id)}">${icon('heart')}</button></article>`).join('');}
function render(){
 TEMPLATES.splice(0,TEMPLATES.length,...(state.library.templates||[]));document.body.classList.toggle('landing',state.page==='home');
 document.body.classList.toggle('editing',state.page==='editor');document.body.classList.toggle('pager-mode',PAGER_PAGES.includes(state.page));$$('#nav button').forEach(b=>b.classList.toggle('active',b.dataset.page===state.page));
 if(state.page==='editor'){renderEditor();return;}
 if(PAGER_PAGES.includes(state.page)){renderPager();return;}
 if(state.page==='templates'){$('#main').innerHTML=`<div class="section-head"><h1 class="page-title">Templates</h1><button class="secondary" data-action="add-template">${icon('plus')} Add template</button></div>${TEMPLATES.length?`<label class="search">${icon('search')}<input id="search" type="search" value="${esc(state.query)}" placeholder="Search your templates" aria-label="Search templates"></label><div class="chips">${['All','Favorites'].map(c=>`<button class="chip ${state.category===c?'active':''}" data-category="${c}">${c}</button>`).join('')}</div><div class="template-grid" id="template-results"></div>`:`<div class="empty">${icon('grid')}<h3>No templates yet</h3><button class="primary" data-action="add-template">${icon('plus')} Add template</button></div>`}`;if(TEMPLATES.length)filterTemplates();}
}

function filterTemplates(){let q=state.query.toLocaleLowerCase('en-US');let rows=TEMPLATES.filter(t=>(state.category==='All'||state.category===t.category||state.category==='Favorites'&&state.library.favorites.includes(t.id))&&t.name.toLocaleLowerCase('en-US').includes(q));$('#template-results').innerHTML=rows.length?templateCards(rows):'<p class="subhead">No matching templates. Try another search.</p>';}
function baseProject(name='New spark',w=1080,h=1080){return {id:uid(),name,w,h,bg:'#f6f1e8',layers:[],frames:[],fps:30,updated:Date.now()};}
function addImageLayer(src,w,h,framed=false){let p=state.project;const fit=Math.min(p.w/w,p.h/h);return {id:uid(),type:'image',src,x:p.w/2,y:p.h/2,w:w*fit,h:h*fit,rotation:0,opacity:1,framed};}
function textLayer(text,y){let p=state.project;return {id:uid(),type:'text',text,x:p.w/2,y,w:p.w*.9,h:p.h*.16,size:Math.round(p.w*.075),color:'#ffffff',stroke:'#17232a',strokeWidth:Math.round(p.w*.006),font:'sans-serif-condensed',bold:true,uppercase:true,align:'center',rotation:0,opacity:1};}
async function newTemplate(id){const t=TEMPLATES.find(x=>x.id===id);if(!t)throw Error('Template not found.');if(state.project)saveProject();let scale=Math.min(1,1080/Math.max(t.w,t.h));state.project=baseProject(t.name,Math.max(64,Math.round(t.w*scale)),Math.max(64,Math.round(t.h*scale)));state.project.layers.push(addImageLayer(t.src,t.w,t.h));state.selected=null;openEditor();}
function openEditor(){state.page='editor';state.tool='text';state.pen=false;state.history=[];state.redo=[];state.frame=0;render();window.scrollTo(0,0);scheduleSave();}
function selected(){return state.project?.layers.find(l=>l.id===state.selected);}
function checkpoint(){state.history.push(JSON.stringify(state.project));if(state.history.length>25)state.history.shift();state.redo=[];}
function changed(rebuild=false){draw();scheduleSave();updateHistoryButtons();if(rebuild)renderPanel();}
function scheduleSave(){clearTimeout(saveTimer);if($('#save-status'))$('#save-status').textContent='Saving…';saveTimer=setTimeout(saveProject,1000);}
function saveProject(){clearTimeout(saveTimer);if(!state.project||!state.loaded)return;let p=state.project;try{validateProject(p);}catch(e){toast('Project not saved: '+e.message);return;}p.updated=Date.now();try{let c=$('#canvas');if(c){let t=document.createElement('canvas');t.width=240;t.height=Math.max(1,Math.round(240*p.h/p.w));t.getContext('2d').drawImage(c,0,0,t.width,t.height);p.thumb=t.toDataURL('image/jpeg',.65);}}catch(e){}
 let i=state.library.projects.findIndex(x=>x.id===p.id);if(i<0){if(state.library.projects.length>=60){toast('Your library has 60 projects. Back up and remove older ones.');return;}state.library.projects.push(clone(p));}else state.library.projects[i]=clone(p);persistLibrary();}
function renderEditor(){stopPlay();let p=state.project;if(!p){showPage('home');return;}$('#main').innerHTML=`<header class="editor-head"><button class="icon-button" data-page="home" aria-label="Save and go back">${icon('back')}</button><div class="editor-name"><button data-action="rename">${esc(p.name)}</button><small id="save-status">Saved on this device</small></div><button class="icon-button" id="undo" data-action="undo" aria-label="Undo">${icon('undo')}</button><button class="icon-button" id="redo" data-action="redo" aria-label="Redo"><svg style="transform:scaleX(-1)"><use href="#i-undo"/></svg></button><button class="primary" data-action="export">Export ${icon('download')}</button></header><div class="canvas-area"><canvas id="canvas" class="${p.transparent?'transparent':''}" width="${p.w}" height="${p.h}" aria-label="Project preview. Drag to move a layer."></canvas></div><div class="canvas-info"><span>${p.w} × ${p.h} px · ${p.frames?.length?p.frames.length+' frames':'no watermark'}</span></div>${p.frames?.length?`<div class="playback-controls"><button id="preview-play" class="icon-button" data-action="play" aria-label="Play or pause">${icon('play')}</button><input id="frame-scrub" aria-label="Animation position" type="range" min="0" max="${p.frames.length-1}" value="${state.frame}"><output id="frame-time">${frameTime()}</output></div><div id="timeline" class="timeline"></div>`:'' }<div class="tools">${[...(p.video?[['video','film','Track']]:[]),['text','text','Text'],['stickers','smile','Stickers'],['image','image','Image'],['draw','pen','Drawing'],['canvas','grid','Canvas'],['layers','layers','Layers'],...(p.frames?.length?[['animation','play','Animation']]:[])].map(([id,i,n])=>`<button class="tool ${id===state.tool?'active':''}" data-tool="${id}">${icon(i)}${n}</button>`).join('')}</div><section class="tool-panel" id="tool-panel"></section>`;if(p.video)mountVideoPlayer();else disposeVideo();renderPanel();draw();attachCanvas();renderTimeline();updateHistoryButtons();}
function updateHistoryButtons(){if($('#undo'))$('#undo').disabled=!state.history.length;if($('#redo'))$('#redo').disabled=!state.redo.length;}
function range(label,prop,val,min,max,step=1){return `<label>${label}<output data-range-value="${prop}">${Math.round(val*100)/100}</output><input type="range" data-prop="${prop}" value="${val}" min="${min}" max="${max}" step="${step}"></label>`;}
function renderPanel(){let p=state.project,l=selected();if(!$('#tool-panel'))return;$$('.tool').forEach(b=>b.classList.toggle('active',b.dataset.tool===state.tool));let html='';
 if(state.tool==='text'){if(l&&(l.type==='text'||l.type==='sticker'))html=`<div class="row between"><h3>Text</h3><button class="mini" data-action="add-text">+ More text</button></div><div class="controls"><label class="span"><textarea id="layer-text" maxlength="2000" aria-label="Layer text">${esc(l.text)}</textarea></label><label>Font<select data-prop="font">${[['Manrope','Manrope'],['sans-serif-condensed','Bold display'],['serif','Serif'],['monospace','Monospace']].map(([v,n])=>`<option value="${v}" ${l.font===v?'selected':''}>${n}</option>`).join('')}</select></label>${range('Size','size',l.size,12,Math.round(p.w*.3))}<label>Color<input type="color" data-prop="color" value="${l.color||'#ffffff'}"></label><label>Outline<input type="color" data-prop="stroke" value="${l.stroke||'#17232a'}"></label>${range('Outline width','strokeWidth',l.strokeWidth||0,0,20)}<label>Alignment<select data-prop="align">${['left','center','right'].map((x,i)=>`<option value="${x}" ${l.align===x?'selected':''}>${['Left','Center','Right'][i]}</option>`).join('')}</select></label><div class="span inline-buttons"><button class="mini" data-action="bold">${l.bold?'✓ ':''}Bold</button><button class="mini" data-action="italic">${l.italic?'✓ ':''}Italic</button><button class="mini" data-action="uppercase">${l.uppercase?'✓ ':''}UPPERCASE</button><button class="mini" data-action="ideas">Inspiration</button><button class="mini" data-action="duplicate-layer">Duplicate</button></div></div>`;else html=`<h3>Text</h3><button class="primary full" data-action="add-text">${icon('plus')} Add text</button>`;}
 if(state.tool==='stickers')html=`<h3>Stickers</h3><div class="stickers">${['✨','😂','🤔','💜','🔥','👀','🙃','🚀','⭐','💡','☕','🌱','💬','❤️','😎','🤖','🐈','🐕'].map(x=>`<button data-sticker="${x}" aria-label="Add ${x}">${x}</button>`).join('')}</div><div class="space"></div><div class="inline-buttons"><button class="mini" data-shape="rect">Rectangle</button><button class="mini" data-shape="circle">Circle</button><button class="mini" data-shape="arrow">Arrow</button><button class="mini" data-shape="bubble">Bubble</button></div>`;
 if(state.tool==='image'){html=`<div class="inline-buttons"><button class="mini" data-action="add-image">${icon('plus')} Image</button><button class="mini" data-action="replace-image">Replace selected</button></div>`;if(l?.type==='image')html+=`<div class="space"></div><div class="controls">${range('Brightness','brightness',l.brightness??100,20,200)}${range('Saturation','saturate',l.saturate??100,0,200)}${range('Contrast','contrast',l.contrast??100,20,200)}${range('Grayscale','grayscale',l.grayscale??0,0,100)}${range('Sepia','sepia',l.sepia??0,0,100)}${range('Invert','invert',l.invert??0,0,100)}${range('Blur','blur',l.blur??0,0,20)}<div class="span inline-buttons"><button class="mini" data-action="flip">Flip</button><button class="mini" data-action="crop">Crop to canvas</button><button class="mini" data-action="erase-bg">Remove background color</button><button class="mini" data-action="posterize">Posterize</button><button class="mini" data-action="sharpen">Sharpen</button><button class="mini" data-action="jpeg-effect">JPEG effect</button></div></div>`;else html+='';}
 if(state.tool==='draw')html=`<h3>Draw right on the preview</h3><div class="controls"><label>Pen color<input id="pen-color" type="color" value="${state.penColor||'#d8f991'}"></label><label>Width<input id="pen-width" type="range" min="2" max="60" value="${state.penWidth||12}"></label></div><button class="secondary full" data-action="pen-toggle" style="margin-top:12px">${state.pen?'✓ Drawing enabled':'Enable drawing'}</button>`;
 if(state.tool==='canvas')html=`<h3>Canvas</h3><div class="controls"><label>Format<select id="canvas-ratio"><option value="">Choose an aspect ratio…</option><option value="1080,1080">Square 1 : 1</option><option value="1080,1350">Post 4 : 5</option><option value="1080,1920">Story 9 : 16</option><option value="1440,1080">Photo 4 : 3</option><option value="1920,1080">Wide 16 : 9</option></select></label><label>Background<input id="bg-color" type="color" value="${p.bg}"></label><div class="span inline-buttons"><button class="mini" data-action="dimensions">Custom dimensions</button><button class="mini" data-action="top-border">Top caption border</button><button class="mini" data-action="demotivator">Demotivator</button><button class="mini" data-action="chart">Insert chart</button></div></div>`;
 if(state.tool==='layers')html=`<div class="row between"><h3>Layers · ${p.layers.length}</h3><span class="pill">Top = front</span></div><div class="layer-list">${p.layers.slice().reverse().map(x=>`<div class="layer-row ${x.id===state.selected?'selected':''}"><button class="layer-name" data-select="${x.id}">${x.hidden?'◌ ':''}${esc(layerName(x))}</button><button class="mini" data-layer-up="${x.id}" aria-label="Bring forward">↑</button><button class="mini" data-layer-down="${x.id}" aria-label="Send backward">↓</button><button class="mini" data-layer-hide="${x.id}" aria-label="Hide or show layer">${x.hidden?'○':'●'}</button><button class="icon-button danger" data-layer-delete="${x.id}" aria-label="Delete layer">${icon('trash')}</button></div>`).join('')}</div>`;
 if(state.tool==='video'&&p.video)html=videoPanel();
 if(state.tool==='animation')html=`<h3>Animation</h3><div class="controls">${p.frameDurations?`<label>Speed<output id="speed-value">${Math.round((p.playbackRate||1)*100)}%</output><input id="animation-speed" type="range" min="25" max="200" step="5" value="${Math.round((p.playbackRate||1)*100)}"></label>`:`<label>Speed · frames/s<output id="fps-value">${p.fps} FPS</output><input id="fps" type="range" min="1" max="50" value="${p.fps}"></label>`}<label>Frame count<input readonly value="${p.frames.length}"></label></div><div class="inline-buttons"><button class="mini" data-action="play">${state.playing?'Pause':'Play'}</button><button class="mini" data-action="reverse">Reverse</button><button class="mini" data-action="frame-left">← Frame</button><button class="mini" data-action="frame-right">Frame →</button><button class="mini" data-action="delete-frame">Delete frame</button><button class="mini" data-action="append-frames">+ Photos</button></div>`;
 if(l&&state.tool!=='layers'&&state.tool!=='draw')html+=`<div class="space"></div><details><summary style="font-size:11px;color:var(--muted);cursor:pointer">Size & rotation</summary><div class="space"></div><div class="controls">${range('X','x',l.x,-p.w,p.w*2)}${range('Y','y',l.y,-p.h,p.h*2)}${range('Width','w',l.w,10,p.w*2)}${range('Height','h',l.h,10,p.h*2)}${range('Rotation','rotation',l.rotation||0,-180,180)}${range('Opacity','opacity',l.opacity??1,0,1,.01)}${l.type==='shape'?`<label>Color<input type="color" data-prop="color" value="${l.color}"></label>`:''}</div><div class="inline-buttons"><button class="mini" data-action="center-layer">Center</button><button class="mini" data-action="duplicate-layer">Duplicate</button><button class="mini danger" data-action="delete-layer">Delete</button></div></details>`;
 $('#tool-panel').innerHTML=html;
}
function layerName(l){return ({text:'T · ',sticker:'Sticker · ',image:'Image',path:'Drawing',shape:'Shape · ',chart:'Chart'})[l.type]+(l.text?.slice(0,24)||l.shape||'');}
function loadImage(src){if(images.has(src))return images.get(src);let promise=new Promise((resolve,reject)=>{let i=new Image();i.onload=()=>{if(images.has(src))imagePixels.set(src,i.naturalWidth*i.naturalHeight);let pixels=[...imagePixels.values()].reduce((sum,n)=>sum+n,0);while(images.size>24||pixels>12000000&&images.size>1){const oldest=images.keys().next().value;if(oldest===src&&images.size===1)break;pixels-=imagePixels.get(oldest)||0;images.delete(oldest);imagePixels.delete(oldest);}resolve(i);};i.onerror=()=>{images.delete(src);imagePixels.delete(src);reject(Error('Could not display the image.'));};i.src=src;});images.set(src,promise);if(images.size>24){const oldest=images.keys().next().value;images.delete(oldest);imagePixels.delete(oldest);}return promise;}
function wrapText(ctx,text,width){let lines=[];for(const para of text.split('\n')){let line='';for(const word of para.split(' ')){if(ctx.measureText(word).width>width){if(line){lines.push(line);line='';}let part='';for(const c of word){if(part&&ctx.measureText(part+c).width>width){lines.push(part);part='';}part+=c;}line=part;continue;}let next=line?line+' '+word:word;if(line&&ctx.measureText(next).width>width){lines.push(line);line=word;}else line=next;}lines.push(line);}return lines;}
async function paint(ctx,p,frame=0,selection=false,overlayOnly=false){
 ctx.clearRect(0,0,p.w,p.h);if(!p.transparent){ctx.fillStyle=p.bg;ctx.fillRect(0,0,p.w,p.h);}
 if(p.video){const rect=videoRect(p);if(overlayOnly)ctx.clearRect(rect.x,rect.y,rect.width,rect.height);else if(videoElement&&videoSource===p.video.src&&videoElement.readyState>=2)ctx.drawImage(videoElement,rect.x,rect.y,rect.width,rect.height);}
 for(const l of p.layers){if(l.hidden)continue;ctx.save();ctx.translate(l.x,l.y);ctx.rotate((l.rotation||0)*Math.PI/180);ctx.globalAlpha=l.opacity??1;if(l.blendMode)ctx.globalCompositeOperation=l.blendMode;
 if(l.type==='image'){let src=l.framed&&p.frames?.length?p.frames[frame%p.frames.length]:l.src;try{let im=await loadImage(src);ctx.filter=`brightness(${l.brightness??100}%) saturate(${l.saturate??100}%) contrast(${l.contrast??100}%) grayscale(${l.grayscale??0}%) sepia(${l.sepia??0}%) invert(${l.invert??0}%) blur(${l.blur??0}px)`;if(l.flip)ctx.scale(-1,1);ctx.drawImage(im,-l.w/2,-l.h/2,l.w,l.h);}catch(e){if(p.frames?.length)throw e;}ctx.filter='none';}
 if(l.type==='text'||l.type==='sticker'){let font=l.type==='sticker'?'sans-serif':l.font==='sans-serif-condensed'?'"Arial Black", Roboto, sans-serif':l.font||'Manrope';ctx.font=`${l.italic?'italic ':''}${l.bold?800:500} ${l.size}px ${font}`;ctx.fillStyle=l.color||'#ffffff';ctx.strokeStyle=l.stroke||'#17232a';ctx.lineWidth=l.strokeWidth||0;ctx.lineJoin='round';ctx.textAlign=l.align||'center';ctx.textBaseline='middle';if('letterSpacing' in ctx)ctx.letterSpacing=(l.letterSpacing||0)+'px';let lines=wrapText(ctx,l.uppercase?(l.text||'').toLocaleUpperCase('en-US'):l.text||'',l.w);if('letterSpacing' in ctx)ctx.letterSpacing=(l.letterSpacing||0)+'px';let lineH=l.size*(l.lineHeight||1.19);const pad=l.textPadding||0;if(l.textBackground&&l.textBackground!=='none'){ctx.save();ctx.fillStyle=l.textBackground;ctx.beginPath();ctx.roundRect(-l.w/2-pad,-lines.length*lineH/2-pad,l.w+2*pad,lines.length*lineH+2*pad,l.textRadius||0);ctx.fill();ctx.restore();}let x=l.align==='left'?-l.w/2:l.align==='right'?l.w/2:0;for(let n=0;n<lines.length;n++){let y=(n-(lines.length-1)/2)*lineH;if(l.strokeWidth)ctx.strokeText(lines[n],x,y);ctx.fillText(lines[n],x,y);}l.h=Math.max(l.size*1.2,lines.length*lineH)+2*pad;}
 if(l.type==='path'){ctx.strokeStyle=l.color;ctx.lineWidth=l.lineWidth;ctx.lineCap='round';ctx.lineJoin='round';ctx.beginPath();l.points.forEach((xy,i)=>i?ctx.lineTo(xy[0],xy[1]):ctx.moveTo(xy[0],xy[1]));if(l.points.length===1){ctx.lineTo(l.points[0][0]+.1,l.points[0][1]);}ctx.stroke();}
 if(l.type==='shape'){ctx.fillStyle=l.color;ctx.beginPath();if(l.shape==='circle')ctx.ellipse(0,0,l.w/2,l.h/2,0,0,Math.PI*2);else if(l.shape==='arrow'){ctx.moveTo(-l.w/2,-l.h*.15);ctx.lineTo(l.w*.08,-l.h*.15);ctx.lineTo(l.w*.08,-l.h/2);ctx.lineTo(l.w/2,0);ctx.lineTo(l.w*.08,l.h/2);ctx.lineTo(l.w*.08,l.h*.15);ctx.lineTo(-l.w/2,l.h*.15);ctx.closePath();}else if(l.shape==='bubble'){ctx.roundRect(-l.w/2,-l.h/2,l.w,l.h*.8,Math.min(l.w,l.h)*.15);ctx.moveTo(-l.w*.25,l.h*.2);ctx.lineTo(-l.w*.35,l.h/2);ctx.lineTo(0,l.h*.2);}else ctx.rect(-l.w/2,-l.h/2,l.w,l.h);ctx.fill();}
 if(l.type==='chart')drawChart(ctx,l);
 if(selection&&l.id===state.selected&&!state.pen){ctx.globalAlpha=1;ctx.strokeStyle='#d8f991';ctx.lineWidth=Math.max(2,p.w/350);ctx.setLineDash([p.w/150,p.w/150]);ctx.strokeRect(-l.w/2-5,-l.h/2-5,l.w+10,l.h+10);ctx.setLineDash([]);}
 ctx.restore();
 }
}
function drawChart(ctx,l){let vals=l.values,max=Math.max(1,...vals),colors=['#bca5ef','#d8f991','#efa97e','#8dbdc4','#e0a7b9'];ctx.fillStyle='#f6f1e8';ctx.fillRect(-l.w/2,-l.h/2,l.w,l.h);if(l.chartType==='pie'){let total=vals.reduce((a,b)=>a+b,0)||1,start=-Math.PI/2;vals.forEach((v,i)=>{ctx.fillStyle=colors[i%colors.length];ctx.beginPath();ctx.moveTo(0,-l.h*.05);ctx.arc(0,-l.h*.05,Math.min(l.w,l.h)*.32,start,start+v/total*Math.PI*2);ctx.closePath();ctx.fill();start+=v/total*Math.PI*2;});}else{let bar=l.w*.76/vals.length;vals.forEach((v,i)=>{let h=v/max*l.h*.64;ctx.fillStyle=colors[i%colors.length];ctx.fillRect(-l.w*.4+i*bar,-l.h*.35+l.h*.64-h,bar*.7,h);ctx.fillStyle='#26353d';ctx.font=`600 ${Math.max(12,l.w*.026)}px Manrope`;ctx.textAlign='center';ctx.fillText((l.labels[i]||'').slice(0,14),-l.w*.4+(i+.35)*bar,l.h*.39);});}if(l.chartType==='pie'){ctx.font=`600 ${Math.max(12,l.w*.024)}px Manrope`;ctx.textAlign='center';ctx.fillStyle='#26353d';ctx.fillText(l.labels.map((s,i)=>`${s}: ${vals[i]}`).join(' · ').slice(0,85),0,l.h*.4);}}

function attachCanvas(){const c=$('#canvas');function pos(e){let r=c.getBoundingClientRect();return [(e.clientX-r.left)*c.width/r.width,(e.clientY-r.top)*c.height/r.height];}
 c.addEventListener('pointerdown',e=>{stopPlay();let [x,y]=pos(e),p=state.project;c.setPointerCapture(e.pointerId);let active=selected();if(active&&!active.locked&&!state.pen){let angle=(active.rotation||0)*Math.PI/180,hx=active.x+(active.w/2-8)*Math.cos(angle)-(active.h/2-8)*Math.sin(angle),hy=active.y+(active.w/2-8)*Math.sin(angle)+(active.h/2-8)*Math.cos(angle);if(Math.hypot(x-hx,y-hy)<p.w/25){checkpoint();drag={resize:true,cx:active.x,cy:active.y,distance:Math.max(1,Math.hypot(x-active.x,y-active.y)),w:active.w,h:active.h,size:active.size};return;}}if(state.pen){checkpoint();let l={id:uid(),type:'path',x:0,y:0,w:p.w,h:p.h,color:state.penColor||'#d8f991',lineWidth:state.penWidth||12,points:[[x,y]],opacity:1,rotation:0};p.layers.push(l);state.selected=l.id;drag={pen:true};draw();return;}let l=p.layers.slice().reverse().find(l=>{if(l.hidden)return false;let a=-(l.rotation||0)*Math.PI/180,dx=x-l.x,dy=y-l.y;return Math.abs(dx*Math.cos(a)-dy*Math.sin(a))<=l.w/2+10&&Math.abs(dx*Math.sin(a)+dy*Math.cos(a))<=l.h/2+10;});state.selected=l?.id||null;if(l){state.tool=toolForLayer(l);state.pen=false;if(!l.locked){checkpoint();drag={x,y,lx:l.x,ly:l.y};}}renderPanel();draw();});
 c.addEventListener('pointermove',e=>{if(!drag)return;let [x,y]=pos(e),l=selected();if(!l)return;if(drag.resize){let scale=Math.max(.05,Math.min(8,Math.hypot(x-drag.cx,y-drag.cy)/drag.distance));l.w=Math.max(10,drag.w*scale);l.h=Math.max(10,drag.h*scale);if(drag.size)l.size=Math.max(8,drag.size*scale);}else if(drag.pen){if(l.points.length<30000)l.points.push([x,y]);}else{l.x=Math.round(drag.lx+x-drag.x);l.y=Math.round(drag.ly+y-drag.y);}draw();});
 const end=()=>{if(drag){drag=null;changed(true);}};c.addEventListener('pointerup',end);c.addEventListener('pointercancel',end);
}



function addText(text='Your text'){checkpoint();let l=textLayer(text,state.project.h/2);l.font='Manrope';l.uppercase=false;state.project.layers.push(l);state.selected=l.id;state.tool='text';state.pen=false;changed(true);$('#layer-text')?.focus();$('#layer-text')?.select();}
function setTool(t){state.tool=t;state.pen=t==='draw';renderPanel();draw();}
function applyRatio(w,h){checkpoint();let p=state.project,sx=w/p.w,sy=h/p.h;p.layers.forEach(l=>{l.x*=sx;l.y*=sy;l.w*=sx;l.h*=sy;if(l.size)l.size*=Math.min(sx,sy);if(l.type==='path'){l.points=l.points.map(([x,y])=>[x*sx,y*sy]);l.lineWidth*=Math.min(sx,sy);}});p.w=w;p.h=h;renderEditor();scheduleSave();}
function fileMenu(){modal('New image',`<button class="menu-button" data-action="pick-main-image">${icon('image')}<span>From your own photo<small>An image from your device</small></span>${icon('arrow')}</button><button class="menu-button" data-action="blank">${icon('plus')}<span>Blank canvas<small>Your colors, text and shapes</small></span>${icon('arrow')}</button><button class="menu-button" data-action="browse-templates">${icon('grid')}<span>Original templates<small>Ready without a connection</small></span>${icon('arrow')}</button>`);}
function pick(kind){closeModal();if(window.Android){Android.pick(kind);return;}let input=$('#file-input');input.value='';input.accept=kind==='backup'?'.json,.iskrio':kind==='animation'?'image/gif,video/*':kind==='video'?'video/*':'image/*';input.multiple=['frames','collage'].includes(kind);input.onchange=async()=>{let files=[];try{busy('Loading images…');for(const f of [...input.files].slice(0,60)){if(f.size>40e6)throw Error('The file exceeds 40 MB.');if(kind==='backup'){files.push({text:await f.text()});continue;}if(f.type==='image/gif'||kind==='video'||kind==='animation')throw Error('Video and animated GIF import are available in the Android app.');const src=await new Promise((r,j)=>{let fr=new FileReader();fr.onload=()=>r(fr.result);fr.onerror=j;fr.readAsDataURL(f);});let im=await loadImage(src),scale=Math.min(1,2048/Math.max(im.width,im.height)),c=document.createElement('canvas');c.width=im.width*scale;c.height=im.height*scale;c.getContext('2d').drawImage(im,0,0,c.width,c.height);files.push({data:c.toDataURL('image/png'),mime:'image/png'});}await receiveFiles(kind,files);idle();}catch(e){idle();toast(e.message||'Import failed.');}};input.click();}
window.nativePicked=async o=>{try{await receiveFiles(o.kind,o.files);if(activeOperation!=='extract'){activeOperation=null;idle();}}catch(e){idle();toast(e.message||'Import failed.');}};
let collageLayout='grid',appendFrames=false;
async function receiveFiles(kind,files){if(!files.length)return;
 if(kind==='template'){if(state.library.templates.length>=100)throw Error('Your template library is full. Remove a template first.');if(!files[0].data)throw Error('Choose a PNG, JPEG or WebP image.');let im=await loadImage(files[0].data);const template={id:uid(),name:'My template',src:files[0].data,w:im.width,h:im.height};state.library.templates.push(template);persistLibrary();showPage('templates');renameTemplate(template.id);return;}
 if(kind==='backup'){let data=JSON.parse(files[0].text);let lib=data.format==='iskrio-backup'?validateLibrary(data.library):{projects:[validateProject(data.project)],favorites:[],templates:[]};if(data.format!=='iskrio-backup'&&data.format!=='iskrio-project')throw Error('This is not an Iskrio backup.');if(state.library.projects.length+lib.projects.length>60)throw Error('Import would exceed the 60-project limit.');if(state.library.templates.length+(lib.templates?.length||0)>100)throw Error('Import would exceed the 100-template limit.');for(let p of lib.projects){p.id=uid();p.updated=Date.now();state.library.projects.push(p);}const templateIds=new Map();for(const t of lib.templates||[]){let old=t.id;t.id=uid();templateIds.set(old,t.id);state.library.templates.push(t);}state.library.favorites=[...new Set([...state.library.favorites,...lib.favorites.map(id=>templateIds.get(id)||id)])];persistLibrary();showPage('library');toast('Backup restored.');return;}
 if(kind==='animation'||kind==='video'||kind==='gif'||files[0].mime==='image/gif'||files[0].mime?.startsWith('video/')){
  if(!window.Android?.importAnimation)throw Error('Automatic animation import requires the updated Android app.');
  closeModal();activeOperation='extract';busy('Loading animation…');
  Android.importAnimation('auto',files[0].uri);return;
 }
 const srcs=files.map(f=>f.data).filter(Boolean);if(!srcs.length)throw Error('No supported image found.');
 if(kind==='frames'){if(appendFrames&&state.project?.frames?.length){if(state.project.frames.length+srcs.length>900)throw Error('Up to 900 frames.');checkpoint();state.project.frames.push(...srcs);if(state.project.frameDurations)state.project.frameDurations.push(...srcs.map(()=>1000/state.project.fps));appendFrames=false;renderEditor();scheduleSave();return;}await newAnimation(srcs,30);return;}
 if(kind==='collage'){await createCollage(srcs);return;}
 let im=await loadImage(srcs[0]);if(kind==='main'){if(state.project)saveProject();let ratio=Math.min(1,1080/Math.max(im.width,im.height));state.project=baseProject('My spark',Math.max(64,Math.round(im.width*ratio)),Math.max(64,Math.round(im.height*ratio)));state.project.layers.push(addImageLayer(srcs[0],im.width,im.height));state.selected=state.project.layers[0].id;openEditor();state.tool='image';renderPanel();return;}
 checkpoint();if(kind==='replace'&&selected()?.type==='image'){selected().src=srcs[0];selected().framed=false;}else{let l=addImageLayer(srcs[0],im.width,im.height);l.w*=.65;l.h*=.65;state.project.layers.push(l);state.selected=l.id;}state.tool='image';changed(true);
}


async function createCollage(srcs){if(state.project)saveProject();let count=Math.min(srcs.length,9),cols=collageLayout==='vertical'?1:collageLayout==='horizontal'?count:Math.ceil(Math.sqrt(count)),rows=Math.ceil(count/cols);state.project=baseProject('Better together',1080,collageLayout==='vertical'?Math.min(1920,count*540):1080);let p=state.project,cellW=p.w/cols,cellH=p.h/rows;for(let i=0;i<count;i++){let im=await loadImage(srcs[i]),cw=cellW-16,ch=cellH-16,c=document.createElement('canvas');c.width=cw;c.height=ch;let sc=Math.max(cw/im.width,ch/im.height);c.getContext('2d').drawImage(im,(cw-im.width*sc)/2,(ch-im.height*sc)/2,im.width*sc,im.height*sc);p.layers.push({id:uid(),type:'image',src:c.toDataURL('image/png'),x:(i%cols+.5)*cellW,y:(Math.floor(i/cols)+.5)*cellH,w:cw,h:ch,rotation:0,opacity:1});}state.selected=null;openEditor();state.tool='layers';renderPanel();}
async function saveFile(name,mime,bytes){let b64;if(typeof bytes==='string')b64=btoa(unescape(encodeURIComponent(bytes)));else{let s='';for(let i=0;i<bytes.length;i+=8192)s+=String.fromCharCode(...bytes.subarray(i,i+8192));b64=btoa(s);}if(window.Android){Android.save(name,mime,b64);idle();}else{let blob=new Blob([typeof bytes==='string'?bytes:bytes],{type:mime}),url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),30000);idle();toast('Your file is ready to save.');}}

function defaultExportSize(p=state.project){return Math.max(p.w,p.h);}
function gifFingerprint(p){return JSON.stringify([p.w,p.h,p.bg,p.transparent,p.layers,p.frames,p.frameDurations,p.playbackRate,p.loopCount]);}
function exportMenu(){modal('Export',`<div class="space"></div><label>Format<select id="export-format">${state.project.video?'<option value="mp4">MP4 · video with audio</option>':''}<option value="png">PNG · best quality</option><option value="jpeg">JPEG · smaller file</option><option value="gif">GIF · silent animation</option><option value="project">Iskrio · editable project</option></select></label><button class="primary full" data-action="do-export">${icon('download')} Save to file</button>`);if(state.project.frames?.length)$('#export-format').value='gif';}
async function cropSelected(){let l=selected(),p=state.project;if(l?.type!=='image')return;checkpoint();let im=await loadImage(l.src),ratio=p.w/p.h,w=im.width,h=im.height;if(w/h>ratio)w=h*ratio;else h=w/ratio;let c=document.createElement('canvas');c.width=Math.min(2048,w);c.height=c.width/ratio;c.getContext('2d').drawImage(im,(im.width-w)/2,(im.height-h)/2,w,h,0,0,c.width,c.height);l.src=c.toDataURL('image/png');l.framed=false;l.w=p.w;l.h=p.h;l.x=p.w/2;l.y=p.h/2;changed(true);}
async function imageEffect(effect){let l=selected();if(l?.type!=='image')return;busy('Processing image…');await new Promise(r=>setTimeout(r,40));try{checkpoint();let im=await loadImage(l.src),c=document.createElement('canvas');c.width=im.width;c.height=im.height;let ctx=c.getContext('2d');ctx.drawImage(im,0,0);if(effect==='jpeg')l.src=c.toDataURL('image/jpeg',.06);else{let d=ctx.getImageData(0,0,c.width,c.height),a=d.data;if(effect==='posterize'){for(let i=0;i<a.length;i+=4)for(let k=0;k<3;k++)a[i+k]=Math.round(a[i+k]/64)*64;}else{let src=new Uint8ClampedArray(a),w=c.width,h=c.height;for(let y=1;y<h-1;y++)for(let x=1;x<w-1;x++){let i=(y*w+x)*4;for(let k=0;k<3;k++)a[i+k]=5*src[i+k]-src[i-4+k]-src[i+4+k]-src[i-w*4+k]-src[i+w*4+k];}}ctx.putImageData(d,0,0);l.src=c.toDataURL('image/png');}l.framed=false;changed();idle();toast('Effect applied. Undo restores the original.');}catch(e){idle();toast('Could not apply the effect.');}}
async function eraseBackground(threshold){let l=selected();if(l?.type!=='image')return;busy('Removing matching colors…');await new Promise(r=>setTimeout(r,50));try{checkpoint();let im=await loadImage(l.src),c=document.createElement('canvas');c.width=im.width;c.height=im.height;let ctx=c.getContext('2d');ctx.drawImage(im,0,0);let d=ctx.getImageData(0,0,c.width,c.height),a=d.data,r=a[0],g=a[1],b=a[2];for(let i=0;i<a.length;i+=4){let dist=Math.sqrt((a[i]-r)**2+(a[i+1]-g)**2+(a[i+2]-b)**2);if(dist<threshold)a[i+3]=0;else if(dist<threshold+20)a[i+3]*=(dist-threshold)/20;}ctx.putImageData(d,0,0);l.src=c.toDataURL('image/png');l.framed=false;changed();idle();toast('Color removed. Undo restores the image.');}catch(e){idle();toast('Could not process the image.');}}
function projectMenu(id){let p=state.library.projects.find(p=>p.id===id);modal(p.name,`<button class="menu-button" data-duplicate-project="${id}">${icon('plus')}<span>Create a copy</span></button><button class="menu-button danger" data-delete-project="${id}">${icon('trash')}<span>Delete project</span></button><p>You will confirm deletion in the next step.</p>`);}
const actions={
 'cancel-operation':()=>{if(activeOperation==='video-backup'||activeOperation==='archive-import'){Android.cancelArchive();return;}if(activeOperation==='video-export'){Android.cancelVideoExport();return;}if(activeOperation==='link'||activeOperation==='link-cancelling'){activeOperation='link-cancelling';busy('Cancelling…');Android.cancelLinkImport();return;}if(activeOperation==='extract')Android.cancelExtraction();else cancelExport=true;},
 'toggle-internet':()=>setInternetEnabled(!internetEnabled),'link-meme':()=>importLinkDialog('main'),'link-gif':()=>importLinkDialog('gif'),'resolve-link':beginLinkImport,'paste-link':async()=>{if(window.Android)Android.pasteLink();else try{nativeClipboard(await navigator.clipboard.readText());}catch(e){toast('Paste the link into the field.');}},
 'add-template':()=>pick('template'),
 'new-image':()=>showPage('meme-start'),'pick-main-image':()=>pick('main'),'browse-templates':()=>{closeModal();showPage('templates');},'close-modal':closeModal,
 blank:()=>{closeModal();if(state.project)saveProject();state.project=baseProject();state.selected=null;openEditor();state.tool='canvas';renderPanel();},
 'gif-menu':()=>modal('New GIF',`<button class="menu-button" data-action="pick-frames">${icon('image')}<span>From multiple photos<small>Up to 60 images at once, 900 frames total</small></span>${icon('arrow')}</button><button class="menu-button" data-action="pick-video">${icon('play')}<span>From a video on your device<small>Choose a segment and make a GIF</small></span>${icon('arrow')}</button><button class="menu-button" data-action="pick-gif">${icon('layers')}<span>Edit an existing GIF<small>Text, stickers, speed and layers</small></span>${icon('arrow')}</button>`),
 'pick-frames':()=>{appendFrames=false;pick('frames');},'pick-video':()=>pick('animation'),'pick-gif':()=>pick('animation'),'pick-animation':()=>pick('animation'),
 'collage-menu':()=>modal('Collage',`<p>Up to 9 photos.</p><div class="space"></div><label>Layout<select id="collage-layout"><option value="grid">Grid</option><option value="vertical">Vertical comic</option><option value="horizontal">Horizontal pair / strip</option></select></label><button class="primary full" data-action="pick-collage">${icon('image')} Choose images</button>`),
 'pick-collage':()=>{collageLayout=$('#collage-layout').value;pick('collage');},
 'add-text':()=>addText(),'add-image':()=>pick('image'),'replace-image':()=>{if(selected()?.type!=='image'){toast('Select an image layer first.');return;}pick('replace');},
 undo:()=>{if(!state.history.length)return;state.redo.push(JSON.stringify(state.project));state.project=JSON.parse(state.history.pop());if(!selected())state.selected=null;renderEditor();scheduleSave();},
 redo:()=>{if(!state.redo.length)return;state.history.push(JSON.stringify(state.project));state.project=JSON.parse(state.redo.pop());renderEditor();scheduleSave();},
 rename:()=>modal('Rename project',`<label>Project name<input id="project-name" maxlength="60" value="${esc(state.project.name)}"></label><button class="primary full" data-action="do-rename">Save name</button>`),
 'do-rename':()=>{let n=$('#project-name').value.trim();if(!n)return;checkpoint();state.project.name=n;closeModal();renderEditor();scheduleSave();},
 italic:()=>{let l=selected();if(l){checkpoint();l.italic=!l.italic;changed(true);}},
 bold:()=>{let l=selected();if(l){checkpoint();l.bold=!l.bold;changed(true);}},uppercase:()=>{let l=selected();if(l){checkpoint();l.uppercase=!l.uppercase;changed(true);}},
 'duplicate-layer':()=>{let l=selected();if(l){checkpoint();let n=clone(l);n.id=uid();n.x+=30;n.y+=30;state.project.layers.push(n);state.selected=n.id;changed(true);}},
 'delete-layer':()=>{if(!selected())return;checkpoint();state.project.layers=state.project.layers.filter(l=>l.id!==state.selected);state.selected=null;changed(true);},
 'center-layer':()=>{let l=selected();if(l){checkpoint();l.x=state.project.w/2;l.y=state.project.h/2;changed(true);}},
 posterize:()=>imageEffect('posterize'),sharpen:()=>imageEffect('sharpen'),'jpeg-effect':()=>imageEffect('jpeg'),
 deselect:()=>{state.selected=null;draw();renderPanel();},flip:()=>{let l=selected();if(l){checkpoint();l.flip=!l.flip;changed();}},crop:cropSelected,
 'erase-bg':()=>{if(selected()?.type!=='image')return;modal('Remove background color',`<p>Local color keying removes pixels similar to the top-left corner of the image. It works best on solid backgrounds. This is a simple tool, not AI segmentation.</p><div class="space"></div><label>Color tolerance<input id="erase-threshold" type="range" min="5" max="180" value="45"></label><button class="primary full" data-action="do-erase">Remove matching color</button>`);},
 'do-erase':()=>{let v=+$('#erase-threshold').value;closeModal();eraseBackground(v);},
 'pen-toggle':()=>{state.pen=!state.pen;renderPanel();draw();},
 dimensions:()=>modal('Custom dimensions',`<div class="controls"><label>Width (px)<input id="dim-w" type="number" min="64" max="2048" value="${state.project.w}"></label><label>Height (px)<input id="dim-h" type="number" min="64" max="2048" value="${state.project.h}"></label></div><button class="primary full" data-action="do-dimensions">Resize</button>`),
 'do-dimensions':()=>{let w=+$('#dim-w').value,h=+$('#dim-h').value;if(!Number.isInteger(w)||!Number.isInteger(h)||w<64||h<64||w>2048||h>2048){toast('Dimensions must be between 64 and 2048 px.');return;}closeModal();applyRatio(w,h);},
 'top-border':()=>{checkpoint();let p=state.project,extra=Math.round(p.h*.23);if(p.h+extra>4096){toast('The canvas is too tall.');return;}p.layers.forEach(l=>l.y+=extra);p.h+=extra;p.bg='#ffffff';let l=textLayer('When your punchline needs its own space.',extra/2);l.size=Math.round(p.w*.05);l.color='#17232a';l.strokeWidth=0;l.uppercase=false;l.font='Manrope';p.layers.push(l);state.selected=l.id;state.tool='text';renderEditor();scheduleSave();},
 demotivator:()=>{checkpoint();let p=state.project;p.layers.forEach(l=>{l.x=p.w/2+(l.x-p.w/2)*.82;l.y=l.y*.75;l.w*=.82;l.h*=.75;if(l.size)l.size*=.75;});p.bg='#111111';let l=textLayer('ONE SMALL STEP',p.h*.84);l.font='serif';l.strokeWidth=0;l.size=Math.round(p.w*.06);p.layers.push(l);let sub=textLayer('Rest is part of the journey.',p.h*.94);sub.font='serif';sub.strokeWidth=0;sub.uppercase=false;sub.size=Math.round(p.w*.028);p.layers.push(sub);state.selected=l.id;state.tool='text';changed(true);},
 chart:()=>modal('Chart',`<p>Create a bar or pie chart that stays editable as a project layer.</p><div class="space"></div><div class="controls"><label class="span">Comma-separated values<input id="chart-values" value="20, 50, 30"></label><label class="span">Comma-separated labels<input id="chart-labels" value="Plan, Reality, Coffee"></label><label>Type<select id="chart-type"><option value="bar">Bar</option><option value="pie">Pie</option></select></label></div><button class="primary full" data-action="do-chart">Insert chart</button>`),
 'do-chart':()=>{let vals=$('#chart-values').value.split(',').map(Number),labels=$('#chart-labels').value.split(',').map(s=>s.trim()),type=$('#chart-type').value;if(!vals.length||vals.length>12||vals.some(v=>!Number.isFinite(v)||v<0)||!vals.some(v=>v>0)){toast('Enter 1 to 12 nonnegative numbers, including at least one positive number.');return;}checkpoint();let p=state.project,l={id:uid(),type:'chart',values:vals,labels,chartType:type,x:p.w/2,y:p.h/2,w:p.w*.85,h:p.h*.65,rotation:0,opacity:1};p.layers.push(l);state.selected=l.id;state.tool='layers';closeModal();changed(true);},
 ideas:()=>{const ideas=['When the plan meets reality.','One more coffee and we can begin.','Me before the deadline. Me after the deadline.','The system is running. The user is resting.','A small step for the world. A giant leap for Monday.','My brain is in power-saving mode today.'];modal('Caption ideas',`<p>Ready-made offline captions. Choose one and make it yours.</p><div class="space"></div>${ideas.map(s=>`<button class="menu-button" data-idea="${esc(s)}"><span>${s}</span>${icon('plus')}</button>`).join('')}`);},
 export:exportMenu,'do-export':()=>doExport($('#export-format').value,defaultExportSize()),

 play:togglePlay,reverse:()=>{checkpoint();state.project.frames.reverse();state.project.frameDurations?.reverse();state.frame=0;renderTimeline();changed();},
 'frame-left':()=>moveFrame(-1),'frame-right':()=>moveFrame(1),'delete-frame':()=>{let p=state.project;if(p.frames.length<2){toast('An animation needs at least one frame.');return;}checkpoint();p.frames.splice(state.frame,1);p.frameDurations?.splice(state.frame,1);state.frame=Math.min(state.frame,p.frames.length-1);renderTimeline();changed(true);},
 'append-frames':()=>{appendFrames=true;pick('frames');},
 'backup-menu':()=>modal('Backups',`<button class="menu-button" data-action="backup-all">${icon('download')}<span>Back up the entire library<small>Projects, frames and favorite templates</small></span></button><button class="menu-button" data-action="restore">${icon('folder')}<span>Import a backup or project<small>.iskrio and .json files</small></span></button><p>Restored projects are added as copies. Existing projects are preserved.</p>`),
 'backup-all':backupLibrary,restore:()=>pick('backup'),
 settings:()=>modal('Settings',`<div class="note-card"><strong>Iskrio 2.5.0</strong></div><div class="space"></div>${internetControl()}<div class="space"></div><button class="secondary full" data-action="backup-menu">${icon('folder')} Backups</button><div class="space"></div><button class="secondary full" data-action="s-licenses">Open-source licenses</button>`)
};
function moveFrame(dir){let i=state.frame,j=i+dir,frames=state.project.frames;if(j<0||j>=frames.length)return;checkpoint();[frames[i],frames[j]]=[frames[j],frames[i]];if(state.project.frameDurations){const times=state.project.frameDurations;[times[i],times[j]]=[times[j],times[i]];}state.frame=j;renderTimeline();changed();}
document.addEventListener('click',async e=>{let b=e.target.closest('button, [data-action]');if(!b)return;if(Date.now()<suppressClicksUntil){e.preventDefault();return;}try{
 if(b.dataset.linkOption){downloadLinkOption(+b.dataset.linkOption);return;}
 if(b.dataset.templateMenu){templateMenu(b.dataset.templateMenu);return;}
 if(b.dataset.renameTemplate){renameTemplate(b.dataset.renameTemplate);return;}
 if(b.dataset.saveTemplateName){let t=state.library.templates.find(t=>t.id===b.dataset.saveTemplateName);if(t){t.name=$('#template-name').value.trim()||'My template';persistLibrary();closeModal();render();}return;}
 if(b.dataset.removeTemplate){let id=b.dataset.removeTemplate;state.library.templates=state.library.templates.filter(t=>t.id!==id);state.library.favorites=state.library.favorites.filter(x=>x!==id);persistLibrary();closeModal();render();return;}
 if(b.dataset.page){closeModal();showPage(b.dataset.page);return;}
 if(b.dataset.action){await actions[b.dataset.action]?.();return;}
 if(b.dataset.template){await newTemplate(b.dataset.template);return;}
 if(b.dataset.favorite){let id=b.dataset.favorite,f=state.library.favorites;state.library.favorites=f.includes(id)?f.filter(x=>x!==id):[...f,id];persistLibrary();render();return;}
 if(b.dataset.category){state.category=b.dataset.category;render();return;}
 if(b.dataset.project){if(state.project)saveProject();state.project=clone(state.library.projects.find(p=>p.id===b.dataset.project));state.selected=null;openEditor();return;}
 if(b.dataset.projectMenu){projectMenu(b.dataset.projectMenu);return;}
 if(b.dataset.duplicateProject){let p=clone(state.library.projects.find(p=>p.id===b.dataset.duplicateProject));if(state.library.projects.length>=60){toast('The library is full.');return;}p.id=uid();p.name+=' · copy';state.library.projects.push(p);persistLibrary();closeModal();render();return;}
 if(b.dataset.deleteProject){let id=b.dataset.deleteProject;modal('Delete this project?',`<p>This deletes the local project. Previously exported files are preserved.</p><button class="primary full" data-confirm-delete="${id}">Yes, delete</button><button class="secondary full" data-action="close-modal">Keep project</button>`);return;}
 if(b.dataset.confirmDelete){state.library.projects=state.library.projects.filter(p=>p.id!==b.dataset.confirmDelete);if(state.project?.id===b.dataset.confirmDelete)state.project=null;persistLibrary();closeModal();render();return;}
 if(b.dataset.tool){setTool(b.dataset.tool);return;}
 if(b.dataset.sticker){checkpoint();let p=state.project,l={id:uid(),type:'sticker',text:b.dataset.sticker,x:p.w/2,y:p.h/2,w:p.w*.25,h:p.w*.25,size:p.w*.18,rotation:0,opacity:1,align:'center',strokeWidth:0};p.layers.push(l);state.selected=l.id;changed(true);return;}
 if(b.dataset.shape){checkpoint();let p=state.project,l={id:uid(),type:'shape',shape:b.dataset.shape,x:p.w/2,y:p.h/2,w:p.w*.45,h:p.h*.24,color:'#cbb8fa',rotation:0,opacity:1};p.layers.push(l);state.selected=l.id;changed(true);return;}
 if(b.dataset.select){state.selected=b.dataset.select;state.tool=toolForLayer(selected());state.pen=false;draw();renderPanel();return;}
 for(const action of ['layerUp','layerDown','layerHide','layerDelete'])if(b.dataset[action]){let id=b.dataset[action],p=state.project,i=p.layers.findIndex(l=>l.id===id);checkpoint();if(action==='layerDelete'){p.layers.splice(i,1);if(state.selected===id)state.selected=null;}if(action==='layerHide')p.layers[i].hidden=!p.layers[i].hidden;if(action==='layerUp'&&i<p.layers.length-1)[p.layers[i],p.layers[i+1]]=[p.layers[i+1],p.layers[i]];if(action==='layerDown'&&i>0)[p.layers[i],p.layers[i-1]]=[p.layers[i-1],p.layers[i]];changed(true);return;}
 if(b.dataset.frame){stopPlay();state.frame=+b.dataset.frame;updateFrameControls();prefetchFrames();draw();return;}
 if(b.dataset.idea){let l=selected();if(l){checkpoint();l.text=b.dataset.idea;closeModal();changed(true);}return;}
 }catch(err){idle();toast(err.message||'Could not complete the action.');}
});
let inputCheckpoint=false;
document.addEventListener('focusin',e=>{if(e.target.matches('[data-prop],#layer-text,#bg-color,#fps,#animation-speed,#video-start,#video-end,#video-volume'))inputCheckpoint=false;});
document.addEventListener('pointerdown',e=>{if(e.target.matches('[data-prop],#bg-color,#fps,#animation-speed,#video-start,#video-end,#video-volume'))inputCheckpoint=false;});
document.addEventListener('input',e=>{let t=e.target;if(videoInput(t))return;if(t.id==='animation-speed'){if(!inputCheckpoint){checkpoint();inputCheckpoint=true;}state.project.playbackRate=+t.value/100;$('#speed-value').textContent=t.value+'%';if(state.playing){stopPlay();togglePlay();}updateFrameControls();changed();return;}if(t.id==='frame-scrub'){stopPlay();state.frame=+t.value;updateFrameControls();prefetchFrames();draw();return;}if(t.dataset.prop){let out=$('[data-range-value="'+t.dataset.prop+'"]');if(out)out.textContent=t.value;}if(t.id==='search'){state.query=t.value;filterTemplates();return;}if(t.id==='pen-color'){state.penColor=t.value;return;}if(t.id==='pen-width'){state.penWidth=+t.value;return;}
 if(t.matches('[data-prop],#layer-text,#bg-color,#fps,#animation-speed,#video-start,#video-end,#video-volume')){if(!inputCheckpoint){checkpoint();inputCheckpoint=true;}let l=selected();if(t.dataset.prop&&l){l[t.dataset.prop]=t.type==='range'?+t.value:t.value;}if(t.id==='layer-text'&&l)l.text=t.value;if(t.id==='bg-color'){state.project.bg=t.value;state.project.transparent=false;$('#canvas')?.classList.remove('transparent');}if(t.id==='fps'){state.project.fps=+t.value;if($('#fps-value'))$('#fps-value').textContent=t.value+' FPS';if(state.playing){stopPlay();togglePlay();}}changed();}
});
document.addEventListener('change',e=>{if(e.target.id==='canvas-ratio'&&e.target.value){let [w,h]=e.target.value.split(',').map(Number);applyRatio(w,h);}inputCheckpoint=false;});
document.addEventListener('keydown',e=>{if(e.key==='Escape'){if(!$('#modal').hidden)closeModal();else if(state.page==='editor')showPage('home');}if(!$('#modal').hidden&&e.key==='Tab'){let els=[...$('#modal').querySelectorAll('button,input,select,textarea')].filter(x=>!x.disabled);let a=els[0],z=els[els.length-1];if(e.shiftKey&&document.activeElement===a){z.focus();e.preventDefault();}else if(!e.shiftKey&&document.activeElement===z){a.focus();e.preventDefault();}}if((e.ctrlKey||e.metaKey)&&e.key==='z'&&state.page==='editor'&&!e.target.matches('input,textarea')){e.preventDefault();e.shiftKey?actions.redo():actions.undo();}});
window.handleBack=()=>{if(!$('#modal').hidden)closeModal();else if(state.page==='editor')showPage('home');else if(state.page!=='home')showPage('home');else if(window.Android){clearTimeout(libraryTimer);if(state.loaded)Android.putLibrary(JSON.stringify(state.library));Android.finishApp();}else toast('Projects are saved in this browser.');};
document.addEventListener('visibilitychange',()=>{if(document.hidden){stopPlay();saveProject();}});
window.addEventListener('beforeunload',()=>saveProject());
$('.brand')?.addEventListener('click',e=>{e.preventDefault();showPage('home');});
const localFrameSource=s=>typeof s==='string'&&!!window.Android&&/^https:\/\/app\.iskrio\.local\/media\/[0-9a-f-]{36}\/frame_[0-9]{5}\.(jpg|png)$/.test(s);
const frameThumbnail=s=>localFrameSource(s)?s.replace(/\.(jpg|png)$/,'.thumb.jpg'):s;
let previewBusy=false,previewAgain=false,previewBuffer=null,playStarted=0,playOrigin=0,activeOperation=null,cancelExport=false;
function frameDelay(project,index){return project.frameDurations?project.frameDurations[index]/(project.playbackRate||1):1000/project.fps;}
function animationDuration(project){return project.frames.reduce((sum,_,index)=>sum+frameDelay(project,index),0);}
function frameOffset(project,index){let value=0;for(let i=0;i<index;i++)value+=frameDelay(project,i);return value;}
function frameAtTime(project,ms){let elapsed=0;for(let i=0;i<project.frames.length;i++){elapsed+=frameDelay(project,i);if(ms<elapsed)return i;}return project.frames.length-1;}
function frameTime(){const p=state.project;return ((frameOffset(p,state.frame)+frameDelay(p,state.frame))/1000).toFixed(2)+' / '+(animationDuration(p)/1000).toFixed(2)+' s';}

function updateFrameControls(){let t=$('#timeline');if(t){t.querySelector('.active')?.classList.remove('active');t.children[state.frame]?.classList.add('active');}if($('#frame-scrub'))$('#frame-scrub').value=state.frame;if($('#frame-time'))$('#frame-time').textContent=frameTime();}
function prefetchFrames(){let p=state.project;if(!p?.frames?.length)return;for(let i=0;i<6;i++)loadImage(p.frames[(state.frame+i)%p.frames.length]).catch(()=>{});}
function toolForLayer(layer){return layer?.type==='image'?'image':layer?.type==='text'||layer?.type==='sticker'?'text':layer?.type==='path'?'draw':'stickers';}
async function portableProject(project,cache=new Map()){
 const copy=clone(project);delete copy.originalGif;
 async function resolve(src){if(!localFrameSource(src))return src;if(!cache.has(src)){let data=Android.readMedia(src);if(!data)throw Error('A frame is missing. Import this video again.');cache.set(src,data);}return cache.get(src);}
 for(let i=0;i<(copy.frames?.length||0);i++){copy.frames[i]=await resolve(copy.frames[i]);if(i%10===0)await new Promise(r=>setTimeout(r,0));}
 for(const layer of copy.layers)if(layer.type==='image')layer.src=await resolve(layer.src);
 return copy;
}
async function backupLibrary(){closeModal();saveProject();busy('Preparing backup…');try{const cache=new Map(),library={favorites:clone(state.library.favorites),templates:clone(state.library.templates||[]),projects:[]};for(const project of state.library.projects)library.projects.push(await portableProject(project,cache));const json=JSON.stringify({format:'iskrio-backup',version:1,library});if(library.projects.some(p=>p.video)){activeOperation='video-backup';Android.backupWithVideos(json);return;}if(json.length>38*1024*1024)throw Error('This backup exceeds 38 MB. Export projects separately.');await saveFile('Iskrio-backup-'+new Date().toISOString().slice(0,10)+'.json','application/json',json);}catch(e){idle();toast(e.message||'Backup failed.');}}
async function draw(){
 if(previewBusy){previewAgain=true;return;}
 previewBusy=true;
 try{do{
  previewAgain=false;let c=$('#canvas'),p=state.project,frame=state.frame;if(!c||!p)break;
  if(!previewBuffer||previewBuffer.width!==p.w||previewBuffer.height!==p.h){previewBuffer=document.createElement('canvas');previewBuffer.width=p.w;previewBuffer.height=p.h;}
  await paint(previewBuffer.getContext('2d'),p,frame,false);
  if(c!==$('#canvas')||p!==state.project){previewAgain=true;continue;}
  let ctx=c.getContext('2d');ctx.clearRect(0,0,c.width,c.height);ctx.drawImage(previewBuffer,0,0);
  const l=selected();if(l&&!l.hidden&&!state.pen&&!state.playing){ctx.save();ctx.translate(l.x,l.y);ctx.rotate((l.rotation||0)*Math.PI/180);ctx.strokeStyle='#d8f991';ctx.lineWidth=p.w/300;ctx.setLineDash([p.w/120,p.w/180]);ctx.strokeRect(-l.w/2-5,-l.h/2-5,l.w+10,l.h+10);ctx.setLineDash([]);ctx.fillStyle='#d8f991';ctx.beginPath();ctx.arc(l.w/2-8,l.h/2-8,p.w/65,0,Math.PI*2);ctx.fill();ctx.restore();}
 }while(previewAgain);}catch(e){stopPlay();toast(e.message||'Could not display this frame.');}finally{previewBusy=false;}
}
function renderTimeline(){if(!$('#timeline'))return;$('#timeline').innerHTML=state.project.frames.map((src,i)=>`<button class="frame ${i===state.frame?'active':''}" data-frame="${i}" aria-label="Frame ${i+1}"><img loading="lazy" decoding="async" src="${frameThumbnail(src)}" alt=""><span>${i+1}</span></button>`).join('');updateFrameControls();}
function stopPlay(){stopVideoPlayback();cancelAnimationFrame(playTimer);state.playing=false;updateVideoControls();const button=$('#preview-play');if(button)button.innerHTML=icon('play');}
function togglePlay(){
 if(state.project?.video){playVideo();return;}
 if(state.playing){stopPlay();draw();renderPanel();return;}
 if(!state.project?.frames?.length)return;
 if(state.frame===state.project.frames.length-1)state.frame=0;
 state.playing=true;playOrigin=state.frame;playStarted=performance.now();prefetchFrames();
 if($('#preview-play'))$('#preview-play').textContent='Ⅱ';
 const project=state.project,duration=animationDuration(project),offset=frameOffset(project,playOrigin);
 function tick(now){if(!state.playing||state.project!==project)return;const elapsed=offset+Math.max(0,now-playStarted),loops=project.loopCount??0;
  if(loops>0&&elapsed>=duration*loops){state.frame=project.frames.length-1;stopPlay();draw();updateFrameControls();renderPanel();return;}
  const frame=frameAtTime(project,elapsed%duration);if(frame!==state.frame){state.frame=frame;prefetchFrames();draw();updateFrameControls();}playTimer=requestAnimationFrame(tick);
 }
 playTimer=requestAnimationFrame(tick);renderPanel();
}
async function newAnimation(srcs,fps=30,options={}){
 if(!srcs.length||srcs.length>900)throw Error('Choose between 1 and 900 frames.');if(state.project)saveProject();
 let im=await loadImage(srcs[0]),scale=options.preserveSize?1:Math.min(1,640/Math.max(im.width,im.height));
 state.project=baseProject('Animation',Math.max(options.preserveSize?1:64,Math.round(im.width*scale)),Math.max(options.preserveSize?1:64,Math.round(im.height*scale)));
 const project=state.project;project.frames=srcs.slice();project.fps=Math.max(1,Math.min(50,Number(fps)||30));
 if(options.frameDurations){project.frameDurations=options.frameDurations.slice();project.playbackRate=1;project.loopCount=options.loopCount??0;}
 project.transparent=options.transparent===true;project.layers.push(addImageLayer(srcs[0],im.width,im.height,true));validateProject(project);
 if(options.originalGifFrame&&localFrameSource(options.originalGifFrame))project.originalGif={frame:options.originalGifFrame,fingerprint:gifFingerprint(project)};
 state.selected=null;openEditor();state.tool='animation';renderPanel();prefetchFrames();
}
window.nativeFrames=async result=>{try{await newAnimation(Array.isArray(result)?result:result.frames,Array.isArray(result)?30:result.fps,Array.isArray(result)?{}:result);const imported=state.project;await Promise.all(imported.frames.slice(0,Math.min(3,imported.frames.length)).map(loadImage));activeOperation=null;idle();if(state.project===imported&&state.page==='editor'&&!document.hidden)togglePlay();}catch(e){activeOperation=null;idle();toast(e.message||'Could not open the animation.');}};

async function doExport(format,size){
 closeModal();stopPlay();activeOperation='export';cancelExport=false;busy('Preparing export…');await new Promise(r=>setTimeout(r,40));
 try{
  let p=state.project,name='Iskrio-'+p.id;
  if(format==='mp4'&&p.video){await exportVideoProject(size);return;}
  if(format==='project'){let json=JSON.stringify({format:'iskrio-project',version:1,project:await portableProject(p)});if(p.video){activeOperation='video-backup';Android.backupWithVideos(json);return;}if(json.length>38*1024*1024)throw Error('This project backup exceeds 38 MB. Use fewer frames.');await saveFile(name+'.iskrio','application/octet-stream',json);return;}
  if(format==='gif'&&p.originalGif&&p.originalGif.fingerprint===gifFingerprint(p)&&window.Android?.saveOriginalGif){Android.saveOriginalGif(name+'.gif',p.originalGif.frame);return;}
  let scale=1;
  let width=Math.max(1,Math.round(p.w*scale)),height=Math.max(1,Math.round(p.h*scale));if(height>4096||width*height>8e6)throw Error('The canvas exceeds the export pixel limit.');
  let c=document.createElement('canvas');c.width=width;c.height=height;let ctx=c.getContext('2d');ctx.scale(width/p.w,height/p.h);
  if(format==='gif'){
   const videoDuration=p.video?p.video.endMs-p.video.startMs:0;if(videoDuration>30000)throw Error('For GIF export, trim the video to 30 seconds or less. MP4 supports the full 10 minutes.');const videoFps=30,count=p.video?Math.ceil(videoDuration*videoFps/1000):p.frames?.length||1,encoder=GifCodec.createEncoder(width,height,p.loopCount??0);
   for(let i=0;i<count;i++){if(cancelExport)throw Error('Export cancelled.');if(p.video)await seekVideoTime(p.video.startMs/1000+i/videoFps);await paint(ctx,p,i,false);encoder.addFrame(ctx.getImageData(0,0,width,height),p.video?Math.min(1000/videoFps,videoDuration-i*1000/videoFps):p.frames?.length?frameDelay(p,i):1000/(p.fps||30));if(i%3===0||i===count-1){busy('Exporting GIF… '+Math.round((i+1)*100/count)+'%');await new Promise(r=>setTimeout(r,0));}}
   await saveFile(name+'.gif','image/gif',encoder.finish());
  }else{await paint(ctx,p,state.frame,false);let data=c.toDataURL(format==='jpeg'?'image/jpeg':'image/png',.95),b64=data.split(',')[1];if(window.Android){Android.save(name+(format==='jpeg'?'.jpg':'.png'),format==='jpeg'?'image/jpeg':'image/png',b64);idle();}else{let binary=atob(b64);await saveFile(name+(format==='jpeg'?'.jpg':'.png'),format==='jpeg'?'image/jpeg':'image/png',Uint8Array.from(binary,x=>x.charCodeAt(0)));}}
 }catch(e){idle();toast(e.message||'Export failed.');}finally{if(!['video-export','video-backup'].includes(activeOperation))activeOperation=null;cancelExport=false;}
}

let suppressClicksUntil=0,swipeStart=null;

function renameTemplate(id){const t=state.library.templates.find(t=>t.id===id);if(!t)return;modal('Template name',`<label>Name<input id="template-name" maxlength="60" value="${esc(t.name)}"></label><button class="primary full" data-save-template-name="${id}">Save</button>`);}
function templateMenu(id){const t=state.library.templates.find(t=>t.id===id);if(!t)return;modal(t.name,`<button class="menu-button" data-rename-template="${id}">${icon('text')}<span>Rename</span></button><button class="menu-button danger" data-remove-template="${id}">${icon('trash')}<span>Remove template</span></button>`);}
document.addEventListener('dblclick',e=>{if(e.target.id==='canvas'&&selected()?.type==='text'){setTool('text');$('#layer-text')?.focus();$('#layer-text')?.select();}});

// Horizontal order: MEME | My creations | GIF.
const PAGER_PAGES=['meme-start','home','gif-start'];
function pagerMarkup(page){
 if(page==='meme-start')return `<header class="start-header"><h1>MEME</h1></header><div class="start-actions"><button class="menu-button" data-action="link-meme">${icon('download')}<span>Import link</span>${icon('arrow')}</button><button class="menu-button" data-action="pick-main-image">${icon('image')}<span>Choose photo</span>${icon('arrow')}</button><button class="menu-button" data-action="blank">${icon('plus')}<span>Blank canvas</span>${icon('arrow')}</button><button class="menu-button" data-page="templates">${icon('grid')}<span>My templates</span>${icon('arrow')}</button><button class="menu-button" data-action="collage-menu">${icon('grid')}<span>Collage</span>${icon('arrow')}</button><button class="menu-button" data-action="restore">${icon('folder')}<span>Open project</span>${icon('arrow')}</button></div>`;
 if(page==='gif-start')return `<header class="start-header"><h1>GIF</h1></header><div class="start-actions"><button class="menu-button" data-action="link-gif">${icon('download')}<span>Import link</span>${icon('arrow')}</button><button class="menu-button" data-action="s-new-studio">${icon('film')}<span>Video studio</span>${icon('arrow')}</button><button class="menu-button" data-action="pick-frames">${icon('image')}<span>Photos to GIF</span>${icon('arrow')}</button><button class="menu-button" data-action="pick-animation">${icon('play')}<span>Open GIF or video</span>${icon('arrow')}</button></div>`;
 const rows=state.library.projects.slice().sort((a,b)=>b.updated-a.updated);
 return `<header class="home-header creations-header"><h1>My creations</h1><div class="row"><button class="icon-button" data-action="backup-menu" aria-label="Backups and import">${icon('folder')}</button><button class="icon-button" data-action="settings" aria-label="Settings">${icon('settings')}</button></div></header>${rows.length?`<div class="template-grid">${rows.map(p=>`<article><button class="template-open" data-project="${esc(p.id)}"><img loading="lazy" decoding="async" class="project-image" src="${esc(p.thumb||'')}" alt="${esc(p.name)}"></button><div class="project-meta"><h3>${esc(p.name)}</h3><button data-project-menu="${esc(p.id)}" aria-label="Project options">⋯</button></div><small class="creation-meta">${p.video?'Video · ':p.frames?.length?'GIF · ':''}${p.w} × ${p.h}</small></article>`).join('')}</div>`:`<div class="creations-empty">${icon('folder')}<h2>No creations yet</h2></div>`}`;
}
function updatePagerNavigation(){
 $$('#nav button').forEach(button=>{const active=button.dataset.page===state.page;button.classList.toggle('active',active);if(active)button.setAttribute('aria-current','page');else button.removeAttribute('aria-current');});
 $$('.pager-page').forEach(pane=>{const active=pane.dataset.pagerPage===state.page;pane.setAttribute('aria-hidden',String(!active));pane.inert=!active;});
}
function renderPager(){
 const scrolls={};$$('.pager-page').forEach(p=>scrolls[p.dataset.pagerPage]=p.scrollTop);
 $('#main').innerHTML=`<div id="page-viewport"><div id="page-track">${PAGER_PAGES.map(page=>`<section class="pager-page" data-pager-page="${page}" aria-label="${page==='home'?'My creations':page==='meme-start'?'MEME':'GIF'}">${pagerMarkup(page)}</section>`).join('')}</div></div>`;
 $$('.pager-page').forEach(p=>{p.scrollTop=scrolls[p.dataset.pagerPage]||0;});
 alignPager(false);updatePagerNavigation();
}
function alignPager(animate){
 const viewport=$('#page-viewport'),track=$('#page-track');if(!viewport||!track)return;
 const reduced=window.matchMedia('(prefers-reduced-motion: reduce)').matches;
 track.style.transition=animate&&!reduced?'transform 340ms cubic-bezier(.22,.75,.25,1)':'none';
 track.style.transform=`translate3d(${-PAGER_PAGES.indexOf(state.page)*viewport.clientWidth}px,0,0)`;
 document.documentElement.style.setProperty('--pager-nav-height',($('#nav')?.offsetHeight||76)+'px');
}
function navigatePager(page,animate=true){
 if(!PAGER_PAGES.includes(page))return;
 state.page=page;state.pen=false;if(page==='home'){const pane=$('[data-pager-page="home"]');if(pane){const top=pane.scrollTop;pane.innerHTML=pagerMarkup('home');pane.scrollTop=top;}}updatePagerNavigation();alignPager(animate);
}
function pagerTranslation(track){
 const matrix=getComputedStyle(track).transform;if(matrix==='none')return 0;
 const values=matrix.slice(matrix.indexOf('(')+1,-1).split(',').map(Number);
 return values.length===16?values[12]:values[4];
}
document.addEventListener('touchstart',e=>{
 if(!PAGER_PAGES.includes(state.page)||!$('#modal').hidden||e.touches.length!==1||e.target.closest('input,textarea,select,.chips,.timeline,#canvas'))return;
 const track=$('#page-track'),viewport=$('#page-viewport');if(!track||!viewport)return;
 const touch=e.touches[0],now=performance.now();
 // An animation can be grabbed mid-flight without jumping to its destination.
 const base=pagerTranslation(track);track.style.transition='none';track.style.transform=`translate3d(${base}px,0,0)`;
 swipeStart={x:touch.clientX,y:touch.clientY,id:touch.identifier,index:PAGER_PAGES.indexOf(state.page),width:viewport.clientWidth,base,axis:null,dx:0,samples:[{x:touch.clientX,time:now}]};
},{passive:true});
document.addEventListener('touchmove',e=>{
 if(!swipeStart)return;
 const start=swipeStart,touch=[...e.touches].find(t=>t.identifier===start.id);
 if(!touch||e.touches.length!==1||!$('#modal').hidden){swipeStart=null;alignPager(true);return;}
 const dx=touch.clientX-start.x,dy=touch.clientY-start.y;
 if(!start.axis){if(Math.hypot(dx,dy)<8)return;if(Math.abs(dx)<Math.abs(dy)*1.2){swipeStart=null;alignPager(true);return;}start.axis='horizontal';}
 e.preventDefault();start.dx=dx;
 const resistance=start.index===0&&dx>0||start.index===PAGER_PAGES.length-1&&dx<0;
 $('#page-track').style.transform=`translate3d(${start.base+dx*(resistance?.22:1)}px,0,0)`;
 const now=performance.now();start.samples.push({x:touch.clientX,time:now});start.samples=start.samples.filter(s=>now-s.time<120);
},{passive:false});
function finishPagerGesture(e,cancelled=false){
 if(!swipeStart)return;
 const start=swipeStart;swipeStart=null;
 if(start.axis!=='horizontal'){alignPager(true);return;}
 if(e.cancelable)e.preventDefault();
 suppressClicksUntil=Date.now()+400;
 const touch=[...(e.changedTouches||[])].find(t=>t.identifier===start.id),now=performance.now();
 let dx=touch?touch.clientX-start.x:start.dx;
 const recent=start.samples.find(s=>now-s.time<=100)||start.samples[start.samples.length-1];
 const elapsed=recent?now-recent.time:0;
 const velocity=recent&&touch&&elapsed>5?(touch.clientX-recent.x)/elapsed:0;
 const projected=dx+Math.max(-1.5,Math.min(1.5,velocity))*150;
 let index=start.index;
 if(!cancelled&&Math.abs(dx)>24&&Math.abs(projected)>start.width*.24)index=Math.max(0,Math.min(PAGER_PAGES.length-1,start.index-(projected>0?1:-1)));
 navigatePager(PAGER_PAGES[index],true);
}
document.addEventListener('touchend',e=>finishPagerGesture(e),{passive:false});
document.addEventListener('touchcancel',e=>finishPagerGesture(e,true),{passive:false});
window.addEventListener('resize',()=>{swipeStart=null;alignPager(false);});

let linkImportMode='main',linkImportOptions=[],linkMediaMode='video';
let internetEnabled=!!(window.Android?.getInternetAccess&&Android.getInternetAccess());
function internetControl(){return `<div class="internet-control"><div><strong>Internet access</strong><small>Link imports only</small></div><button class="internet-switch" data-action="toggle-internet" data-internet-switch role="switch" aria-label="Internet access for link imports" aria-checked="${internetEnabled}"><span data-internet-state>${internetEnabled?'On':'Off'}</span><span class="switch-track" aria-hidden="true"><span></span></span></button></div>`;}
function updateInternetControls(){
 $$('[data-internet-switch]').forEach(button=>{button.setAttribute('aria-checked',String(internetEnabled));button.querySelector('[data-internet-state]').textContent=internetEnabled?'On':'Off';});
 const download=$('[data-action="resolve-link"]');if(download)download.disabled=!internetEnabled;
}
function setInternetEnabled(enabled){
 internetEnabled=!!enabled;updateInternetControls();
 if(!enabled&&activeOperation==='link')activeOperation='link-cancelling';
 if(window.Android?.setInternetAccess)Android.setInternetAccess(!!enabled);else nativeInternetAccess({enabled:!!enabled,reason:enabled?'enabled':'disabled'});
}
window.nativeInternetAccess=result=>{
 internetEnabled=result.enabled===true;updateInternetControls();
 if(!internetEnabled&&result.reason==='disabled'&&['link','link-cancelling'].includes(activeOperation)){
  activeOperation='link-cancelling';busy('Disconnecting…');
  if(!window.Android?.isLinkImportActive||!Android.isLinkImportActive()){activeOperation=null;idle();}
 }
};
window.linkToken=id=>((Number(id)/1e15)*Math.PI).toString(36).replace(/(0+|\.)/g,'');
function importLinkDialog(mode='main'){
 linkImportMode=mode;linkImportOptions=[];linkMediaMode='video';
 modal('Import link',`${internetControl()}<div class="space"></div><label>Import<select id="link-media-mode"><option value="video">Video + audio</option><option value="audio">Audio only</option></select></label><label>HTTPS link<input id="import-link-url" type="url" inputmode="url" autocapitalize="off" autocomplete="off" spellcheck="false" maxlength="8192" placeholder="https://…"></label><div class="link-import-actions"><button class="secondary" data-action="paste-link">Paste</button><button class="primary" data-action="resolve-link" ${internetEnabled?'':'disabled'}>Download & import</button></div>`);
}
function beginLinkImport(){
 if(!internetEnabled){toast('Turn on Internet access to import a link.');return;}
 linkMediaMode=$('#link-media-mode')?.value||linkMediaMode;if(linkMediaMode==='audio'&&(state.project?.audioTracks?.length||0)>=20){toast('Remove an audio item before importing another.');return;}const source=$('#import-link-url').value.trim();
 if(!/^https:\/\//i.test(source)){toast('Paste an HTTPS link.');return;}
 if(!window.Android){toast('Link import is available in the Android app.');return;}
 closeModal(true);activeOperation='link';busy('Checking link…');Android.resolveLink(source,'');
}
function downloadLinkOption(index){
 const option=linkImportOptions[index];if(!option)return;
 if(!internetEnabled){toast('Internet access is off.');return;}
 closeModal(true);activeOperation='link';busy(linkMediaMode==='audio'?'Downloading audio…':'Downloading…');if(linkMediaMode==='audio')Android.downloadLinkAudio(option.url);else Android.downloadLinkMedia(option.url);
}
window.nativeClipboard=text=>{const input=$('#import-link-url');if(!input)return;const match=String(text||'').match(/https:\/\/[^\s<>"']+/i);if(match)input.value=match[0];else toast('The clipboard does not contain an HTTPS link.');};
window.nativeLinkPostId=result=>{if(activeOperation!=='link'||!/^\d{5,25}$/.test(result.postId))return;Android.resolveLink('https://x.com/i/status/'+result.postId,linkToken(result.postId));};
window.nativeLinkProgress=value=>{if(activeOperation==='link')busy((linkMediaMode==='audio'?'Downloading audio…':'Downloading…')+(value<0?'':' '+value+'%'));};
window.nativeLinkOptions=result=>{
 if(activeOperation!=='link')return;
 activeOperation=null;idle();linkImportOptions=Array.isArray(result.items)?result.items:[];
 if(linkMediaMode==='audio'){const groups=new Map();for(const item of linkImportOptions.filter(o=>['video','audio','unknown'].includes(o.kind))){const key=item.group||'media';if(!groups.has(key))groups.set(key,{...item,label:'Audio '+(groups.size+1)});}linkImportOptions=[...groups.values()];}if(!linkImportOptions.length){nativeError(linkMediaMode==='audio'?'No audio source was found in this link.':'No downloadable media found.');return;}
 if(linkImportOptions.length===1){downloadLinkOption(0);return;}
 modal('Choose media',internetControl()+'<div class="space"></div>'+linkImportOptions.map((option,index)=>`<button class="menu-button" data-link-option="${index}">${icon(option.kind==='image'?'image':'play')}<span>${esc(option.label)}</span>${icon('download')}</button>`).join(''));
};
window.nativeLinkFile=async result=>{
 if(activeOperation!=='link')return;
 activeOperation=null;idle();
 try{if(linkImportMode==='gif'&&result.files?.[0]?.data)await newAnimation([result.files[0].data],30);else await receiveFiles(result.kind,result.files);}
 catch(error){toast(error.message||'Could not import the downloaded media.');}
};

const localVideoSource=source=>typeof source==='string'&&/^https:\/\/app\.iskrio\.local\/videos\/[0-9a-f-]{36}\/source\.(mp4|webm|png|jpg)$/.test(source);
let videoElement=null,videoSource='',videoReady=null,videoTick=0;
const videoTime=seconds=>{seconds=Math.max(0,Number(seconds)||0);return Math.floor(seconds/60)+':'+Math.floor(seconds%60).toString().padStart(2,'0');};
function videoRect(project){const scale=Math.min(project.w/project.video.width,project.h/project.video.height),width=project.video.width*scale,height=project.video.height*scale;return {x:(project.w-width)/2,y:(project.h-height)/2,width,height};}
function disposeVideo(){cancelAnimationFrame(videoTick);if(videoElement){videoElement.pause();videoElement.removeAttribute('src');videoElement.load();videoElement.remove();}videoElement=null;videoSource='';videoReady=null;}
function mountVideoPlayer(){
 const p=state.project;if(!p?.video){disposeVideo();return;}
 if(videoSource!==p.video.src||!videoElement){
  disposeVideo();videoSource=p.video.src;videoElement=document.createElement('video');const element=videoElement; element.className='source-video';element.playsInline=true;element.preload='auto';element.crossOrigin='anonymous';element.setAttribute('aria-hidden','true');element.tabIndex=-1;document.body.appendChild(element);
  videoReady=new Promise((resolve,reject)=>{element.addEventListener('loadeddata',()=>resolve(element),{once:true});element.addEventListener('error',()=>reject(Error('This video cannot be played by this device. Try a different codec.')),{once:true});});videoReady.catch(error=>{if(state.project?.video?.src===videoSource){stopPlay();toast(error.message);}});
  element.addEventListener('timeupdate',()=>{if(state.project?.video?.src===videoSource)updateVideoControls();});
  element.addEventListener('seeked',()=>{if(state.project?.video?.src===videoSource){draw();updateVideoControls();}});
  element.addEventListener('ended',()=>{if(state.project?.video?.src===videoSource&&state.playing){element.currentTime=state.project.video.startMs/1000;element.play().catch(()=>stopPlay());}});
  element.src=videoSource;
 }
 videoElement.volume=p.video.volume;videoElement.muted=p.video.muted;
 if(videoElement.readyState>=1&&(videoElement.currentTime<p.video.startMs/1000||videoElement.currentTime>=p.video.endMs/1000))videoElement.currentTime=p.video.startMs/1000;
 if(!$('#video-playback'))$('.canvas-info').insertAdjacentHTML('afterend',`<div id="video-playback" class="playback-controls"><button class="icon-button" data-action="play" aria-label="Play or pause video" id="video-play">${icon('play')}</button><input id="video-scrub" type="range" min="0" max="${(p.video.endMs-p.video.startMs)/1000}" step="0.01" value="0" aria-label="Video position"><output id="video-clock"></output><button class="icon-button" data-action="video-mute" aria-label="Mute or unmute" id="video-sound">${p.video.muted?'🔇':'🔊'}</button></div>`);
 updateVideoControls();
}
function updateVideoControls(){const p=state.project;if(!p?.video)return;const time=videoElement?.currentTime||p.video.startMs/1000,length=(p.video.endMs-p.video.startMs)/1000;if($('#video-scrub')){$('#video-scrub').max=length;$('#video-scrub').value=Math.max(0,time-p.video.startMs/1000);}if($('#video-clock'))$('#video-clock').textContent=videoTime(time-p.video.startMs/1000)+' / '+videoTime(length);if($('#video-play'))$('#video-play').innerHTML=state.playing?'Ⅱ':icon('play');if($('#video-sound'))$('#video-sound').textContent=p.video.muted?'🔇':'🔊';}
function stopVideoPlayback(){cancelAnimationFrame(videoTick);videoElement?.pause();}
async function playVideo(){
 if(state.playing){stopPlay();draw();renderPanel();return;}
 mountVideoPlayer();const p=state.project,element=videoElement;
 try{await videoReady;if(p!==state.project)return;if(element.currentTime<p.video.startMs/1000||element.currentTime>=p.video.endMs/1000-.02)element.currentTime=p.video.startMs/1000;element.volume=p.video.volume;element.muted=p.video.muted;await element.play();state.playing=true;updateVideoControls();
  function tick(){if(!state.playing||p!==state.project)return;if(element.currentTime>=p.video.endMs/1000)element.currentTime=p.video.startMs/1000;draw();updateVideoControls();videoTick=requestAnimationFrame(tick);}videoTick=requestAnimationFrame(tick);renderPanel();
 }catch(error){stopPlay();toast(error.message||'Tap Play to start the video.');}
}
function videoPanel(){const v=state.project.video;return `<h3>Video</h3><div class="controls"><label class="span">Start<output id="video-start-value">${videoTime(v.startMs/1000)}</output><input id="video-start" type="range" min="0" max="${v.durationMs/1000}" step="0.1" value="${v.startMs/1000}"></label><label class="span">End<output id="video-end-value">${videoTime(v.endMs/1000)}</output><input id="video-end" type="range" min="0" max="${v.durationMs/1000}" step="0.1" value="${v.endMs/1000}"></label><label class="span">Original audio<output id="video-volume-value">${v.hasAudio?Math.round(v.volume*100)+'%':'No audio track'}</output><input id="video-volume" type="range" min="0" max="100" value="${Math.round(v.volume*100)}" ${v.hasAudio?'':'disabled'}></label></div><div class="inline-buttons"><button class="mini" data-action="play">${state.playing?'Pause':'Play'}</button><button class="mini" data-action="video-mute" ${v.hasAudio?'':'disabled'}>${v.muted?'Unmute':'Mute'}</button><button class="mini" data-action="video-reset">Reset trim</button></div>`;}
function videoInput(element){
 const p=state.project;if(!p?.video)return false;const v=p.video;
 if(element.id==='video-scrub'){stopPlay();videoElement.currentTime=v.startMs/1000+Number(element.value);updateVideoControls();return true;}
 if(!['video-start','video-end','video-volume'].includes(element.id))return false;
 if(!inputCheckpoint){checkpoint();inputCheckpoint=true;}
 if(element.id==='video-start'){v.startMs=Math.min(v.endMs-100,Math.round(Number(element.value)*1000));v.startMs=Math.max(0,v.startMs);element.value=v.startMs/1000;$('#video-start-value').textContent=videoTime(v.startMs/1000);videoElement.currentTime=v.startMs/1000;}
 if(element.id==='video-end'){v.endMs=Math.max(v.startMs+100,Math.round(Number(element.value)*1000));v.endMs=Math.min(v.durationMs,v.endMs);element.value=v.endMs/1000;$('#video-end-value').textContent=videoTime(v.endMs/1000);if(videoElement.currentTime>v.endMs/1000)videoElement.currentTime=v.startMs/1000;}
 if(element.id==='video-volume'){v.volume=Number(element.value)/100;v.muted=v.volume===0;videoElement.volume=v.volume;videoElement.muted=v.muted;$('#video-volume-value').textContent=element.value+'%';}
 updateVideoControls();changed();return true;
}
window.nativeVideo=async info=>{
 try{if(!localVideoSource(info.src))throw Error('Invalid local video.');if(state.project)saveProject();const scale=Math.min(1,1080/Math.max(info.width,info.height));state.project=baseProject('Video',Math.max(64,Math.round(info.width*scale)),Math.max(64,Math.round(info.height*scale)));state.project.bg='#000000';state.project.video={...info,startMs:0,endMs:info.durationMs,volume:1,muted:false};validateProject(state.project);state.selected=null;state.tool='video';openEditor();state.tool='video';renderPanel();await videoReady;videoElement.currentTime=0;await draw();activeOperation=null;idle();playVideo();}catch(error){activeOperation=null;idle();toast(error.message||'Could not open this video.');}
};
async function exportVideoProject(size){
 const p=state.project;stopPlay();const ratio=Math.min(size/Math.max(p.w,p.h),1920/Math.max(p.w,p.h)),width=Math.max(2,Math.round(p.w*ratio/2)*2),height=Math.max(2,Math.round(p.h*ratio/2)*2);
 const c=document.createElement('canvas');c.width=width;c.height=height;const ctx=c.getContext('2d');ctx.scale(width/p.w,height/p.h);await document.fonts.ready;await paint(ctx,p,0,false,true);activeOperation='video-export';busy('Exporting video…');Android.exportVideo(p.video.src,p.video.startMs,p.video.endMs,width,height,p.video.volume,p.video.muted,c.toDataURL('image/png').split(',')[1]);
}
window.nativeVideoExportProgress=progress=>{if(activeOperation==='video-export')busy(progress<0?'Exporting video…':'Exporting video… '+progress+'%');};
window.nativeVideoExportReady=result=>{activeOperation=null;idle();Android.saveVideo(result.token);};
actions['video-mute']=()=>{if(!state.project?.video)return;checkpoint();const v=state.project.video;v.muted=!v.muted;videoElement.muted=v.muted;renderPanel();updateVideoControls();scheduleSave();};
actions['video-reset']=()=>{checkpoint();const v=state.project.video;v.startMs=0;v.endMs=v.durationMs;videoElement.currentTime=0;renderPanel();updateVideoControls();scheduleSave();};


async function seekVideoTime(time){if(Math.abs(videoElement.currentTime-time)<.001&&videoElement.readyState>=2)return;await new Promise((resolve,reject)=>{let timeout=setTimeout(()=>{videoElement.removeEventListener('seeked',ready);reject(Error('Could not seek this video frame.'));},15000);function ready(){clearTimeout(timeout);resolve();}videoElement.addEventListener('seeked',ready,{once:true});videoElement.currentTime=time;});}
window.nativeArchiveProgress=progress=>{if(activeOperation==='video-backup')busy('Preparing video backup… '+progress+'%');};
window.nativeArchiveReady=result=>{activeOperation=null;idle();Android.saveVideo(result.token);};
window.nativeArchiveImporting=()=>{activeOperation='archive-import';busy('Restoring video project…');};
if($('#busy-internet'))$('#busy-internet').innerHTML=internetControl();
loadLibrary();
