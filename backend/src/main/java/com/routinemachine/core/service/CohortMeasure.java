package com.routinemachine.core.service;

// A pilot measure with its own denominator, never a bare percentage — "each figure with its
// denominator" (docs/product-direction.md's cohort dashboard section).
public record CohortMeasure(int count, int denominator) {
}
