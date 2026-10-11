package com.parth.orderflow.demo;

import java.time.Instant;

public record DemoStatusResponse(Instant expiresAt, long idleTtlMinutes) {
}
