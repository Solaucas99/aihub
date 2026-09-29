const { runAgent, structuredOutput } = require('../../openai');

const VEGA_FORMAT = structuredOutput('vega_lite_conversion', {
    type: 'object',
    additionalProperties: false,
    required: ['spec_json'],
    properties: {
        spec_json: {
            type: 'string',
            description: 'JSON string contendo uma especificacao Vega-Lite v5 sem data.values.'
        }
    }
});

const DEFAULT_MODEL = 'gpt-5.1';

function parseJsonField(value, label) {
    if (!value) return null;
    if (typeof value === 'object') return value;
    try {
        return JSON.parse(value);
    } catch {
        throw new Error(`${label} nao contem JSON valido.`);
    }
}

function conversionModel() {
    return process.env.GENIE_VIZ_CONVERSION_MODEL || DEFAULT_MODEL;
}

function conversionRetries() {
    const value = Number(process.env.GENIE_VIZ_CONVERSION_RETRIES || 2);
    return Number.isSafeInteger(value) && value >= 0 ? value : 2;
}

function collectEncodingFields(spec) {
    const fields = new Set();
    const visit = value => {
        if (!value || typeof value !== 'object') return;
        if (typeof value.field === 'string') fields.add(value.field);
        if (typeof value.fieldName === 'string') fields.add(value.fieldName);
        for (const child of Object.values(value)) visit(child);
    };
    visit(spec.encoding);
    return fields;
}

function rejectNullEncodingValues(spec) {
    const visit = (value, path = 'spec') => {
        if (!value || typeof value !== 'object') return;
        for (const [key, child] of Object.entries(value)) {
            const childPath = `${path}.${key}`;
            if (child === null && ['field', 'type', 'value', 'aggregate', 'sort'].includes(key)) {
                throw new Error(`Spec Vega-Lite contem valor nulo em ${childPath}.`);
            }
            visit(child, childPath);
        }
    };
    visit(spec.encoding || {}, 'encoding');
}

function validateComposition(spec) {
    const compositionKeys = ['layer', 'hconcat', 'vconcat', 'concat', 'facet', 'repeat'];
    const used = compositionKeys.filter(key => spec[key] !== undefined);
    if (used.length > 1) {
        throw new Error(`Spec Vega-Lite usa composicoes conflitantes: ${used.join(', ')}.`);
    }
    if (spec.layer && spec.mark) {
        throw new Error('Spec Vega-Lite com layer nao deve conter mark no nivel raiz.');
    }
    if (spec.layer && !Array.isArray(spec.layer)) {
        throw new Error('Spec Vega-Lite layer precisa ser array.');
    }
    if (Array.isArray(spec.layer) && spec.layer.some(layer => !layer.mark && !layer.layer)) {
        throw new Error('Cada camada Vega-Lite precisa conter mark ou layer.');
    }
}

function validateVegaLiteSpec(spec, columns = []) {
    if (!spec || typeof spec !== 'object' || Array.isArray(spec)) {
        throw new Error('Spec Vega-Lite precisa ser um objeto.');
    }
    validateComposition(spec);
    if (!spec.encoding && !spec.layer) {
        throw new Error('Spec Vega-Lite precisa conter encoding ou layer.');
    }
    if (!spec.layer && !spec.mark) {
        throw new Error('Spec Vega-Lite simples precisa conter mark.');
    }
    if (spec.data?.url) {
        throw new Error('Spec Vega-Lite nao pode conter data.url.');
    }
    rejectNullEncodingValues(spec);
    const allowedFields = new Set(columns.map(column => column.name));
    for (const field of collectEncodingFields(spec)) {
        if (allowedFields.size && !allowedFields.has(field)) {
            throw new Error(`Spec Vega-Lite referencia campo inexistente: ${field}`);
        }
    }
    return spec;
}

function injectVegaLiteValues(spec, values) {
    return {
        $schema: 'https://vega.github.io/schema/vega-lite/v5.json',
        ...spec,
        data: { values }
    };
}

async function convertHeliosToVegaLite({ definition, chartType, title, columns = [], sampleRows = [] }) {
    const parsedDefinition = parseJsonField(definition, 'definition');
    const renderSpec = parsedDefinition?.renderSpec || parsedDefinition;
    let lastError = null;
    for (let attempt = 0; attempt <= conversionRetries(); attempt++) {
        try {
            const spec = await requestVegaLiteSpec({
                title,
                chartType,
                columns,
                sampleRows,
                renderSpec,
                lastError
            });
            return validateVegaLiteSpec(spec, columns);
        } catch (error) {
            lastError = error;
        }
    }
    throw lastError;
}

async function requestVegaLiteSpec({ title, chartType, columns = [], sampleRows = [], renderSpec, lastError = null }) {
    const content = await runAgent({
        model: conversionModel(),
        responseFormat: VEGA_FORMAT,
        prompt: [
            'Converta uma especificacao de grafico HELIOS do Databricks para Vega-Lite v5.',
            'Retorne somente JSON no schema solicitado.',
            'O campo spec_json deve ser uma string JSON valida contendo o objeto Vega-Lite.',
            'Use JSON compacto em spec_json e escape corretamente aspas, quebras de linha e barras invertidas.',
            'Preserve titulo, tipo de grafico, eixos, series, labels e formatos quando possivel.',
            'Use somente os campos listados em columns.',
            'Se usar layer, nao inclua mark no nivel raiz; cada item de layer deve ter seu proprio mark.',
            'Nunca retorne valores null dentro de encoding, condition, field, type, value, aggregate ou sort.',
            'Nao crie dados, URLs, data.url, transforms que dependam de campos inexistentes, nem valores ficticios.',
            'Nao inclua data.values; o backend vai inserir os dados depois.',
            lastError ? `A tentativa anterior falhou com: ${lastError.message}. Corrija somente o spec_json.` : ''
        ].filter(Boolean).join('\n'),
        messages: [{
            role: 'user',
            content: JSON.stringify({
                title,
                chart_type: chartType,
                columns: columns.map(column => ({ name: column.name, type: column.type })),
                sample_rows: sampleRows.slice(0, 5),
                helios_render_spec: renderSpec
            })
        }]
    });
    const parsed = JSON.parse(content);
    return parseJsonField(parsed.spec_json, 'spec_json');
}

module.exports = {
    convertHeliosToVegaLite,
    validateVegaLiteSpec,
    injectVegaLiteValues,
    parseJsonField
};
