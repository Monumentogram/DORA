import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import com.monumentogram.dora.stage0.asr.i1.AsrSyntheticScoringOracle;
import com.monumentogram.dora.stage0.asr.i1.AsrSyntheticScoringOracle.*;

/** Length-prefixed UTF-8 bridge. No normalization, alignment or acoustic claim. */
public final class CampaignOracle {
    private CampaignOracle() {}
    private static String text(DataInputStream in) throws Exception {
        int n=in.readInt();
        if(n<0 || n>524288) throw new IllegalArgumentException();
        byte[] b=in.readNBytes(n);
        if(b.length!=n) throw new IllegalArgumentException();
        return new String(b,StandardCharsets.UTF_8);
    }
    private static List<String> tokens(DataInputStream in) throws Exception {
        int n=in.readInt();
        if(n<0 || n>4096) throw new IllegalArgumentException();
        List<String> out=new ArrayList<>();
        for(int i=0;i<n;i++) out.add(text(in));
        return out;
    }
    public static void main(String[] args) throws Exception {
        DataInputStream in=new DataInputStream(System.in);
        CaseInput c=new CaseInput(text(in),LanguageSlice.valueOf(text(in)),AcousticSlice.QUIET,
            tokens(in),tokens(in),tokens(in),tokens(in),List.of());
        if(in.read()!=-1) throw new IllegalArgumentException();
        EvaluationResult r=AsrSyntheticScoringOracle.evaluate(new ScoringRequest(List.of(c)));
        if(!(r instanceof EvaluationAccepted accepted)) System.exit(2);
        else System.out.println(accepted.score().canonicalJson());
    }
}
