const { getGenieQueryResult } = require('./query_result');
const { convertHeliosToVegaLite, injectVegaLiteValues } = require('./helios_to_vega');

const VIZ_ENABLED = () => String(process.env.GENIE_VIZ_ENABLED ?? 'true').toLowerCase() !== 'false';
const MAX_ROWS = () => Number(process.env.GENIE_VIZ_MAX_ROWS || 500);

async function enrichVisualization(vizArtifact, query, context, report = () => {}) {
    if (!VIZ_ENABLED()) {
        return publicVizArtifact({ ...vizArtifact, status: 'DISABLED' });
    }
    if (!query?.id) {
        return publicVizArtifact({ ...vizArtifact, status: 'DATA_UNAVAILABLE', error: 'Query associada nao encontrada.' });
    }

    try {
        await report({ status: 'RUNNING', title: 'Preparando dados do grafico', type: 'QUERY_RESULT_LOADING' });
        const result = await getGenieQueryResult({
            spaceId: context.spaceId,
            conversationId: context.conversationId,
            messageId: context.messageId,
            attachmentId: query.id
        });
        const values = result.rows.slice(0, MAX_ROWS());
        const dataPolicy = queryResultPolicy(result);

        if (vizArtifact.render_provider === 'HTML_TABLE') {
            return readyTableArtifact(vizArtifact, result, values, dataPolicy, report);
        }

        return readyVegaLiteArtifact(vizArtifact, result, values, dataPolicy, report);
    } catch (error) {
        await report({ status: 'ATTENTION', title: 'Nao consegui preparar um grafico', type: 'VISUALIZATION_FAILED' });
        return publicVizArtifact({
            ...vizArtifact,
            status: 'SPEC_CONVERSION_FAILED',
            error: error.message
        });
    }
}

function queryResultPolicy(result) {
    const maxRows = MAX_ROWS();
    return {
        max_rows: maxRows,
        truncated: result.rows.length > maxRows || result.truncated,
        original_row_count: result.row_count
    };
}

async function readyTableArtifact(vizArtifact, result, values, dataPolicy, report) {
    await report({ status: 'COMPLETED', title: 'Tabela pronta', type: 'VISUALIZATION_READY' });
    return publicVizArtifact({
        ...vizArtifact,
        status: 'READY',
        spec: null,
        table: {
            columns: result.columns.map(column => ({
                name: column.name,
                type: column.type || null,
                label: column.name
            })),
            rows: values
        },
        data_policy: dataPolicy,
        query_result: {
            columns: result.columns,
            row_count: result.row_count,
            state: result.state
        }
    });
}

async function readyVegaLiteArtifact(vizArtifact, result, values, dataPolicy, report) {
    await report({ status: 'RUNNING', title: 'Convertendo grafico', type: 'VISUALIZATION_CONVERTING' });
    const vegaSpec = await convertHeliosToVegaLite({
        definition: vizArtifact.provider_payload.definition,
        chartType: vizArtifact.visualization_type,
        title: vizArtifact.title,
        columns: result.columns,
        sampleRows: values
    });
    await report({ status: 'COMPLETED', title: 'Grafico pronto', type: 'VISUALIZATION_READY' });
    return publicVizArtifact({
        ...vizArtifact,
        status: 'READY',
        spec: injectVegaLiteValues(vegaSpec, values),
        data_policy: dataPolicy,
        query_result: {
            columns: result.columns,
            row_count: result.row_count,
            state: result.state
        }
    });
}

function publicVizArtifact(vizArtifact) {
    const { provider_payload, ...publicArtifact } = vizArtifact;
    return publicArtifact;
}

module.exports = { enrichVisualization };
