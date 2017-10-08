import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import com.thingworx.types.collections.ValueCollection;
import com.thingworx.types.primitives.StringPrimitive;
import com.threed.jpct.DeSerializer;
import com.threed.jpct.Loader;
import com.threed.jpct.Matrix;
import com.threed.jpct.Object3D;
import com.threed.jpct.SimpleVector;

public class Main {
	private static final String APPKEY = "ecdf41a0-d8f4-43a3-806b-a7bfbdf246d8";
	private static final String SERVER = "https://itmrub.cloud.thingworx.com:443";

	public static void main(String[] args) {

		Object3D[] box;

		try {
			box = loadModel(100);
			new DeSerializer().serializeArray(box, new FileOutputStream(
					"output/curiosity.ser"), true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static Object3D[] loadModel(float scale) throws IOException {
		InputStream streamModel = new FileInputStream(
				"input/curiosity.obj");
		InputStream streamMtl = new FileInputStream(
				"input/curiosity.mtl");

		Object3D[] model = Loader.loadOBJ(streamModel, streamMtl, scale);
		String name;

		for (int i = 0; i < model.length; i++) {
			model[i].setCenter(SimpleVector.ORIGIN);
			model[i].setRotationMatrix(new Matrix());
			name = "partNr" + i;
			model[i].setName(name);
			addDataRow(name);
			model[i].build();
		}
		return model;
		
	}

	private static void addDataRow(String name) {
		ValueCollection row = new ValueCollection();
		try {
			row.setValue("part", new StringPrimitive(name));
			
			String url = SERVER + "/Thingworx/Things/Assembly/Services/AddEntry?appKey=" + APPKEY;
			URL obj = new URL(url);
			
			HttpURLConnection con = (HttpURLConnection) obj.openConnection();			
			con.setRequestMethod("POST");
			con.setRequestProperty("Accept", "application/json");
			con.setRequestProperty("Content-Type", "application/json");
			con.setRequestProperty("appKey", APPKEY);
			//for POST/PUT, if request body
			con.setDoOutput(true);	
			
		    String payload = ("{\"JSONInput\":{\"part\":\"" +  name + "\",\"color\":\"NONE\",\"colorAuthor\":\"\",\"symbol\":\"NONE\",\"symbolAuthor\":\"\", \"comment\":\"\", \"commentAuthor\":\"\"}}");
			
			OutputStreamWriter out = new OutputStreamWriter(con.getOutputStream());
		    out.write(payload);
		    out.flush();
		    out.close();
		    
		    System.out.println("\nAdding Part Number : " + name);
		    int responseCode = con.getResponseCode();
		    System.out.println("Sending 'POST' request to URL : " + url);
		    System.out.println("Response Code : " + responseCode);
		    
		    String responseMessage = con.getResponseMessage();
		    System.out.println("Response Message : " + responseMessage);
			
		} catch (Exception e) {
			System.out.println("ERROR");
			e.printStackTrace();
		}
		
	}

}
