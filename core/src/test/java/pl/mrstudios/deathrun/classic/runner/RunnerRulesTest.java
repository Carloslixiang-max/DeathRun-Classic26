package pl.mrstudios.deathrun.classic.runner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RunnerRulesTest {
    @Test void startLivesAreConfigurableAndNeverStartEliminated() {
        assertEquals(10,RunnerRules.startLives(10)); assertEquals(5,RunnerRules.startLives(5));
        assertEquals(10,RunnerRules.startLives(0)); assertEquals(10,RunnerRules.startLives(-1));
    }
    @Test void jumpMinimumExtendsOldTwoSecondConfigWithoutShorteningCustomEffects() {
        assertEquals(200,RunnerRules.effectTicks(2f,10f)); assertEquals(300,RunnerRules.effectTicks(15f,10f));
        assertEquals(40,RunnerRules.effectTicks(2f,0f)); assertEquals(200,RunnerRules.effectTicks(Float.NaN,10f));
    }
    @Test void traceAnalyzerUsesTheSelectedLifeBudget() {
        var result=pl.mrstudios.deathrun.classic.playtest.PlaytestRulesAnalyzer.analyze(
                java.util.List.of("t | ROLE | player=Runner role=RUNNER lives=10 points=0"),
                java.util.List.of(1),java.util.List.of(0),1,10);
        assertTrue(result.checks().stream().filter(c->c.key().equals("runner-start-lives")).findFirst().orElseThrow().passed());
    }
}
