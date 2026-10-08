package com.momna.modules.checkin.definition;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WeightedCheckinDefinitionCatalogTest {
    @Test
    void loadsFourteenDefinitionsAndNinetyNineItemsFromCanonicalBundle() {
        var catalog = new WeightedCheckinDefinitionCatalog(new ObjectMapper());
        assertEquals(14, catalog.all().size());
        assertEquals(99, catalog.all().stream().mapToInt(x -> x.items().size()).sum());
        assertTrue(catalog.all().stream().allMatch(x -> x.definitionVersion() == 3));
        assertTrue(catalog.all().stream().allMatch(x -> x.poolVersion().equals("weighted-v3")));
    }
}
