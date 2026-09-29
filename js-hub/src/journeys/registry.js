const fs = require('fs');
const path = require('path');

/** @param {import('./contracts').JourneyDefinition[]} definitions
 * @param {import('./contracts').JourneyServices} services
 */
function createJourneyRegistry(definitions, services) {
    const journeys = new Map();
    for (const definition of definitions) {
        if (!definition?.type || typeof definition.create !== 'function') {
            throw new Error('Definicao de jornada precisa de type e create. Use defineJourney.');
        }
        if (journeys.has(definition.type)) throw new Error('Jornada duplicada: ' + definition.type);
        const journey = definition.create(services);
        if (journey?.type !== definition.type || typeof journey.start !== 'function' || typeof journey.resume !== 'function') {
            throw new Error('Jornada precisa de type, start e resume.');
        }
        journeys.set(journey.type, journey);
    }

    return {
        get(type) {
            const journey = journeys.get(type);
            if (!journey) throw new Error('Jornada nao registrada: ' + type);
            return journey;
        },
        has: type => journeys.has(type)
    };
}

function loadJourneyDefinitions(directory = path.join(__dirname, 'definitions')) {
    return fs.readdirSync(directory)
        .filter(file => file.endsWith('.js'))
        .map(file => require(path.join(directory, file)));
}

module.exports = { createJourneyRegistry, loadJourneyDefinitions };
