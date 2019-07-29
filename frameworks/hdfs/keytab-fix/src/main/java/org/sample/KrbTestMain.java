package org.sample;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

public class KrbTestMain {
	public static void main(String[] args) {
		try {
			String principal = "*";
			String keytab = "";
			if(args.length > 0) {
				keytab = args[0];
			} else {
				System.err.println("Required atleast 1 argument, 0 provided.");
				System.exit(1);
			}
			File keytabFile = new File(keytab);
			if (!keytabFile.exists()) {
				System.err.println("Keytab does not exists: " + keytab);
				System.exit(1);
			}
			final String[] spnegoPrincipals;
			System.out.println("Principal is: " + principal);
			System.out.println("Keytab File is: " + keytab);
			if (principal.equals("*")) {
				spnegoPrincipals = KerberosUtil.getPrincipalNames(keytab, Pattern.compile(".*/.*"));
			} else {
				spnegoPrincipals = new String[]{principal};
			}
			System.out.println("Read Number of Principals: " + spnegoPrincipals.length);
			System.out.println("All Read Principals: ");
			for(String principals: spnegoPrincipals) {
				System.out.println("\t" + principals);
			}
		} catch (IOException ex) {
			System.err.println(ex);
		}
	}
}
