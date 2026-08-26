package dsk.basic;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class BasicTokenizerBenchmark {

    private static final int LINE_COUNT = 5000;

    private String source;
    private byte[] tokens;

    @Setup
    public void setup() {
        source = generateProgram(LINE_COUNT);
        tokens = BasicTokenizer.tokenizeProgram(source);
    }

    @Benchmark
    public byte[] tokenize() {
        return BasicTokenizer.tokenizeProgram(source);
    }

    @Benchmark
    public String detokenizeSpaced() {
        return BasicDetokenizer.spacedListing(tokens);
    }

    private static String generateProgram(int lines) {
        StringBuilder sb = new StringBuilder(lines * 40);
        for (int i = 1; i <= lines; i++) {
            int n = i * 10;
            sb.append(n).append(" FOR i=1 TO 10:PRINT i,i*2,i^2:NEXT:IF x>").append(n)
                    .append(" THEN GOTO ").append(n + 10).append(" ELSE x=x+1\r\n");
        }
        return sb.toString();
    }
}
