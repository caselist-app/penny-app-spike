/*
 * Compile-only stub. Never shipped, never runs.
 *
 * Its whole job is to give the linker something named libvm_payload.so to
 * resolve AVmPayload_notifyPayloadReady against, so that the built payload
 * carries a DT_NEEDED entry for it. Microdroid supplies the real library in
 * the guest; if this one were ever packaged, the guest would load a version
 * of the call that does nothing and onPayloadReady would never fire.
 *
 * Same reasoning as stubs/android/system/virtualmachine — see build.sh.
 */
void AVmPayload_notifyPayloadReady(void) {}
