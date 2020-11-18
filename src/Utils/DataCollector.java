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
			FileWriter fw = new FileWriter("LatencySender.csv", false);
			PrintWriter pw = new PrintWriter(fw, false);
	        pw.flush();
	        pw.close();
	        fw.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	public static void saveLatency(Perturbation p, double tick) {
		
		CSVWriter writer;
		try {
			writer = new CSVWriter(new FileWriter("LatencySender.csv", true), ';', '\0');
			String[] entries = {p.getSource() + "", p.getReference() + "", "payload", tick + ""};
			writer.writeNext(entries);
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
