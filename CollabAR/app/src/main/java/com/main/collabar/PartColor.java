package com.main.collabar;


public enum PartColor {
    RED,
    GREEN,
    YELLOW,
    NONE;

    @Override
    public String toString() {
        String name = "";
        switch (ordinal()) {
            case 0:
                name = "Rot";
                break;
            case 1:
                name = "Gruen";
                break;
            case 2:
                name = "Gelb";
                break;
            case 3:
                name = "Nicht gefaerbt";
                break;
            default:
                name = "";
                break;
        }
        return name;
    }
}


