const {
    HUB_CONTRACT,
    createMarkdownOutput,
    normalizeAgentOutput
} = require('./contracts/hub_response_contract');
const {
    cloneWithPrefixedIds,
    mergeStructuredOutputs,
    validateArtifactReferences,
    extractArtifactManifest
} = require('./contracts/artifact_references');
const {
    sanitizeArtifactsForClient,
    sanitizeStructuredOutputForClient
} = require('./contracts/client_response_sanitizer');

module.exports = {
    HUB_CONTRACT,
    createMarkdownOutput,
    normalizeAgentOutput,
    cloneWithPrefixedIds,
    mergeStructuredOutputs,
    validateArtifactReferences,
    extractArtifactManifest,
    sanitizeArtifactsForClient,
    sanitizeStructuredOutputForClient
};
