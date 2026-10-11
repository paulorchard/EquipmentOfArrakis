// Shared by make_fizzle.js and make_cutter_ray_sounds.js: cuts an Ogg Vorbis clip at packet level, without re-encoding, and reads a file out of
// the game's Assets.zip. The vanilla clips are never stored in the repo; only the cut results are.
//
// An Ogg Vorbis file is pages of packets: three header packets (identification, comment, setup) and then audio packets. An audio packet
// holds a short or a long block (the sizes are in the identification header; which of the two is given by its "mode", whose table sits at
// the very end of the setup header). Block n adds prev/4 + cur/4 samples to the decoded length; the first one adds none. So the length
// at each packet can be had without decoding. The cut keeps the headers and the audio packets up to the first one that brings the
// decoded length to the target (seconds x the file's own sample rate, rounded) or more, writes them to new pages, and gives the last
// page the end-of-stream flag and the granule position = target, which makes the decoder drop everything after it. Page sequence
// numbers and CRCs are redone. The tail is not faded (that would need a re-encode): the cut is abrupt, and may click.
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

/** The game's Assets.zip: the argument if given, else the install under %APPDATA%. */
function assetsZip(arg) {
    return arg || path.join(process.env.APPDATA || '', 'Hytale', 'install', 'release', 'package', 'game', 'latest', 'Assets.zip');
}


// ---- reading one entry out of the zip (central directory at the end; no zip64 needed under 4 GB) ----
function readZipEntry(file, name) {
    const fd = fs.openSync(file, 'r');
    try {
        const size = fs.fstatSync(fd).size;
        const tailLen = Math.min(size, 70000);
        const tail = Buffer.alloc(tailLen);
        fs.readSync(fd, tail, 0, tailLen, size - tailLen);
        let eocd = tail.lastIndexOf(Buffer.from([0x50, 0x4b, 0x05, 0x06]));
        if (eocd < 0) throw new Error('no end-of-central-directory record');
        const count = tail.readUInt16LE(eocd + 10);
        const cdSize = tail.readUInt32LE(eocd + 12);
        const cdOff = tail.readUInt32LE(eocd + 16);
        if (cdOff === 0xffffffff || count === 0xffff) throw new Error('zip64 archive: not supported by this script');
        const cd = Buffer.alloc(cdSize);
        fs.readSync(fd, cd, 0, cdSize, cdOff);
        let p = 0;
        while (p < cdSize) {
            if (cd.readUInt32LE(p) !== 0x02014b50) throw new Error('bad central directory');
            const method = cd.readUInt16LE(p + 10);
            const csize = cd.readUInt32LE(p + 20);
            const nlen = cd.readUInt16LE(p + 28), elen = cd.readUInt16LE(p + 30), clen = cd.readUInt16LE(p + 32);
            const lho = cd.readUInt32LE(p + 42);
            const n = cd.toString('utf8', p + 46, p + 46 + nlen);
            if (n === name) {
                const lh = Buffer.alloc(30);
                fs.readSync(fd, lh, 0, 30, lho);
                const dataOff = lho + 30 + lh.readUInt16LE(26) + lh.readUInt16LE(28);
                const raw = Buffer.alloc(csize);
                fs.readSync(fd, raw, 0, csize, dataOff);
                if (method === 0) return raw;
                if (method === 8) return zlib.inflateRawSync(raw);
                throw new Error('unsupported compression ' + method);
            }
            p += 46 + nlen + elen + clen;
        }
        throw new Error(name + ' not in ' + file);
    } finally {
        fs.closeSync(fd);
    }
}

// ---- Ogg ----
let crcTable = null;
function crc32(buf) {
    if (!crcTable) {
        crcTable = new Uint32Array(256);
        for (let i = 0; i < 256; i++) {
            let r = i << 24;
            for (let k = 0; k < 8; k++) r = (r & 0x80000000) ? ((r << 1) ^ 0x04c11db7) : (r << 1);
            crcTable[i] = r >>> 0;
        }
    }
    let c = 0;
    for (let i = 0; i < buf.length; i++) c = ((c << 8) ^ crcTable[((c >>> 24) ^ buf[i]) & 0xff]) >>> 0;
    return c >>> 0;
}

/** Pages of a file: {flags, granule (BigInt), serial, seq, lacing[], data, crcOk}. */
function parsePages(buf) {
    const pages = [];
    let p = 0;
    while (p < buf.length) {
        if (buf.toString('latin1', p, p + 4) !== 'OggS') throw new Error('no OggS at ' + p);
        const nseg = buf[p + 26];
        const lacing = Array.from(buf.subarray(p + 27, p + 27 + nseg));
        const len = lacing.reduce((a, b) => a + b, 0);
        const headerLen = 27 + nseg;
        const whole = Buffer.from(buf.subarray(p, p + headerLen + len));
        const stored = whole.readUInt32LE(22);
        whole.writeUInt32LE(0, 22);
        pages.push({
            flags: buf[p + 5], granule: buf.readBigInt64LE(p + 6), serial: buf.readUInt32LE(p + 14), seq: buf.readUInt32LE(p + 18),
            lacing, data: Buffer.from(buf.subarray(p + headerLen, p + headerLen + len)), crcOk: crc32(whole) === stored,
        });
        p += headerLen + len;
    }
    return pages;
}

function writePage(flags, granule, serial, seq, lacing, data) {
    const out = Buffer.alloc(27 + lacing.length + data.length);
    out.write('OggS', 0, 'latin1');
    out[4] = 0;
    out[5] = flags;
    out.writeBigInt64LE(BigInt(granule), 6);
    out.writeUInt32LE(serial, 14);
    out.writeUInt32LE(seq, 18);
    out[26] = lacing.length;
    Buffer.from(lacing).copy(out, 27);
    data.copy(out, 27 + lacing.length);
    out.writeUInt32LE(crc32(out), 22);
    return out;
}

/** Packets with the index of the page each one ends on. */
function packetsOf(pages) {
    const packets = [];
    let cur = [];
    pages.forEach((pg, pi) => {
        let off = 0, run = 0;
        for (const l of pg.lacing) {
            run += l;
            if (l < 255) {
                cur.push(pg.data.subarray(off, off + run));
                packets.push({ data: Buffer.concat(cur), endPage: pi });
                cur = [];
                off += run;
                run = 0;
            }
        }
        if (run > 0) cur.push(pg.data.subarray(off, off + run));
    });
    if (cur.length) throw new Error('last packet is not complete');
    return packets;
}

// ---- Vorbis ----
class Bits {
    constructor(buf) { this.buf = buf; }
    bit(pos) { return (this.buf[pos >> 3] >> (pos & 7)) & 1; }
    read(pos, n) { let v = 0; for (let i = 0; i < n; i++) v |= this.bit(pos + i) << i; return v; }
}

/** The mode table of the setup header: the blockflag of each mode, found by reading back from the framing bit at the end (the largest count whose zero fields all check out). */
function readModes(setup) {
    const b = new Bits(setup);
    let last = setup.length - 1;
    if (setup[last] === 0) throw new Error('setup header ends in a zero byte');
    const framingPos = last * 8 + (31 - Math.clz32(setup[last]));
    for (let n = 64; n >= 1; n--) {
        const start = framingPos - 41 * n; // each mode: blockflag 1, windowtype 16, transformtype 16, mapping 8
        if (start - 6 < 0) continue;
        if (b.read(start - 6, 6) + 1 !== n) continue;
        const modes = [];
        let ok = true;
        for (let m = 0; m < n && ok; m++) {
            const at = start + 41 * m;
            if (b.read(at + 1, 16) !== 0 || b.read(at + 17, 16) !== 0) ok = false;
            modes.push(b.read(at, 1));
        }
        if (ok) return modes;
    }
    throw new Error('mode table not found');
}

function ilog(x) { let n = 0; while (x > 0) { n++; x >>= 1; } return n; }

/** Cuts an Ogg Vorbis file to `seconds` from its start, without re-encoding. Returns {buffer, target, rate}. */
function trimOgg(source, seconds, log = console.log) {
    const pages = parsePages(source);
    if (!pages.every(p => p.crcOk)) throw new Error('the source has a bad page CRC');
    if (new Set(pages.map(p => p.serial)).size !== 1) throw new Error('more than one stream');
    const packets = packetsOf(pages);
    const [ident, comment, setup] = packets;
    if (ident.data[0] !== 1 || ident.data.toString('latin1', 1, 7) !== 'vorbis') throw new Error('no identification header');
    if (setup.data[0] !== 5) throw new Error('no setup header');
    const rate = ident.data.readUInt32LE(12);
    const bs = ident.data[28];
    const short = 1 << (bs & 15), long = 1 << (bs >> 4);
    const modes = readModes(setup.data);
    const modeBits = ilog(modes.length - 1);
    log(`source: ${source.length} bytes, ${pages.length} pages, ${rate} Hz, blocks ${short}/${long}, modes ${modes.join(',')}`);

    // Decoded length after each audio packet, checked against the granule positions of the pages of the source.
    let prev = -1, total = 0;
    const audio = packets.slice(3).map(pk => {
        if (pk.data[0] & 1) throw new Error('not an audio packet');
        const block = modes[(pk.data[0] >> 1) & ((1 << modeBits) - 1)] ? long : short;
        if (prev >= 0) total += prev / 4 + block / 4;
        prev = block;
        return { ...pk, total };
    });
    const pageEnd = new Map();
    audio.forEach(a => pageEnd.set(a.endPage, a.total));
    for (const [pi, t] of pageEnd) {
        const g = pages[pi].granule;
        const isLast = pi === pages.length - 1;
        if (!isLast && g !== BigInt(t)) throw new Error(`granule mismatch on page ${pi}: file ${g}, computed ${t}`);
        if (isLast && g > BigInt(t)) throw new Error(`last granule ${g} beyond computed ${t}`);
        if (isLast) log(`last page: granule ${g}, computed ${t}`);
    }
    log('granule positions of all source pages match the block sizes read from the headers');

    const TARGET = Math.round(seconds * rate);
    const keep = audio.findIndex(a => a.total >= TARGET);
    if (keep < 0) throw new Error('the clip is shorter than the cut');
    const kept = audio.slice(0, keep + 1);
    log(`keeping ${kept.length} of ${audio.length} audio packets; decoded length ${kept[keep].total}, cut at ${TARGET} (${(TARGET / rate).toFixed(4)} s)`);

    // Pages: the identification header alone, then the comment and setup headers, then the audio on the original page boundaries.
    const serial = pages[0].serial;
    const out = [];
    let seq = 0;
    const lacingOf = len => { const l = new Array(Math.floor(len / 255)).fill(255); l.push(len % 255); return l; };
    out.push(writePage(0x02, 0, serial, seq++, lacingOf(ident.data.length), ident.data));
    // comment + setup: flow their segments into pages of at most 255 lacing values
    let segs = [...lacingOf(comment.data.length), ...lacingOf(setup.data.length)];
    let blob = Buffer.concat([comment.data, setup.data]);
    let cont = false;
    while (segs.length) {
        const take = segs.slice(0, 255);
        const bytes = take.reduce((a, b) => a + b, 0);
        const rest = segs.slice(255);
        const endsPacket = take[take.length - 1] < 255;
        out.push(writePage(cont ? 0x01 : 0, rest.length === 0 || endsPacket ? 0 : -1, serial, seq++, take, blob.subarray(0, bytes)));
        blob = blob.subarray(bytes);
        segs = rest;
        cont = !endsPacket;
    }
    // audio, grouped by the original page each packet ended on
    const groups = [];
    for (const a of kept) {
        if (!groups.length || groups[groups.length - 1].page !== a.endPage) groups.push({ page: a.endPage, items: [] });
        groups[groups.length - 1].items.push(a);
    }
    groups.forEach((g, gi) => {
        const lacing = g.items.flatMap(a => lacingOf(a.data.length));
        if (lacing.length > 255) throw new Error('too many segments for one page');
        const isLast = gi === groups.length - 1;
        const granule = isLast ? TARGET : g.items[g.items.length - 1].total;
        out.push(writePage(isLast ? 0x04 : 0, granule, serial, seq++, lacing, Buffer.concat(g.items.map(a => a.data))));
    });
    const result = Buffer.concat(out);

    // Independent re-parse of the output.
    const back = parsePages(result);
    if (!back.every(p => p.crcOk)) throw new Error('bad CRC in the output');
    if (back[0].flags !== 0x02 || back[back.length - 1].flags !== 0x04) throw new Error('BOS/EOS flags');
    if (back.some((p, i) => p.seq !== i)) throw new Error('sequence numbers');
    if (back[back.length - 1].granule !== BigInt(TARGET)) throw new Error('last granule');
    for (let i = 3; i < back.length - 1; i++) {
        const same = pages.find(p => p.granule === back[i].granule);
        if (!same) throw new Error('granule of page ' + i + ' not in the source');
    }
    const bp = packetsOf(back);
    if (bp.length !== 3 + kept.length) throw new Error('packet count');
    if (!bp[0].data.equals(ident.data) || !bp[1].data.equals(comment.data) || !bp[2].data.equals(setup.data)) throw new Error('headers changed');
    log(`cut: ${result.length} bytes, ${back.length} pages, last granule ${back[back.length - 1].granule} = ${(TARGET / rate).toFixed(4)} s`);
    return { buffer: result, target: TARGET, rate };
}

module.exports = { trimOgg, readZipEntry, assetsZip, parsePages, packetsOf };
