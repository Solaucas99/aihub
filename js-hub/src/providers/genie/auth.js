function requiredEnv(name) {
    const value = process.env[name];
    if (!value) {
        throw new Error(`${name} is not set in environment variables.`);
    }

    return value;
}

function decodeJwtClaims(token) {
    try {
        const payload = token.split('.')[1];
        const json = Buffer.from(payload, 'base64').toString('utf8');
        const { aud, appid, tid, roles, scp } = JSON.parse(json);
        return { aud, appid, tid, roles, scp };
    } catch (err) {
        return null;
    }
}

async function genieAuth({ signal } = {}) {
    const authUrl = requiredEnv('GENIE_BASE_URL') + '/oidc/v1/token';
    const clientId = requiredEnv('GENIE_CLIENT_ID');
    const clientSecret = requiredEnv('GENIE_CLIENT_SECRET');
    const defaultBody = new URLSearchParams({
        grant_type: 'client_credentials',
        scope: 'all-apis'
    });

    const request = await fetch(authUrl, {
        method: 'POST',
        headers: {
            'Authorization': `Basic ${Buffer.from(`${clientId}:${clientSecret}`).toString('base64')}`,
            'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: defaultBody.toString(),
        signal
    });

    const jsonRequest = await request.json();
    console.log('Genie Auth Response:', {
        token_type: jsonRequest.token_type,
        expires_in: jsonRequest.expires_in,
        scope: jsonRequest.scope,
        has_access_token: Boolean(jsonRequest.access_token)
    });

    if (!request.ok) {
        throw new Error(`GENIE_AUTH_URL failed with HTTP ${request.status}: ${jsonRequest.error || jsonRequest.error_description || 'auth failed'}`);
    }

    if (jsonRequest.access_token) {
        console.log('Genie Auth Token:', JSON.stringify(decodeJwtClaims(jsonRequest.access_token)));
    }

    if (!jsonRequest.access_token) {
        throw new Error('GENIE_AUTH_URL did not return access_token.');
    }

    return jsonRequest.access_token;
}

module.exports = { genieAuth };
