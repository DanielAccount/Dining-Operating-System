import * as QRCode from 'qrcode';

/**
 * Sanity check for the one third-party dependency in the tables screen.
 *
 * `qrcode` is a CommonJS package, so the `import * as` form (and the
 * bundler's interop of it) is exactly the path TablesComponent exercises
 * at runtime. If a bundler or dependency upgrade ever breaks that interop,
 * this fails long before a owner discovers QR images that never render.
 */
describe('QRCode library (Tables screen dependency)', () => {
  it('renders a printable PNG data URL from a table qrUrl', async () => {
    const dataUrl = await QRCode.toDataURL('http://localhost:4200/t/Gkr2ybHlxj', {
      width: 200,
      margin: 2,
    });

    expect(dataUrl.startsWith('data:image/png;base64,')).toBe(true);
  });

  it('produces a different image for a different URL', async () => {
    const a = await QRCode.toDataURL('http://localhost:4200/t/AAAAAAAAAA', { width: 64 });
    const b = await QRCode.toDataURL('http://localhost:4200/t/BBBBBBBBBB', { width: 64 });

    expect(a).not.toBe(b);
  });
});
