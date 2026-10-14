package de.vali.minigames.game;

/** Disjoint survival item pools, or a mixture of every pool. */
public enum ItemRaceDifficulty {
    EASY("easy", "leicht"),
    NORMAL("normal", "normal"),
    HARD("hard", "schwer"),
    MIXED("mixed", "gemischt");

    private final String commandName;
    private final String germanName;

    ItemRaceDifficulty(String commandName, String germanName) {
        this.commandName = commandName;
        this.germanName = germanName;
    }

    public String commandName() {
        return commandName;
    }

    public String germanName() {
        return germanName;
    }
}
