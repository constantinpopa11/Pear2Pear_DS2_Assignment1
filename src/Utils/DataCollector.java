package Utils;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import au.com.bytecode.opencsv.*;

import communication.*;

public class DataCollector {

	public static void clearFiles() {
		try {
			//clear LatencySender
			FileWriter fw = new FileWriter("LatencySender.csv", false);
			PrintWriter pw = new PrintWriter(fw, false);
			pw.write("Source;Reference;Payload;Tick" + '\n');
	        pw.flush();
	        pw.close();
	        fw.close();
	        
	        //clear LatencyReceiver
	        FileWriter fw2 = new FileWriter("LatencyReceiver.csv", false);
			PrintWriter pw2 = new PrintWriter(fw2, false);
			pw2.write("Source;Reference;Payload;Tick" + '\n');
	        pw2.flush();
	        pw2.close();
	        fw2.close();
	        
	        //clear IsolatedRelays
	        FileWriter fw3 = new FileWriter("IsolatedRelays.csv", false);
			PrintWriter pw3 = new PrintWriter(fw3, false);
			pw3.write("ID;Tick" + '\n');
	        pw3.flush();
	        pw3.close();
	        fw3.close();
	        
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	public static void saveLatency(Perturbation p, double tick, String fileName) {
		
		CSVWriter writer;
		try {
			writer = new CSVWriter(new FileWriter(fileName, true), ';', '\0');
			String[] entries = {p.getSource() + "", p.getReference() + "", "payload", tick + ""};
			writer.writeNext(entries);
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void saveIsolation(int id, double tick, String fileName) {
		CSVWriter writer;
		try {
			writer = new CSVWriter(new FileWriter(fileName, true), ';', '\0');
			String[] entries = {id + "",  tick + ""};
			writer.writeNext(entries);
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
