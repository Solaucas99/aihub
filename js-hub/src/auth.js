require('dotenv').config();

module.exports = { openAIAuth };

function requiredEnv(name) {
    const value = process.env[name];
    if (!value) {
        throw new Error(`${name} is not set in environment variables.`);
    }

    return value;
}

function openAIAuth() {
    return requiredEnv('OPENAI_API_KEY');
}
