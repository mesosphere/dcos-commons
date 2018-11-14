

import sun.security.krb5.internal.ktab.KeyTab;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        List<String> arguments = Arrays.asList(args);

        if (arguments.size() == 0 ||
                arguments.contains("-h") ||
                arguments.contains("--help") ||
                arguments.contains("help")) {
            printHelp();
        }

        if (arguments.size() > 1) {
            printHelp();
        }

        File raw = new File(arguments.get(0));
        if (!raw.exists()) {
            System.out.println("Supplied file does not exist!");
            System.exit(1);
        }

        KeyTab keyTab = KeyTab.getInstance(raw);

        if (keyTab.isValid()) {
            System.out.println("This keytab is a-ok");
            System.exit(0);
        } else {
            System.out.println("Keytab not valid :(");
            System.exit(1);
        }
    }

    public static void printHelp() {
        System.out.println("Usage: keytab-validator <path to file>");
        System.exit(1);
    }
}


