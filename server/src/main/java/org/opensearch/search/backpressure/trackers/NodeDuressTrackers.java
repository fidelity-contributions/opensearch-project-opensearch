/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.search.backpressure.trackers;

import org.opensearch.common.util.Streak;
import org.opensearch.common.util.TimeBasedExpiryTracker;
import org.opensearch.wlm.ResourceType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * NodeDuressTrackers is used to check if the node is in duress based on various resources.
 *
 * @opensearch.internal
 */
public class NodeDuressTrackers {
    private final Map<ResourceType, NodeDuressTracker> duressTrackers;
    private final Map<ResourceType, Boolean> resourceDuressCache = new ConcurrentHashMap<>();
    private final BooleanSupplier nodeDuressCacheExpiryChecker;

    public NodeDuressTrackers(Map<ResourceType, NodeDuressTracker> duressTrackers) {
        this(duressTrackers, new TimeBasedExpiryTracker(System::nanoTime));
    }

    public NodeDuressTrackers(Map<ResourceType, NodeDuressTracker> duressTrackers, BooleanSupplier nodeDuressCacheExpiryChecker) {
        this.duressTrackers = duressTrackers;
        for (ResourceType resourceType : ResourceType.values()) {
            resourceDuressCache.put(resourceType, false);
        }
        this.nodeDuressCacheExpiryChecker = nodeDuressCacheExpiryChecker;
    }

    /**
     * Method to check the {@link ResourceType} in duress
     * @return Boolean
     */
    public boolean isResourceInDuress(ResourceType resourceType) {
        updateCache();
        return resourceDuressCache.get(resourceType);
    }

    /**
     * Method to evaluate whether the node is in duress or not
     * @return true if node is in duress because of either system resource
     */
    public boolean isNodeInDuress() {
        for (ResourceType resourceType : ResourceType.values()) {
            if (isResourceInDuress(resourceType)) {
                return true;
            }
        }
        return false;
    }

    private void updateCache() {
        if (nodeDuressCacheExpiryChecker.getAsBoolean()) {
            for (ResourceType resourceType : ResourceType.values())
                resourceDuressCache.put(resourceType, duressTrackers.get(resourceType).test());
        }
    }

    /**
     * NodeDuressTracker is used to check if the node is in duress
     * @opensearch.internal
     */
    public static class NodeDuressTracker {
        /**
         * Tracks the number of consecutive breaches.
         */
        private final Streak breaches = new Streak();

        /**
         * Predicate that returns true when the node is in duress.
         */
        private final BooleanSupplier isNodeInDuress;

        /**
         * Predicate that returns the max number of breaches allowed for this resource before we mark it as in duress
         */
        private final IntSupplier maxBreachAllowedSupplier;

        public NodeDuressTracker(BooleanSupplier isNodeInDuress, IntSupplier maxBreachAllowedSupplier) {
            this.isNodeInDuress = isNodeInDuress;
            this.maxBreachAllowedSupplier = maxBreachAllowedSupplier;
        }

        /**
         * Returns true if the node is in duress consecutively for the past 'n' observations.
         */
        public boolean test() {
            return breaches.record(isNodeInDuress.getAsBoolean()) >= maxBreachAllowedSupplier.getAsInt();
        }
    }
}
