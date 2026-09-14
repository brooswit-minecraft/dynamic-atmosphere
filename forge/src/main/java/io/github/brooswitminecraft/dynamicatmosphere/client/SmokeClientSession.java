package io.github.brooswitminecraft.dynamicatmosphere.client;

/** Existing Smoke entry point over the shared transient material lifecycle. */
final class SmokeClientSession extends MaterialClientSession {
    SmokeClientSession() { super(AtmosphereRenderMaterial.SMOKE); }
}
