// A small PNG reader and writer for the HUD generator: 8 bit, not interlaced, colour types 0 (grey), 2 (RGB), 3 (palette with tRNS),
// 4 (grey + alpha) and 6 (RGBA), which covers every file under the client's Interface folder that the HUD uses. Images are
// { width, height, pixels } with pixels a Buffer of RGBA bytes. The writer is deterministic (fixed zlib level, no ancillary chunks), so a
// regenerated file is byte identical.
const fs = require('fs');
const zlib = require('zlib');

function crc32(buf) {
    let crc = 0xffffffff;
    for (let n = 0; n < buf.length; n++) {
        let c = (crc ^ buf[n]) & 0xff;
        for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
        crc = (crc >>> 8) ^ c;
    }
    return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(body));
    return Buffer.concat([len, body, crc]);
}

function readPng(bufferOrFile) {
    const buf = Buffer.isBuffer(bufferOrFile) ? bufferOrFile : fs.readFileSync(bufferOrFile);
    let pos = 8;
    let width = 0, height = 0, depth = 0, ctype = 0, interlace = 0;
    let palette = null, trns = null;
    const data = [];
    while (pos < buf.length) {
        const len = buf.readUInt32BE(pos);
        const type = buf.toString('ascii', pos + 4, pos + 8);
        const body = buf.subarray(pos + 8, pos + 8 + len);
        if (type === 'IHDR') {
            width = body.readUInt32BE(0); height = body.readUInt32BE(4); depth = body[8]; ctype = body[9]; interlace = body[12];
        } else if (type === 'PLTE') palette = body;
        else if (type === 'tRNS') trns = body;
        else if (type === 'IDAT') data.push(body);
        else if (type === 'IEND') break;
        pos += 12 + len;
    }
    if (depth !== 8 || interlace !== 0) throw new Error('only 8 bit, not interlaced PNG is supported');
    const channels = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[ctype];
    if (!channels) throw new Error('unsupported colour type ' + ctype);
    const raw = zlib.inflateSync(Buffer.concat(data));
    const stride = width * channels;
    const rows = Buffer.alloc(height * stride);
    for (let y = 0; y < height; y++) {
        const filter = raw[y * (stride + 1)];
        const src = y * (stride + 1) + 1;
        for (let x = 0; x < stride; x++) {
            const a = x >= channels ? rows[y * stride + x - channels] : 0;
            const b = y > 0 ? rows[(y - 1) * stride + x] : 0;
            const c = x >= channels && y > 0 ? rows[(y - 1) * stride + x - channels] : 0;
            let v = raw[src + x];
            if (filter === 1) v += a;
            else if (filter === 2) v += b;
            else if (filter === 3) v += (a + b) >> 1;
            else if (filter === 4) {
                const p = a + b - c;
                const pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
                v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
            } else if (filter !== 0) throw new Error('bad filter ' + filter);
            rows[y * stride + x] = v & 0xff;
        }
    }
    const pixels = Buffer.alloc(width * height * 4);
    for (let i = 0; i < width * height; i++) {
        let r, g, b, a = 255;
        if (ctype === 6) { r = rows[i * 4]; g = rows[i * 4 + 1]; b = rows[i * 4 + 2]; a = rows[i * 4 + 3]; }
        else if (ctype === 2) { r = rows[i * 3]; g = rows[i * 3 + 1]; b = rows[i * 3 + 2]; }
        else if (ctype === 0) { r = g = b = rows[i]; }
        else if (ctype === 4) { r = g = b = rows[i * 2]; a = rows[i * 2 + 1]; }
        else {
            const index = rows[i];
            r = palette[index * 3]; g = palette[index * 3 + 1]; b = palette[index * 3 + 2];
            a = trns && index < trns.length ? trns[index] : 255;
        }
        pixels[i * 4] = r; pixels[i * 4 + 1] = g; pixels[i * 4 + 2] = b; pixels[i * 4 + 3] = a;
    }
    return { width, height, pixels };
}

function encodePng(image) {
    const stride = image.width * 4;
    const raw = Buffer.alloc(image.height * (stride + 1));
    for (let y = 0; y < image.height; y++) {
        raw[y * (stride + 1)] = 0;
        image.pixels.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride);
    }
    const header = Buffer.alloc(13);
    header.writeUInt32BE(image.width, 0);
    header.writeUInt32BE(image.height, 4);
    header[8] = 8; header[9] = 6;
    return Buffer.concat([
        Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
        chunk('IHDR', header),
        chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
        chunk('IEND', Buffer.alloc(0)),
    ]);
}

function writePng(file, image) {
    fs.mkdirSync(require('path').dirname(file), { recursive: true });
    fs.writeFileSync(file, encodePng(image));
}

module.exports = { readPng, encodePng, writePng };
