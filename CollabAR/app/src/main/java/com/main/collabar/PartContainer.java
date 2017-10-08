package com.main.collabar;

/*
* This is the container class for all Object3D's of all building parts of an assembly. It's a simple singleton class
* */


import java.util.ArrayList;

public class PartContainer {
    private static PartContainer container = new PartContainer();
    private ArrayList<BuildingPart> list = new ArrayList<>();

    private PartContainer(){}


    public static PartContainer getInstance(){
        return container;
    }

    public BuildingPart getObjByID(int id){
        for (BuildingPart obj : list){
            if (obj.getID()  == id){
                return obj;
            }
        }
        return null;
    }

    public BuildingPart getObjByName(String name){
        for (BuildingPart obj : list){
            if (obj.getName().equals(name)){
                return obj;
            }
        }
        return null;
    }

    public void addObject (BuildingPart obj){
        list.add(obj);
    }

    public ArrayList<BuildingPart> getList(){
        ArrayList<BuildingPart> listCopy = new ArrayList<>(list);
        return listCopy;
    }

    public void resetAll(){
        for (BuildingPart p : list){
            p.reset();
        }
    }

    @Override
    public String toString() {
        String string = "PartContainer{" +
                "list =  : ";
        if (list != null) {
            for (BuildingPart obj : list) {
                string = string + " id: ";
                string = string + String.valueOf(obj.getID());
                string = string + ", ";
            }
            string = string +"}";
        }
        else{
            string = string +"empty";
        }

        return string;
    }


    public static void resetContainer() {
        container = new PartContainer();
    }
}
