# Alpha v9 — 24-hour architecture and qualification

Owner scope:2026-10-30 Alpha v9 request, superseded for duration by the24-hour supplement. BasePR127/23ae744. Detailed plan: [implementation](superpowers/plans/2026-10-30-alpha-v9-24h.md).

One logical recording supports86,400seconds; existing16kHz monoPCM16, encrypted5secondunits, ten-minutephysicalrotation and sourceidentity stay. Aggregate2,764,800,000bytes require64-bit arithmetic. Audio bytes are never materialized as one array/file.

The v8 full-source scan per5minute window would authenticate about796GB for24hours. The successor authenticates the complete source once when opening an attempt/resume, then returns a process-local handle. Each selected range checks current source/authorization/deletion and reauthenticates its immutable encryptedunits before consumers receive ownedvalidateddata. Snapshot handles confer no deletion/readpermission afterrevocation. Disk/network/decoder work remains outside authorization callbacks. Indexedmetadata and bounded reads avoid repeated complete audio scans; scaling is measured before architecturePASS.

Transcript progression is additiveSQLCipher migration v4->v5->v6 with bounded immutablepages, partmanifests, progressfences and append-only pageedits. OldDTR1/2 formats remain readable; DTR3 adds a paged reference. Completedparts survivecancel/interruption; stale attemptresults cannotcommit. A finalmachineversion is published only when all parts arecomplete. Search/export stream pages and snapshot revisions. No replacement of the existing active-version/history mechanism.

LocalWhisper remains source-pinned and processes up to288 five-minute windows. Exact partitions preserve chronology; no heuristic removal of repeatedspeech. Boundaryquality and actual nativecoverage are distinct evidence. Existingx86 testnativebuild supports functionaltesting; admittedARM/Samsung behavior remains separatelyqualified.

Cloud uses up to48 balanced<=30minutechildren, exactLong source ranges and globaltimestamps, authenticated featuregate and persistent idempotentparent/childidentities. Android holds<=8MiB uploadwindow+64KiBpart; backend validates/decryptsstream with boundedcursor/cache. Parentaggregatecostreserved beforepaidwork, childrenconsumeallocationsonce. Firstacceptedbyte setsone24hdeadline sharedbychildren; retry/sessionrefresh neverextendsit. Live defaults remain disabled/0.25USDdaily with existing5USDceiling and unchanged20USDmonthlyAWSBudget. Softwaretests may inject explicitfakeproviderprices; they do not establishlivecapability.

Recordingbugs remain independent investigations: firstappend/backpressure requires stage/thread evidence and a minimalprovenfix; API28post-wake must compare externalUI responsiveness versusEspresso. No timeout/bufferincrease merelytohidefailure.

Qualification separates implementedmax, syntheticencryptedmax, realcapturemax, Localnative, Cloudsoftware/live and physicaldevice. Runshortrepro/regressions before300/310/1800s realcapture and native30m. Longer2/4/8/24hour physicalsessions arestaged; a full24htrial requiresavailabledevice/power/explicitpermission and doesnotblockallreleasework. No privatephone install, productionAWSchange, userdatadeletion, budgetincrease or mainmerge.

Releasev9 isconditional onrequiredverifiedlocalfunctionality, samepackage/ownersigner and preservedv8data/model/history. Securityissue125/privacyapprovals remainseparate; noarchitectureorAPKresult closes them.
