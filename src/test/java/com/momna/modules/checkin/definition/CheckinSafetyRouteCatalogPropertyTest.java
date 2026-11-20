package com.momna.modules.checkin.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.momna.modules.checkin.definition.CheckinSafetyRouteCatalog.ItemContribution;
import java.math.BigDecimal;
import java.util.List;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

/** Ported from the working Kotlin CheckinSafetyRouteCatalogPropertyTest. */
class CheckinSafetyRouteCatalogPropertyTest {
    private final CheckinSafetyRouteCatalog catalog = new CheckinSafetyRouteCatalog();

    @Provide
    Arbitrary<String> safetyItems() {
        return Arbitraries.of(catalog.bindings().stream().map(CheckinSafetyRouteCatalog.Binding::itemCode).toList());
    }

    @Property(tries = 500)
    void routeSelectionIsMonotoneByContributionAndDeterministicOnTies(
        @ForAll("safetyItems") String first,
        @ForAll("safetyItems") String second,
        @ForAll @IntRange(min = 1, max = 1000) int firstScore,
        @ForAll @IntRange(min = 1, max = 1000) int secondScore
    ) {
        Assume.that(!first.equals(second));
        var selection = catalog.selectRoute(List.of(
            new ItemContribution(first, BigDecimal.valueOf(firstScore)),
            new ItemContribution(second, BigDecimal.valueOf(secondScore))));
        var firstBinding = catalog.bindingForItem(first);
        var secondBinding = catalog.bindingForItem(second);
        String expected;
        if (firstScore > secondScore) expected = first;
        else if (secondScore > firstScore) expected = second;
        else if (firstBinding.displayOrder() < secondBinding.displayOrder()) expected = first;
        else if (secondBinding.displayOrder() < firstBinding.displayOrder()) expected = second;
        else expected = first.compareTo(second) <= 0 ? first : second;
        assertEquals(expected, selection.itemCode());
        assertEquals(catalog.bindingForItem(expected).routeCode(), selection.route().code());
    }
}
