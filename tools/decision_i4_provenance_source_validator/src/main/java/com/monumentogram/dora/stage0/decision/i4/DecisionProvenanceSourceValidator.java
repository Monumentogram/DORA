package com.monumentogram.dora.stage0.decision.i4;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Dependency-free Decision I4 mechanics for generated source/provenance envelopes.
 *
 * <p>This is a bounded Stage 0 synthetic host fixture. It is not a product schema, external model
 * parser, governed benchmark, or candidate application path.
 */
public final class DecisionProvenanceSourceValidator {
    public static final String PROFILE_VERSION =
            "decision-i4-provenance-source-validator-stage0-v0.1";
    public static final String ENVELOPE_SCHEMA_VERSION =
            "DORA_DECISION_I4_PROVENANCE_ENVELOPE_STAGE0_V0_1";
    public static final String SOURCE_SET_VERSION =
            "DORA_DECISION_I4_GENERATED_SOURCE_SET_STAGE0_V0_1";
    public static final String RANGE_UNIT =
            "UTF8_BYTE_OFFSETS_HALF_OPEN_STAGE0_V0_1";
    public static final String CLAIM_CEILING =
            "DECISION_I4_SYNTHETIC_PROVENANCE_SOURCE_MECHANICS_EXERCISED";
    public static final int GENERATED_SOURCE_COUNT = 3;
    public static final int GENERATED_CASE_COUNT = 14;
    public static final int EXPECTED_ACCEPTED_COUNT = 3;
    public static final int EXPECTED_REJECTED_COUNT = 11;

    private static final Pattern OPAQUE_ID = Pattern.compile("[A-Z][A-Z0-9_]{0,95}");
    private static final Comparator<CandidateResult> RESULT_ORDER =
            Comparator.comparing(CandidateResult::candidateId);
    private static final List<GeneratedSource> SOURCES = createSources();
    private static final Map<String, GeneratedSource> SOURCES_BY_ID = indexSources(SOURCES);

    private DecisionProvenanceSourceValidator() {}

    public enum LanguageSlice {
        RU,
        EN,
        MIXED_RU_EN
    }

    public enum ValidationStatus {
        ACCEPTED,
        REJECTED
    }

    /** Declaration order is the canonical diagnostic order. */
    public enum Diagnostic {
        SCHEMA_VERSION_MISMATCH,
        SOURCE_VERSION_MISMATCH,
        UNKNOWN_SOURCE_ID,
        WHOLE_SOURCE_SHA256_MISMATCH,
        RANGE_NEGATIVE,
        RANGE_OUT_OF_BOUNDS,
        RANGE_REVERSED,
        RANGE_EMPTY,
        RANGE_NOT_UTF8_BOUNDARY,
        EXCERPT_SHA256_MISMATCH
    }

    public record GeneratedSource(
            String sourceId,
            LanguageSlice language,
            String text,
            String wholeSourceSha256,
            long excerptStartInclusive,
            long excerptEndExclusive,
            String excerptSha256) {
        public GeneratedSource {
            requireId(sourceId, "sourceId");
            Objects.requireNonNull(language, "language");
            Objects.requireNonNull(text, "text");
            requireDigest(wholeSourceSha256, "wholeSourceSha256");
            requireDigest(excerptSha256, "excerptSha256");
            if (text.isEmpty()) {
                throw new IllegalArgumentException("generated source text must not be empty");
            }
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            if (!sha256(bytes).equals(wholeSourceSha256)) {
                throw new IllegalArgumentException("generated whole-source digest mismatch");
            }
            if (excerptStartInclusive < 0
                    || excerptStartInclusive >= excerptEndExclusive
                    || excerptEndExclusive > bytes.length) {
                throw new IllegalArgumentException("generated excerpt range is invalid");
            }
            int start = Math.toIntExact(excerptStartInclusive);
            int end = Math.toIntExact(excerptEndExclusive);
            if (!isUtf8Boundary(bytes, start) || !isUtf8Boundary(bytes, end)) {
                throw new IllegalArgumentException("generated excerpt is not UTF-8 aligned");
            }
            if (!sha256(Arrays.copyOfRange(bytes, start, end)).equals(excerptSha256)) {
                throw new IllegalArgumentException("generated excerpt digest mismatch");
            }
        }
    }

    public record ProvenanceEnvelope(
            String candidateId,
            String schemaVersion,
            String sourceSetVersion,
            String sourceId,
            String declaredWholeSourceSha256,
            long startInclusive,
            long endExclusive,
            String declaredExcerptSha256) {
        public ProvenanceEnvelope {
            requireId(candidateId, "candidateId");
            Objects.requireNonNull(schemaVersion, "schemaVersion");
            Objects.requireNonNull(sourceSetVersion, "sourceSetVersion");
            Objects.requireNonNull(sourceId, "sourceId");
            Objects.requireNonNull(declaredWholeSourceSha256, "declaredWholeSourceSha256");
            Objects.requireNonNull(declaredExcerptSha256, "declaredExcerptSha256");
        }
    }

    public record CandidateResult(
            String candidateId, ValidationStatus status, List<Diagnostic> diagnostics) {
        public CandidateResult {
            requireId(candidateId, "candidateId");
            Objects.requireNonNull(status, "status");
            diagnostics = List.copyOf(diagnostics);
            if ((status == ValidationStatus.ACCEPTED) != diagnostics.isEmpty()) {
                throw new IllegalArgumentException("status and diagnostics disagree");
            }
        }
    }

    public record ValidationBatch(
            List<CandidateResult> results, int acceptedCount, int rejectedCount) {
        public ValidationBatch {
            results = List.copyOf(results);
            if (acceptedCount < 0
                    || rejectedCount < 0
                    || Math.addExact(acceptedCount, rejectedCount) != results.size()) {
                throw new IllegalArgumentException("validation counts disagree");
            }
        }

        /** Canonical output contains fixture identities and diagnostics, never generated text. */
        public String canonicalOutput() {
            StringBuilder output = new StringBuilder();
            output.append("profile=").append(PROFILE_VERSION).append('\n');
            output.append("envelopeSchema=").append(ENVELOPE_SCHEMA_VERSION).append('\n');
            output.append("sourceVersion=").append(SOURCE_SET_VERSION).append('\n');
            output.append("rangeUnit=").append(RANGE_UNIT).append('\n');
            output.append("sourceCount=").append(GENERATED_SOURCE_COUNT).append('\n');
            output.append("caseCount=").append(results.size()).append('\n');
            output.append("acceptedCount=").append(acceptedCount).append('\n');
            output.append("rejectedCount=").append(rejectedCount).append('\n');
            output.append("autoApply=false\n");
            output.append("stateMutation=false\n");
            output.append("pocVerdict=NOT_RUN\n");
            output.append("pocReadiness=BLOCKED_UNCHANGED\n");
            output.append("claim=").append(CLAIM_CEILING).append('\n');
            for (CandidateResult result : results) {
                output.append("case=")
                        .append(result.candidateId())
                        .append('|')
                        .append(result.status());
                for (Diagnostic diagnostic : result.diagnostics()) {
                    output.append('|').append(diagnostic);
                }
                output.append('\n');
            }
            return output.toString();
        }
    }

    /** Returns the exact immutable generated RU/EN/mixed source registry. */
    public static List<GeneratedSource> generatedSources() {
        return SOURCES;
    }

    /** Returns the exact immutable three-accept/eleven-reject generated envelope matrix. */
    public static List<ProvenanceEnvelope> generatedCases() {
        GeneratedSource ru = source("SOURCE_RU");
        GeneratedSource en = source("SOURCE_EN");
        GeneratedSource mixed = source("SOURCE_MIXED");
        ProvenanceEnvelope acceptRu = acceptedEnvelope("CASE_ACCEPT_RU", ru);
        ProvenanceEnvelope acceptEn = acceptedEnvelope("CASE_ACCEPT_EN", en);
        ProvenanceEnvelope acceptMixed = acceptedEnvelope("CASE_ACCEPT_MIXED", mixed);
        return List.of(
                acceptRu,
                acceptEn,
                acceptMixed,
                copy(
                        acceptRu,
                        "CASE_REJECT_FORGED_SOURCE_ID",
                        acceptRu.schemaVersion(),
                        acceptRu.sourceSetVersion(),
                        "SOURCE_FORGED",
                        acceptRu.declaredWholeSourceSha256(),
                        acceptRu.startInclusive(),
                        acceptRu.endExclusive(),
                        acceptRu.declaredExcerptSha256()),
                copy(
                        acceptEn,
                        "CASE_REJECT_WHOLE_SHA",
                        acceptEn.schemaVersion(),
                        acceptEn.sourceSetVersion(),
                        acceptEn.sourceId(),
                        "0".repeat(64),
                        acceptEn.startInclusive(),
                        acceptEn.endExclusive(),
                        acceptEn.declaredExcerptSha256()),
                copy(
                        acceptMixed,
                        "CASE_REJECT_EXCERPT_SHA",
                        acceptMixed.schemaVersion(),
                        acceptMixed.sourceSetVersion(),
                        acceptMixed.sourceId(),
                        acceptMixed.declaredWholeSourceSha256(),
                        acceptMixed.startInclusive(),
                        acceptMixed.endExclusive(),
                        "f".repeat(64)),
                copy(
                        acceptRu,
                        "CASE_REJECT_NEGATIVE_RANGE",
                        acceptRu.schemaVersion(),
                        acceptRu.sourceSetVersion(),
                        acceptRu.sourceId(),
                        acceptRu.declaredWholeSourceSha256(),
                        -1,
                        acceptRu.endExclusive(),
                        acceptRu.declaredExcerptSha256()),
                copy(
                        acceptEn,
                        "CASE_REJECT_OUT_OF_RANGE",
                        acceptEn.schemaVersion(),
                        acceptEn.sourceSetVersion(),
                        acceptEn.sourceId(),
                        acceptEn.declaredWholeSourceSha256(),
                        acceptEn.startInclusive(),
                        en.text().getBytes(StandardCharsets.UTF_8).length + 1L,
                        acceptEn.declaredExcerptSha256()),
                copy(
                        acceptMixed,
                        "CASE_REJECT_REVERSED_RANGE",
                        acceptMixed.schemaVersion(),
                        acceptMixed.sourceSetVersion(),
                        acceptMixed.sourceId(),
                        acceptMixed.declaredWholeSourceSha256(),
                        acceptMixed.endExclusive(),
                        acceptMixed.startInclusive(),
                        acceptMixed.declaredExcerptSha256()),
                copy(
                        acceptEn,
                        "CASE_REJECT_EMPTY_RANGE",
                        acceptEn.schemaVersion(),
                        acceptEn.sourceSetVersion(),
                        acceptEn.sourceId(),
                        acceptEn.declaredWholeSourceSha256(),
                        acceptEn.startInclusive(),
                        acceptEn.startInclusive(),
                        acceptEn.declaredExcerptSha256()),
                copy(
                        acceptRu,
                        "CASE_REJECT_MID_UTF8_START",
                        acceptRu.schemaVersion(),
                        acceptRu.sourceSetVersion(),
                        acceptRu.sourceId(),
                        acceptRu.declaredWholeSourceSha256(),
                        Math.addExact(acceptRu.startInclusive(), 1L),
                        acceptRu.endExclusive(),
                        acceptRu.declaredExcerptSha256()),
                copy(
                        acceptRu,
                        "CASE_REJECT_MID_UTF8_END",
                        acceptRu.schemaVersion(),
                        acceptRu.sourceSetVersion(),
                        acceptRu.sourceId(),
                        acceptRu.declaredWholeSourceSha256(),
                        acceptRu.startInclusive(),
                        Math.subtractExact(acceptRu.endExclusive(), 1L),
                        acceptRu.declaredExcerptSha256()),
                copy(
                        acceptMixed,
                        "CASE_REJECT_SCHEMA_VERSION",
                        "DORA_DECISION_I4_PROVENANCE_ENVELOPE_STAGE0_V0_2",
                        acceptMixed.sourceSetVersion(),
                        acceptMixed.sourceId(),
                        acceptMixed.declaredWholeSourceSha256(),
                        acceptMixed.startInclusive(),
                        acceptMixed.endExclusive(),
                        acceptMixed.declaredExcerptSha256()),
                copy(
                        acceptMixed,
                        "CASE_REJECT_SOURCE_VERSION",
                        acceptMixed.schemaVersion(),
                        "DORA_DECISION_I4_GENERATED_SOURCE_SET_STAGE0_V0_2",
                        acceptMixed.sourceId(),
                        acceptMixed.declaredWholeSourceSha256(),
                        acceptMixed.startInclusive(),
                        acceptMixed.endExclusive(),
                        acceptMixed.declaredExcerptSha256()));
    }

    /** Validates envelopes without mutating or applying any candidate. */
    public static ValidationBatch validate(List<ProvenanceEnvelope> envelopes) {
        Objects.requireNonNull(envelopes, "envelopes");
        Set<String> candidateIds = new HashSet<>();
        List<CandidateResult> results = new ArrayList<>(envelopes.size());
        int accepted = 0;
        for (ProvenanceEnvelope envelope : envelopes) {
            Objects.requireNonNull(envelope, "envelope");
            if (!candidateIds.add(envelope.candidateId())) {
                throw new IllegalArgumentException("duplicate candidateId");
            }
            List<Diagnostic> diagnostics = validateEnvelope(envelope);
            ValidationStatus status =
                    diagnostics.isEmpty()
                            ? ValidationStatus.ACCEPTED
                            : ValidationStatus.REJECTED;
            if (status == ValidationStatus.ACCEPTED) {
                accepted = Math.addExact(accepted, 1);
            }
            results.add(new CandidateResult(envelope.candidateId(), status, diagnostics));
        }
        results.sort(RESULT_ORDER);
        return new ValidationBatch(results, accepted, Math.subtractExact(results.size(), accepted));
    }

    /** Runs only the exact generated fixture matrix and verifies its frozen cardinality. */
    public static ValidationBatch runGeneratedCaseMatrix() {
        List<ProvenanceEnvelope> cases = generatedCases();
        if (cases.size() != GENERATED_CASE_COUNT) {
            throw new IllegalStateException("generated case count drifted");
        }
        ValidationBatch result = validate(cases);
        if (result.acceptedCount() != EXPECTED_ACCEPTED_COUNT
                || result.rejectedCount() != EXPECTED_REJECTED_COUNT) {
            throw new IllegalStateException("generated outcome counts drifted");
        }
        return result;
    }

    public static String sha256Utf8(String value) {
        Objects.requireNonNull(value, "value");
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static List<Diagnostic> validateEnvelope(ProvenanceEnvelope envelope) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (!ENVELOPE_SCHEMA_VERSION.equals(envelope.schemaVersion())) {
            diagnostics.add(Diagnostic.SCHEMA_VERSION_MISMATCH);
        }
        if (!SOURCE_SET_VERSION.equals(envelope.sourceSetVersion())) {
            diagnostics.add(Diagnostic.SOURCE_VERSION_MISMATCH);
        }
        GeneratedSource source = SOURCES_BY_ID.get(envelope.sourceId());
        if (source == null) {
            diagnostics.add(Diagnostic.UNKNOWN_SOURCE_ID);
            return List.copyOf(diagnostics);
        }
        if (!source.wholeSourceSha256().equals(envelope.declaredWholeSourceSha256())) {
            diagnostics.add(Diagnostic.WHOLE_SOURCE_SHA256_MISMATCH);
        }

        byte[] sourceBytes = source.text().getBytes(StandardCharsets.UTF_8);
        long start = envelope.startInclusive();
        long end = envelope.endExclusive();
        boolean rangeValid = false;
        if (start < 0 || end < 0) {
            diagnostics.add(Diagnostic.RANGE_NEGATIVE);
        } else if (start > end) {
            diagnostics.add(Diagnostic.RANGE_REVERSED);
        } else if (start == end) {
            diagnostics.add(Diagnostic.RANGE_EMPTY);
        } else if (start > sourceBytes.length || end > sourceBytes.length) {
            diagnostics.add(Diagnostic.RANGE_OUT_OF_BOUNDS);
        } else {
            int startIndex = Math.toIntExact(start);
            int endIndex = Math.toIntExact(end);
            if (!isUtf8Boundary(sourceBytes, startIndex)
                    || !isUtf8Boundary(sourceBytes, endIndex)) {
                diagnostics.add(Diagnostic.RANGE_NOT_UTF8_BOUNDARY);
            } else {
                rangeValid = true;
            }
        }
        if (rangeValid) {
            String actualExcerptSha256 =
                    sha256(
                            Arrays.copyOfRange(
                                    sourceBytes,
                                    Math.toIntExact(start),
                                    Math.toIntExact(end)));
            if (!actualExcerptSha256.equals(envelope.declaredExcerptSha256())) {
                diagnostics.add(Diagnostic.EXCERPT_SHA256_MISMATCH);
            }
        }
        diagnostics.sort(Comparator.comparingInt(Enum::ordinal));
        return List.copyOf(diagnostics);
    }

    private static List<GeneratedSource> createSources() {
        GeneratedSource ru =
                generatedSource(
                        "SOURCE_RU",
                        LanguageSlice.RU,
                        "\u0421\u0418\u041d\u0422\u0415\u0422\u0418\u041a\u0410: ",
                        "\u0440\u0435\u0448\u0435\u043d\u0438\u0435",
                        " \u043f\u043e\u0441\u043b\u0435 \u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0438.");
        GeneratedSource en =
                generatedSource(
                        "SOURCE_EN",
                        LanguageSlice.EN,
                        "SYNTHETIC: ",
                        "generated_en_excerpt",
                        " after review.");
        GeneratedSource mixed =
                generatedSource(
                        "SOURCE_MIXED",
                        LanguageSlice.MIXED_RU_EN,
                        "SYNTHETIC: ",
                        "\u0440\u0435\u0448\u0435\u043d\u0438\u0435",
                        " accepted after review.");
        return List.of(ru, en, mixed);
    }

    private static GeneratedSource generatedSource(
            String sourceId,
            LanguageSlice language,
            String prefix,
            String excerpt,
            String suffix) {
        String text = prefix + excerpt + suffix;
        long start = prefix.getBytes(StandardCharsets.UTF_8).length;
        long end = Math.addExact(start, excerpt.getBytes(StandardCharsets.UTF_8).length);
        return new GeneratedSource(
                sourceId,
                language,
                text,
                sha256Utf8(text),
                start,
                end,
                sha256Utf8(excerpt));
    }

    private static Map<String, GeneratedSource> indexSources(List<GeneratedSource> sources) {
        Map<String, GeneratedSource> result = new HashMap<>();
        for (GeneratedSource source : sources) {
            if (result.putIfAbsent(source.sourceId(), source) != null) {
                throw new IllegalStateException("duplicate generated source ID");
            }
        }
        if (result.size() != GENERATED_SOURCE_COUNT) {
            throw new IllegalStateException("generated source count drifted");
        }
        return Collections.unmodifiableMap(result);
    }

    private static GeneratedSource source(String sourceId) {
        GeneratedSource source = SOURCES_BY_ID.get(sourceId);
        if (source == null) {
            throw new IllegalStateException("missing generated source");
        }
        return source;
    }

    private static ProvenanceEnvelope acceptedEnvelope(
            String candidateId, GeneratedSource source) {
        return new ProvenanceEnvelope(
                candidateId,
                ENVELOPE_SCHEMA_VERSION,
                SOURCE_SET_VERSION,
                source.sourceId(),
                source.wholeSourceSha256(),
                source.excerptStartInclusive(),
                source.excerptEndExclusive(),
                source.excerptSha256());
    }

    private static ProvenanceEnvelope copy(
            ProvenanceEnvelope ignored,
            String candidateId,
            String schemaVersion,
            String sourceSetVersion,
            String sourceId,
            String declaredWholeSourceSha256,
            long startInclusive,
            long endExclusive,
            String declaredExcerptSha256) {
        Objects.requireNonNull(ignored, "ignored");
        return new ProvenanceEnvelope(
                candidateId,
                schemaVersion,
                sourceSetVersion,
                sourceId,
                declaredWholeSourceSha256,
                startInclusive,
                endExclusive,
                declaredExcerptSha256);
    }

    private static boolean isUtf8Boundary(byte[] bytes, int offset) {
        return offset == 0 || offset == bytes.length || (bytes[offset] & 0xC0) != 0x80;
    }

    private static void requireId(String value, String name) {
        if (value == null || !OPAQUE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be an opaque ASCII ID");
        }
    }

    private static void requireDigest(String value, String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Java 17 must provide SHA-256", impossible);
        }
    }
}
