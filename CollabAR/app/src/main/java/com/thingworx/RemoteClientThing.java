package com.thingworx;

/*
* Copyright 2016 PTC Inc.
* Class adapted from Android Edge SDK by ThingWorx
*
* This class is connected to a RemoteThing on the ThingWorx server.
* It facilitates real-time server-client communication by pushing and pulling data
*
*/

import android.util.Log;

import com.main.collabar.BuildingPart;
import com.main.collabar.PartColor;
import com.main.collabar.PartContainer;
import com.main.collabar.PartSymbol;
import com.thingworx.communications.client.ConnectedThingClient;
import com.thingworx.communications.client.things.VirtualThing;
import com.thingworx.metadata.DataShapeDefinition;
import com.thingworx.metadata.FieldDefinition;
import com.thingworx.metadata.annotations.ThingworxPropertyDefinition;
import com.thingworx.metadata.annotations.ThingworxPropertyDefinitions;
import com.thingworx.metadata.annotations.ThingworxServiceDefinition;
import com.thingworx.metadata.annotations.ThingworxServiceParameter;
import com.thingworx.metadata.collections.FieldDefinitionCollection;
import com.thingworx.relationships.RelationshipTypes;
import com.thingworx.types.BaseTypes;
import com.thingworx.types.InfoTable;
import com.thingworx.types.collections.ValueCollection;
import com.thingworx.types.primitives.InfoTablePrimitive;
import com.thingworx.types.primitives.StringPrimitive;


@ThingworxPropertyDefinitions(properties = {
        @ThingworxPropertyDefinition(name="transMatrix", description="Transformation Matrix", baseType="STRING", category="", aspects={"isReadOnly:false", "defaultValue:0", "dataChangeType:ALWAYS"}),

})
public class RemoteClientThing extends VirtualThing {

    //standard matrix
    String matrix = "[1,0,0,0,0,1,0,0,0,0,1,0,0,0,-10000,1]";
    InfoTable changeTable;
    DataShapeDefinition datafields;

    public RemoteClientThing(String name, String description, ConnectedThingClient client) throws Exception {
        super(name, description, client);

        FieldDefinitionCollection fields = new FieldDefinitionCollection();
        fields.addFieldDefinition(new FieldDefinition("part", BaseTypes.STRING));
        fields.addFieldDefinition(new FieldDefinition("color", BaseTypes.STRING));
        fields.addFieldDefinition(new FieldDefinition("colorAuthor", BaseTypes.STRING));
        fields.addFieldDefinition(new FieldDefinition("symbol", BaseTypes.STRING));
        fields.addFieldDefinition(new FieldDefinition("symbolAuthor", BaseTypes.STRING));
        fields.addFieldDefinition(new FieldDefinition("comment", BaseTypes.STRING));
        fields.addFieldDefinition(new FieldDefinition("commentAuthor", BaseTypes.STRING));

        datafields = new DataShapeDefinition(fields);
        changeTable = new InfoTable(datafields);

        ValueCollection row1 = new ValueCollection();
        changeTable.addRow(row1);

        // Copy all the default values from the aspects of the properties defined above to this
        // instance. This gives it an initial state but does not push it to the server.
        initializeFromAnnotations();


    }

    /**
     * The application that binds this Thing to its connection is responsible for calling this method
     * periodically to allow this Thing to generate simulated data. If your application generates
     * data instead of simulating it, your would update your properties when new data is available
     * and then call updateSubscribedProperties() to push these values to the server. This method
     * can also be used to poll your hardware if it does not deliver its own data asynchronously.
     * @throws Exception
     *
     * In this method, the current pose matrix of the AR Client is constantly requested
     */
    @Override
    public void processScanRequest() throws Exception {
        InfoTable result = this.getClient().readProperty(RelationshipTypes.ThingworxEntityTypes.Things,
                this.getName(), "transMatrix", 10000);
        matrix = result.getFirstRow().getStringValue("transMatrix");
        updateSubscribedProperties(15000);
    }



    //parse the String which contains the matrix into a float array
    public float[] getMatrix (){
        String s = matrix;
        String[] values = s.replaceAll("\\[", "").replaceAll("\\]", "").replaceAll("\\s", "").split(",");
        float[] results = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            try {
                results[i] = Float.parseFloat(values[i]);
            } catch (NumberFormatException nfe) {
                Log.d("FORMATERROR","Not a valid float.");
            }
        }
        return results;
    }


    //Sends changes made by the user in the UI to the server. This can be done by invoking a webservice with specific variables
    public void sendChanges(BuildingPart part, String author) throws Exception{
        changeTable.getRow(0).setValue("part", new StringPrimitive(part.getName()));
        changeTable.getRow(0).setValue("color", new StringPrimitive(part.getColor().name()));
        changeTable.getRow(0).setValue("symbol", new StringPrimitive(part.getSymbol().name()));
        changeTable.getRow(0).setValue("comment", new StringPrimitive(part.getComment()));
        changeTable.getRow(0).setValue("colorAuthor", new StringPrimitive(author));
        changeTable.getRow(0).setValue("symbolAuthor", new StringPrimitive(author));
        changeTable.getRow(0).setValue("commentAuthor", new StringPrimitive(author));

        ValueCollection params = new ValueCollection();
        params.put("changedRow", new InfoTablePrimitive(changeTable));
        getClient().invokeService(RelationshipTypes.ThingworxEntityTypes.Things,
                this.getName(), "PushChanges", params, 10000);

    }

    @ThingworxServiceDefinition(name="ApplyChanges", description="Applies the changes in a building part pushed from another client")
    public void ApplyChanges(
            @ThingworxServiceParameter(name="changeRow", description="Infotable containing the part changes",
                    baseType="INFOTABLE", aspects={"dataShape:AssemblyShape"}) InfoTable changeRow) throws Exception {

        changeRow.setDataShape(datafields);
        String partName = changeRow.getFirstRow().getStringValue("part");
        BuildingPart currPart = PartContainer.getInstance().getObjByName(partName);

        try {
            currPart.setColor(PartColor.valueOf(changeRow.getFirstRow().getStringValue("color")));
            currPart.setSymbol(PartSymbol.valueOf(changeRow.getFirstRow().getStringValue("symbol")));
        }
        catch (Exception e2){
            Log.d("THING-ERROR", "Failed to set Color or Symbol. " + e2.getMessage());
        }

        currPart.setComment(changeRow.getFirstRow().getStringValue("comment"));
        currPart.setAuthors(changeRow.getFirstRow().getStringValue("colorAuthor"),
                changeRow.getFirstRow().getStringValue("symbolAuthor"), changeRow.getFirstRow().getStringValue("commentAuthor"));
        currPart.saveState();

    }

}
