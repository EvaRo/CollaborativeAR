package com.main.collabar;

/*
* Simple class saving the current state of a BuildingPart object
* */

class PartState {
    private PartColor color;
    private PartSymbol symbol;
    private String comment;

    public PartState(BuildingPart part){
        this.color = part.getColor();
        this.symbol = part.getSymbol();
        this.comment = part.getComment();
    }

    public PartColor getColor() {
        return color;
    }

    public void setColor(PartColor color) {
        this.color = color;
    }

    public PartSymbol getSymbol() {
        return symbol;
    }

    public void setSymbol(PartSymbol symbol) {
        this.symbol = symbol;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

}
