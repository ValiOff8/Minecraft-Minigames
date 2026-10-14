package de.vali.minigames.game;

/** Disjoint sound pools, or a mixture of every pool. */
public enum SoundDifficulty {
    EASY("easy", "leicht"),
    NORMAL("normal", "normal"),
    HARD("hard", "schwer"),
    MIXED("mixed", "gemischt");

    private final String commandName;
    private final String germanName;

    SoundDifficulty(String commandName, String germanName) {
        this.commandName = commandName;
        this.germanName = germanName;
    }

    public String commandName() {
        return commandName;
    }

    public String germanName() {
        return germanName;
    }

    public boolean includes(SoundRiddle riddle) {
        return this == MIXED || riddle.difficulty() == this;
    }
}
