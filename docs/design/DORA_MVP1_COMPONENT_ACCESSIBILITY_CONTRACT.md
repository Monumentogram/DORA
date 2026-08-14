# Dora MVP 1 — Component Accessibility Contract

Status: reusable design/engineering evidence template for `DES-A11Y-001`\
Version: 1.0\
Date: 14 August 2026\
Source snapshot: `main` `2d9eccb3b02e2a54bd4d4c3346c6622a59b93902`\
Decision boundary: `DEC-040` is **Provisional**

## 1. Purpose and authority

This contract defines the minimum accessibility annotation and evidence record required for every
Dora component before that component can pass its applicable component gate. It is subordinate to,
and cannot override, the Technical Plan, Design Spec, Product Decisions, accepted ADRs or Test
Strategy. If those sources conflict, the affected component remains blocked until the conflict is
resolved through a DEC or ADR.

Completing this template does not by itself prove that a component, flow or application is
accessible. A component passes only when the required evidence for its implemented version and
fixtures is present and passes. Missing or failed critical-component evidence is fail-closed.

The baseline is WCAG 2.2 AA-inspired Android critical-flow gates plus Android-specific semantics,
TalkBack, Switch Access, keyboard/D-pad, scaling and system-setting checks. This wording does not
claim WCAG conformance, certification or support for every device.

Source basis:

- [Technical Plan](../DORA_MVP1_TECHNICAL_PLAN.md) §§31, 34 and 35;
- [Design Spec](../DORA_MVP1_DESIGN_SPEC.md) §§4.7, 8–15, 17, 21–23, 26–27, 30–34 and 36–38;
- [Product Decisions](../DORA_MVP1_PRODUCT_DECISIONS.md) `DEC-020`, `DEC-022`–`DEC-024`,
  `DEC-026`–`DEC-034` and `DEC-038`–`DEC-041`;
- [Test Strategy](../DORA_MVP1_TEST_STRATEGY.md) `TS-ACCESSIBILITY`, Tier A and Tier C;
- [Design tokens](DORA_MVP1_DESIGN_TOKENS.json) and
  [screen inventory](DORA_MVP1_SCREEN_INVENTORY.csv).

## 2. Scope and criticality

Create one completed record from §9 for each component version that is proposed for implementation
or reuse. The record must identify the component's purpose and one of these criticality levels:

| Level | Meaning | Examples | Missing evidence |
|---|---|---|---|
| `CRITICAL` | Failure can prevent recording control, source verification, destructive-scope understanding or recovery. | Start, Pause, Resume, Stop, finalize status/action, `DoraWave` status, active-recording return, `SourceChip`, media seek alternative, Delete confirmation. | Component/flow is `BLOCKED`; no Ready for Dev or release claim. |
| `PRIMARY` | Failure blocks a primary task or hides its state, but has a documented critical-flow fallback. | Navigation destinations, review actions, editable fields, processing/error actions. | Component is not Ready for Dev until evidence passes or the approved fallback is selected. |
| `SUPPORTING` | Supplemental content or action whose omission does not block a primary task. | Noncritical metadata, optional disclosure and secondary filters. | Record the gap and owner; it cannot be promoted into a primary/critical role. |

Criticality is based on behavior, not visual prominence. A reused Material component still needs a
Dora-specific record for its labels, states, content, layout context and evidence.

## 3. Required component definition

Before visual or implementation approval, define all applicable items below.

### 3.1. Identity, purpose and state

- Stable component name, version, owner, domain purpose and criticality.
- Entry/exit conditions and the exact domain states represented.
- All applicable states: default, pressed, focused, selected, disabled, loading/busy, empty, error,
  unavailable and completed. Add domain-specific states rather than collapsing them into a generic
  spinner or color change.
- Visible and non-visual meaning for each state. Status, error, origin and selection never rely on
  color alone; use text plus an icon, shape or other non-color cue.
- A disabled critical action has an understandable reason and a reachable recovery path. Loading
  does not silently remove a critical action or imply progress that is not measured.

### 3.2. Semantic node model

Document the expected semantic tree, including for each meaningful node:

- accessible name;
- role;
- state or state description;
- value or progress description, when real and useful;
- available actions and their results;
- grouping and traversal relationship to parent and sibling nodes.

Use standard Material/Compose behavior where it represents the domain truth. Custom semantics are
required when a custom surface would otherwise lose name, role, state, value or actions.

Grouping rules:

1. Merge related static text only when the merged phrase is concise and preserves meaning.
2. Do not merge independently actionable children into a parent card or container.
3. Do not expose both a parent action and duplicate child action with the same result.
4. Decorative imagery, repeated waveform bars, dividers and texture are excluded from the semantic
   tree.
5. `clearAndSetSemantics` or an equivalent replacement is allowed only when the replacement exposes
   every user-relevant name, role, state, value and action; the replacement must be tested.
6. Dynamic content keeps a stable node identity where practical so focus is not reset on each
   update.

Every icon-only action has a stable accessible label and, where useful, a visible tooltip. Labels
describe the result (`Вернуться к записи`, `Открыть источник 10:52`) rather than the glyph
(`Микрофон`, `Стрелка`).

### 3.3. Focus, traversal and modal behavior

- Default traversal follows the Design Spec order: title → status → content → primary action →
  secondary actions, adjusted only when the component's reading order requires a documented
  alternative.
- TalkBack, Switch Access and keyboard/D-pad traversal reach every applicable action without sight
  or pointer exploration. Focus order remains stable across state updates.
- Focus has a visible indicator with at least 3:1 contrast against adjacent colors and is not hidden
  by the Dora Dock, rail, sheet, snackbar, system bars, display cutout, hinge or IME.
- Opening a modal moves initial focus to its title or first explanatory node, not automatically to a
  destructive confirmation. Traversal remains within the modal while it is active.
- Back, Escape or an equivalent dismiss action is documented. Dismiss must not trigger Start, Stop,
  Delete or another destructive action as a side effect, and may be disabled only while dismissal is
  genuinely unsafe with an announced reason.
- Closing a modal restores focus to the invoking control if it still exists. Otherwise focus moves
  to the nearest stable logical heading/status; it never drops unpredictably to the screen root.
- When asynchronous content removes the focused node, define the same deterministic restoration
  rule.

### 3.4. Actions, gestures and target geometry

- Every interactive target is at least 48 × 48 dp. Critical recording actions follow the Design
  Spec's larger 64–72 dp treatment.
- Expanded hit areas may extend beyond visual bounds only when they do not overlap, reorder or steal
  a neighboring action. Record the visual size, effective hit area and spacing behavior.
- Independent targets have at least 8 dp between them. A design that cannot preserve this spacing
  remains blocked pending a higher-precedence decision; overlapping hit areas are never accepted.
- Swipe, drag, pinch, long press, custom drawing or other gesture interaction has a visible,
  independently focusable non-gesture alternative with the same result.
- Keyboard/D-pad activation, cancellation and directional movement are specified where the
  component can appear in keyboard-capable, large-screen or multi-window contexts.
- Critical Start, Pause, Resume, Stop/finalize, source verification and Delete actions remain
  independently reachable and understandable; they are never hidden behind a gesture-only surface
  or combined into one ambiguous animated target.

### 3.5. Contrast, color and content visibility

- Normal text is at least 4.5:1; large text is at least 3:1.
- Meaningful non-text controls, boundaries and focus indicators are at least 3:1 against adjacent
  colors where required by the Design Spec.
- Measure the real component state, including actual background, opacity, overlay, disabled,
  pressed, selected, focused, error, light, dark and deep-recording variants. A token-pair result is
  not automatically evidence for every rendered state.
- Status, speaker, confidence, error, selection and progress meaning are available without hue.
- The record links the measured pair/tool/result or an equivalent reproducible calculation. Passing
  this design gate is not an external accessibility certification.

### 3.6. Font scale, reflow and localization

- At 200% font scale, primary flows contain no clipped or hidden critical content/action and require
  no horizontal content pan. Test maximum supported system font/display settings as an additional
  stress case when the component participates in a primary flow.
- Labels wrap or the layout reflows; ellipsis does not hide action, assignee, deadline, current
  status, source or destructive scope.
- Fixed horizontal structures provide an accessible reflow alternative. For example, overflowing
  conversation tabs become a dropdown/list rather than clipped tabs.
- Test RU and EN, 30–40% expansion or pseudo-localized text, long labels, mixed RU/EN names, Unicode,
  plural fixtures (`1 задача / 2 задачи / 5 задач`) and every state description used by the
  component.
- Text remains selectable where the Design Spec requires selectable transcript or summary content;
  adjacent controls do not break selection handles.

### 3.7. Adaptive layout, insets and occlusion

Record only contexts in which the component can appear:

- compact, medium and expanded width;
- compact landscape and resized/multi-window layouts;
- gesture and three-button navigation insets;
- status/navigation bars, display cutout, IME and, where relevant, fold/hinge posture.

Critical controls and focused content remain visible and reachable. Resize or rotation preserves
component state and does not change the action's meaning. Advanced foldable posture support remains
subject to `DEC-041`/D10 evidence; the contract must not claim it from static annotations alone.

### 3.8. Motion, haptics and announcements

- Respect system animator scale. At animator 0× or in the reduced-motion variant, state meaning and
  every action remain available without positional, scale or continuous-motion dependence.
- No information is motion-only, haptic-only or sound-only. No flashing or strobe behavior is
  introduced.
- Live regions announce only meaningful, actionable state changes. Repeated values, timers,
  amplitude samples, animation frames and unchanged states are not announcements.
- Announcements are concise, deduplicated and do not interrupt a user on every refresh. Record the
  trigger, exact semantic intent and suppression/debounce rule.

## 4. Special component contracts

### 4.1. `DoraWave`

- The radial bars, arcs and texture contribute no independently focusable nodes.
- The waveform exposes one stable, concise non-visual status node containing the real recording
  state and, when useful, a coarse stable signal value (`Тихо / Нормально / Громко`). It never
  claims save, quality, consent or processing state from amplitude.
- Only meaningful recording-state changes use the live region. Amplitude, bars, timer seconds and
  silence-countdown ticks are never live-region events. Coarse signal value changes are rate-limited
  to the Design Spec rule and are not announced as continuous chatter.
- Captured duration may be a separate stable, focusable text node in the containing recording
  layout, but it is not announced every second.
- `DoraWave` is not a button. Mark, Pause/Resume and Stop are separate controls with their own
  names, roles, states and targets.
- Reduced motion retains the same state/value text and actions; background rendering produces no
  semantic updates.

### 4.2. Navigation and active recording

- `DoraDock` exposes four destinations plus a separate record action; the action is not announced
  as a fifth destination.
- Selected destination, active recording and return-to-recording state use label/state plus a
  non-color cue.
- The persistent active-recording banner is announced once on appearance or meaningful state
  change, is not dismissible while capture is active and exposes a separate `Вернуться к записи`
  action.

### 4.3. Recording controls and finalize

- Start is not announced as active until the real recorder/foreground-service start succeeds.
- Pause, Resume and Stop have distinct labels, roles and state results. Stop opens confirmation;
  opening or dismissing that modal does not stop capture.
- Stop confirmation announces that recording continues, identifies the effect of finalization and
  places initial focus on explanatory content rather than the destructive action.
- Finalizing exposes truthful stages without fabricated percent. A busy state prevents duplicate
  Stop but does not silently erase status or safe navigation behavior.

### 4.4. Source verification and media control

- A source action announces target type and timestamp and remains a separate focusable action from
  its parent card or utterance.
- A seek bar exposes accessible increments and an independent timestamp-input or equivalent action;
  drag is never the only way to seek.
- Unavailable audio is a named state with an understandable alternative (for example, transcript
  context); it is not represented by disabled color alone.

### 4.5. Delete and other destructive actions

- The accessible name identifies the object and scope. Local completion and remote pending/failed
  deletion remain separate states.
- Confirmation content enumerates what is deleted and what may remain; focus does not default to
  the destructive action.
- Delete always has a visible, focusable action and cancellation path. Swipe may be an enhancement,
  never the sole action.

## 5. State and fixture matrix

For each component, mark every applicable fixture `PASS`, `FAIL`, `NOT_RUN` or `NOT_APPLICABLE` with
a rationale for `NOT_APPLICABLE`:

| Dimension | Required fixtures |
|---|---|
| State | default, pressed, focused, selected, disabled, loading/busy, empty, error, unavailable, completed, plus domain states |
| Theme | light, dark and deep-recording surface where applicable |
| Content | RU, EN, long label, 30–40% expansion/pseudo-locale, plurals, mixed Unicode/name, missing/unknown value |
| Scale | 100%, 200%, maximum supported font/display stress for primary flows |
| Window | applicable compact, landscape, medium, expanded, resize/multi-window |
| Occlusion | applicable system bars, gesture/three-button navigation, IME, cutout and hinge |
| Input | touch, TalkBack, Switch Access, keyboard/D-pad where applicable, non-gesture alternative |
| Motion | normal, reduced motion and animator 0× |
| Visual | normal/pressed/focus/disabled/error contrast and grayscale/color-vision meaning |

Synthetic fixtures are the default. This contract does not authorize real voice, meeting or
participant data.

## 6. Evidence and verdict rules

### 6.1. Tier A component gate

Before an implemented component is accepted in a Pull Request, its exact version must have:

1. completed semantic-tree and action annotations;
2. automated semantics results for names, roles, states, actions and traversal-relevant structure;
3. target-size and non-overlap evidence;
4. reproducible contrast results for applicable final states;
5. screenshot/reflow evidence for applicable theme/window/200% fixtures;
6. automated host/static/Compose checks required by the Test Strategy;
7. recorded manual-audit status, even when the truthful status is `NOT_RUN`.

`PASS` applies only to the tested component version, commit and fixtures. A changed semantic tree,
action, content model, target geometry, color/opacity, font behavior, motion or layout invalidates
the affected evidence and returns those checks to `NOT_RUN`.

### 6.2. Fail-closed rule

A `CRITICAL` component is `BLOCKED` at the current gate when any evidence required at that gate is
missing, `NOT_RUN`, expired or failed. Before Ready for Dev/merge, this includes every applicable
Tier A item. Before D9/release, it additionally includes the required manual, physical, specialist
and critical-flow evidence. Recording a later-gate field as `NOT_RUN` is truthful and does not
pretend that gate has passed; it becomes blocking as soon as that gate is reached. An exception
record may explain a temporary deviation, owner and fallback, but cannot convert an unmet critical
name/role/state/action, reachability, target, contrast or reflow gate into `PASS`. Select the
documented standard-component or simplified fallback instead.

`PRIMARY` and `SUPPORTING` exceptions require an owner, rationale, bounded scope, fallback and
expiry/review trigger. Expired exceptions fail closed. `NOT_APPLICABLE` always requires a concrete
reason tied to component behavior.

### 6.3. D9/release boundary

Tier A proves only the scoped component checks executed for the exact implementation. It does not
replace:

- end-to-end D9 critical-flow validation;
- specialist accessibility audit;
- manual physical TalkBack/Switch/keyboard RU/EN matrix;
- assistive-technology user study where recruitment is possible and separately authorized;
- Tier B/Tier C device and release gates.

No component record may say `D9 complete`, `release accessible`, `WCAG certified` or equivalent
unless those separate authoritative gates have actually completed and are linked.

## 7. Contract result vocabulary

Use only these results:

| Result | Meaning |
|---|---|
| `TEMPLATE_COMPLETE` | The reusable `DES-A11Y-001` contract exists; no component pass is implied. |
| `PASS` | All applicable Tier A evidence for the exact component/version/commit and declared fixtures passes. |
| `BLOCKED` | Required evidence is missing, not run, expired or failed, or an authoritative conflict exists. |
| `NOT_APPLICABLE` | The check cannot apply to this component behavior and a concrete rationale is recorded. |

Do not use `CERTIFIED`, `COMPLIANT` or `AUDIT_COMPLETE` for this component template.

## 8. Exit-evidence traceability for `DES-A11Y-001`

| Backlog exit evidence | Contract location |
|---|---|
| Semantics | §§3.1–3.2, 4 and 9 |
| Focus | §3.3 and §9 |
| Touch | §3.4 and §9 |
| Contrast | §3.5 and §9 |
| 200% | §3.6 and §9 |

This mapping closes the contract/template deliverable only. It does not claim that any future or
existing Dora UI component has passed the template.

## 9. Copyable component evidence template

Copy this section once per component version. Do not remove fields; use `NOT_APPLICABLE` with a
rationale or `NOT_RUN` where evidence does not exist.

```text
# Component accessibility record

recordId:
recordVersion:
recordDate/timezone:
componentName:
componentVersion:
componentCommit:
componentTreeOrArtifactDigest:
owner:
criticality: CRITICAL | PRIMARY | SUPPORTING
purpose:
entryExitContext:
relatedScreens:
authoritativeSources:
decisionStatus: DEC-040=Provisional

## States and fixtures
domainStates:
uiStates:
themes:
languagesAndContentFixtures:
fontAndDisplayScaleFixtures:
windowAndPostureFixtures:
insetImeCutoutHingeFixtures:
inputModes:
motionModes:

## Semantic node model
nodeTree:
namesRolesStatesValues:
actionsAndResults:
groupMergeClearRules:
decorativeExclusions:
liveRegionTriggers:
liveRegionSuppression:

## Focus and interaction
traversalOrder:
focusIndicatorContrast:
focusRestoration:
modalInitialFocusAndContainment:
backEscapeBehavior:
talkBackReachability:
switchAccessReachability:
keyboardDpadReachability:
gestureAlternatives:

## Geometry and visual evidence
visualSizeDp:
effectiveHitAreaDp:
targetSpacingAndOverlapResult:
textContrastEvidence:
nonTextAndFocusContrastEvidence:
colorIndependentMeaningEvidence:

## Reflow, localization and adaptive evidence
fontScale200Result:
maximumScaleStressResult:
horizontalContentPanResult:
ruEnLongLabelPluralResult:
compactMediumExpandedResult:
insetImeSystemBarsHingeResult:

## Motion and states
animator0xReducedMotionResult:
loadingEmptyErrorDisabledSelectedPressedFocusResult:
stateMeaningWithoutMotionSoundHapticResult:

## Verification
fixtureIdsAndDigests:
automatedSemanticsCommandAndResult:
automatedTierACommandAndResult:
screenshotReflowEvidence:
contrastToolAndResult:
manualTalkBackAuditStatus: NOT_RUN | PASS | FAIL
manualSwitchAuditStatus: NOT_RUN | PASS | FAIL
manualKeyboardDpadAuditStatus: NOT_RUN | PASS | FAIL | NOT_APPLICABLE
specialistAuditStatus: NOT_RUN | PASS | FAIL
physicalDeviceEvidence:

## Exceptions
exceptionId:
affectedGate:
owner:
rationale:
fallback:
expiryOrReviewTrigger:

## Verdict
tierAResult: PASS | BLOCKED
unresolvedFindings:
evidenceInvalidationTriggers:
nonClaims:
reviewerAndFormalStatus:
```

## 10. Current non-claims

At publication of version 1.0:

- no Compose/Kotlin/UI component was implemented or changed by `DES-A11Y-001`;
- no emulator or physical-device accessibility execution was performed;
- no TalkBack, Switch Access, keyboard/D-pad, specialist or assistive-technology user audit was
  completed;
- no real participant, meeting, voice or other personal data was collected;
- `POC-DATA-001` remains `BLOCKED`; only synthetic accessibility fixtures are authorized;
- no WCAG/AA certification, formal native-app conformance, D9 completion, release readiness or
  all-device support is claimed;
- `DEC-040` remains `Provisional`; `QA-A11Y-001`, D9, Tier B and Tier C remain separate gates.
