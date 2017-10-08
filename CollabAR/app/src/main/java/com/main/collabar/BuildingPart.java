package com.main.collabar;

/*
* Class representing a single building part. It extends jPCT's object class "Object3D"
* Additional attributes are 3DObjects for the two planes indicating comments and symbols in a building part,
* the colour, the attributed symbol and the comment of a building part, as well as the authors of the three modifiers
* saved in a String array. The attribute PartState summarizes the current modifiers of the building part
* and saves it in a PartState object. The boolean isAR is used to determine if the building part
* has to be initialized transparent (true) or not (false)
*
* */

import android.app.Activity;

import com.threed.jpct.Loader;
import com.threed.jpct.Object3D;
import com.threed.jpct.SimpleVector;

import java.io.InputStream;

public class BuildingPart extends Object3D {

    private static final long serialVersionUID = 1L;
    private Object3D symbolPlane;
    private Object3D commentPlane;
    private PartColor color = PartColor.NONE;
    private PartSymbol symbol = PartSymbol.NONE;
    private String comment;
    private PartState state;
    private String[] authors = {"", "", ""};
    private boolean isAR;


    public BuildingPart(Object3D obj, Activity activity) {
        super(obj);
        if (activity.getClass().getSimpleName().equals("ARMain")){
            isAR = true;
        }
        else {
            isAR = false;
        }
        this.setTexture("silver");
        this.strip();
        this.build();
        this.setCollisionMode(Object3D.COLLISION_CHECK_OTHERS);
        if (isAR){
            this.setTransparency(0);
        }

        try{
            InputStream streamOBJ = activity.getAssets().open("plane.obj");
            InputStream streamMTL = activity.getAssets().open("plane.mtl");
            Object3D[] temp = Loader.loadOBJ(streamOBJ, streamMTL, 20f);
            symbolPlane =  new Object3D(Object3D.mergeAll(temp));
            commentPlane =  new Object3D(symbolPlane);
            commentPlane.setTexture("comment");

            symbolPlane.setVisibility(false);
            commentPlane.setVisibility(false);

            SimpleVector partVector = getBoxMax();
            //lifting the plane, so that the upper corner of the Object doesn't cut it (1 = 10 cm)
            partVector.z += 3;
            symbolPlane.setOrigin(partVector);
            symbolPlane.build();
            //to set the comment plane to the right of the symbol plane (1 = 10 cm)
            partVector.x += 4;
            commentPlane.setOrigin(partVector);
            commentPlane.build();

        }
        catch (Exception e){
            e.printStackTrace();
        }

        this.setColor(PartColor.NONE);
        this.setSymbol(PartSymbol.NONE);
        this.setComment("");
        this.state = new PartState(this);
    }

    public PartColor getColor() {
        return color;
    }

    public void setColor(PartColor color) {
        this.color = color;
        switch (color){
            case RED : this.setTexture("red");
                if (isAR){this.setTransparency(100);}
                break;
            case GREEN : this.setTexture("green");
                if (isAR){this.setTransparency(100);}
                break;
            case YELLOW : this.setTexture("yellow");
                if (isAR){this.setTransparency(100);}
                break;
            case NONE : if (isAR){this.setTransparency(0);}
                else{
                this.setTexture("silver");
            }
        }
    }

    public PartSymbol getSymbol() {
        return symbol;
    }

    public void setSymbol(PartSymbol symbol) {
        this.symbol = symbol;
        switch (symbol){
            case RIGHT :
                symbolPlane.setTexture("right");
                symbolPlane.setVisibility(true);
                break;
            case WRONG :
                symbolPlane.setTexture("wrong");
                symbolPlane.setVisibility(true);
                break;
            case DANGER :
                symbolPlane.setTexture("danger");
                symbolPlane.setVisibility(true);
                break;
            case EXCLAIM :
                symbolPlane.setTexture("exclaim");
                symbolPlane.setVisibility(true);
                break;
            case QUESTION :
                symbolPlane.setTexture("question");
                symbolPlane.setVisibility(true);
                break;
            case NONE :
                symbolPlane.setVisibility(false);
        }
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
        if (comment.equals("") || comment.equals(" ")){
            commentPlane.setVisibility(false);
        }
        else {
            commentPlane.setVisibility(true);
        }
    }

    //returns the vector of the upper-front-right corner of the objects bounding box
    public SimpleVector getBoxMax(){
        return new SimpleVector(getMesh().getBoundingBox()[1],
                getMesh().getBoundingBox()[3],getMesh().getBoundingBox()[5]);
    }


    public Object3D getSymbolPlane() {
        return symbolPlane;
    }

    public Object3D getCommentPlane() {
        return commentPlane;
    }

    //resets all modifiers of the buillding part (no symbol, no color, no comment)
    public void reset(){
        setComment("");
        setColor(PartColor.NONE);
        setSymbol(PartSymbol.NONE);
        if (isAR){
        this.setTransparency(100);}
        else{
            this.setTexture("silver");}
    }

    //Saves the current state of all modifiers in the building part's PartState object
    public void saveState(){
        state.setSymbol(this.getSymbol());
        state.setColor(this.getColor());
        state.setComment(this.getComment());
    }

    //restores the state of the building part to the last saved modification
    public void restoreFromLastState(){
        this.setColor(state.getColor());
        this.setSymbol(state.getSymbol());
        this.setComment(state.getComment());
    }

    public String[] getAuthors() {
        return authors;
    }

    // array[0] = Author of Color, array[1] = Author of Symbol, array[2] = Author of Comment
    public void setAuthors(String auth_col,String auth_sym,String auth_com) {
        this.authors[0] = auth_col;
        this.authors[1] = auth_sym;
        this.authors[2] = auth_com;
    }


    @Override
    public String toString() {
        String partNumber = getName().replaceAll("[a-zA-Z]","");
        return "Bauteil Nr. " + partNumber + "\n" +
                "Farbe: " + color.toString() + "\n" +
                "\t\t gesetzt von: " + getAuthors()[0] + "\n" +
                "Symbol: " + symbol.toString() + "\n" +
                "\t\t gesetzt von: " + getAuthors()[1] + "\n" +
                "Kommentar: " + comment + "\n" +
                "\t\t gesetzt von: " + getAuthors()[2];
    }

}
