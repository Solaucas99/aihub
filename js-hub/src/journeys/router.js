function createJourneyRouter(journeys, getDefaultJourneyType) {
    return function routeJourney({ journeyHint } = {}) {
        if (!journeyHint) return getDefaultJourneyType();
        if (!journeys.has(journeyHint)) {
            throw new Error(`Jornada "${journeyHint}" nao registrada.`);
        }
        return journeyHint;
    };
}

module.exports = { createJourneyRouter };
