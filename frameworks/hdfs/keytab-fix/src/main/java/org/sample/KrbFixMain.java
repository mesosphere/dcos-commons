package org.sample;

import java.io.File;
import java.io.IOException;

public class KrbFixMain {
	public static void main(String[] args) throws IOException {
		String keytabPath = "";
		if(args.length > 0) {
			keytabPath = args[0];
		} else {
			System.err.println("Keytab File is not specified!\nUsage: KrbFixMain file.keytab");
			System.exit(1);
		}
		File keytabFile = new File(keytabPath);
		if(!keytabFile.exists()){
			System.err.println("Keytab File does not exists: " + keytabPath);
			System.exit(1);
		}
		System.out.println("Fixing KeyTab File..."+keytabPath);
		String keytabOut = "hdfs.keytab";
		Keytab keytab = Keytab.loadKeytab(keytabFile);
		
		keytab.store(new File(keytabOut));
		System.out.println("Fixed KeyTab File is: "+keytabOut);
	}
}
