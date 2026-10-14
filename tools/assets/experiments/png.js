// EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments").
//
// A minimal PNG reader and writer for 8 bit RGBA images that are not interlaced (what the Scrap flamethrower texture is).
const fs = require('fs');
const zlib = require('zlib');

function readPng(file) {
    const buf = fs.readFileSync(file);
    let pos = 8;
    let width = 0, height = 0;
    const data = [];
    while (pos < buf.length) {
        const length = buf.readUInt32BE(pos);
        const type = buf.toString('ascii', pos + 4, pos + 8);
        if (type === 'IHDR') {
            width = buf.readUInt32BE(pos + 8);
            height = buf.readUInt32BE(pos + 12);
            if (buf[pos + 16] !== 8 || buf[pos + 17] !== 6 || buf[pos + 20] !== 0) {
                throw new Error('only 8 bit RGBA, not interlaced');
            }
        } else if (type === 'IDAT') {
            data.push(buf.subarray(pos + 8, pos + 8 + length));
        }
        pos += 12 + length;
    }
    const raw = zlib.inflateSync(Buffer.concat(data));
    const stride = width * 4;
    const out = Buffer.alloc(height * stride);
    for (let y = 0; y < height; y++) {
        const filter = raw[y * (stride + 1)];
        for (let i = 0; i < stride; i++) {
            const x = raw[y * (stride + 1) + 1 + i];
            const a = i >= 4 ? out[y * stride + i - 4] : 0;
            const b = y > 0 ? out[(y - 1) * stride + i] : 0;
            const c = i >= 4 && y > 0 ? out[(y - 1) * stride + i - 4] : 0;
            let v;
            switch (filter) {
                case 0: v = x; break;
                case 1: v = x + a; break;
                case 2: v = x + b; break;
                case 3: v = x + ((a + b) >> 1); break;
                default: {
                    const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
                    v = x + (pa <= pb && pa <= pc ? a : pb <= pc ? b : c);
                }
            }
            out[y * stride + i] = v & 255;
        }
    }
    return {
        width,
        height,
        pixels: out,
        at(x, y) {
            if (x < 0 || y < 0 || x >= width || y >= height) {
                return null;
            }
            const o = (y * width + x) * 4;
            return [out[o], out[o + 1], out[o + 2], out[o + 3]];
        },
        set(x, y, p) {
            const o = (y * width + x) * 4;
            out[o] = p[0]; out[o + 1] = p[1]; out[o + 2] = p[2]; out[o + 3] = p[3];
        },
    };
}

const CRC_TABLE = (() => {
    const table = new Int32Array(256);
    for (let n = 0; n < 256; n++) {
        let c = n;
        for (let k = 0; k < 8; k++) {
            c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
        }
        table[n] = c;
    }
    return table;
})();

function crc32(buf) {
    let c = -1;
    for (const byte of buf) {
        c = CRC_TABLE[(c ^ byte) & 255] ^ (c >>> 8);
    }
    return (c ^ -1) >>> 0;
}

function chunk(type, data) {
    const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
    const out = Buffer.alloc(body.length + 8);
    out.writeUInt32BE(data.length, 0);
    body.copy(out, 4);
    out.writeUInt32BE(crc32(body), body.length + 4);
    return out;
}

function writePng(file, image, comment) {
    const stride = image.width * 4;
    const raw = Buffer.alloc(image.height * (stride + 1));
    for (let y = 0; y < image.height; y++) {
        raw[y * (stride + 1)] = 0; // filter: none
        image.pixels.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride);
    }
    const header = Buffer.alloc(13);
    header.writeUInt32BE(image.width, 0);
    header.writeUInt32BE(image.height, 4);
    header[8] = 8; header[9] = 6;
    fs.writeFileSync(file, Buffer.concat([
        Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
        chunk('IHDR', header),
        ...(comment ? [chunk('tEXt', Buffer.from('Comment' + String.fromCharCode(0) + comment, 'latin1'))] : []),
        chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
        chunk('IEND', Buffer.alloc(0)),
    ]));
}

module.exports = { readPng, writePng };
