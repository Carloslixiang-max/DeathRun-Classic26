package pl.mrstudios.deathrun.classic.runner;

public final class RunnerRules {
    public static final int DEFAULT_START_LIVES = 10;
    public static final float DEFAULT_JUMP_SECONDS = 10f;
    private RunnerRules() {}
    public static int startLives(int configured) {
        return configured <= 0 ? DEFAULT_START_LIVES : Math.min(configured, 1000);
    }
    public static int effectTicks(float configuredSeconds, float minimumSeconds) {
        double duration = Float.isFinite(configuredSeconds) ? configuredSeconds : 0;
        double minimum = Float.isFinite(minimumSeconds) ? minimumSeconds : DEFAULT_JUMP_SECONDS;
        return (int)Math.min(Integer.MAX_VALUE, Math.max(1, Math.ceil(Math.max(duration,minimum) * 20.0)));
    }
}
