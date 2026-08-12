/*
 * Governance-only helper for POC-RECOVERY-001 dependency evidence.
 *
 * Compile and run this file only from the Python inventory verifier with the
 * SHA-256-pinned Bouncy Castle classpath downloaded into a temporary folder.
 */

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.Security;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;

public final class OpenPgpDetachedSignatureVerifier {
    private OpenPgpDetachedSignatureVerifier() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "usage: OpenPgpDetachedSignatureVerifier <public-key> <signature> <artifact>");
        }

        Security.addProvider(new BouncyCastleProvider());
        Path publicKeyPath = Path.of(args[0]);
        Path signaturePath = Path.of(args[1]);
        Path artifactPath = Path.of(args[2]);

        PGPPublicKeyRingCollection keyRings;
        try (InputStream input = decoderStream(publicKeyPath)) {
            keyRings = new PGPPublicKeyRingCollection(input, new JcaKeyFingerprintCalculator());
        }

        PGPSignature signature;
        try (InputStream input = decoderStream(signaturePath)) {
            PGPObjectFactory objects = new PGPObjectFactory(input, new JcaKeyFingerprintCalculator());
            Object object = objects.nextObject();
            if (!(object instanceof PGPSignatureList)) {
                throw new IllegalArgumentException("detached signature does not contain a signature list");
            }
            PGPSignatureList signatures = (PGPSignatureList) object;
            if (signatures.size() != 1 || objects.nextObject() != null) {
                throw new IllegalArgumentException("expected exactly one detached OpenPGP signature");
            }
            signature = signatures.get(0);
        }

        PGPPublicKeyRing matchingRing = null;
        PGPPublicKey signingKey = null;
        Iterator<PGPPublicKeyRing> ringIterator = keyRings.getKeyRings();
        while (ringIterator.hasNext() && signingKey == null) {
            PGPPublicKeyRing ring = ringIterator.next();
            Iterator<PGPPublicKey> keyIterator = ring.getPublicKeys();
            while (keyIterator.hasNext()) {
                PGPPublicKey candidate = keyIterator.next();
                if (candidate.getKeyID() == signature.getKeyID()) {
                    matchingRing = ring;
                    signingKey = candidate;
                    break;
                }
            }
        }
        if (matchingRing == null || signingKey == null) {
            throw new IllegalArgumentException(
                    String.format(Locale.ROOT, "public key for signature key ID %016X is absent", signature.getKeyID()));
        }

        signature.init(
                new JcaPGPContentVerifierBuilderProvider().setProvider(BouncyCastleProvider.PROVIDER_NAME),
                signingKey);
        try (InputStream artifact = new BufferedInputStream(new FileInputStream(artifactPath.toFile()))) {
            byte[] buffer = new byte[1024 * 1024];
            int count;
            while ((count = artifact.read(buffer)) >= 0) {
                if (count > 0) {
                    signature.update(buffer, 0, count);
                }
            }
        }
        if (!signature.verify()) {
            throw new SecurityException("detached OpenPGP signature is cryptographically invalid");
        }

        PGPPublicKey primaryKey = matchingRing.getPublicKey();
        List<String> userIds = new ArrayList<>();
        Iterator<String> userIdIterator = primaryKey.getUserIDs();
        while (userIdIterator.hasNext()) {
            userIds.add(sanitize(userIdIterator.next()));
        }

        emit("verified", "true");
        emit("primaryFingerprint", fingerprint(primaryKey));
        emit("signingFingerprint", fingerprint(signingKey));
        emit("signingKeyId", String.format(Locale.ROOT, "%016X", signingKey.getKeyID()));
        emit("signatureCreatedUtc", Instant.ofEpochMilli(signature.getCreationTime().getTime()).toString());
        emit("publicKeyAlgorithm", Integer.toString(signature.getKeyAlgorithm()));
        emit("hashAlgorithm", PGPUtil.getDigestName(signature.getHashAlgorithm()));
        emit("primaryUserIds", String.join(" | ", userIds));
    }

    private static InputStream decoderStream(Path path) throws Exception {
        InputStream input = new BufferedInputStream(new FileInputStream(path.toFile()));
        input.mark(1);
        int first = input.read();
        input.reset();
        if (first == '-') {
            return new ArmoredInputStream(input);
        }
        return input;
    }

    private static String fingerprint(PGPPublicKey key) {
        StringBuilder result = new StringBuilder();
        for (byte value : key.getFingerprint()) {
            result.append(String.format(Locale.ROOT, "%02X", value & 0xff));
        }
        return result.toString();
    }

    private static String sanitize(String value) {
        return value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static void emit(String key, String value) {
        System.out.println(key + "\t" + value);
    }
}
