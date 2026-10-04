# Stage 8.6 remediation execution plan

Authorized baseline: `8438de2e387d7adbbe479a1ff24c1ecd31c2395c`, same stacked draft PR99.
Two independent tracks: instrument/isolate API28, and characterize a proposed modeled battery source.
No physical acceptance, long campaign, 200 cycles, Sheet write, or production tuning.

1. Add tested streaming diagnostics in `tools/persistence_instrumentation_diagnostics.py` and integrate
   `tools/run_encrypted_persistence_device.py`. Keep full package execution, inventory and 1800s limit.
   Allowlisted bounded output, 30s heartbeat, 120s inactivity capture; diagnostic-only class/method
   batches have 600s hard limit and fresh test-package/credential lifecycle. No credential in errors.
2. Test partial status parsing, duplicate/unexpected identities, completed inventory, timeout receipts,
   deterministic partitions, cleanup on failure, and secret suppression. Keep existing parser tests.
3. Build exact predecessor/current Android sources. Run the same diagnostic runner against both on
   one explicitly verified disposable API28 emulator. Preserve every invocation and actual test order.
   Start with exact deterministic classes; split only a demonstrated problematic class. Then run full
   monolithic inventory to expose cross-class contamination. Capture before killing stalled process.
4. Fix only demonstrated defect, with regression/reproduction and API36 verification. If the failure
   does not reproduce, keep cause unknown until bounded comparable runs and final exact-SHA evidence.
5. Freeze a separate battery protocol before RB01. Six fresh runs in order RB01,RD01,RD02,RB02,RB03,RD03;
   pairs remain fixed. Same-epoch start/end modeled UID deltas, stable package identities and controls.
   Exact window feasibility is unresolved: owner clarification requested for bounded acquisition
   uncertainty; no measurement proceeds on assumed consent. Numeric repeatability criteria and printed
   quantization rules must be sealed before results. Current/percent never converted to energy.
6. Implement fail-closed parser/protocol tests. Preserve all invalid attempts. Observe AccuBattery
   session boundaries as secondary evidence. Retain hardware microWh NOT_EVALUATED.
7. Verify temporary DORA recordings by authenticated readback and delete only those experiment assets
   using product mechanisms. Preserve the existing 46 identities and metadata. Baseline saves no PCM.
8. Admit only reviewed tooling/workflow diagnostic additions through an explicit successor seal;
   historical evidence/inventory and production bytes remain frozen unless a demonstrated fix requires
   a reviewed exception. CI always uploads sanitized partial diagnostics, never raw broad logcat.
9. Exact final SHA must run all four mandatory jobs. Independent adversarial review, positive-canary
   publication audit, full 47-item report. No PASS with missing/failed API28 or unadmitted battery source.

Review focus: shared process callbacks/SQLCipher leases, orphan worker cleanup, instrumentation timeout
output loss, PIN in exception strings, package/epoch carryover, snapshot timing uncertainty, variable
numeric precision, failed-run preservation, and distinction between model estimates and hardware energy.
