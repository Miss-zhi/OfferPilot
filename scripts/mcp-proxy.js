/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */

/**
 * MCP web-search 代理服务器
 *
 * 问题背景：open-web-search v1.2.0 的 notifications/initialized 响应返回
 * Content-Type: text/plain，而 AgentScope 的 MCP SDK (0.17.0) 严格只接受
 * application/json 或 text/event-stream，导致初始化失败。
 *
 * 此代理将所有 text/plain 响应头的 Content-Type 替换为 application/json。
 */

const http = require('http');

const TARGET_HOST = 'localhost';
const TARGET_PORT = 3002;
const PROXY_PORT = 3003;

const server = http.createServer((clientReq, clientRes) => {
  const options = {
    hostname: TARGET_HOST,
    port: TARGET_PORT,
    path: clientReq.url,
    method: clientReq.method,
    headers: { ...clientReq.headers },
  };

  // 确保接受双 Accept 头
  options.headers['accept'] = 'application/json, text/event-stream';
  // 移除 hop-by-hop headers
  delete options.headers['host'];
  delete options.headers['connection'];

  const proxyReq = http.request(options, (proxyRes) => {
    // 修复 Content-Type: text/plain → application/json
    const headers = { ...proxyRes.headers };
    const ct = (headers['content-type'] || '').toLowerCase();
    if (ct.startsWith('text/plain')) {
      headers['content-type'] = 'application/json; charset=utf-8';
      console.log(`[mcp-proxy] Fixed Content-Type for ${clientReq.method} ${clientReq.url}: text/plain → application/json`);
    }

    clientRes.writeHead(proxyRes.statusCode, headers);
    proxyRes.pipe(clientRes);
  });

  proxyReq.on('error', (err) => {
    console.error(`[mcp-proxy] Error proxying ${clientReq.method} ${clientReq.url}:`, err.message);
    clientRes.writeHead(502);
    clientRes.end('Proxy error');
  });

  clientReq.pipe(proxyReq);
});

server.listen(PROXY_PORT, () => {
  console.log(`[mcp-proxy] Listening on port ${PROXY_PORT}, proxying to ${TARGET_HOST}:${TARGET_PORT}`);
});
