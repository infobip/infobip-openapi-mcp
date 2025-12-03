package com.infobip.openapi.mcp.auth.scope;

import com.infobip.openapi.mcp.auth.ScopeProperties;
import java.util.*;

public class MinimalSetCalculator {

    public Set<String> calculateMinimalScopes(
            List<List<String>> discoveredScopes, ScopeProperties.ScopeAlgorithm algorithm) {
        var filteredScopes = discoveredScopes.stream()
                .filter(scopes -> !scopes.isEmpty())
                .map(Set::copyOf)
                .toList();

        return switch (algorithm) {
            case NONE -> Set.of();
            case GREEDY -> greedyImplementation(filteredScopes);
        };
    }

    private Set<String> greedyImplementation(List<Set<String>> scopeSets) {
        var selectedScopes = new HashSet<String>();

        // Pre-compute reverse mapping with mutable sets for efficient removal
        var scopeToCoverage = new HashMap<String, Set<Integer>>();
        for (int i = 0; i < scopeSets.size(); i++) {
            for (var scope : scopeSets.get(i)) {
                scopeToCoverage.computeIfAbsent(scope, k -> new HashSet<>()).add(i);
            }
        }

        var uncoveredOps = new BitSet(scopeSets.size());
        uncoveredOps.set(0, scopeSets.size());

        var remainingScopes = new HashSet<>(scopeToCoverage.keySet());

        while (!uncoveredOps.isEmpty() && !remainingScopes.isEmpty()) {
            String bestScope = null;
            var bestCoverage = 0;

            for (var iterator = remainingScopes.iterator(); iterator.hasNext(); ) {
                var scope = iterator.next();
                var coverage = scopeToCoverage.get(scope);

                // Count only uncovered operations using BitSet intersection
                int actualCoverage = 0;
                for (var op : coverage) {
                    if (uncoveredOps.get(op)) {
                        actualCoverage++;
                    }
                }

                // Remove exhausted scopes
                if (actualCoverage == 0) {
                    iterator.remove();
                    continue;
                }

                if (actualCoverage > bestCoverage) {
                    bestCoverage = actualCoverage;
                    bestScope = scope;
                }
            }

            if (bestScope == null) {
                break;
            }

            selectedScopes.add(bestScope);
            for (var op : scopeToCoverage.get(bestScope)) {
                uncoveredOps.clear(op);
            }
            remainingScopes.remove(bestScope);
        }

        return selectedScopes;
    }
}
