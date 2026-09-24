// Local development only.
//
// The website's dev server (`yarn dev` in irons-grotto-1/apps/web) runs on
// https://localhost:3000 with a self-signed certificate, which RuneLite's Java
// does not trust. This relays plain HTTP on :3001 to it, so the plugin can use
// Server URL = http://localhost:3001.
//
//   node scripts/dev-relay.mjs
import http from 'node:http';
import https from 'node:https';

const port = Number(process.env.RELAY_PORT ?? 3001);

http
  .createServer((req, res) => {
    const upstream = https.request(
      {
        host: 'localhost',
        port: 3000,
        path: req.url,
        method: req.method,
        headers: { ...req.headers, host: 'localhost:3000' },
        rejectUnauthorized: false,
      },
      (up) => {
        res.writeHead(up.statusCode ?? 502, up.headers);
        up.pipe(res);
      },
    );
    upstream.on('error', (err) => {
      res.writeHead(502, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: String(err) }));
    });
    req.pipe(upstream);
    console.log(new Date().toISOString(), req.method, req.url);
  })
  .listen(port, () =>
    console.log(`relay on http://localhost:${port} → https://localhost:3000`),
  );
