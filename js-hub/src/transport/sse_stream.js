function createSseStream(res, options = {}) {
    const heartbeatMs = resolveHeartbeatMs(options.heartbeatMs);

    res.writeHead(200, {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        Connection: 'keep-alive'
    });
    res.flushHeaders();

    const send = (event, data) => {
        if (!res.destroyed && !res.writableEnded) {
            res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
        }
    };

    const heartbeat = setInterval(() => {
        send('heartbeat', { at: new Date().toISOString() });
    }, heartbeatMs);

    return {
        send,
        close() {
            clearInterval(heartbeat);
            if (!res.destroyed && !res.writableEnded) res.end();
        }
    };
}

function resolveHeartbeatMs(value = process.env.SSE_HEARTBEAT_MS) {
    const heartbeatMs = Number(value || 15000);
    return Number.isFinite(heartbeatMs) && heartbeatMs > 0 ? heartbeatMs : 15000;
}

module.exports = { createSseStream };
