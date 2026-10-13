package de.vali.minigames.game;

/** Disjoint sound pools, or a mixture of every pool. */
public enum SoundDifficulty {
    EASY("leicht", "easy"),
    NORMAL("normal", "normal"),
    HARD("schwer", "hard"),
    MIXED("gemischt", "mixed");

    private final String commandName;
    private final String englishName;

    SoundDifficulty(String commandName, String englishName) {
        this.commandName = commandName;
        this.englishName = englishName;
    }

    public String commandName() {
        return commandName;
    }

    public String englishName() {
        return englishName;
    }

    public boolean includes(SoundRiddle riddle) {
        return this == MIXED || riddle.difficulty() == this;
    }
}
