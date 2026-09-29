function indexGenieAttachments(attachments = []) {
    const indexed = { queries: [], visualizations: [], reports: [], texts: [], notes: [], subQuestions: [] };
    for (const attachment of attachments) {
        if (attachment.query) indexed.queries.push(attachment.query);
        if (attachment.viz) indexed.visualizations.push(attachment.viz);
        if (attachment.deep_research_report) indexed.reports.push(attachment.deep_research_report);
        if (attachment.text) indexed.texts.push(attachment.text);
        if (attachment.research_thought) indexed.notes.push(attachment.research_thought);
        if (attachment.sub_question) indexed.subQuestions.push(attachment.sub_question);
    }
    return indexed;
}

function cleanFallbackMarkdown(markdown = '') {
    return String(markdown)
        .split('\n')
        .filter(line => !line.trim().startsWith(':embedVisualization['))
        .join('\n')
        .replace(/\n{3,}/g, '\n\n')
        .trim();
}

function matchVizAttachmentByEmbedId(embedId, visualizations = []) {
    const suffix = String(embedId || '').replace(/^viz_/, '');
    return visualizations.find(viz => String(viz.asset_id || '').endsWith(suffix)) || null;
}

module.exports = {
    indexGenieAttachments,
    cleanFallbackMarkdown,
    matchVizAttachmentByEmbedId
};
