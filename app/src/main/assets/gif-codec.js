/* Iskrio offline GIF89a encoder. Original implementation; no runtime dependencies. */
(function (root) {
  'use strict';
  const MAX_PIXELS = 4194304;

  class Bytes {
    constructor() { this.blocks = []; this.block = new Uint8Array(65536); this.pos = 0; this.length = 0; }
    byte(value) {
      if(this.length>=40*1024*1024)throw new Error("The GIF exceeds 40 MB. Use a shorter animation.");
      if (this.pos === this.block.length) { this.blocks.push(this.block); this.block = new Uint8Array(65536); this.pos = 0; }
      this.block[this.pos++] = value; this.length++;
    }
    word(value) { this.byte(value & 255); this.byte((value >>> 8) & 255); }
    ascii(value) { for (let i = 0; i < value.length; i++) this.byte(value.charCodeAt(i)); }
    finish() {
      const result = new Uint8Array(this.length); let offset = 0;
      for (const block of this.blocks) { result.set(block, offset); offset += block.length; }
      result.set(this.block.subarray(0, this.pos), offset); return result;
    }
  }

  // Keep source colors exactly when the frame fits a GIF palette. For edited
  // true-color frames, weighted median cut adapts the palette to the actual image.
  function indexedFrame(rgba, pixels) {
    const colors=new Map();let transparent=false,overflow=false;
    for(let i=0;i<pixels;i++){const n=i*4;if(rgba[n+3]<128){transparent=true;continue;}const key=(rgba[n]<<16)|(rgba[n+1]<<8)|rgba[n+2];if(colors.has(key))colors.set(key,colors.get(key)+1);else if(colors.size<257)colors.set(key,1);else{overflow=true;break;}}
    const limit=transparent?255:256;let palette,exact=null;
    if(!overflow&&colors.size<=limit){palette=[...colors.keys()].map(k=>[k>>>16,(k>>>8)&255,k&255]);exact=new Map([...colors.keys()].map((k,i)=>[k,i]));}
    else{
      const bins=new Map();transparent=false;
      for(let i=0;i<pixels;i++){const n=i*4;if(rgba[n+3]<128){transparent=true;continue;}const r=rgba[n],g=rgba[n+1],b=rgba[n+2],key=((r>>>3)<<10)|((g>>>3)<<5)|(b>>>3),v=bins.get(key);if(v){v[0]+=r;v[1]+=g;v[2]+=b;v[3]++;}else bins.set(key,[r,g,b,1]);}
      const points=[...bins.values()].map(v=>[v[0]/v[3],v[1]/v[3],v[2]/v[3],v[3]]);
      function box(items){const min=[255,255,255],max=[0,0,0];let weight=0;for(const c of items){weight+=c[3];for(let k=0;k<3;k++){min[k]=Math.min(min[k],c[k]);max[k]=Math.max(max[k],c[k]);}}const ranges=max.map((v,k)=>v-min[k]),axis=ranges.indexOf(Math.max(...ranges));return {items,weight,axis,score:items.length>1?ranges[axis]*Math.sqrt(weight):0};}
      const boxes=points.length?[box(points)]:[];
      while(boxes.length<(transparent?255:256)){let best=-1;for(let i=0;i<boxes.length;i++)if(boxes[i].score>0&&(best<0||boxes[i].score>boxes[best].score))best=i;if(best<0)break;const v=boxes[best],items=v.items.sort((x,y)=>x[v.axis]-y[v.axis]);let sum=0,split=1;for(;split<items.length;split++){sum+=items[split-1][3];if(sum>=v.weight/2)break;}boxes.splice(best,1,box(items.slice(0,split)),box(items.slice(split)));}
      palette=boxes.map(v=>{const sum=[0,0,0];for(const c of v.items)for(let k=0;k<3;k++)sum[k]+=c[k]*c[3];return sum.map(n=>Math.round(n/v.weight));});
    }
    if(!palette.length)palette.push([0,0,0]);const transparentIndex=transparent?palette.length:-1;if(transparent)palette.push([0,0,0]);
    const indices=new Uint8Array(pixels),cache=new Map();
    for(let i=0;i<pixels;i++){const n=i*4;if(rgba[n+3]<128){indices[i]=transparentIndex;continue;}const r=rgba[n],g=rgba[n+1],b=rgba[n+2],key=(r<<16)|(g<<8)|b;let index=exact?.get(key);if(index===undefined){const bin=((r>>>3)<<10)|((g>>>3)<<5)|(b>>>3);index=cache.get(bin);if(index===undefined){let distance=Infinity;for(let k=0;k<palette.length-(transparent?1:0);k++){const c=palette[k],d=2*(r-c[0])**2+4*(g-c[1])**2+(b-c[2])**2;if(d<distance){distance=d;index=k;}}cache.set(bin,index);}}indices[i]=index;}
    let bits=1;while((1<<bits)<palette.length)bits++;return {palette,indices,bits,transparentIndex};
  }

  function writeLzw(out, indices, pixels) {
    out.byte(8); // GIF minimum code size
    const CLEAR = 256, END = 257;
    let dict = new Map(), next = 258, width = 9;
    let bits = 0, bitCount = 0, count = 0;
    const chunk = new Uint8Array(255);
    function flush() { if (count) { out.byte(count); for (let i = 0; i < count; i++) out.byte(chunk[i]); count = 0; } }
    function byte(value) { chunk[count++] = value; if (count === 255) flush(); }
    function code(value) {
      bits |= value << bitCount; bitCount += width;
      while (bitCount >= 8) { byte(bits & 255); bits >>>= 8; bitCount -= 8; }
      // The decoder creates a dictionary entry after reading the next code.
      // Its dictionary trails ours by one entry, hence widening after writing.
      if (next === (1 << width) && width < 12) width++;
    }
    code(CLEAR);
    let prefix = indices[0];
    for (let i = 1; i < pixels; i++) {
      const symbol = indices[i], key = prefix * 256 + symbol;
      const found = dict.get(key);
      if (found !== undefined) { prefix = found; continue; }
      code(prefix);
      if (next < 4096) dict.set(key, next++);
      else { code(CLEAR); dict = new Map(); next = 258; width = 9; }
      prefix = symbol;
    }
    code(prefix); code(END);
    if (bitCount) byte(bits & 255);
    flush(); out.byte(0);
  }

  function createEncoder(width, height, loopCount = 0) {
    if(!Number.isInteger(loopCount)||loopCount<0||loopCount>65536)throw new Error('Invalid loop count.');
    if (!Number.isInteger(width) || !Number.isInteger(height) || width < 1 || height < 1 || width > 65535 || height > 65535 || width * height > MAX_PIXELS)
      throw new Error('Invalid GIF dimensions.');
    const pixels = width * height, out = new Bytes();
    let count = 0, elapsedMs = 0, emittedTicks = 0, finished = false;
    out.ascii('GIF89a'); out.word(width); out.word(height); out.byte(112); out.byte(0); out.byte(0);
    if(loopCount!==1){out.byte(33); out.byte(255); out.byte(11); out.ascii('NETSCAPE2.0');
    out.byte(3); out.byte(1); out.word(loopCount===0?0:loopCount-1); out.byte(0);}
    return Object.freeze({
      addFrame(frame, delayMs = 100) {
        if (finished) throw new Error('The GIF encoder is already finished.');
        if (!frame || !frame.data || frame.data.length !== pixels * 4) throw new Error('All frames must have the same dimensions.');
        if (!Number.isFinite(delayMs) || delayMs < 0 || delayMs > 655350) throw new Error('Invalid frame duration.');
        if (count >= 900 || pixels * (count + 1) > 512000000) throw new Error('The GIF is too large. Reduce the resolution or frame count.');
        // GIF timing is in hundredths. Carry rounding across frames: 30 FPS
        // becomes 3,4,3 ticks instead of shortening every frame to 3 ticks.
        elapsedMs += delayMs;
        const ticks = Math.max(1, Math.min(65535, Math.round(elapsedMs / 10) - emittedTicks));
        emittedTicks += ticks;
        const indexed=indexedFrame(frame.data,pixels);
        // Full composited frames clear to transparency before the next frame.
        // This prevents trails when an object moves or a transparent area changes.
        out.byte(33); out.byte(249); out.byte(4); out.byte(8|(indexed.transparentIndex>=0?1:0)); out.word(ticks); out.byte(Math.max(0,indexed.transparentIndex)); out.byte(0);
        out.byte(44); out.word(0); out.word(0); out.word(width); out.word(height); out.byte(128|(indexed.bits-1));
        for(let i=0;i<(1<<indexed.bits);i++){const c=indexed.palette[i]||[0,0,0];out.byte(c[0]);out.byte(c[1]);out.byte(c[2]);}
        writeLzw(out, indexed.indices, pixels); count++;
      },
      finish() {
        if (finished) throw new Error('The GIF encoder is already finished.');
        if (!count) throw new Error('A GIF needs at least one frame.');
        finished = true; out.byte(59); return out.finish();
      }
    });
  }
  function encode(frames, width, height, delayMs) {
    if (!Array.isArray(frames) || !frames.length) throw new Error('A GIF needs at least one frame.');
    if (Array.isArray(delayMs) && delayMs.length !== frames.length) throw new Error('Frame timing values are missing.');
    const encoder = createEncoder(width, height);
    frames.forEach((frame, i) => encoder.addFrame(frame, delayMs === undefined ? 100 : Array.isArray(delayMs) ? delayMs[i] : delayMs));
    return encoder.finish();
  }
  root.GifCodec = Object.freeze({ encode, createEncoder });
})(typeof window !== 'undefined' ? window : globalThis);
