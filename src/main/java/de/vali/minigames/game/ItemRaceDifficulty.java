package de.vali.minigames.game;

/** Disjoint survival item pools, or a mixture of every pool. */
public enum ItemRaceDifficulty {
    EASY("leicht", "easy"),
    NORMAL("normal", "normal"),
    HARD("schwer", "hard"),
    MIXED("gemischt", "mixed");

    private final String commandName;
    private final String englishName;

    ItemRaceDifficulty(String commandName, String englishName) {
        this.commandName = commandName;
        this.englishName = englishName;
    }

    public String commandName() {
        return commandName;
    }

    public String englishName() {
        return englishName;
    }
}
