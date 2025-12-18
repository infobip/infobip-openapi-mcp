package com.infobip.openapi.mcp.auth.scope;

import static com.infobip.openapi.mcp.auth.ScopeProperties.ScopeAlgorithm.GREEDY;
import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MinimalSetCalculatorTest {

    private final MinimalSetCalculator calculator = new MinimalSetCalculator();

    @Test
    void shouldDiscoverSimpleScopes() {
        // Given
        var givenScopes = List.of(
                List.of("scope1", "scope2"),
                List.of("scope2", "scope5"),
                List.of("scope3", "scope4"),
                List.of("scope1", "scope4"));

        // When
        var actualScopes = calculator.calculateMinimalScopes(givenScopes, GREEDY);

        // Then
        then(actualScopes).isNotNull();
        then(actualScopes).hasSize(2);
        thenAllGivenScopesShouldBeCovered(givenScopes, actualScopes);
    }

    @Test
    void shouldDiscoverManyScopesUnderTimeLimit() {
        // Given
        var givenTimeLimit = Duration.ofSeconds(10); // 10 seconds to make sure tests pass on older hardware
        var givenUpperBound = 10000;
        var givenScopes = IntStream.range(1, givenUpperBound)
                .mapToObj(i -> List.of("scope" + i, "scope" + (i + 1)))
                .collect(Collectors.toList());
        Collections.shuffle(givenScopes);

        var givenExpectedScopes = 5000;
        var givenTolerance = (int) (givenExpectedScopes * 0.1); // 10% tolerance

        // When
        var actualScopes =
                assertTimeoutPreemptively(givenTimeLimit, () -> calculator.calculateMinimalScopes(givenScopes, GREEDY));

        // Then
        then(actualScopes).isNotNull();
        then(actualScopes).hasSizeBetween(givenExpectedScopes, givenExpectedScopes + givenTolerance);
        thenAllGivenScopesShouldBeCovered(givenScopes, actualScopes);
    }

    @Test
    void shouldHandleEmptyInput() {
        // Given
        var givenScopes = List.<List<String>>of();

        // When
        var actualScopes = calculator.calculateMinimalScopes(givenScopes, GREEDY);

        // Then
        then(actualScopes).isNotNull();
        then(actualScopes).isEmpty();
    }

    @Test
    void shouldHandleSingleOperationWithMultipleScopes() {
        // Given
        var givenScopes = List.of(List.of("scope1", "scope2", "scope3"));

        // When
        var actualScopes = calculator.calculateMinimalScopes(givenScopes, GREEDY);

        // Then
        then(actualScopes).isNotNull();
        then(actualScopes).hasSize(1);
        then(actualScopes).containsAnyOf("scope1", "scope2", "scope3");
        thenAllGivenScopesShouldBeCovered(givenScopes, actualScopes);
    }

    @Test
    void shouldSelectUniversalScopeWhenPresent() {
        // Given
        var givenScopes = List.of(
                List.of("universal", "scope1"),
                List.of("universal", "scope2"),
                List.of("universal", "scope3"),
                List.of("universal", "scope4"));

        // When
        var actualScopes = calculator.calculateMinimalScopes(givenScopes, GREEDY);

        // Then
        then(actualScopes).isNotNull();
        then(actualScopes).hasSize(1);
        then(actualScopes).contains("universal");
        thenAllGivenScopesShouldBeCovered(givenScopes, actualScopes);
    }

    @Test
    void shouldHandleNoOverlappingScopes() {
        // Given
        var givenScopes = List.of(List.of("scope1"), List.of("scope2"), List.of("scope3"), List.of("scope4"));

        // When
        var actualScopes = calculator.calculateMinimalScopes(givenScopes, GREEDY);

        // Then
        then(actualScopes).isNotNull();
        then(actualScopes).hasSize(4);
        then(actualScopes).containsExactlyInAnyOrder("scope1", "scope2", "scope3", "scope4");
        thenAllGivenScopesShouldBeCovered(givenScopes, actualScopes);
    }

    @Test
    void shouldHandleCompleteOverlap() {
        // Given
        var givenScopes = List.of(
                List.of("scope1", "scope2", "scope3"),
                List.of("scope1", "scope2", "scope3"),
                List.of("scope1", "scope2", "scope3"));

        // When
        var actualScopes = calculator.calculateMinimalScopes(givenScopes, GREEDY);

        // Then
        then(actualScopes).isNotNull();
        then(actualScopes).hasSize(1);
        then(actualScopes).containsAnyOf("scope1", "scope2", "scope3");
        thenAllGivenScopesShouldBeCovered(givenScopes, actualScopes);
    }

    @Test
    void shouldHandleLargeNumberOfScopesPerOperation() {
        // Given
        var givenScopes = List.of(
                IntStream.range(1, 100).mapToObj(i -> "scope" + i).toList(),
                IntStream.range(50, 150).mapToObj(i -> "scope" + i).toList(),
                IntStream.range(100, 200).mapToObj(i -> "scope" + i).toList());

        // When
        var actualScopes = calculator.calculateMinimalScopes(givenScopes, GREEDY);

        // Then
        then(actualScopes).isNotNull();
        then(actualScopes).isNotEmpty();
        then(actualScopes).hasSize(2);
        thenAllGivenScopesShouldBeCovered(givenScopes, actualScopes);
    }

    @Test
    void shouldHandleDuplicateScopesInSameOperation() {
        // Given
        var givenScopes = List.of(
                List.of("scope1", "scope1", "scope2"),
                List.of("scope2", "scope3", "scope3"),
                List.of("scope1", "scope3"));

        // When
        var actualScopes = calculator.calculateMinimalScopes(givenScopes, GREEDY);

        // Then
        then(actualScopes).isNotNull();
        then(actualScopes).hasSize(2);
        thenAllGivenScopesShouldBeCovered(givenScopes, actualScopes);
    }

    @Test
    void shouldPreferScopesWithHigherCoverage() {
        // Given
        var givenScopes = List.of(
                List.of("common", "rare1"), List.of("common", "rare2"), List.of("common", "rare3"), List.of("rare4"));

        // When
        var actualScopes = calculator.calculateMinimalScopes(givenScopes, GREEDY);

        // Then
        then(actualScopes).isNotNull();
        then(actualScopes).hasSize(2);
        then(actualScopes).contains("common");
        thenAllGivenScopesShouldBeCovered(givenScopes, actualScopes);
    }

    @Test
    void shouldHandleDisjointGroups() {
        // Given
        var givenScopes = List.of(
                List.of("groupA1", "groupA2"),
                List.of("groupA2", "groupA3"),
                List.of("groupB1", "groupB2"),
                List.of("groupB2", "groupB3"));

        // When
        var actualScopes = calculator.calculateMinimalScopes(givenScopes, GREEDY);

        // Then
        then(actualScopes).isNotNull();
        then(actualScopes).hasSize(2);
        thenAllGivenScopesShouldBeCovered(givenScopes, actualScopes);
    }

    @Test
    void shouldHandleOperationsWithEmptyScopes() {
        // Given
        var givenScopes = List.of(List.of("scope1", "scope2"), List.<String>of(), List.of("scope3", "scope4"));

        // When
        var actualScopes = calculator.calculateMinimalScopes(givenScopes, GREEDY);

        // Then
        then(actualScopes).isNotNull();
        then(actualScopes).hasSize(2);
        thenAllGivenScopesShouldBeCovered(givenScopes, actualScopes);
    }

    @Test
    void shouldBeConsistentWithMultipleRuns() {
        // Given
        var givenScopes =
                List.of(List.of("scope1", "scope2"), List.of("scope2", "scope3"), List.of("scope3", "scope4"));

        // When
        var result1 = calculator.calculateMinimalScopes(givenScopes, GREEDY);
        var result2 = calculator.calculateMinimalScopes(givenScopes, GREEDY);
        var result3 = calculator.calculateMinimalScopes(givenScopes, GREEDY);

        // Then - all results should have same size (deterministic)
        then(result1).hasSameSizeAs(result2);
        then(result2).hasSameSizeAs(result3);
        thenAllGivenScopesShouldBeCovered(givenScopes, result1);
        thenAllGivenScopesShouldBeCovered(givenScopes, result2);
        thenAllGivenScopesShouldBeCovered(givenScopes, result3);
    }

    @Test
    void shouldHandleWorstCaseScenario() {
        // Given
        var givenScopes = List.of(List.of("a", "b", "c"), List.of("a", "d"), List.of("b", "e"), List.of("c", "f"));

        // When
        var actualScopes = calculator.calculateMinimalScopes(givenScopes, GREEDY);

        // Then
        then(actualScopes).isNotNull();
        then(actualScopes).hasSize(3);
        thenAllGivenScopesShouldBeCovered(givenScopes, actualScopes);
    }

    @Test
    void shouldHandleLargeScaleWithHighOverlap() {
        // Given
        var givenTimeLimit = Duration.ofSeconds(3);
        var givenScopes = new ArrayList<List<String>>();

        // Create 500 operations where many scopes are shared
        for (int i = 0; i < 500; i++) {
            var scopes = new ArrayList<String>();
            scopes.add("common_scope"); // Every operation has this
            scopes.add("scope_" + (i % 50)); // Groups of 10 share additional scope
            scopes.add("unique_" + i); // Each has one unique
            givenScopes.add(scopes);
        }

        // When
        var actualScopes =
                assertTimeoutPreemptively(givenTimeLimit, () -> calculator.calculateMinimalScopes(givenScopes, GREEDY));

        // Then
        then(actualScopes).isNotNull();
        then(actualScopes).contains("common_scope");
        thenAllGivenScopesShouldBeCovered(givenScopes, actualScopes);
    }

    @Test
    void shouldHandleScopesWithSpecialCharacters() {
        // Given
        var givenScopes = List.of(
                List.of("scope:read", "scope:write"),
                List.of("scope:write", "scope:delete"),
                List.of("admin:*", "scope:read"));

        // When
        var actualScopes = calculator.calculateMinimalScopes(givenScopes, GREEDY);

        // Then
        then(actualScopes).isNotNull();
        then(actualScopes).hasSize(2);
        thenAllGivenScopesShouldBeCovered(givenScopes, actualScopes);
    }

    private void thenAllGivenScopesShouldBeCovered(List<List<String>> givenScopes, Set<String> actualScopes) {
        for (var scopes : givenScopes) {
            if (scopes.isEmpty()) {
                continue;
            }
            var isCovered = scopes.stream().anyMatch(actualScopes::contains);
            then(isCovered).isTrue();
        }
    }
}
