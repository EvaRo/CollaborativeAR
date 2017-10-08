package com.main.collabar;

public enum PartSymbol {
    RIGHT,
    WRONG,
    DANGER,
    EXCLAIM,
    QUESTION,
    NONE;

    @Override
    public String toString() {
        String name = "";
        switch (ordinal()) {
            case 0:
                name = "Korrekt";
                break;
            case 1:
                name = "Falsch";
                break;
            case 2:
                name = "Wartung";
                break;
            case 3:
                name = "Achtung";
                break;
            case 4:
                name = "Frage";
                break;
            case 5:
                name = "Kein Symbol";
                break;
            default:
                name = "";
                break;
        }
        return name;
    }
}
